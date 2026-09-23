# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Signal+** — Android app for RF signal diagnostics on mobile networks (5G NR, LTE, WCDMA, GSM, CDMA). It reads raw modem data via `TelephonyManager`, converts ARFCN values to frequencies using 3GPP standards, and presents live metrics with custom Canvas visualizations.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (R8 minification + resource shrinking)
./gradlew test                   # Run unit tests (JVM)
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew testDebugUnitTest      # Debug variant unit tests only
```

**SDK/toolchain:** compileSdk 36, minSdk 26, targetSdk 36, JVM target 17, Kotlin 2.4.0, AGP 9.1.1 (pinned below 9.2.0 for local Android Studio compatibility), Gradle 9.6.1. Room 2.8.4 (KSP 2.3.10), DataStore Preferences 1.2.1 — no other DI/architecture framework.

## Architecture

MVVM with Unidirectional Data Flow: Hardware → Repository → ViewModel (StateFlow) → Compose UI. `SignalViewModel` is a composition root, not a single monolithic implementation — see "Key design decisions" below.

```
app/src/main/java/com/example/ls/
├── MainActivity.kt              # Single activity, edge-to-edge, portrait-only
├── data/
│   ├── SignaData.kt             # All data models: enums (incl. NetworkType.hasDedicatedDbmMetric), sealed classes, UiState
│   ├── SignalHistory.kt         # Circular buffer (90 points) with rolling statistics, in-memory only
│   ├── SignalRepositry.kt       # Hardware abstraction over TelephonyManager (per-subscription TelephonyManager cached)
│   ├── SignalReadingRepository.kt # Room-backed permanent history (Mutex-serialized insert/getAll/clearAll/pruneOlderThan)
│   ├── SettingsRepository.kt    # DataStore-backed user settings: poll interval, selected SIM, tower triangulation toggle, history retention days
│   ├── LocationTracker.kt       # Low-frequency GPS/network location (plain LocationManager, no Play Services) feeding tower triangulation
│   ├── TowerPositionMath.kt     # Pure math: signal-weighted centroid, "farthest real pair" spread heuristic, bearing (no Android deps)
│   ├── TowerPositionRepository.kt # Room-backed per-tower position estimates (Mutex + epoch fencing token vs. clearAll races); TowerDisplayEntry feeds TowersScreen
│   ├── FrequencyUtils.kt        # 3GPP ARFCN↔frequency math (NR/LTE/WCDMA/GSM)
│   ├── CsvFormatter.kt          # Pure history→CSV formatting + filename sanitization (no Android deps)
│   └── db/                      # Room: AppDatabase (singleton, versioned migrations), SignalReadingDao/Entity, TowerPositionDao/Entity
├── util/
│   └── CsvExportUtil.kt         # filesDir write (survives cache clears) + FileProvider Uri + share/list/rename/delete
└── ui/
    ├── SignalViewModel.kt       # Composition root: tab/foreground state + wires the three delegates below
    ├── ExportManagerViewModel.kt # Screen-scoped ViewModel for the export-file manager (list/rename/delete/share)
    ├── delegate/                # Plain (non-ViewModel) classes SignalViewModel composes — see "Key design decisions"
    │   ├── SignalPollingController.kt      # Polling loop, uiState, SIM selection, RAM history — the "trunk"
    │   ├── PersistedHistoryController.kt   # Room history, CSV export, retention pruning
    │   └── TowerTriangulationController.kt # GPS + tower centroid fold, epoch guard, per-tower throttle
    └── screens/ + components/  # Stateless Compose functions; Canvas gauge & chart; Settings/History/Info/Towers/ExportManager screens
```

**Key design decisions:**
- `SignalRepository` uses reflection to access `PhysicalChannelConfig` bandwidth on older APIs.
- All hardware I/O runs on `Dispatchers.IO`; StateFlow emissions are consumed on the main thread by Compose.
- `UiState` is a sealed class (Loading / PermissionRequired / Success / Error) — every screen branch handles all four states.
- Data models in `SignaData.kt` are pure `data class`/`enum`/`sealed class` with no Android dependencies, making them unit-testable in isolation.
- Signal data has **three** independent persistence tracks, all written from the same point in `SignalPollingController.refreshOnce()` via one `onReading` hook: `SignalHistory` (90-point RAM ring buffer, feeds the live chart), `SignalReadingRepository` (Room, retention-pruned, feeds CSV export), and `TowerPositionRepository` (Room, one row per detected tower, opt-in).
- `SignalViewModel` is a slim composition root, not a monolith: it owns only tab/foreground state and wires three plain (non-`ViewModel`) delegate classes in `ui/delegate/` — `SignalPollingController` (the "trunk," calls a `suspend (CellSignalData, Long) -> Unit` hook once per valid reading), `PersistedHistoryController` and `TowerTriangulationController` (both registered on that hook, neither has its own poll loop). This shape was chosen over splitting into multiple `ViewModel`s because tower triangulation piggybacks on the poll loop and depends on tab/foreground state — a multi-`ViewModel` split would need a hidden cross-VM dependency or a `Flow` bridge risking the epoch race-guard tower folding relies on. `SignalViewModel`'s public API is unchanged by this internally — `MainScreen.kt` talks to it exactly as before.
- `ExportManagerScreen`, by contrast, *is* backed by its own normal `viewModel()`-scoped `ExportManagerViewModel` — it has no cross-cutting coupling to the rest of the app, so a regular screen-scoped ViewModel (not a delegate) is the right shape there. Works without a `NavHost` (the app has none) because `viewModel()` resolves to the Activity's `ViewModelStore`, which survives `SettingsScreen`'s boolean toggle in/out of the export manager.
- Tower position estimates use a signal-strength-weighted centroid (`TowerPositionMath`) with a "farthest real pair" spread heuristic instead of a bounding-box diagonal — the latter can overestimate spread from two hypothetical corners no real sample occupied; farthest-real-pair never overestimates, only underestimates (delays promotion rather than fabricating a position).
- `TowersScreen` ("Torres" tab, between Histórico and Info in `MainScreen.kt`'s `TABS` list) renders a custom Canvas "radar scope" (range rings + bearing, not a street map — the project deliberately has no map-tile dependency) centered on `TowerTriangulationController.currentLocation`, plotting every `TowerDisplayEntry` from `TowerPositionRepository.getAllEstimatesDetailed()`. Unlike `SignalViewModel.towerEstimates` (which only carries a resolved lat/lon once a tower is `Known`), `TowerDisplayEntry` always exposes the raw weighted centroid regardless of confidence, so towers still `Estimating` can be plotted too (dimmed/pulsing) instead of only appearing once already confident. The GPS start/stop gate in `TowerTriangulationController` was widened from `tab == SIGNAL` to `tab == SIGNAL || tab == TOWERS`, since the Towers tab now also needs a live location fix as its radar center.
- History retention (`SettingsRepository.historyRetentionDays`, default 30 days, `0` = keep forever) prevents `signal_readings` from growing unbounded (it's written on every poll tick, up to 5/s at the 200ms floor) — pruning is opportunistic (checked on every write) but throttled to at most once/hour.

## Permissions

`READ_PHONE_STATE` (+ `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` on API ≥ 28, required to read cell identity) are requested upfront on first launch — this is the base flow, driven by the `PermissionRequired` UiState branch. Tower triangulation (opt-in, off by default) needs the same location permissions but requests them **in context**, only when the user enables the Settings toggle — via a separate launcher in `MainScreen.kt`, not the `PermissionRequired` flow (full-screen blocking would be disproportionate for a secondary opt-in feature); denial/revocation surfaces via a `Snackbar` instead.