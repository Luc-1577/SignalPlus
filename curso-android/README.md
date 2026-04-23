# 📱 Curso Android Moderno — Do Zero ao Sênior

> Aprenda a criar apps Android modernos, bonitos e bem arquitetados usando **Kotlin + Jetpack Compose + MVVM**.
> Este curso usa o app **SignalMeter** como projeto real de referência.

---

## 🗺️ Mapa do Curso

```
VOCÊ ESTÁ AQUI
      │
      ▼
┌─────────────────────────────────────────────────────────────────┐
│  MÓDULO 01 — Kotlin Essencial                                   │
│  Aprenda a linguagem antes de tudo                              │
│  📄 kotlin-essencial.md · null-safety.md · colecoes.md         │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  MÓDULO 02 — Arquitetura                                        │
│  Por que organizar o código importa                             │
│  📄 mvvm.md · modelos-de-dados.md                              │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  MÓDULO 03 — Coroutines & Flow                                  │
│  Código assíncrono sem sofrimento                               │
│  📄 coroutines.md · dispatchers.md · stateflow.md              │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  MÓDULO 04 — Jetpack Compose                                    │
│  A nova forma de construir UIs no Android                       │
│  📄 compose-basico.md · estado.md · layouts.md                 │
│  📄 animacoes.md · canvas.md                                   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  MÓDULO 05 — Construindo o App Completo                         │
│  Mão na massa: SignalMeter do zero                              │
│  📄 setup.md · dados.md · repositorio.md                       │
│  📄 viewmodel.md · telas.md · componentes.md                   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  MÓDULO 06 — Design e Tema                                      │
│  Fazendo o app ficar bonito de verdade                          │
│  📄 design-system.md                                           │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  MÓDULO 07 — Caminho para Sênior                                │
│  O que aprender depois deste curso                              │
│  📄 testes.md · proximos-passos.md                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📚 Índice Completo

### Módulo 01 — Kotlin Essencial
| Arquivo | O que você vai aprender |
|---------|------------------------|
| [01 - Kotlin Essencial](modulo-01-kotlin/01-kotlin-essencial.md) | Variáveis, funções, classes, lambdas, extensões |
| [02 - Null Safety](modulo-01-kotlin/02-null-safety.md) | O sistema de tipos nullable do Kotlin |
| [03 - Coleções Funcionais](modulo-01-kotlin/03-colecoes-funcionais.md) | map, filter, let, run, apply, also |

### Módulo 02 — Arquitetura
| Arquivo | O que você vai aprender |
|---------|------------------------|
| [01 - MVVM do Zero](modulo-02-arquitetura/01-mvvm.md) | Por que arquitetura importa, MVVM completo |
| [02 - Modelos de Dados](modulo-02-arquitetura/02-modelos-dados.md) | data class, sealed class, enum, imutabilidade |

### Módulo 03 — Coroutines & Flow
| Arquivo | O que você vai aprender |
|---------|------------------------|
| [01 - Coroutines](modulo-03-coroutines/01-coroutines.md) | O que são, suspend, launch, async |
| [02 - Dispatchers](modulo-03-coroutines/02-dispatchers.md) | Main, IO, Default — onde cada código roda |
| [03 - StateFlow](modulo-03-coroutines/03-stateflow.md) | Flow, StateFlow, MutableStateFlow |

### Módulo 04 — Jetpack Compose
| Arquivo | O que você vai aprender |
|---------|------------------------|
| [01 - Compose Básico](modulo-04-compose/01-compose-basico.md) | @Composable, recomposição, paradigma declarativo |
| [02 - Estado](modulo-04-compose/02-estado.md) | remember, mutableStateOf, collectAsState |
| [03 - Layouts](modulo-04-compose/03-layouts.md) | Column, Row, Box, LazyColumn, Modifier |
| [04 - Animações](modulo-04-compose/04-animacoes.md) | animateFloatAsState, AnimatedContent, InfiniteTransition |
| [05 - Canvas](modulo-04-compose/05-canvas.md) | drawArc, drawPath, drawCircle, geometria |

### Módulo 05 — Construindo o App
| Arquivo | O que você vai aprender |
|---------|------------------------|
| [01 - Setup do Projeto](modulo-05-projeto/01-setup.md) | Criar projeto, Gradle, Version Catalog |
| [02 - Camada de Dados](modulo-05-projeto/02-dados.md) | SignaData.kt, FrequencyUtils.kt, SignalHistory.kt |
| [03 - Repositório](modulo-05-projeto/03-repositorio.md) | SignalRepository + TelephonyManager |
| [04 - ViewModel](modulo-05-projeto/04-viewmodel.md) | SignalViewModel, polling, StateFlow |
| [05 - Telas e Permissões](modulo-05-projeto/05-telas.md) | MainScreen, permissões, navegação por abas |
| [06 - Componentes Visuais](modulo-05-projeto/06-componentes.md) | Gauge, Chart, Cards, Badges |

### Módulo 06 — Design
| Arquivo | O que você vai aprender |
|---------|------------------------|
| [01 - Design System](modulo-06-design/01-design-system.md) | Paleta semântica, tipografia, Material3 |

### Módulo 07 — Caminho para Sênior
| Arquivo | O que você vai aprender |
|---------|------------------------|
| [01 - Testes](modulo-07-senior/01-testes.md) | JUnit, testes unitários, testes de composable |
| [02 - Próximos Passos](modulo-07-senior/02-proximos-passos.md) | Hilt, Room, Navigation, o roadmap completo |

---

## ⏱️ Tempo Estimado

| Módulo | Tempo de estudo | Dificuldade |
|--------|----------------|-------------|
| Módulo 01 | 3–5 horas | ⭐ Iniciante |
| Módulo 02 | 2–3 horas | ⭐⭐ Fácil |
| Módulo 03 | 4–6 horas | ⭐⭐⭐ Médio |
| Módulo 04 | 6–10 horas | ⭐⭐⭐ Médio |
| Módulo 05 | 8–12 horas | ⭐⭐⭐⭐ Difícil |
| Módulo 06 | 2–3 horas | ⭐⭐ Fácil |
| Módulo 07 | 4–8 horas | ⭐⭐⭐⭐ Difícil |

**Total estimado: 30–47 horas de estudo sólido.**

---

## 💡 Como usar este curso

1. **Não pule módulos.** Cada um assume conhecimento do anterior.
2. **Digite o código, não copie.** A digitação cria memória muscular.
3. **Quebre o código de propósito.** Entender erros é metade do aprendizado.
4. **Consulte o projeto real** (pasta `app/`) para ver tudo funcionando junto.
5. **Faça os desafios** ao final de cada seção — eles consolidam o aprendizado.

---

> *"Todo sênior já foi iniciante. A diferença é que o sênior errou mais vezes — e aprendeu com cada uma."*
