package com.volumecontrol.app.service

import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado das duas permissões de que o painel flutuante depende, para a interface mostrar o que falta:
 *  - "Sobrepor a outros apps" (SYSTEM_ALERT_WINDOW): sem ela o Android não deixa desenhar por cima.
 *  - Notificações: sem elas não existe o botão "Abrir painel".
 * Atualizado ao abrir o app e ao voltar das telas de configuração.
 */
object OverlayState {
    private val _canDraw = MutableStateFlow(false)
    val canDraw: StateFlow<Boolean> = _canDraw.asStateFlow()

    private val _notificationsOk = MutableStateFlow(true)
    val notificationsOk: StateFlow<Boolean> = _notificationsOk.asStateFlow()

    private val _resumes = MutableStateFlow(0)
    /** Aumenta a cada vez que o app volta para a tela (para reagir ao retorno das configurações). */
    val resumes: StateFlow<Int> = _resumes.asStateFlow()

    fun refresh(context: Context) {
        _canDraw.value = canDrawOverlays(context)
        _notificationsOk.value = notificationsAllowed(context)
        _resumes.value = _resumes.value + 1
    }

    /** Consulta ao vivo (usada pelo serviço, que não pode confiar num valor antigo). */
    fun canDrawOverlays(context: Context): Boolean =
        android.provider.Settings.canDrawOverlays(context)

    private fun notificationsAllowed(context: Context): Boolean = try {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) {
            false
        } else {
            val channel = nm.getNotificationChannel(VolumeService.CHANNEL_ID)
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        }
    } catch (t: Throwable) {
        true
    }
}
