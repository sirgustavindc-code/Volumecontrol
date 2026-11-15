package com.volumecontrol.app.ui

import android.view.MotionEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volumecontrol.app.R
import com.volumecontrol.app.data.AppEntry
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.PanelRules
import com.volumecontrol.app.engine.PlaybackState
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.engine.ShizukuStatus
import kotlin.math.abs
import kotlin.math.roundToInt

/** Cores do painel (escuro e semitransparente, inspirado na imagem de referência). */
internal object PanelColors {
    val Surface = Color(0xE6151621)
    val Row = Color(0x14FFFFFF)
    val Border = Color(0x33FFFFFF)
    val Accent = Color(0xFF9D6BFF)
    val Button = Color(0xFF272A3A)
    val Track = Color(0xFF3A3D4E)
    val Thumb = Color(0xFFEDE4FF)
    val Text = Color(0xFFF2F3F7)
    val TextDim = Color(0xFFA6AABB)
    val Error = Color(0xFFFFB4AB)

    /** Roxo, azul, verde e laranja: cada app mantém sempre a mesma cor. */
    val Palette = listOf(Color(0xFF9D6BFF), Color(0xFF3D8BFF), Color(0xFF2EE59D), Color(0xFFFFA033))
}

/** Estado visual do painel (lido pelo Compose, alterado pelo controlador da janela). */
class PanelUiState {
    var minimized by mutableStateOf(false)
    var expanded by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
    var widthDp by mutableIntStateOf(PanelRules.MAX_WIDTH_DP)
    var maxListHeightDp by mutableIntStateOf(220)
}

/** O que a interface pede à janela (arrastar, fechar, abrir o app). Implementado pelo controlador. */
interface PanelHost {
    fun dragStart(rawX: Float, rawY: Float)
    fun dragMove(rawX: Float, rawY: Float)
    fun dragEnd()
    fun close()
    fun openApp()
}

@Composable
fun FloatingPanelContent(state: PanelUiState, host: PanelHost) {
    VolumeControlTheme(dark = true) {
        if (state.minimized) MinimizedBubble(state, host) else FullPanel(state, host)
    }
}

/**
 * Arrastar usando as coordenadas reais da tela (rawX/rawY). Com a janela se movendo junto com o dedo,
 * as coordenadas locais quase não mudam, por isso não servem para calcular o deslocamento.
 * Um toque curto (sem mover) chama [onTap].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.dragHandle(host: PanelHost, onTap: (() -> Unit)? = null): Modifier {
    val slop = with(LocalDensity.current) { 8.dp.toPx() }
    var downX by remember { mutableFloatStateOf(0f) }
    var downY by remember { mutableFloatStateOf(0f) }
    var moved by remember { mutableStateOf(false) }
    return this.pointerInteropFilter { ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                moved = false
                host.dragStart(ev.rawX, ev.rawY)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)) moved = true
                if (moved) host.dragMove(ev.rawX, ev.rawY)
                true
            }
            MotionEvent.ACTION_UP -> {
                host.dragEnd()
                if (!moved) onTap?.invoke()
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                host.dragEnd()
                true
            }
            else -> false
        }
    }
}

/** Painel minimizado: só um botão redondo (ocupa o mínimo de espaço). Toque para expandir; arraste para mover. */
@Composable
private fun MinimizedBubble(state: PanelUiState, host: PanelHost) {
    val playing by PlaybackState.playing.collectAsState()
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(PanelColors.Surface)
            .border(1.dp, PanelColors.Border, CircleShape)
            .dragHandle(host, onTap = { state.minimized = false })
            .semantics { contentDescription = "Expandir o painel de volume" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shortcut),
            contentDescription = null,
            tint = PanelColors.Accent,
            modifier = Modifier.size(26.dp),
        )
        if (playing.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 9.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(PanelColors.Accent),
            )
        }
    }
}

@Composable
private fun FullPanel(state: PanelUiState, host: PanelHost) {
    val volumes by Settings.volumes.collectAsState()
    val playing by PlaybackState.playing.collectAsState()
    val unsupported by PlaybackState.unsupported.collectAsState()
    val enabled by Settings.enabled.collectAsState()
    val status by ShizukuMonitor.status.collectAsState()
    val ready = enabled && status == ShizukuStatus.READY

    // Apps que o usuário mexeu nesta abertura: a linha não some do meio da lista enquanto ele arrasta.
    val pinned = remember { mutableStateListOf<String>() }
    var selected by remember { mutableStateOf<String?>(null) }

    val byPackage = state.apps.associateBy { it.packageName }
    val visible = PanelRules
        .visiblePackages(
            all = state.apps.map { it.packageName },
            playing = playing,
            configured = volumes.keys,
            expanded = state.expanded,
            pinned = pinned.toSet(),
        )
        .mapNotNull { byPackage[it] }

    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .width(state.widthDp.dp)
            .clip(shape)
            .background(PanelColors.Surface)
            .border(1.dp, PanelColors.Border, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Cabeçalho: a área do título (com a barrinha roxa) arrasta o painel.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).dragHandle(host)) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 44.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(PanelColors.Accent),
                )
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shortcut),
                        contentDescription = null,
                        tint = PanelColors.Accent,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Volume Control",
                        color = PanelColors.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            RoundButton("Minimizar", onClick = { state.minimized = true }) {
                Icon(painterResource(R.drawable.ic_panel_minimize), null, tint = PanelColors.Text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            RoundButton(
                if (state.expanded) "Mostrar só os apps em uso" else "Mostrar todos os apps",
                onClick = { state.expanded = !state.expanded },
            ) {
                Icon(
                    painterResource(R.drawable.ic_panel_expand),
                    null,
                    tint = if (state.expanded) PanelColors.Accent else PanelColors.Text,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            RoundButton("Fechar painel", onClick = host::close) {
                Icon(Icons.Default.Close, null, tint = PanelColors.Text, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.size(8.dp))

        when {
            !ready -> {
                Text(
                    if (!enabled) "O controle individual está desligado."
                    else "O Shizuku não está pronto. Abra o Volume Control para ver o que falta.",
                    color = PanelColors.TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Button(onClick = host::openApp, modifier = Modifier.padding(start = 6.dp)) { Text("Abrir Volume Control") }
            }
            visible.isEmpty() -> Text(
                if (state.apps.isEmpty()) "Carregando apps..."
                else "Nenhum app tocando agora. Toque em  ⛶  para ver todos os apps.",
                color = PanelColors.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.heightIn(max = state.maxListHeightDp.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visible, key = { it.packageName }) { app ->
                    PanelRow(
                        app = app,
                        percent = volumes[app.packageName] ?: 100,
                        isPlaying = playing.contains(app.packageName),
                        isUnsupported = unsupported.contains(app.packageName),
                        selected = selected == app.packageName,
                        color = PanelColors.Palette[PanelRules.colorIndex(app.packageName, PanelColors.Palette.size)],
                        onChange = { percent ->
                            Settings.setVolume(app.packageName, percent, persist = false)
                            selected = app.packageName
                            if (app.packageName !in pinned) pinned.add(app.packageName)
                        },
                        onFinish = { Settings.persist() },
                    )
                }
            }
        }

        Spacer(Modifier.size(8.dp))

        // Rodapé: engrenagem (abre o app), estado e cadeado (fixar posição).
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PanelColors.Row)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton("Abrir o Volume Control", sizeDp = 36, onClick = host::openApp) {
                Icon(Icons.Default.Settings, null, tint = PanelColors.Text, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(PanelColors.Accent))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.locked) "Posição fixada" else "Painel flutuante ativo",
                        color = PanelColors.TextDim,
                        fontSize = 13.sp,
                    )
                }
            }
            RoundButton(
                if (state.locked) "Soltar a posição do painel" else "Fixar a posição do painel",
                sizeDp = 36,
                onClick = {
                    state.locked = !state.locked
                    Settings.setPanelLocked(state.locked)
                },
            ) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    tint = if (state.locked) PanelColors.Accent else PanelColors.TextDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundButton(
    description: String,
    sizeDp: Int = 40,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(PanelColors.Button)
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Uma linha por app: ícone e nome reais do app instalado, controle deslizante, porcentagem e indicador de áudio. */
@Composable
private fun PanelRow(
    app: AppEntry,
    percent: Int,
    isPlaying: Boolean,
    isUnsupported: Boolean,
    selected: Boolean,
    color: Color,
    onChange: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PanelColors.Row)
            .then(if (selected) Modifier.border(1.5.dp, PanelColors.Accent, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    color = PanelColors.Text,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isUnsupported) {
                    Text("  sem controle", color = PanelColors.Error, fontSize = 11.sp, maxLines = 1)
                }
            }
            Slider(
                value = percent.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                onValueChangeFinished = onFinish,
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = PanelColors.Thumb,
                    activeTrackColor = color,
                    inactiveTrackColor = PanelColors.Track,
                ),
                thumb = {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(PanelColors.Thumb),
                    )
                },
            )
        }
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier.width(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "$percent%",
                color = PanelColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            // Espaço sempre reservado, para a largura do slider não mudar quando o app começa a tocar.
            Box(Modifier.size(20.dp), contentAlignment = Alignment.CenterEnd) {
                if (isPlaying) EqualizerBars(PanelColors.Accent)
            }
        }
    }
}

/** Barrinhas animadas: indicam que o app está reproduzindo áudio agora. Só anima enquanto aparece. */
@Composable
private fun EqualizerBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "equalizador")
    val a by transition.animateFloat(
        initialValue = 0.30f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    val b by transition.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "b",
    )
    val c by transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "c",
    )
    Canvas(Modifier.size(16.dp)) {
        val barWidth = size.width / 5f
        listOf(a, b, c).forEachIndexed { i, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(i * 2f * barWidth, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
