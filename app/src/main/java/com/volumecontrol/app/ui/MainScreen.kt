package com.volumecontrol.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volumecontrol.app.data.AppEntry
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.PlaybackState
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.engine.ShizukuStatus
import com.volumecontrol.app.service.VolumeKeysService
import com.volumecontrol.app.service.VolumeService
import kotlin.math.roundToInt

private const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current

    val apps by vm.apps.collectAsState()
    val loading by vm.loading.collectAsState()
    val volumes by Settings.volumes.collectAsState()
    val enabled by Settings.enabled.collectAsState()
    val dark by Settings.darkTheme.collectAsState()
    val status by ShizukuMonitor.status.collectAsState()
    val playing by PlaybackState.playing.collectAsState()
    val unsupported by PlaybackState.unsupported.collectAsState()
    val keysEnabled by Settings.keysEnabled.collectAsState()
    val keysConnected by VolumeKeysService.connected.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var onlyPlaying by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val active = enabled && status == ShizukuStatus.READY

    // Pede a permissão de notificação (Android 13+) uma vez, para a notificação do serviço aparecer.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Liga o serviço quando o controle está ativo e o Shizuku está pronto.
    LaunchedEffect(enabled, status) {
        if (enabled && status == ShizukuStatus.READY) {
            ContextCompat.startForegroundService(context, Intent(context, VolumeService::class.java))
        }
    }

    val filtered = remember(apps, query, onlyPlaying, playing) {
        apps.filter { a ->
            (query.isBlank() || a.label.contains(query, ignoreCase = true)) &&
                (!onlyPlaying || playing.contains(a.packageName))
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Cabeçalho
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Volume Control",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Volume separado para cada app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (dark) "Usar tema claro" else "Usar tema escuro") },
                            onClick = {
                                menuOpen = false
                                Settings.setDarkTheme(!dark)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Restaurar volumes padrão") },
                            onClick = {
                                menuOpen = false
                                confirmReset = true
                            },
                        )
                    }
                }
            }

            // Chave geral
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Controle individual",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (enabled) "Cada app usa o volume definido abaixo"
                            else "Desativado: todos os apps com volume normal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { Settings.setEnabled(it) })
                }
            }

            // Teclas de volume no app em uso
            KeysCard(keysEnabled = keysEnabled, connected = keysConnected)

            // Aviso do Shizuku (só quando falta algo)
            if (enabled && status != ShizukuStatus.READY) {
                ShizukuCard(status)
            }

            // Busca + filtro
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Buscar app") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilterChip(
                    selected = onlyPlaying,
                    onClick = { onlyPlaying = !onlyPlaying },
                    label = { Text("Tocando agora") },
                )
            }

            // Lista
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (onlyPlaying) "Nenhum app tocando áudio agora" else "Nenhum app encontrado",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            percent = volumes[app.packageName] ?: 100,
                            isPlaying = playing.contains(app.packageName),
                            isUnsupported = unsupported.contains(app.packageName),
                            sliderEnabled = active,
                        )
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restaurar volumes padrão?") },
            text = { Text("Todos os apps voltarão para 100%.") },
            confirmButton = {
                TextButton(onClick = {
                    Settings.resetAll()
                    confirmReset = false
                }) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun KeysCard(keysEnabled: Boolean, connected: Boolean) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Teclas de volume no app em uso",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Com um app tocando na tela, as teclas de volume mudam só o volume dele (5% por toque).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = keysEnabled, onCheckedChange = { Settings.setKeysEnabled(it) })
            }
            if (keysEnabled && !connected) {
                Text(
                    "Falta ligar o serviço de acessibilidade: Acessibilidade > Apps instalados > Volume Control. " +
                        "Se aparecer \"configuração restrita\", vá em Informações do app > ⋮ > Permitir configurações restritas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (e: Exception) {
                            // tela indisponível: nada a fazer
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Abrir acessibilidade") }
            }
        }
    }
}

@Composable
private fun ShizukuCard(status: ShizukuStatus) {
    val context = LocalContext.current

    val title: String
    val body: String
    val button: String
    when (status) {
        ShizukuStatus.NOT_INSTALLED -> {
            title = "Falta o Shizuku"
            body = "O Android não deixa um app comum mudar o volume de outros apps. " +
                "O Shizuku (gratuito) empresta essa permissão de forma segura. Instale-o e volte aqui."
            button = "Baixar Shizuku"
        }
        ShizukuStatus.NOT_RUNNING -> {
            title = "Shizuku não está ativo"
            body = "Abra o Shizuku e toque em \"Iniciar\" (via Depuração sem fio). " +
                "É preciso repetir isso depois de reiniciar o celular."
            button = "Abrir Shizuku"
        }
        else -> {
            title = "Permissão necessária"
            body = "Autorize o Volume Control no Shizuku para ele poder controlar o volume de cada app."
            button = "Conceder permissão"
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.size(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    when (status) {
                        ShizukuStatus.NOT_INSTALLED -> try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (e: Exception) {
                            // sem navegador instalado: nada a fazer
                        }
                        ShizukuStatus.NOT_RUNNING -> ShizukuMonitor.openShizukuApp(context)
                        else -> ShizukuMonitor.requestPermission()
                    }
                }) { Text(button) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { ShizukuMonitor.refresh() }) { Text("Verificar de novo") }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    percent: Int,
    isPlaying: Boolean,
    isUnsupported: Boolean,
    sliderEnabled: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isUnsupported) {
                        Text(
                            "Este app não aceita controle externo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (isPlaying) {
                        Text(
                            "Tocando agora",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (percent < 100) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = percent.toFloat(),
                onValueChange = { Settings.setVolume(app.packageName, it.roundToInt(), persist = false) },
                onValueChangeFinished = { Settings.persist() },
                valueRange = 0f..100f,
                enabled = sliderEnabled,
            )
        }
    }
}
