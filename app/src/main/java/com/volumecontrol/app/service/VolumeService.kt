package com.volumecontrol.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.volumecontrol.app.MainActivity
import com.volumecontrol.app.R
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.AudioEngine
import com.volumecontrol.app.engine.PlaybackState
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.engine.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano. Faz duas coisas:
 *
 * 1. Motor: reaplica os volumes sempre que algo muda (um app começa/pára de tocar, o usuário mexe
 *    num controle, o Shizuku muda de estado) e a cada 2 s enquanto o Shizuku está pronto.
 * 2. Painel flutuante: mantém a notificação com o botão "Abrir painel" e é dono da janela do painel.
 *
 * Tem uma única notificação. O serviço fica ligado enquanto o painel flutuante estiver ativo OU o
 * motor tiver trabalho (ver [com.volumecontrol.app.engine.ServicePolicy]).
 */
class VolumeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val trigger = Channel<Unit>(Channel.CONFLATED)
    private lateinit var engine: AudioEngine
    private lateinit var panel: FloatingPanelController
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = AudioEngine(this)
        panel = FloatingPanelController(this, uiScope)
        createChannel()
        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        // API pública: avisa quando algum app começa ou pára de tocar.
        val audioManager = getSystemService(AudioManager::class.java)
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                trigger.trySend(Unit)
            }
        }
        playbackCallback = callback
        audioManager.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))

        scope.launch { Settings.volumes.collect { trigger.trySend(Unit) } }
        scope.launch { Settings.enabled.collect { trigger.trySend(Unit) } }
        scope.launch { ShizukuMonitor.status.collect { trigger.trySend(Unit) } }
        uiScope.launch { Settings.floatingEnabled.collect { onFloatingChanged() } }
        scope.launch {
            while (isActive) {
                delay(REAPPLY_MS)
                if (ShizukuMonitor.status.value == ShizukuStatus.READY) trigger.trySend(Unit)
            }
        }
        scope.launch { runLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_OPEN_PANEL -> openPanel()
            ACTION_STOP_FLOATING -> Settings.setFloatingEnabled(false)
            // Android 14+ deixa o usuário dispensar a notificação de um serviço. Abrir o app (que
            // reinicia o serviço) a coloca de volta.
            else -> refreshNotification()
        }
        trigger.trySend(Unit)
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        panel.onConfigurationChanged()
    }

    private fun openPanel() {
        if (!floatingActive()) {
            Toast.makeText(this, R.string.panel_needs_permission, Toast.LENGTH_LONG).show()
            return
        }
        if (!panel.show()) {
            Toast.makeText(this, R.string.panel_failed, Toast.LENGTH_LONG).show()
        }
    }

    /** O painel foi ligado ou desligado nas configurações. */
    private fun onFloatingChanged() {
        if (!floatingActive()) panel.hide()
        refreshNotification()
        trigger.trySend(Unit)
    }

    /** Painel ligado pelo usuário E permissão "Sobrepor a outros apps" concedida (consulta ao vivo). */
    private fun floatingActive(): Boolean =
        Settings.floatingEnabled.value && OverlayState.canDrawOverlays(this)

    private suspend fun runLoop() {
        for (tick in trigger) {
            val enabled = Settings.enabled.value
            val ready = ShizukuMonitor.status.value == ShizukuStatus.READY

            if (!enabled) {
                // Devolve a 100% o que foi alterado. Sem Shizuku não dá para restaurar agora: a lista
                // de alterados fica gravada e a restauração acontece quando ele estiver pronto.
                if (ready && Settings.touched().isNotEmpty()) engine.apply(emptyMap(), false)
                PlaybackState.clear()
                // O painel flutuante mantém o serviço vivo mesmo com o controle desligado.
                if (!floatingActive()) {
                    stopSelf()
                    break
                }
                continue
            }
            if (!ready) {
                PlaybackState.clear()
                continue
            }
            when (val outcome = engine.apply(Settings.volumes.value, true)) {
                is AudioEngine.Outcome.Ok -> PlaybackState.update(outcome.playing, outcome.unsupported)
                is AudioEngine.Outcome.Failed -> PlaybackState.fail(outcome.problem)
            }
        }
    }

    override fun onDestroy() {
        playbackCallback?.let { getSystemService(AudioManager::class.java).unregisterAudioPlaybackCallback(it) }
        panel.hide()
        uiScope.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun serviceIntent(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, VolumeService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildNotification(): Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val floating = floatingActive()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(if (floating) R.string.notif_text_floating else R.string.notif_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Sem isto o Android 12+ pode atrasar até 10 s a aparição da notificação do serviço.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (floating) {
            builder.addAction(0, getString(R.string.action_open_panel), serviceIntent(REQ_OPEN, ACTION_OPEN_PANEL))
            builder.addAction(0, getString(R.string.action_stop_panel), serviceIntent(REQ_STOP, ACTION_STOP_FLOATING))
        }
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "volume_control"
        const val ACTION_OPEN_PANEL = "com.volumecontrol.app.action.OPEN_PANEL"
        const val ACTION_STOP_FLOATING = "com.volumecontrol.app.action.STOP_FLOATING"
        private const val NOTIF_ID = 1
        private const val REQ_OPEN = 1
        private const val REQ_STOP = 2
        private const val REAPPLY_MS = 2000L
    }
}
