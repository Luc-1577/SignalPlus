package com.example.ls.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ls.data.*
import com.example.ls.ui.AppTab
import com.example.ls.ui.SignalViewModel
import com.example.ls.ui.components.*
import com.example.ls.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ── Tabs ───────────────────────────────────────────────────────────────────

private data class TabItem(val tab: AppTab, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem(AppTab.SIGNAL, "Sinal", Icons.Default.SignalCellularAlt),
    TabItem(AppTab.HISTORY, "Histórico", Icons.Default.ShowChart),
    TabItem(AppTab.INFO, "Info", Icons.Default.Info),
)

// ── MainScreen ─────────────────────────────────────────────────────────────

@Composable
fun MainScreen(vm: SignalViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val currentTab by vm.currentTab.collectAsStateWithLifecycle()

    val perms = remember {
        buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }.toTypedArray()
    }

    fun hasPerms() = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) vm.startPolling() else vm.onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (hasPerms()) vm.startPolling() else launcher.launch(perms)
    }
    DisposableEffect(Unit) { onDispose { vm.stopPolling() } }

    Scaffold(
        containerColor = BgDeep,
        topBar = { AppTopBar(state) },
        bottomBar = {
            AppBottomBar(currentTab = currentTab, onTabClick = { vm.selectTab(it) })
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                is SignalUiState.Loading -> LoadingScreen()
                is SignalUiState.PermissionRequired -> PermissionScreen { launcher.launch(perms) }
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
                            )

                            AppTab.INFO -> InfoScreen()
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
                Text(
                    "Signal Meter",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
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
                icon = { Icon(item.icon, item.label, Modifier.size(22.dp)) },
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
private fun PermissionScreen(onRequest: () -> Unit) {
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
                "Permita acesso ao telefone e localização para medir o sinal real da antena.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(32.dp))
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
                    // Os campos base (ASU, Nível) são sempre exibidos.
                    // dBm só é adicionado manualmente para tecnologias que NÃO
                    // colocam já o dBm no extraMetrics com outro nome
                    // (LTE já coloca "RSRP", 5G já coloca "SS-RSRP",
                    //  3G já coloca "RSCP", 2G já coloca "RSSI").
                    val hasDedicated = primary.networkType in listOf(
                        NetworkType.LTE, NetworkType.NR_5G,
                        NetworkType.WCDMA, NetworkType.HSPA,
                        NetworkType.GSM_2G,
                    )
                    val allMetrics = buildMap {
                        if (!hasDedicated) put("dBm", "${primary.dbm} dBm")
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
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
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