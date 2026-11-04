# Signal+

Aplicativo Android para diagnóstico de sinal RF de redes móveis — **5G NR, LTE, WCDMA e GSM** — com leitura direta do modem via `TelephonyManager`, visualização em tempo real e exportação dos dados.

Projeto pessoal, escrito em Kotlin com Jetpack Compose.

## Funcionalidades

- **Leitura de sinal em tempo real** das células servidora e vizinhas, por tecnologia:
  - **5G NR:** SS-RSRP, SS-RSRQ, SS-SINR
  - **LTE:** RSRP, RSRQ, SINR, Timing Advance
  - **WCDMA / GSM:** nível de sinal (ASU/dBm)
- **Identificadores de rádio:** PCI, TAC/LAC, Cell ID (NCI/CI/CID), eNB/gNB ID e ARFCN.
- **Conversão de frequência conforme 3GPP:** traduz EARFCN (LTE), NR-ARFCN (5G), UARFCN (WCDMA) e ARFCN (GSM) em banda e frequência em MHz.
- **Estimativa da posição de torres:** calcula a localização aproximada de cada célula por centroide das leituras ponderado pela potência do sinal (dBm convertido para escala linear), combinado com o GPS do aparelho.
- **Histórico persistente** das leituras e **exportação em CSV**.
- **Gráficos e medidores** desenhados em tempo real com Canvas.
- **Suporte a dual-SIM**, com seletor do chip em Configurações.
- Interface com tema claro/escuro e cuidado de contraste (WCAG AA).

## Arquitetura

- **Padrão:** MVVM com `StateFlow`.
- **UI:** Jetpack Compose + Material 3, organizada em telas (principal, histórico, torres, exportação, configurações, info).
- **Persistência:** Room (histórico de leituras e posições de torres, com migrações versionadas) e DataStore (preferências).
- **Camada pura testável:** a conversão de frequências (`FrequencyUtils`) e a matemática de triangulação (`TowerPositionMath`) são isoladas de dependências do Android, o que permite testá-las como testes unitários de JVM.

## Testes

Cerca de 180 testes, entre unitários (JVM) e instrumentados (Android), cobrindo conversão de frequências por banda, a matemática de estimativa de torres, os DAOs e migrações do Room, a formatação/exportação CSV e a semântica de acessibilidade da UI.

## Stack

Kotlin · Jetpack Compose · Material 3 · MVVM · StateFlow · Room · DataStore · `TelephonyManager` · Kotlin Coroutines

## Como rodar

**Requisitos**
- Android Studio recente (AGP 9.x, Kotlin 2.4, JDK 17)
- Dispositivo físico com Android 8.0 (API 26) ou superior — *os dados de rádio não aparecem em emulador*

**Passos**
```bash
git clone https://github.com/Luc-1577/SignalPlus.git
cd SignalPlus
```
Abra o projeto no Android Studio e rode no dispositivo (`Run ▶`), ou pela linha de comando:
```bash
./gradlew installDebug
```

**Permissões** solicitadas em tempo de execução, necessárias para a leitura do sinal e a estimativa de posição:
- `READ_PHONE_STATE`
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`

## Observações

As métricas disponíveis dependem do fabricante, da versão do Android e da rede: alguns campos podem vir vazios quando o hardware não os expõe. O app apenas **lê** informações já fornecidas pelo sistema operacional e não realiza nenhuma transmissão ou alteração na rede.
