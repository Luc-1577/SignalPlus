# Módulo 05 · Aula 05 — Telas: Permissões, Navegação e Composição da UI

> **Objetivo:** Implementar a tela principal com gerenciamento de permissões, Scaffold e navegação por abas.

---

## Hierarquia das telas

```
MainScreen
├── AppTopBar            (TopAppBar com LiveDot)
├── AppBottomBar         (NavigationBar com 3 abas)
└── Conteúdo (muda por estado e aba):
    ├── Loading          → LoadingScreen
    ├── PermissionRequired → PermissionScreen
    ├── Error            → ErrorScreen
    └── Success:
        ├── SIGNAL  → SignalTabContent
        ├── HISTORY → HistoryScreen
        └── INFO    → InfoScreen
```

---

## `MainScreen` — a tela orquestradora

```kotlin
@Composable
fun MainScreen(vm: SignalViewModel = viewModel()) {
    val context    = LocalContext.current
    val state      by vm.uiState.collectAsStateWithLifecycle()
    val currentTab by vm.currentTab.collectAsStateWithLifecycle()

    // ── Permissões ──────────────────────────────────────────
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
        // results = Map<String, Boolean> — permissão → concedida?
        if (results.values.any { it }) vm.startPolling()
        else vm.onPermissionDenied()
    }

    // Verifica permissão ao entrar na tela (roda 1 vez)
    LaunchedEffect(Unit) {
        if (hasPerms()) vm.startPolling()
        else launcher.launch(perms)
    }

    // Para o polling quando a tela sai de composição
    DisposableEffect(Unit) {
        onDispose { vm.stopPolling() }
    }

    // ── Layout principal ────────────────────────────────────
    Scaffold(
        containerColor = BgDeep,
        topBar = { AppTopBar(state) },
        bottomBar = {
            AppBottomBar(
                currentTab = currentTab,
                onTabClick = { vm.selectTab(it) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                is SignalUiState.Loading          -> LoadingScreen()
                is SignalUiState.PermissionRequired -> PermissionScreen { launcher.launch(perms) }
                is SignalUiState.Error            -> ErrorScreen(s.message) { launcher.launch(perms) }
                is SignalUiState.Success          -> {
                    // AnimatedContent anima a transição entre abas
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            slideInHorizontally { dir * it } + fadeIn() togetherWith
                                slideOutHorizontally { -dir * it } + fadeOut()
                        },
                        label = "tabs",
                    ) { tab ->
                        when (tab) {
                            AppTab.SIGNAL  -> SignalTabContent(s)
                            AppTab.HISTORY -> HistoryScreen(
                                history = vm.history,
                                onClear = { vm.clearHistory() },
                            )
                            AppTab.INFO    -> InfoScreen()
                        }
                    }
                }
            }
        }
    }
}
```

---

## Entendendo o gerenciamento de permissões

### Por que permissão de localização para sinal?

No Android 9 (API 28) e superior, a Google exigiu permissão de localização para
`getAllCellInfo()` e campos como `CellIdentityLte.tac` e `CellIdentityNr.tac`.

O motivo: a posição de antenas celulares pode ser usada para triangular a localização
física do usuário. Para proteger a privacidade, exige consentimento explícito.

```
API < 28 (Android < 9):  READ_PHONE_STATE é suficiente
API ≥ 28 (Android 9+):   READ_PHONE_STATE + ACCESS_FINE_LOCATION necessários
```

### O fluxo de permissão

```
LaunchedEffect(Unit) {
    ├─ hasPerms() == true  →  vm.startPolling()  [polling inicia imediatamente]
    └─ hasPerms() == false →  launcher.launch()
        ├─ Sistema exibe diálogo
        ├─ results.any { it } == true  →  vm.startPolling()  [usuário concedeu]
        └─ results.all { !it } == false →  vm.onPermissionDenied() [usuário negou]
            └─ _uiState = PermissionRequired  [UI mostra tela de permissão]
```

---

## A barra superior com LiveDot

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(state: SignalUiState) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.SignalCellularAlt, null, tint = AccentBlue)
                Text("Signal Meter", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        actions = {
            // LiveDot só aparece quando temos dados
            if (state is SignalUiState.Success) {
                LiveDot(updatedAt = state.updatedAt)
                Spacer(Modifier.width(16.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BgMid),
    )
}

@Composable
private fun LiveDot(updatedAt: Long) {
    // Formata o timestamp uma vez (quando updatedAt muda)
    val fmt     = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(updatedAt) { fmt.format(Date(updatedAt)) }

    // Animação infinita de "breathing"
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.35f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(1_100, easing = EaseInOutSine),
            RepeatMode.Reverse,
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
                .background(QualityExcel.copy(alpha = pulse)),  // verde pulsante
        )
        Text(timeStr, fontSize = 11.sp, color = TextMuted)
    }
}
```

---

## A barra de navegação inferior

```kotlin
private data class TabItem(
    val tab   : AppTab,
    val label : String,
    val icon  : ImageVector,
)

private val TABS = listOf(
    TabItem(AppTab.SIGNAL,  "Sinal",      Icons.Default.SignalCellularAlt),
    TabItem(AppTab.HISTORY, "Histórico",  Icons.Default.ShowChart),
    TabItem(AppTab.INFO,    "Info",        Icons.Default.Info),
)

@Composable
private fun AppBottomBar(currentTab: AppTab, onTabClick: (AppTab) -> Unit) {
    NavigationBar(containerColor = BgMid, tonalElevation = 0.dp) {
        TABS.forEach { item ->
            val selected = item.tab == currentTab

            NavigationBarItem(
                selected = selected,
                onClick  = { onTabClick(item.tab) },
                icon     = { Icon(item.icon, item.label, Modifier.size(22.dp)) },
                label    = {
                    Text(
                        item.label,
                        fontSize   = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = AccentBlue,
                    selectedTextColor   = AccentBlue,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor      = AccentBlue.copy(alpha = 0.12f),
                ),
            )
        }
    }
}
```

---

## Telas de estado

```kotlin
// Loading
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

// Permissão negada
@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(40.dp),
        ) {
            Icon(Icons.Default.SignalCellularAlt, null,
                tint = AccentBlue, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("Permissão necessária",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(
                "Permita acesso ao telefone e localização para medir o sinal real da antena.",
                fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRequest,
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Conceder permissões", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Erro
@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(40.dp),
        ) {
            Icon(Icons.Default.ErrorOutline, null,
                tint = QualityVeryPoor, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Erro ao ler sinal",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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
```

---

## Boas práticas para múltiplos estados de tela

### Padrão: estado como sealed class + when exaustivo

```kotlin
// ✅ O compilador garante que todos os estados são tratados
when (val s = state) {
    is SignalUiState.Loading          -> LoadingScreen()
    is SignalUiState.PermissionRequired -> PermissionScreen { ... }
    is SignalUiState.Error            -> ErrorScreen(s.message) { ... }
    is SignalUiState.Success          -> SuccessContent(s)
    // Se adicionar um novo estado, o compilador avisa aqui!
}
```

### Smart cast com `val s = state`

```kotlin
// Sem `val s = state`:
when (state) {
    is SignalUiState.Success -> {
        state.primary   // ERRO: state pode mudar entre o `is` e o acesso!
    }
}

// Com `val s = state` (captura o valor atual):
when (val s = state) {
    is SignalUiState.Success -> {
        s.primary   // seguro! s é imutável aqui
        s.allCells
    }
}
```

---

## 📌 Resumo

```kotlin
MainScreen
├── LaunchedEffect(Unit) → verifica/solicita permissões + startPolling()
├── DisposableEffect     → onDispose { stopPolling() }
├── Scaffold (topBar, bottomBar, content)
└── when(state) → Loading / PermissionRequired / Error / Success
    └── AnimatedContent(currentTab) → slide entre abas
        └── SIGNAL / HISTORY / INFO

Permissões:
├── remember { buildList { add(READ_PHONE_STATE); if(API>=28) add(LOCATION) } }
├── rememberLauncherForActivityResult(RequestMultiplePermissions)
└── results.any { it } → startPolling() || onPermissionDenied()
```

**Próxima aula →** [Componentes Visuais](06-componentes.md) — implementando Gauge, Chart e Cards do zero.
