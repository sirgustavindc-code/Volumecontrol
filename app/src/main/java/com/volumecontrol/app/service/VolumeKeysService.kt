package com.volumecontrol.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.PlaybackState
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.engine.ShizukuStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Serviço de acessibilidade com duas funções, sem ler o conteúdo da tela:
 *  1. Saber qual app está em primeiro plano (evento de troca de janela).
 *  2. Capturar as teclas de volume: se o app em primeiro plano está tocando áudio, as teclas mudam
 *     só o volume dele (5% por toque). Caso contrário, as teclas funcionam normalmente (volume do sistema).
 */
class VolumeKeysService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPkg: String? = null
    private var ignored: Set<String> = emptySet()
    private var pill: TextView? = null
    private val hidePill = Runnable { removePill() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedFlow.value = true
        // Teclado, barra do sistema e diálogos do sistema não contam como "app em uso".
        val imePackages = try {
            getSystemService(InputMethodManager::class.java)
                .enabledInputMethodList.map { it.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        ignored = (imePackages + listOf("com.android.systemui", "android", packageName)).toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in ignored) foregroundPkg = pkg
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (!Settings.keysEnabled.value || !Settings.enabled.value) return false
        if (ShizukuMonitor.status.value != ShizukuStatus.READY) return false
        if (!getSystemService(PowerManager::class.java).isInteractive) return false

        val pkg = foregroundPkg ?: return false
        // Só assume as teclas se o app da tela está mesmo tocando áudio.
        if (!PlaybackState.playing.value.contains(pkg)) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            val delta = if (code == KeyEvent.KEYCODE_VOLUME_UP) STEP else -STEP
            val current = Settings.volumes.value[pkg] ?: 100
            val next = (current + delta).coerceIn(0, 100)
            Settings.setVolume(pkg, next, persist = true)
            showPill("${labelOf(pkg)}  $next%")
        }
        return true // consome o toque (ação DOWN e UP) para o sistema não mexer no volume geral
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connectedFlow.value = false
        handler.removeCallbacks(hidePill)
        removePill()
        return super.onUnbind(intent)
    }

    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /** Aviso pequeno no topo da tela (janela de acessibilidade, não exige permissão extra). */
    private fun showPill(text: String) {
        val wm = getSystemService(WindowManager::class.java)
        val view = pill ?: TextView(this).also { tv ->
            val density = resources.displayMetrics.density
            tv.setTextColor(Color.WHITE)
            tv.textSize = 16f
            tv.setPadding((20 * density).toInt(), (10 * density).toInt(), (20 * density).toInt(), (10 * density).toInt())
            tv.background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6171A1F"))
                cornerRadius = 40 * density
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (96 * density).toInt()
            }
            try {
                wm.addView(tv, lp)
                pill = tv
            } catch (e: Exception) {
                return
            }
        }
        view.text = text
        handler.removeCallbacks(hidePill)
        handler.postDelayed(hidePill, 1500)
    }

    private fun removePill() {
        pill?.let {
            try {
                getSystemService(WindowManager::class.java).removeView(it)
            } catch (e: Exception) {
                // já removido
            }
        }
        pill = null
    }

    companion object {
        private const val STEP = 5
        private val connectedFlow = MutableStateFlow(false)
        /** true enquanto o serviço de acessibilidade está ligado nas configurações do celular. */
        val connected: StateFlow<Boolean> = connectedFlow.asStateFlow()
    }
}
