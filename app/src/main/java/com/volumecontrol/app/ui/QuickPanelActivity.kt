package com.volumecontrol.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volumecontrol.app.MainActivity
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.PlaybackState
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.engine.ShizukuStatus
import com.volumecontrol.app.service.ServiceController

/**
 * Painel rápido: uma folha na parte de baixo da tela com os apps que estão tocando agora, cada um
 * com seu controle. Abre pelo botão dos Ajustes rápidos ou pelo atalho do ícone do app. Não precisa
 * de acessibilidade. Toque fora para fechar.
 */
class QuickPanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark by Settings.darkTheme.collectAsState()
            VolumeControlTheme(dark = dark) {
                PanelScreen(
                    onClose = { finish() },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuMonitor.refresh()
        ServiceController.ensureRunning(this)
    }
}

@Composable
private fun PanelScreen(onClose: () -> Unit, onOpenApp: () -> Unit, vm: MainViewModel = viewModel()) {
    val apps by vm.apps.collectAsState()
    val playing by PlaybackState.playing.collectAsState()
    val unsupported by PlaybackState.unsupported.collectAsState()
    val volumes by Settings.volumes.collectAsState()
    val enabled by Settings.enabled.collectAsState()
    val status by ShizukuMonitor.status.collectAsState()

    val ready = enabled && status == ShizukuStatus.READY
    val visible = remember(apps, playing) { apps.filter { it.packageName in playing } }
    val noRipple = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = noRipple, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                // Toques dentro da folha não fecham o painel.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Tocando agora",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                when {
                    !ready -> {
                        Text(
                            if (!enabled) "O controle individual está desligado."
                            else "O Shizuku não está pronto. Abra o Volume Control para ver o que falta.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenApp) { Text("Abrir Volume Control") }
                    }
                    visible.isEmpty() -> Text(
                        "Nenhum app tocando áudio agora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Column(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        visible.forEach { app ->
                            AppRow(
                                app = app,
                                percent = volumes[app.packageName] ?: 100,
                                isPlaying = true,
                                isUnsupported = unsupported.contains(app.packageName),
                                sliderEnabled = true,
                            )
                        }
                    }
                }
                if (ready) {
                    TextButton(onClick = onOpenApp) { Text("Todos os apps") }
                }
            }
        }
    }
}
