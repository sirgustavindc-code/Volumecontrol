package com.volumecontrol.app.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
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
import com.volumecontrol.app.engine.KeyRules
import com.volumecontrol.app.engine.PlaybackState
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.engine.ShizukuStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Serviço de acessibilidade OPCIONAL (o app funciona sem ele, pelos controles da lista e pelo
 * painel rápido). Faz só duas coisas:
 *  1. Descobre qual app está em primeiro plano (apenas o nome do pacote, no evento de troca de janela).
 *  2. Captura as teclas de volume: se esse app está tocando, as teclas mudam só o volume dele.
 *
 * Não lê o conteúdo da tela (canRetrieveWindowContent = false), não guarda nem envia nada.
 * Limitação da API do Android: ao pedir os eventos de teclas físicas, o serviço recebe TODAS as
 * teclas físicas; o código descarta imediatamente tudo que não for volume + / volume -.
 *
 * As teclas passam direto para o Android (sem interferência) quando: a opção está desligada, o
 * Shizuku não está pronto, a tela está bloqueada/apagada, há ligação ou chamada em andamento
 * (modo de áudio diferente do normal), o app em uso não está tocando, ou o app já está em 100%
 * e é um toque novo em "volume +" (assim o volume geral continua acessível).
 */
class VolumeKeysService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPkg: String? = null
    private var ignored: Set<String> = emptySet()
    private var pill: TextView? = null
    private val hidePill = Runnable { removePill() }

    // Teclas cujo "pressionar" consumimos: o "soltar" correspondente também precisa ser consumido.
    private val consumedKeys = HashSet<Int>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connectedFlow.value = true
        // Teclado, barra do sistema e painel lateral não contam como "app em uso".
        val imePackages = try {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        ignored = (imePackages + listOf(
            "com.android.systemui",
            "android",
            packageName,
            "com.samsung.android.app.cocktailbarservice", // painel lateral da Samsung (nome não verificado)
        )).toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in ignored) foregroundPkg = pkg
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        // Qualquer outra tecla: devolve na hora, sem olhar, guardar ou registrar.
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        if (event.action == KeyEvent.ACTION_UP) return consumedKeys.remove(code)

        val pkg = activeTargetPackage() ?: return false
        val up = code == KeyEvent.KEYCODE_VOLUME_UP
        val current = Settings.volumes.value[pkg] ?: 100
        val decision = KeyRules.decide(up, current, event.repeatCount)
        if (!decision.consume) return false

        consumedKeys.add(code)
        if (decision.newPercent != current) Settings.setVolume(pkg, decision.newPercent, persist = true)
        showPill("${labelOf(pkg)}  ${decision.newPercent}%")
        return true
    }

    /** O app que deve receber as teclas agora, ou null para deixar o Android cuidar. */
    private fun activeTargetPackage(): String? {
        if (!Settings.keysEnabled.value || !Settings.enabled.value) return null
        if (ShizukuMonitor.status.value != ShizukuStatus.READY) return null
        if (!getSystemService(PowerManager::class.java).isInteractive) return null
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return null
        if (getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL) return null
        val pkg = foregroundPkg ?: return null
        return if (PlaybackState.playing.value.contains(pkg)) pkg else null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        connectedFlow.value = false
        consumedKeys.clear()
        handler.removeCallbacks(hidePill)
        removePill()
        return super.onUnbind(intent)
    }

    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /** Aviso pequeno no topo da tela (janela de acessibilidade; não exige permissão extra). */
    private fun showPill(text: String) {
        val view = pill ?: createPill() ?: return
        view.text = text
        handler.removeCallbacks(hidePill)
        handler.postDelayed(hidePill, 1500)
    }

    private fun createPill(): TextView? {
        val density = resources.displayMetrics.density
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding((20 * density).toInt(), (10 * density).toInt(), (20 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6171A1F"))
                cornerRadius = 40 * density
            }
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
        return try {
            getSystemService(WindowManager::class.java).addView(tv, lp)
            pill = tv
            tv
        } catch (e: Exception) {
            null
        }
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
        @Volatile private var instance: VolumeKeysService? = null
        private val connectedFlow = MutableStateFlow(false)

        /** true enquanto o serviço de acessibilidade está ligado nas configurações do celular. */
        val connected: StateFlow<Boolean> = connectedFlow.asStateFlow()

        /** Desliga o serviço de acessibilidade por completo (ele deixa de receber qualquer evento). */
        fun disable() {
            try {
                instance?.disableSelf()
            } catch (e: Exception) {
                // o usuário ainda pode desligar em Configurações > Acessibilidade
            }
        }
    }
}
