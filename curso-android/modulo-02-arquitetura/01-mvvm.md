# Módulo 02 · Aula 01 — Arquitetura MVVM do Zero

> **Pré-requisito:** Módulo 01 completo
> **Objetivo:** Entender por que arquitetura importa e como o MVVM funciona na prática.

---

## Por que arquitetura importa?

Imagine que você está construindo um app. No começo, tudo está numa `Activity`:

```kotlin
// ❌ Código sem arquitetura — a "God Activity"
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lógica de UI
        val textView = findViewById<TextView>(R.id.dbmText)

        // Lógica de negócio
        val telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        // Lógica de dados
        val cells = telephony.allCellInfo
        val primary = cells?.firstOrNull { it.isRegistered }

        // Tudo misturado!
        textView.text = "${primary?.dbm} dBm"

        // E a Activity também gerencia o timer...
        val timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() { /* atualiza */ }
        }, 0, 1000)
    }
}
```

Isso funciona para um protótipo. Mas em 3 meses:
- O arquivo tem 1000 linhas
- Você não consegue testar a lógica sem um Android
- Girar o celular reinicia o timer e perde dados
- Adicionar uma nova funcionalidade exige entender tudo ao mesmo tempo

---

## A Solução: Separar Responsabilidades

**Princípio:** Cada classe deve ter uma única razão para existir.

O MVVM divide o código em três camadas:

```
┌──────────────────────────────────────────────────────┐
│  VIEW (o que o usuário vê)                           │
│  Composables, Activities, Fragments                  │
│  "Eu apenas mostro dados e reporto eventos"          │
└─────────────────────┬────────────────────────────────┘
                      │ observa / chama
                      ▼
┌──────────────────────────────────────────────────────┐
│  VIEWMODEL (o que a tela precisa saber)              │
│  Estado, lógica de UI, ciclo de vida                 │
│  "Eu processo dados e publico estado"                │
└─────────────────────┬────────────────────────────────┘
                      │ usa
                      ▼
┌──────────────────────────────────────────────────────┐
│  MODEL (os dados reais)                              │
│  Repository, data classes, APIs                      │
│  "Eu forneço dados, não sei nada de UI"              │
└──────────────────────────────────────────────────────┘
```

---

## MVVM no SignalMeter

### A View — `MainScreen.kt`

```kotlin
@Composable
fun MainScreen(vm: SignalViewModel = viewModel()) {
    // Observa o estado do ViewModel
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Só renderiza baseado no estado
    when (state) {
        is SignalUiState.Loading  -> LoadingScreen()
        is SignalUiState.Success  -> SignalContent(state)
        is SignalUiState.Error    -> ErrorScreen(state.message)
        ...
    }
}
```

A View **não** sabe como os dados chegam. Ela apenas:
1. Observa o `uiState`
2. Renderiza o estado atual
3. Delega ações ao ViewModel (`vm.startPolling()`, `vm.selectTab()`)

### O ViewModel — `SignalViewModel.kt`

```kotlin
class SignalViewModel(app: Application) : AndroidViewModel(app) {

    // Referência ao repositório (Model)
    private val repository = SignalRepository(app)

    // Estado publicado para a View
    private val _uiState = MutableStateFlow<SignalUiState>(SignalUiState.Loading)
    val uiState: StateFlow<SignalUiState> = _uiState.asStateFlow()

    // Lógica: loop de polling
    fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                val cells = repository.readCells()          // usa o Model
                _uiState.value = SignalUiState.Success(cells) // publica novo estado
                delay(1_000L)
            }
        }
    }
}
```

O ViewModel **não** importa nada de Compose. Ele:
1. Usa o repositório para obter dados
2. Processa e transforma os dados
3. Publica o estado via `StateFlow`
4. Sobrevive a rotações de tela

### O Model — `SignalRepository.kt`

```kotlin
class SignalRepository(private val context: Context) {

    suspend fun readCells(): List<CellSignalData> = withContext(Dispatchers.IO) {
        // Acessa o hardware
        val rawList = telephony.allCellInfo
        // Parseia e retorna
        rawList?.mapNotNull { parseCellInfo(it) } ?: emptyList()
    }
}
```

O Repositório **não** sabe nada de UI. Ele:
1. Acessa hardware, banco de dados ou API
2. Transforma os dados brutos em modelos limpos
3. Retorna resultados sem se importar com quem chamou

---

## O Fluxo de Dados Unidirecional (UDF)

Um princípio crucial do MVVM moderno:

```
Eventos sobem:    View → ViewModel → Model
Dados descem:     Model → ViewModel → View

NUNCA o contrário:
 ✗ Model não conhece o ViewModel
 ✗ ViewModel não conhece a View
```

```
Usuário toca "Atualizar"
       │
       ▼
View: vm.startPolling()           ← evento sobe
       │
       ▼
ViewModel: repository.readCells() ← delega ao model
       │
       ▼
Model: telephony.allCellInfo      ← acessa hardware
       │
       ▼
Modelo retorna List<CellInfo>
       │
       ▼
ViewModel: _uiState.value = ...   ← processa e publica
       │
       ▼
View: recompõe com novo estado    ← dado desce
```

---

## Por que ViewModel sobrevive à rotação?

A `Activity` é destruída e recriada quando você gira o celular. Mas o `ViewModel` não:

```
Girou o celular
       │
       ▼
Android destrói MainActivity
Android cria nova MainActivity
       │
       ▼
viewModel() detecta que já existe um ViewModel para esta tela
Retorna o MESMO ViewModel (não cria novo!)
       │
       ▼
O novo Composable observa o mesmo StateFlow
O loop de polling continua sem interrupção
O histórico de 90 leituras está intacto
```

**Como o Android sabe que é "o mesmo" ViewModel?**
Ele usa a `Activity` como chave — enquanto a Activity não for concluída de verdade
(botão voltar, `finish()`), o ViewModel persiste.

---

## Vantagens do MVVM na prática

| Situação | Sem MVVM | Com MVVM |
|----------|----------|----------|
| Rotação de tela | Estado perdido, tudo reinicia | Estado preservado automaticamente |
| Testes | Precisa de emulador para testar lógica | Lógica testável em JVM puro |
| Mudança de design | Reescreve tudo | Só muda a View |
| Nova fonte de dados | Reescreve tudo | Só muda o Repository |
| Colaboração em equipe | Conflitos constantes | Times independentes por camada |

---

## A regra de dependência

Uma forma simples de lembrar:

```
View conhece ViewModel
ViewModel conhece Model
Model não conhece ninguém além de si mesmo

View NÃO conhece Model diretamente
Model NÃO conhece View ou ViewModel
```

Se você se pegar fazendo `import com.example.ls.ui.*` dentro de `data/`, algo está errado.

---

## MVVM vs. alternativas

### MVP (Model-View-Presenter)
```
View ←→ Presenter ←→ Model
```
O Presenter tem referência à View (interface). Funciona, mas:
- Testa bem o Presenter, mas ainda acoplado à interface
- Não se integra naturalmente com Compose (que é stateless)
- Hoje é considerado ultrapassado para novos projetos

### MVI (Model-View-Intent)
```
View → Intent → Model.reduce() → State → View
```
Mais estrito: toda mudança de estado é uma função pura `reduce(oldState, intent) → newState`.
Vantagens:
- Estado completamente previsível e rastreável
- Fácil de fazer time-travel debugging

Desvantagens:
- Mais verboso (um Intent por ação, um reducer)
- Overhead para casos simples como polling

**Quando usar MVI:** Apps com formulários complexos, estados interdependentes, ou onde
auditabilidade de estado é crítica.

---

## 🧪 Desafio

**Exercício de design:** Você vai criar um app de calculadora de IMC.

Identifique quais elementos pertencem a cada camada:
- Campo de peso (Float)
- Campo de altura (Float)
- Botão "Calcular"
- Resultado do IMC (Double?)
- Classificação ("Normal", "Sobrepeso"...)
- Fórmula: `imc = peso / (altura * altura)`
- Texto exibido na tela
- Cor do texto (verde, amarelo, vermelho)

Preencha:
```
MODEL:     _______________________________________________
VIEWMODEL: _______________________________________________
VIEW:      _______________________________________________
```

---

## 📌 Resumo

```
MODEL      → dados e regras de negócio (não sabe de UI)
VIEWMODEL  → estado e orquestração (não sabe de Compose)
VIEW       → renderização (não sabe de hardware/dados)

UDF        → dados fluem para baixo, eventos sobem
StateFlow  → o canal de comunicação ViewModel → View
viewModel()→ garante que o ViewModel sobrevive a rotações
```

**Próxima aula →** [Modelos de Dados](02-modelos-dados.md) — como representar dados com data class, sealed class e enum class.
