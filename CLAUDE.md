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

**SDK/toolchain:** compileSdk 36, minSdk 26, targetSdk 36, JVM target 17, Kotlin 2.4.0, AGP 9.2.0, Gradle 9.6.1.

## Architecture

MVVM with Unidirectional Data Flow: Hardware → Repository → ViewModel (StateFlow) → Compose UI.

```
app/src/main/java/com/example/ls/
├── MainActivity.kt              # Single activity, edge-to-edge, portrait-only
├── data/
│   ├── SignaData.kt             # All data models: enums, sealed classes, UiState
│   ├── SignalHistory.kt         # Circular buffer (90 points) with rolling statistics, in-memory only
│   ├── SignalRepositry.kt       # Hardware abstraction over TelephonyManager
│   ├── SignalReadingRepository.kt # Room-backed permanent history (Mutex-serialized insert/getAll/clearAll)
│   ├── SettingsRepository.kt    # DataStore-backed user settings (poll interval)
│   ├── FrequencyUtils.kt        # 3GPP ARFCN↔frequency math (NR/LTE/WCDMA/GSM)
│   ├── CsvFormatter.kt          # Pure history→CSV formatting (no Android deps)
│   └── db/                      # Room: AppDatabase (singleton), SignalReadingDao, SignalReadingEntity
├── util/
│   └── CsvExportUtil.kt         # cacheDir write + FileProvider Uri + share Intent
└── ui/
    ├── SignalViewModel.kt       # Configurable polling (default 1s), StateFlow, tab state
    └── screens/ + components/  # Stateless Compose functions; Canvas gauge & chart; SettingsScreen
```

**Key design decisions:**
- `SignalRepository` uses reflection to access `PhysicalChannelConfig` bandwidth on older APIs.
- All hardware I/O runs on `Dispatchers.IO`; StateFlow emissions are consumed on the main thread by Compose.
- `UiState` is a sealed class (Loading / PermissionRequired / Success / Error) — every screen branch handles all four states.
- Data models in `SignaData.kt` are pure `data class`/`enum`/`sealed class` with no Android dependencies, making them unit-testable in isolation.
- Signal history has two independent tracks: `SignalHistory` (90-point RAM ring buffer, feeds the live chart) and `SignalReadingRepository` (Room, unbounded, feeds CSV export) — both written from the same point in `SignalViewModel.refreshOnce()`, but through separate models (`SignalPoint` vs `SignalReadingEntity`).

## Permissions

`READ_PHONE_STATE` + `ACCESS_FINE_LOCATION` (required on API ≥ 28 to read cell identity). Permission rationale flow is driven by the `PermissionRequired` UiState branch.