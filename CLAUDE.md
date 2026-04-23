# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**SignalMeter** — Android app for RF signal diagnostics on mobile networks (5G NR, LTE, WCDMA, GSM, CDMA). It reads raw modem data via `TelephonyManager`, converts ARFCN values to frequencies using 3GPP standards, and presents live metrics with custom Canvas visualizations.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (R8 minification + resource shrinking)
./gradlew test                   # Run unit tests (JVM)
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew testDebugUnitTest      # Debug variant unit tests only
```

**SDK/toolchain:** compileSdk 35, minSdk 26, targetSdk 35, JVM target 17, Kotlin 2.2.10, AGP 9.1.0, Gradle 9.3.1.

## Architecture

MVVM with Unidirectional Data Flow: Hardware → Repository → ViewModel (StateFlow) → Compose UI.

```
app/src/main/java/com/example/ls/
├── MainActivity.kt              # Single activity, edge-to-edge, portrait-only
├── data/
│   ├── SignaData.kt             # All data models: enums, sealed classes, UiState
│   ├── SignalHistory.kt         # Circular buffer (90 points) with rolling statistics
│   ├── SignalRepositry.kt       # Hardware abstraction over TelephonyManager
│   └── FrequencyUtils.kt       # 3GPP ARFCN↔frequency math (NR/LTE/WCDMA/GSM)
└── ui/
    ├── SignalViewModel.kt       # 1-second polling, StateFlow, tab state
    └── screens/ + components/  # Stateless Compose functions; Canvas gauge & chart
```

**Key design decisions:**
- `SignalRepository` uses reflection to access `PhysicalChannelConfig` bandwidth on older APIs.
- All hardware I/O runs on `Dispatchers.IO`; StateFlow emissions are consumed on the main thread by Compose.
- `UiState` is a sealed class (Loading / PermissionRequired / Success / Error) — every screen branch handles all four states.
- Data models in `SignaData.kt` are pure `data class`/`enum`/`sealed class` with no Android dependencies, making them unit-testable in isolation.

## Permissions

`READ_PHONE_STATE` + `ACCESS_FINE_LOCATION` (required on API ≥ 28 to read cell identity). Permission rationale flow is driven by the `PermissionRequired` UiState branch.