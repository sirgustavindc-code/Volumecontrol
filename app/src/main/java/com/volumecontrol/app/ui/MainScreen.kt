package com.volumecontrol.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.EngineProblem
import com.volumecontrol.app.engine.PlaybackState
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.engine.ShizukuStatus
import com.volumecontrol.app.service.OverlayState
import com.volumecontrol.app.service.ServiceController
import com.volumecontrol.app.service.VolumeKeysService

private const val FILTER_ALL = 0
private const val FILTER_PLAYING = 1
private const val FILTER_CONFIGURED = 2

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
    val problem by PlaybackState.problem.collectAsState()
    val keysEnabled by Settings.keysEnabled.collectAsState()
    val keysConnected by VolumeKeysService.connected.collectAsState()
    val floatingEnabled by Settings.floatingEnabled.collectAsState()
    val canDraw by OverlayState.canDraw.collectAsState()
    val notificationsOk by OverlayState.notificationsOk.collectAsState()
    val resumes by OverlayState.resumes.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var filterMode by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    // true enquanto o usuário está na tela de permissão "Sobrepor a outros apps" para ligar o painel
    var wantsFloating by rememberSaveable { mutableStateOf(false) }

    val active = enabled && status == ShizukuStatus.READY

    // Pede a permissão de notificação (Android 13+) para a notificação do serviço aparecer.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Liga o serviço quando há trabalho a fazer (controle ativo, ou apps a restaurar) e o Shizuku está pronto.
    LaunchedEffect(enabled, status, floatingEnabled, canDraw) { ServiceController.ensureRunning(context) }

    // Volta da tela de permissão: se o usuário concedeu, liga o painel; se não, desiste do pedido.
    LaunchedEffect(resumes) {
        if (wantsFloating) {
            if (OverlayState.canDraw.value) Settings.setFloatingEnabled(true)
            wantsFloating = false
        }
    }

    val filtered = remember(apps, query, filterMode, playing, volumes) {
        apps.filter { a ->
            (query.isBlank() || a.label.contains(query, ignoreCase = true)) &&
                when (filterMode) {
                    FILTER_PLAYING -> a.packageName in playing
                    FILTER_CONFIGURED -> volumes.containsKey(a.packageName)
                    else -> true
                }
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
                        if (volumes.isEmpty()) "Volume separado para cada app"
                        else "${volumes.size} ${if (volumes.size == 1) "app configurado" else "apps configurados"}",
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
                        DropdownMenuItem(
                            text = { Text("Bateria (evitar que o app seja fechado)") },
                            onClick = {
                                menuOpen = false
                                openBatterySettings(context)
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

            // Painel flutuante (por cima de jogos e apps, aberto pela notificação)
            FloatingCard(
                enabled = floatingEnabled,
                canDraw = canDraw,
                notificationsOk = notificationsOk,
                onToggle = { on ->
                    when {
                        !on -> Settings.setFloatingEnabled(false)
                        canDraw -> Settings.setFloatingEnabled(true)
                        else -> {
                            wantsFloating = true
                            openOverlayPermission(context)
                        }
                    }
                },
                onOpenNow = { ServiceController.openPanel(context) },
            )

            // Estado do Shizuku: só aparece quando falta alguma coisa
            if (enabled && status != ShizukuStatus.READY) {
                ShizukuCard(status)
            }

            // Erro do motor de áudio (Shizuku pronto, mas o sistema não respondeu)
            if (enabled && status == ShizukuStatus.READY && problem != null) {
                ProblemCard(problem!!)
            }

            // Teclas de volume no app em uso (opcional)
            KeysCard(
                keysEnabled = keysEnabled,
                connected = keysConnected,
                onToggle = { on ->
                    Settings.setKeysEnabled(on)
                    if (!on) VolumeKeysService.disable() // desliga o serviço de acessibilidade de vez
                },
            )

            // Busca + filtros
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
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filterMode == FILTER_PLAYING,
                    onClick = { filterMode = if (filterMode == FILTER_PLAYING) FILTER_ALL else FILTER_PLAYING },
                    label = { Text("Tocando agora") },
                )
                FilterChip(
                    selected = filterMode == FILTER_CONFIGURED,
                    onClick = { filterMode = if (filterMode == FILTER_CONFIGURED) FILTER_ALL else FILTER_CONFIGURED },
                    label = { Text("Configurados") },
                )
            }

            // Lista
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when (filterMode) {
                            FILTER_PLAYING -> "Nenhum app tocando áudio agora"
                            FILTER_CONFIGURED -> "Nenhum app configurado ainda"
                            else -> "Nenhum app encontrado"
                        },
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
            text = { Text("Todos os apps voltam para 100%, inclusive os que estão tocando agora.") },
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

private fun openBatterySettings(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        // tela indisponível neste aparelho: nada a fazer
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.error,
    content: @Composable () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = tint)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.size(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

private fun openOverlayPermission(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        // tela indisponível neste aparelho: nada a fazer
    }
}

private fun openNotificationSettings(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        // tela indisponível neste aparelho: nada a fazer
    }
}

@Composable
private fun FloatingCard(
    enabled: Boolean,
    canDraw: Boolean,
    notificationsOk: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenNow: () -> Unit,
) {
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
                        "Painel flutuante",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Controles de volume por cima de jogos e apps. Puxe a barra de notificações " +
                            "e toque em \"Abrir painel\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled && !canDraw) {
                Text(
                    "Falta a permissão \"Sobrepor a outros apps\" (Configurações > Apps > Volume Control). " +
                        "Se o botão aparecer bloqueado, vá em Informações do app > ⋮ > Permitir configurações restritas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = { openOverlayPermission(context) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Conceder permissão") }
            }
            if (enabled && !notificationsOk) {
                Text(
                    "As notificações do Volume Control estão bloqueadas, então o botão \"Abrir painel\" " +
                        "não aparece. Permita as notificações para usar o painel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = { openNotificationSettings(context) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Abrir ajustes de notificação") }
            }
            if (enabled && canDraw) {
                Text(
                    "Dica: se o botão não aparecer, expanda a notificação do Volume Control (deslize-a para baixo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = onOpenNow,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Mostrar o painel agora") }
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
            button = "Instalar o Shizuku"
        }
        ShizukuStatus.NOT_RUNNING -> {
            title = "O Shizuku está parado"
            body = "Abra o Shizuku e toque em \"Iniciar\" (por Depuração sem fio). " +
                "É preciso repetir isso depois de reiniciar o celular. Enquanto ele estiver parado, " +
                "os volumes ficam como estão e nada trava."
            button = "Abrir Shizuku"
        }
        ShizukuStatus.OUTDATED -> {
            title = "Shizuku desatualizado"
            body = "Esta versão do Shizuku é antiga demais. Atualize o app Shizuku e inicie-o de novo."
            button = "Abrir Shizuku"
        }
        ShizukuStatus.DENIED -> {
            title = "Permissão negada"
            body = "Você negou o acesso do Volume Control no Shizuku. Para liberar, abra o Shizuku, " +
                "vá em \"Apps autorizados\" e ative o Volume Control."
            button = "Abrir Shizuku"
        }
        else -> { // NEEDS_PERMISSION (READY nunca chega aqui)
            title = "Falta autorizar"
            body = "Autorize o Volume Control no Shizuku para ele poder controlar o volume de cada app."
            button = "Conceder permissão"
        }
    }

    InfoCard(title, body) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                when (status) {
                    ShizukuStatus.NOT_INSTALLED -> ShizukuMonitor.openInstallPage(context)
                    ShizukuStatus.NEEDS_PERMISSION -> ShizukuMonitor.requestPermission()
                    else -> ShizukuMonitor.openShizukuApp(context)
                }
            }) { Text(button) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { ShizukuMonitor.refresh() }) { Text("Verificar de novo") }
        }
    }
}

@Composable
private fun ProblemCard(problem: EngineProblem) {
    val (title, body) = when (problem) {
        EngineProblem.API_UNAVAILABLE ->
            "Função não disponível neste Android" to
                "Esta versão do sistema não libera a função interna usada para o volume por app " +
                "(isso pode acontecer depois de uma atualização). Nada foi alterado nos seus apps."
        EngineProblem.CALL_FAILED ->
            "O sistema de áudio não respondeu" to
                "Confira se o Shizuku continua ativo e autorizado. O app tenta de novo sozinho."
    }
    InfoCard(title, body)
}

@Composable
private fun KeysCard(keysEnabled: Boolean, connected: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
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
                        "Opcional. Com um app tocando na tela, as teclas mudam só o volume dele (5% por toque). " +
                            "Sem ele, use a lista ou o painel rápido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = keysEnabled, onCheckedChange = onToggle)
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
