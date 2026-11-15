package com.volumecontrol.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.ServicePolicy
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.engine.ShizukuStatus

/** Liga o serviço de fundo quando (e só quando) ele tem trabalho a fazer. */
object ServiceController {
    private const val TAG = "ServiceController"

    fun ensureRunning(context: Context) {
        val should = ServicePolicy.shouldRun(
            enabled = Settings.enabled.value,
            ready = ShizukuMonitor.status.value == ShizukuStatus.READY,
            hasTouched = Settings.touched().isNotEmpty(),
            floatingActive = Settings.floatingEnabled.value && OverlayState.canDrawOverlays(context),
        )
        if (!should) return
        start(context, null)
    }

    /** Pede ao serviço para mostrar o painel (liga o serviço antes, se preciso). Retorna false se o Android recusar. */
    fun openPanel(context: Context): Boolean = start(context, VolumeService.ACTION_OPEN_PANEL)

    private fun start(context: Context, action: String?): Boolean = try {
        val intent = Intent(context, VolumeService::class.java)
        if (action != null) intent.action = action
        ContextCompat.startForegroundService(context, intent)
        true
    } catch (t: Throwable) {
        // Ex.: o Android 14+ recusa iniciar serviço em primeiro plano vindo do segundo plano.
        Log.w(TAG, "Não foi possível iniciar o serviço agora", t)
        false
    }
}
