package com.volumecontrol.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.volumecontrol.app.MainActivity
import com.volumecontrol.app.data.AppRepository
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.PanelRules
import com.volumecontrol.app.ui.FloatingPanelContent
import com.volumecontrol.app.ui.PanelHost
import com.volumecontrol.app.ui.PanelUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Janela flutuante do painel, desenhada por cima de outros apps com o mecanismo oficial do Android:
 * permissão SYSTEM_ALERT_WINDOW (que o usuário concede) + janela TYPE_APPLICATION_OVERLAY.
 *
 * Quem a controla é o [VolumeService] (serviço em primeiro plano). Todos os métodos devem ser
 * chamados na thread principal.
 *
 * A janela só ocupa o tamanho do painel (WRAP_CONTENT) e usa FLAG_NOT_TOUCH_MODAL: toques fora
 * dela continuam indo normalmente para o jogo ou app que está por baixo. Ela não toma o foco
 * (FLAG_NOT_FOCUSABLE), então o teclado e as teclas do aparelho seguem com o app de baixo.
 * Por isso o painel não tem campo de texto.
 */
class FloatingPanelController(
    private val service: Service,
    private val scope: CoroutineScope,
) : PanelHost {

    private val wm: WindowManager = service.getSystemService(WindowManager::class.java)
    private val ui = PanelUiState()

    private var view: ComposeView? = null
    private var owner: PanelOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var loadJob: Job? = null

    private var dragRawX = 0f
    private var dragRawY = 0f
    private var dragOriginX = 0
    private var dragOriginY = 0

    val isShowing: Boolean get() = view != null

    /** Mostra o painel (ou, se já estiver na tela, expande se estava minimizado). Retorna false se falhar. */
    fun show(): Boolean {
        if (view != null) {
            ui.minimized = false
            return true
        }
        updateMetrics()

        val density = service.resources.displayMetrics.density
        val bounds = screenBounds()
        val widthPx = (ui.widthDp * density).roundToInt()
        val saved = Settings.panelPosition()
        val start = if (saved != null) {
            PanelRules.clamp(saved.first, saved.second, widthPx, 1, bounds.width(), bounds.height())
        } else {
            PanelRules.defaultPosition(widthPx, bounds.width(), (12 * density).roundToInt(), (72 * density).roundToInt())
        }

        ui.minimized = false
        ui.expanded = false
        ui.locked = Settings.panelLocked.value

        val lifecycleOwner = PanelOwner()
        val composeView = ComposeView(service).apply {
            // O Compose precisa destes três "donos" na árvore da view; um Service não os tem, então criamos.
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent { FloatingPanelContent(ui, this@FloatingPanelController) }
            // Sempre que o tamanho muda (minimizar, expandir, lista maior), garante que continua na tela.
            addOnLayoutChangeListener { v, l, t, r, b, oldL, oldT, oldR, oldB ->
                if ((r - l) != (oldR - oldL) || (b - t) != (oldB - oldT)) v.post { reclamp() }
            }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = start.x
            y = start.y
            title = "VolumeControlPanel"
        }

        lifecycleOwner.start()
        return try {
            wm.addView(composeView, lp)
            view = composeView
            owner = lifecycleOwner
            params = lp
            loadApps()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível mostrar o painel", t)
            lifecycleOwner.destroy()
            false
        }
    }

    /** Remove a janela e libera tudo. Pode ser chamado várias vezes. */
    fun hide() {
        loadJob?.cancel()
        loadJob = null
        val v = view
        if (v != null) {
            try {
                wm.removeView(v)
            } catch (t: Throwable) {
                // A janela já tinha sido removida (ex.: o usuário revogou a permissão).
            }
            try {
                v.disposeComposition()
            } catch (t: Throwable) {
                // já liberada
            }
        }
        owner?.destroy()
        view = null
        owner = null
        params = null
    }

    /** O aparelho girou: recalcula tamanho do painel e mantém a posição dentro da tela. */
    fun onConfigurationChanged() {
        updateMetrics()
        view?.post { reclamp() }
    }

    // ---- PanelHost (pedidos da interface) ----

    override fun dragStart(rawX: Float, rawY: Float) {
        val lp = params ?: return
        dragRawX = rawX
        dragRawY = rawY
        dragOriginX = lp.x
        dragOriginY = lp.y
    }

    override fun dragMove(rawX: Float, rawY: Float) {
        if (ui.locked) return
        val v = view ?: return
        val lp = params ?: return
        val b = screenBounds()
        val pos = PanelRules.clamp(
            x = dragOriginX + (rawX - dragRawX).roundToInt(),
            y = dragOriginY + (rawY - dragRawY).roundToInt(),
            viewWidth = v.width,
            viewHeight = v.height,
            screenWidth = b.width(),
            screenHeight = b.height(),
        )
        if (pos.x != lp.x || pos.y != lp.y) {
            lp.x = pos.x
            lp.y = pos.y
            updateLayout(v, lp)
        }
    }

    override fun dragEnd() {
        val lp = params ?: return
        Settings.setPanelPosition(lp.x, lp.y)
    }

    override fun close() = hide()

    override fun openApp() {
        try {
            service.startActivity(
                Intent(service, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível abrir o app a partir do painel", t)
        }
    }

    // ---- internos ----

    private fun loadApps() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val list = withContext(Dispatchers.IO) { AppRepository.load(service) }
            ui.apps = list
        }
    }

    private fun updateMetrics() {
        val b = screenBounds()
        val density = service.resources.displayMetrics.density
        ui.widthDp = PanelRules.panelWidthDp((b.width() / density).toInt())
        ui.maxListHeightDp = PanelRules.maxListHeightDp((b.height() / density).toInt())
    }

    private fun reclamp() {
        val v: View = view ?: return
        val lp = params ?: return
        if (v.width == 0 || v.height == 0) return
        val b = screenBounds()
        val pos = PanelRules.clamp(lp.x, lp.y, v.width, v.height, b.width(), b.height())
        if (pos.x != lp.x || pos.y != lp.y) {
            lp.x = pos.x
            lp.y = pos.y
            updateLayout(v, lp)
        }
    }

    private fun updateLayout(v: View, lp: WindowManager.LayoutParams) {
        try {
            wm.updateViewLayout(v, lp)
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível reposicionar o painel", t)
        }
    }

    private fun screenBounds(): Rect = try {
        wm.currentWindowMetrics.bounds
    } catch (t: Throwable) {
        val m = service.resources.displayMetrics
        Rect(0, 0, m.widthPixels, m.heightPixels)
    }

    /** Ciclo de vida mínimo para o Compose funcionar numa janela criada por um Service. */
    private class PanelOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        fun start() {
            controller.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }

    private companion object {
        const val TAG = "FloatingPanel"
    }
}
