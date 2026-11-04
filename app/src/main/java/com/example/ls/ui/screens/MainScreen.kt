package com.example.ls.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ls.data.*
import com.example.ls.ui.AppTab
import com.example.ls.ui.SignalViewModel
import com.example.ls.ui.components.*
import com.example.ls.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** hasPerms() e hasLocationPerms() eram o mesmo `perms.all { checkSelfPermission... } `
 *  duas vezes, cada um fechando sobre um array de permissões diferente. */
private fun android.content.Context.hasAllPermissions(perms: Array<String>): Boolean =
    perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

// ── Tabs ───────────────────────────────────────────────────────────────────

private data class TabItem(val tab: AppTab, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem(AppTab.SIGNAL, "Sinal", Icons.Default.SignalCellularAlt),
    TabItem(AppTab.HISTORY, "Histórico", Icons.AutoMirrored.Filled.ShowChart),
    TabItem(AppTab.TOWERS, "Torres", Icons.Default.Explore),
    TabItem(AppTab.INFO, "Info", Icons.Default.Info),
    TabItem(AppTab.SETTINGS, "Config", Icons.Default.Settings),
)

// ── MainScreen ─────────────────────────────────────────────────────────────

@Composable
fun MainScreen(vm: SignalViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val currentTab by vm.currentTab.collectAsStateWithLifecycle()
    val pollIntervalMs by vm.pollIntervalMs.collectAsStateWithLifecycle()
    val availableSims by vm.availableSims.collectAsStateWithLifecycle()
    val selectedSubscriptionId by vm.selectedSubscriptionId.collectAsStateWithLifecycle()
    val towerTriangulationEnabled by vm.towerTriangulationEnabled.collectAsStateWithLifecycle()
    val historyRetentionDays by vm.historyRetentionDays.collectAsStateWithLifecycle()
    val exportError by vm.exportError.collectAsStateWithLifecycle()
    val clearEstimatesError by vm.clearEstimatesError.collectAsStateWithLifecycle()
    val currentLocation by vm.currentLocation.collectAsStateWithLifecycle()
    val towerEstimatesDetailed by vm.towerEstimatesDetailed.collectAsStateWithLifecycle()

    val perms = remember {
        buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            // Pedida no pacote inicial só a partir da API 28, que é quando
            // getAllCellInfo()/requestCellInfoUpdate() passam a exigir
            // localização — abaixo disso a leitura de sinal (a feature
            // central do app) não depende de localização nenhuma. A
            // triangulação de torres (opt-in, desligada por padrão) também
            // precisa de localização em QUALQUER versão, mas como é uma
            // feature secundária — não a razão de ninguém abrir o app —,
            // ela é pedida em contexto, só quando o usuário liga o toggle
            // em Configurações (ver locationPermsLauncher abaixo), seguindo
            // a prática recomendada pela própria documentação do Android
            // (pedir no momento de uso, não empacotado no início).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }.toTypedArray()
    }

    val locationPerms = remember {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    fun hasLocationPerms() = context.hasAllPermissions(locationPerms)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Checa por permissão: só é conclusivo depois de já termos pedido pelo menos
    // uma vez (antes da primeira solicitação o sistema também retorna false para
    // toda permissão nunca pedida). Se QUALQUER permissão em [permsToCheck] não
    // pode mais mostrar rationale, ela foi negada permanentemente — pedir de novo
    // não adianta, só dá pra resolver via Configurações do sistema. Aceita o
    // grupo a checar (não só `perms`, o fixo do fluxo principal) porque o fluxo
    // secundário de localização da triangulação (locationPermsLauncher abaixo)
    // precisa da mesma checagem sobre `locationPerms`, senão relança o pedido pra
    // sempre mesmo após negação permanente, sem nenhum jeito de recuperar o
    // toggle a não ser descobrir sozinho o caminho até as Configurações do app.
    fun hasAnyPermanentlyDenied(permsToCheck: Array<String> = perms): Boolean {
        val activity = context.findActivity() ?: return false
        return permsToCheck.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        )
    }

    val locationPermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Reavalia via hasLocationPerms() (exige as DUAS) em vez de confiar no
        // mapa `results` do callback — LocationTracker.start() só escolhe
        // GPS_PROVIDER (que exige FINE) quando o GPS do sistema está ligado, e
        // cai pra NETWORK_PROVIDER senão; conceder só COARSE deixaria o toggle
        // "ligado" permanentemente sem nunca funcionar de fato. Checar as duas
        // aqui mantém consistência com o gate usado antes de abrir o launcher.
        if (hasLocationPerms()) {
            vm.setTowerTriangulationEnabled(true)
        } else {
            // Diferente do fluxo principal (que usa PermissionRequired, uma tela
            // cheia bloqueante), a triangulação é opt-in e secundária — bloquear
            // o app inteiro por causa dela seria desproporcional. Um snackbar
            // basta pra explicar por que o switch não ligou — mas se a negação
            // foi permanente, relançar esse mesmo launcher da próxima vez que o
            // usuário tentar ligar o toggle não faz nada (o sistema rejeita em
            // silêncio), deixando o switch parecendo quebrado pra sempre sem essa
            // distinção; a ação do Snackbar dá um caminho de volta.
            coroutineScope.launch {
                if (hasAnyPermanentlyDenied(locationPerms)) {
                    val result = snackbarHostState.showSnackbar(
                        message = "Permissão de localização negada permanentemente.",
                        actionLabel = "Configurações",
                    )
                    if (result == SnackbarResult.ActionPerformed) openAppSettings()
                } else {
                    snackbarHostState.showSnackbar("Permissão de localização negada — triangulação de torres não foi ativada.")
                }
            }
        }
    }

    fun hasPerms() = context.hasAllPermissions(perms)

    var permanentlyDenied by remember { mutableStateOf(false) }
    // Diferencia o primeiríssimo ON_START (onde ainda não pedimos nada) dos
    // seguintes — ver o comentário dentro do LifecycleStartEffect abaixo.
    var hasRequestedInitialPermission by remember { mutableStateOf(false) }
    // Cancela um check de revogação anterior ainda em voo antes de lançar outro —
    // sem isso, alternar entre background/foreground rapidamente empilha várias
    // checagens sobrepostas, cada uma podendo desligar a triangulação e mostrar
    // seu próprio Snackbar (Snackbars duplicados).
    var revocationCheckJob by remember { mutableStateOf<Job?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasPerms()) {
            permanentlyDenied = false
            vm.startPolling()
        } else {
            permanentlyDenied = hasAnyPermanentlyDenied()
            vm.onPermissionDenied()
        }
    }

    // Amarrado ao ciclo de vida (não à composição): retoma o polling quando o
    // app volta do background (ON_START) SE a permissão já estiver concedida.
    // Pausa em ON_STOP, em vez de só parar quando a Activity inteira é
    // destruída. Também é quem faz o pedido inicial de permissão — não um
    // LaunchedEffect(Unit) separado: os dois efeitos podiam dar zoom um no
    // outro no cold start (LifecycleStartEffect rodando ANTES do diálogo real
    // do sistema, disparado pelo outro efeito, sequer aparecer), fazendo
    // hasAnyPermanentlyDenied() avaliar `true` num app recém-instalado — ela
    // não distingue "nunca pedido" de "negado pra sempre"
    // (shouldShowRequestPermissionRationale() devolve false pros dois casos).
    // Ter um único efeito cuidando de tudo elimina essa corrida.
    LifecycleStartEffect(Unit) {
        when {
            hasPerms() -> {
                permanentlyDenied = false
                vm.startPolling()
            }
            !hasRequestedInitialPermission -> launcher.launch(perms)
            else -> {
                // Sem isso, revogar telefone/localização pelas Configurações do
                // sistema enquanto o app está em background deixava a tela
                // congelada no último SignalUiState.Success (dado velho) pra
                // sempre — nem startPolling() nem nenhuma transição de estado
                // rodava nesse caminho de volta ao foreground.
                permanentlyDenied = hasAnyPermanentlyDenied()
                vm.onPermissionDenied()
            }
        }
        hasRequestedInitialPermission = true
        vm.setAppInForeground(true)
        // A permissão de localização pode ter sido revogada pelas Configurações
        // do sistema enquanto o app estava em background (o app não é notificado
        // disso em tempo real) — sem essa checagem no retorno, o switch de
        // triangulação continuaria mostrando "ligado" indefinidamente enquanto a
        // feature ficava silenciosamente morta (LocationTracker.start() vira
        // no-op sem a permissão). Usa a leitura fresca (suspend), não
        // vm.towerTriangulationEnabled.value: logo após um cold start, esse
        // StateFlow pode ainda estar no valor-semente `false` (a primeira
        // emissão do DataStore é assíncrona), o que faria esse guard nunca
        // disparar justo no caso mais comum de checar (app recém-reaberto).
        revocationCheckJob?.cancel()
        revocationCheckJob = coroutineScope.launch {
            if (vm.isTowerTriangulationEnabledPersisted() && !hasLocationPerms()) {
                vm.setTowerTriangulationEnabled(false)
                snackbarHostState.showSnackbar("Permissão de localização foi revogada — triangulação de torres desativada.")
            }
        }
        onStopOrDispose { vm.stopPolling(); vm.setAppInForeground(false) }
    }

    // exportReadingsAsCsv()/clearTowerEstimates() já não engolem a falha em
    // silêncio — sem isso, os botões "Exportar" (Histórico) e "Apagar
    // estimativas de torre" (Configurações) simplesmente não faziam nada
    // quando davam erro, indistinguível de um botão quebrado.
    LaunchedEffect(exportError) {
        exportError?.let {
            snackbarHostState.showSnackbar(it)
            vm.dismissExportError()
        }
    }
    LaunchedEffect(clearEstimatesError) {
        clearEstimatesError?.let {
            snackbarHostState.showSnackbar(it)
            vm.dismissClearEstimatesError()
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = { AppTopBar(state) },
        bottomBar = {
            AppBottomBar(currentTab = currentTab, onTabClick = { vm.selectTab(it) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                is SignalUiState.Loading -> LoadingScreen()
                is SignalUiState.PermissionRequired -> PermissionScreen(
                    permanentlyDenied = permanentlyDenied,
                    onRequest = { launcher.launch(perms) },
                    onOpenSettings = { openAppSettings() },
                )
                is SignalUiState.Error -> ErrorScreen(s.message) { launcher.launch(perms) }
                is SignalUiState.Success -> {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val d = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            slideInHorizontally { d * it } + fadeIn() togetherWith
                                    slideOutHorizontally { -d * it } + fadeOut()
                        },
                        label = "tabs",
                    ) { tab ->
                        when (tab) {
                            AppTab.SIGNAL -> SignalTabContent(s)
                            AppTab.HISTORY -> HistoryScreen(
                                history = vm.history,
                                onClear = { vm.clearHistory() },
                                onExport = { onReady -> vm.exportReadingsAsCsv(onReady) },
                                pollIntervalMs = pollIntervalMs,
                            )

                            AppTab.TOWERS -> TowersScreen(
                                towerTriangulationEnabled = towerTriangulationEnabled,
                                currentLocation = currentLocation,
                                towers = towerEstimatesDetailed,
                                onRefresh = { vm.refreshTowerEstimatesDetailed() },
                            )

                            AppTab.INFO -> InfoScreen()
                            AppTab.SETTINGS -> SettingsScreen(
                                pollIntervalMs = pollIntervalMs,
                                onPollIntervalChange = { vm.setPollIntervalMs(it) },
                                onClearPersistedHistory = { vm.clearPersistedHistory() },
                                availableSims = availableSims,
                                selectedSubscriptionId = selectedSubscriptionId,
                                onSelectSubscription = { vm.setSelectedSubscriptionId(it) },
                                towerTriangulationEnabled = towerTriangulationEnabled,
                                onToggleTowerTriangulation = { enabled ->
                                    if (enabled && !hasLocationPerms()) {
                                        locationPermsLauncher.launch(locationPerms)
                                    } else {
                                        vm.setTowerTriangulationEnabled(enabled)
                                    }
                                },
                                onClearTowerEstimates = { vm.clearTowerEstimates() },
                                historyRetentionDays = historyRetentionDays,
                                onHistoryRetentionDaysChange = { vm.setHistoryRetentionDays(it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── TopBar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(state: SignalUiState) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.SignalCellularAlt, null,
                    tint = AccentBlue,
                    modifier = Modifier.size(22.dp),
                )
                Wordmark(fontSize = 17.sp, markSize = 14.dp)
            }
        },
        actions = {
            if (state is SignalUiState.Success) {
                LiveDot(updatedAt = state.updatedAt)
                Spacer(Modifier.width(16.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BgMid),
    )
}

// ── BottomBar ──────────────────────────────────────────────────────────────

@Composable
private fun AppBottomBar(currentTab: AppTab, onTabClick: (AppTab) -> Unit) {
    NavigationBar(containerColor = BgMid, tonalElevation = 0.dp) {
        TABS.forEach { item ->
            val selected = item.tab == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabClick(item.tab) },
                // contentDescription = null: o rótulo visível logo abaixo já nomeia
                // o item — repetir o mesmo texto no ícone fazia o TalkBack ler o
                // nome da aba duas vezes.
                icon = { Icon(item.icon, null, Modifier.size(22.dp)) },
                label = {
                    Text(
                        item.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentBlue,
                    selectedTextColor = AccentBlue,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = AccentBlue.copy(alpha = 0.12f),
                ),
            )
        }
    }
}

// ── State screens ──────────────────────────────────────────────────────────

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(20.dp))
            Text("Lendo sinal…", color = TextSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PermissionScreen(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp),
        ) {
            Icon(
                Icons.Default.SignalCellularAlt,
                null,
                tint = AccentBlue,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Permissão necessária",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (permanentlyDenied)
                    "Você negou a permissão permanentemente. Abra as configurações do app e conceda acesso ao telefone e localização para medir o sinal real da antena."
                else
                    "Permita acesso ao telefone e localização para medir o sinal real da antena.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(32.dp))
            if (permanentlyDenied) {
                Button(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir configurações", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onRequest,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Conceder permissões", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp),
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                null,
                tint = QualityVeryPoor,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Erro ao ler sinal",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tentar novamente")
            }
        }
    }
}

// ── Signal Tab ─────────────────────────────────────────────────────────────

@Composable
private fun SignalTabContent(state: SignalUiState.Success) {
    val primary = state.primary
    val quality = if (primary != null) signalQuality(primary.dbm, primary.networkType)
    else SignalQuality.NO_SIGNAL

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {

        // ── 1. Gauge ───────────────────────────────────────────────────────
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to quality.color.copy(alpha = 0.09f),
                                    0.7f to Color.Transparent,
                                ),
                                radius = 500f,
                            )
                        ),
                ) {
                    SignalGauge(cell = primary, modifier = Modifier.padding(vertical = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                if (primary != null) {
                    Text(
                        primary.operator,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                QualityBadge(quality = quality)
            }
        }

        // ── 2. Identificadores de célula (TAC, eNB, Cell ID, PCI, ARFCN…) ─
        if (primary != null) {
            item {
                CellIdentifiersCard(cell = primary)
            }
        }

        // ── 3. Métricas de qualidade de sinal ─────────────────────────────
        // Seção sempre visível quando há célula primária — mostra no mínimo
        // dBm, ASU e Nível. Se o hardware reportar mais (RSRP, RSRQ, CQI,
        // SS-SINR…) elas aparecem automaticamente via extraMetrics.
        if (primary != null) {
            item {
                Column {
                    SectionHeader("Métricas de sinal")
                    // Os campos base (ASU, Nível) são sempre exibidos. dBm só é
                    // adicionado manualmente para tecnologias que NÃO colocam já
                    // um dBm nomeado em extraMetrics (ver NetworkType.hasDedicatedDbmMetric).
                    val allMetrics = buildMap {
                        if (!primary.networkType.hasDedicatedDbmMetric) put("dBm", "${primary.dbm} dBm")
                        put("ASU", "${primary.asuLevel}")
                        put("Nível", "${primary.level} / 4")
                        putAll(primary.extraMetrics)
                    }
                    allMetrics.entries.toList().chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        ) {
                            row.forEach { (k, v) ->
                                MetricCard(
                                    label = k,
                                    value = v,
                                    accent = quality.color,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── 4. Células vizinhas detectadas ─────────────────────────────────
        if (state.allCells.isNotEmpty()) {
            item {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        Text(
                            "REDES DETECTADAS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.8.sp,
                        )
                        Text("${state.allCells.size} cel.", fontSize = 11.sp, color = TextDim)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.allCells.forEach { cell -> CellRow(cell = cell) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Live dot no TopBar ─────────────────────────────────────────────────────

@Composable
private fun LiveDot(updatedAt: Long) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val timeStr = remember(updatedAt) { fmt.format(Date(updatedAt)) }
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot_a",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(QualityExcel.copy(alpha = pulse)),
        )
        Text(timeStr, fontSize = 11.sp, color = TextMuted)
    }
}