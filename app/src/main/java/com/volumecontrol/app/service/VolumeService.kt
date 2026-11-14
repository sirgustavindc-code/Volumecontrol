package com.volumecontrol.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
 * Serviço em primeiro plano que reaplica os volumes sempre que algo muda:
 * um app começa/pára de tocar, o usuário mexe num controle, ou o Shizuku muda de estado.
 * Também reaplica a cada 2 s, caso um app redefina o próprio volume.
 */
class VolumeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trigger = Channel<Unit>(Channel.CONFLATED)
    private lateinit var engine: AudioEngine
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = AudioEngine(this)
        createChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

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
        scope.launch {
            while (isActive) {
                delay(REAPPLY_MS)
                trigger.trySend(Unit)
            }
        }
        scope.launch { runLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        trigger.trySend(Unit)
        return START_NOT_STICKY
    }

    private suspend fun runLoop() {
        for (event in trigger) {
            val enabled = Settings.enabled.value
            val ready = ShizukuMonitor.status.value == ShizukuStatus.READY

            if (!enabled) {
                // Devolve tudo a 100% e encerra o serviço.
                if (ready) engine.apply(emptyMap(), false)
                PlaybackState.clear()
                stopSelf()
                break
            }
            if (!ready) {
                PlaybackState.clear()
                continue
            }
            val result = engine.apply(Settings.volumes.value, true)
            if (result != null) {
                PlaybackState.update(result.playing, result.unsupported)
            }
        }
    }

    override fun onDestroy() {
        playbackCallback?.let { getSystemService(AudioManager::class.java).unregisterAudioPlaybackCallback(it) }
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

    private fun buildNotification(): Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "volume_control"
        const val NOTIF_ID = 1
        const val REAPPLY_MS = 2000L
    }
}
