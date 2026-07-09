# Módulo 07 · Aula 02 — Próximos Passos: O Roadmap para Sênior

> **Objetivo:** Saber exatamente o que estudar depois deste curso para se tornar um desenvolvedor Android sênior.

---

## O que você já sabe

Parabéns por chegar até aqui! Você já domina:

```
✅ Kotlin — val/var, data class, sealed class, enum, lambdas, null safety
✅ Coleções funcionais — map, filter, forEach, sortedBy
✅ Arquitetura MVVM — Model, View, ViewModel, UDF
✅ Coroutines — suspend, launch, async, withContext, delay
✅ Dispatchers — Main, IO, Default
✅ StateFlow — MutableStateFlow, asStateFlow, collectAsStateWithLifecycle
✅ Jetpack Compose — @Composable, recomposição, Modifier, LazyColumn
✅ Estado no Compose — remember, LaunchedEffect, DisposableEffect
✅ Layouts — Column, Row, Box, Scaffold, NavigationBar
✅ Animações — animateFloatAsState, AnimatedContent, InfiniteTransition
✅ Canvas — drawArc, drawPath, Path Bézier, Brush gradiente
✅ Design System — paleta semântica, Material 3, tipografia
✅ Testes unitários — JUnit, testando lógica pura
✅ Gradle — Version Catalog, build types, ProGuard
```

---

## O Roadmap Sênior

### Nível 2 — Intermediário (3–6 meses)

#### 1. Hilt — Injeção de Dependência

```kotlin
// Sem Hilt (como está o projeto):
class SignalViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SignalRepository(app)   // acoplado!
}

// Com Hilt — desacoplado e testável:
@HiltViewModel
class SignalViewModel @Inject constructor(
    private val repository: SignalRepository,   // injetado pelo Hilt
) : ViewModel()

// No teste, você injeta um FakeSignalRepository
```

**Por que aprender:** Sem injeção de dependência, classes ficam acopladas e difíceis de testar.
Hilt é a solução oficial do Google para Android.

**Recursos:**
- Codelab oficial: [developer.android.com/codelabs/android-hilt](https://developer.android.com/codelabs/android-hilt)

---

#### 2. Room — Banco de dados local

```kotlin
// Entidade
@Entity(tableName = "signal_readings")
data class SignalReading(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestampMs: Long,
    val dbm        : Int,
    val networkType: String,
)

// DAO
@Dao
interface SignalReadingDao {
    @Insert
    suspend fun insert(reading: SignalReading)

    @Query("SELECT * FROM signal_readings ORDER BY timestampMs DESC LIMIT 100")
    fun observeRecent(): Flow<List<SignalReading>>

    @Query("DELETE FROM signal_readings")
    suspend fun deleteAll()
}

// Database
@Database(entities = [SignalReading::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signalReadingDao(): SignalReadingDao
}
```

**Por que aprender:** O `SignalHistory` atual perde dados ao fechar o app. Room persiste.

---

#### 3. Navigation Compose — navegação entre telas

Desde o Navigation 2.8, a forma recomendada é **type-safe**: cada destino é uma
classe `@Serializable`, sem rotas em `String` (elimina erros de digitação e de tipo
em tempo de compilação):

```kotlin
@Serializable
object Main

@Serializable
data class Details(val cellId: String)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = Main) {
        composable<Main> {
            MainScreen(
                onNavigateToDetails = { cellId ->
                    navController.navigate(Details(cellId))
                }
            )
        }
        composable<Details> { backStackEntry ->
            val details = backStackEntry.toRoute<Details>()
            CellDetailsScreen(cellId = details.cellId)
        }
    }
}
```

**Por que aprender:** Para apps com múltiplas telas, Navigation é indispensável.

---

#### 4. Retrofit + OkHttp — comunicação com APIs REST

```kotlin
// Interface da API
interface SignalApi {
    @GET("towers/{lat}/{lng}")
    suspend fun getTowersNearby(
        @Path("lat") lat: Double,
        @Path("lng") lng: Double,
    ): List<TowerDto>
}

// Criando o cliente
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.exemplo.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val api = retrofit.create(SignalApi::class.java)

// Usando no repositório
suspend fun getTowersNearby(lat: Double, lng: Double): List<Tower> =
    withContext(Dispatchers.IO) {
        api.getTowersNearby(lat, lng).map { it.toTower() }
    }
```

---

#### 5. DataStore — preferências do usuário

```kotlin
// Substituto moderno do SharedPreferences
val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

val POLL_INTERVAL_KEY = intPreferencesKey("poll_interval")

// Escrevendo
suspend fun setPollInterval(seconds: Int) {
    context.dataStore.edit { prefs ->
        prefs[POLL_INTERVAL_KEY] = seconds
    }
}

// Lendo (como Flow!)
val pollInterval: Flow<Int> = context.dataStore.data
    .map { prefs -> prefs[POLL_INTERVAL_KEY] ?: 1 }
```

---

### Nível 3 — Avançado (6–12 meses)

#### 6. Testing avançado — MockK e Turbine

```kotlin
// MockK — mocking framework idiomático para Kotlin
@Test
fun `deve publicar Success quando repositorio retorna celulas`() = runTest {
    val mockRepo = mockk<SignalRepository>()
    coEvery { mockRepo.readCells() } returns listOf(
        CellSignalData(NetworkType.LTE, -87, 43, 3, true, "Vivo")
    )

    val vm = SignalViewModel(app, mockRepo)
    vm.startPolling()
    advanceTimeBy(1_500)

    // Turbine — testa flows facilmente
    vm.uiState.test {
        val state = awaitItem()
        assertIs<SignalUiState.Success>(state)
        assertEquals(-87, state.primary?.dbm)
        cancelAndIgnoreRemainingEvents()
    }
}
```

---

#### 7. Compose Performance — evitando recomposições desnecessárias

```kotlin
// Problema: lambda recriada a cada recomposição
@Composable
fun TelaProblema(vm: ViewModel) {
    // Esta lambda é uma nova instância a cada recomposição!
    Button(onClick = { vm.doAction() }) { Text("Ação") }
}

// Solução: remember para estabilizar lambdas
@Composable
fun TelaCorreta(vm: ViewModel) {
    val onAction = remember(vm) { { vm.doAction() } }
    Button(onClick = onAction) { Text("Ação") }
}

// Solução 2: usar @Stable e @Immutable para classes de dados
@Immutable
data class SignalUiModel(val dbm: Int, val type: NetworkType)
// Compose sabe que esta classe não muda internamente → recompõe menos
```

---

#### 8. WorkManager — tarefas em background

```kotlin
// Para tarefas que precisam rodar mesmo com o app fechado
class SignalRecordingWorker(context: Context, params: WorkerParameters)
    : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cells = repository.readCells()
        database.signalReadingDao().insertAll(cells.toReadings())
        return Result.success()
    }
}

// Agendando
val request = PeriodicWorkRequestBuilder<SignalRecordingWorker>(
    repeatInterval = 15, TimeUnit.MINUTES
).build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "signal_recording",
    ExistingPeriodicWorkPolicy.KEEP,
    request,
)
```

---

#### 9. Flow avançado — SharedFlow, combine, flatMapLatest

```kotlin
// combine — atualiza quando qualquer um dos dois Flows muda
val qualidadeFlow = combine(dbmFlow, networkTypeFlow) { dbm, type ->
    signalQuality(dbm, type)
}

// flatMapLatest — cancela a anterior ao receber novo valor
val detalhesFlow = cellIdFlow.flatMapLatest { cellId ->
    repository.observeCellDetails(cellId)   // cancela observação anterior
}

// SharedFlow para eventos únicos (toast, navegação)
private val _events = MutableSharedFlow<UiEvent>()
val events = _events.asSharedFlow()

fun navigateToDetails(cellId: String) {
    viewModelScope.launch {
        _events.emit(UiEvent.Navigate("details/$cellId"))
    }
}
```

---

#### 10. Compose Modifiers customizados

```kotlin
// Criando um Modifier customizado
fun Modifier.signalGlow(color: Color, intensity: Float): Modifier = this.then(
    Modifier.drawBehind {
        drawCircle(
            color  = color.copy(alpha = intensity * 0.3f),
            radius = size.minDimension * 0.8f,
            center = center,
        )
    }
)

// Usando:
Box(modifier = Modifier.signalGlow(quality.color, 0.7f)) {
    Text("-87 dBm")
}
```

---

### Recursos para estudar

#### Documentação oficial
- [developer.android.com/jetpack/compose](https://developer.android.com/jetpack/compose)
- [developer.android.com/kotlin/coroutines](https://developer.android.com/kotlin/coroutines)
- [developer.android.com/topic/architecture](https://developer.android.com/topic/architecture)

#### Codelabs (tutoriais práticos do Google)
- [Jetpack Compose Basics](https://developer.android.com/codelabs/jetpack-compose-basics)
- [Advanced State and Side Effects in Compose](https://developer.android.com/codelabs/jetpack-compose-advanced-state-side-effects)
- [Hilt in Android Apps](https://developer.android.com/codelabs/android-hilt)
- [Room with a View](https://developer.android.com/codelabs/android-room-with-a-view-kotlin)

#### Livros
- *Kotlin in Action* (Dmitry Jemerov & Svetlana Isakova)
- *Android Development with Kotlin* (Marcin Moskala)
- *Jetpack Compose Internals* (Jorge Castillo)

---

## Linha do tempo realista

```
Mês 1–2   → Completar este curso, fazer o SignalMeter funcionar
Mês 3–4   → Adicionar Room ao SignalMeter + aprender Hilt
Mês 5–6   → Fazer um app próprio do zero (qualquer ideia!)
Mês 7–9   → Aprender Navigation, Retrofit, testes com MockK
Mês 10–12 → Contribute para projetos open source, ou freelance
Ano 2+    → Especialização (Compose animations, performance, arquitetura modular)
```

---

## O segredo para ficar bom de verdade

```
1. Código todos os dias — mesmo que 30 minutos
2. Leia código de outros — GitHub, projetos open source
3. Quebre coisas de propósito — entender o erro é metade do aprendizado
4. Faça apps reais — projetos pessoais ensinam mais que tutoriais
5. Explique para outros — se você não consegue explicar, não entendeu
6. Aceite que nunca vai saber tudo — sênior = sabe onde procurar
```

---

## 🎯 Desafio final do curso

**Adicione estas funcionalidades ao SignalMeter:**

1. **Persistência** — use Room para salvar o histórico de leituras (não perde ao fechar)
2. **Configurações** — use DataStore para salvar o intervalo de polling preferido (1s, 2s, 5s)
3. **Compartilhar** — adicione um botão para exportar o histórico como CSV
4. **Widget** — crie um widget de tela inicial que mostra o dBm atual

---

## 📌 Resumo do curso inteiro

```
Módulo 01 → Kotlin: val/var, data class, sealed, enum, lambdas, null safety
Módulo 02 → MVVM: separação de responsabilidades, UDF, modelos de dados
Módulo 03 → Coroutines: suspend, launch, withContext, StateFlow
Módulo 04 → Compose: @Composable, estado, layouts, animações, Canvas
Módulo 05 → Projeto: setup, dados, repositório, ViewModel, telas, componentes
Módulo 06 → Design: paleta semântica, Material 3, hierarquia visual
Módulo 07 → Senior: testes JUnit, roadmap para especialização

Próximo: Hilt, Room, Navigation, Retrofit, MockK, WorkManager
```

---

> *"O sênior não sabe mais que o júnior porque estudou por mais tempo.*
> *O sênior cometeu mais erros — e aprendeu com cada um."*

**Bons estudos, e bora codar! 🚀**
