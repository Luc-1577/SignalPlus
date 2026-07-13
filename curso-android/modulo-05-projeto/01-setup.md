# Módulo 05 · Aula 01 — Setup do Projeto: Gradle, Dependências e Estrutura

> **Objetivo:** Criar o projeto do zero com todas as configurações corretas.

---

## 1. Criando o projeto no Android Studio

1. **File → New → New Project**
2. Selecione **Empty Activity**
3. Configure:
   - **Name:** Signal+
   - **Package name:** com.example.ls
   - **Save location:** à sua escolha
   - **Language:** Kotlin
   - **Minimum SDK:** API 26 (Android 8.0)
   - **Build configuration language:** Kotlin DSL (build.gradle.kts)

---

## 2. Estrutura do Gradle

O Gradle é o sistema de build do Android. Tem dois arquivos principais:

### `settings.gradle.kts` — configuração do projeto

```kotlin
// O nome que aparece no Android Studio
rootProject.name = "Signal+"

// Diz ao Gradle onde buscar as bibliotecas
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// Declara os módulos do projeto
include(":app")
```

### `build.gradle.kts` (raiz) — plugins globais

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
}
```

`apply false` significa: "declare o plugin, mas não aplique aqui — será aplicado nos módulos".

### `app/build.gradle.kts` — configuração do módulo app

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace  = "com.example.ls"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ls"
        minSdk    = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true    // habilita R8 (ProGuard) — reduz o tamanho
            isShrinkResources = true    // remove recursos não usados
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"    // ID diferente do release
            versionNameSuffix   = "-debug"    // versão identificável
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true      // habilita o compilador Compose
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

**Nota:** o bloco `android.kotlinOptions { jvmTarget = "17" }` (usado em versões antigas
de tutoriais) foi depreciado a partir do Kotlin 2.0. Hoje o `jvmTarget` é configurado
no bloco `kotlin { compilerOptions { ... } }`, fora do `android {}`.

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM — uma versão para todas as libs Compose
    val bom = platform(libs.androidx.compose.bom)
    implementation(bom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Só em debug — ferramentas de inspeção do Compose
    debugImplementation(libs.androidx.ui.tooling)
}
```

---

## 3. Version Catalog — centralizando versões

O Version Catalog (`gradle/libs.versions.toml`) centraliza todas as versões em um lugar:

```toml
[versions]
# Ferramentas de build
agp             = "9.2.0"
kotlin          = "2.4.0"

# AndroidX
core-ktx        = "1.17.0"
lifecycle       = "2.10.0"
activity-compose = "1.13.0"

# Compose
compose-bom     = "2026.06.00"

[libraries]
# AndroidX
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }

# Compose (sem versão explícita — gerenciada pelo BOM)
androidx-compose-bom  = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
androidx-ui           = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics  = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-ui-tooling   = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-material3    = { group = "androidx.compose.material3", name = "material3" }
androidx-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android      = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose      = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

**Por que Version Catalog?**
- Versão declarada em um único arquivo — não há risco de versões inconsistentes entre módulos
- IDE completa automaticamente (`libs.androidx.material3`)
- Atualizações em um lugar só

---

## 4. `gradle.properties` — otimizações de build

```properties
# Tamanho da heap da JVM para compilação
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8

# Builds paralelas de módulos
org.gradle.parallel=true

# Reutiliza configuração entre builds (mais rápido)
org.gradle.configuration-cache=true

# Kotlin
kotlin.code.style=official
kotlin.incremental=true
```

**Nota:** desde o AGP 8.0, o R8 já roda em "full mode" por padrão — a propriedade
`android.enableR8.fullMode=true` (comum em tutoriais mais antigos) é redundante e
pode ser removida.

---

## 5. `AndroidManifest.xml` — configuração do app

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissões necessárias -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SignalPlus">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

**`android:exported="true"`:** Obrigatório para Activities que podem ser iniciadas
externamente (como a activity principal com intent-filter MAIN/LAUNCHER).

**`android:screenOrientation="portrait"`:** Fixa a orientação em retrato. Sem isso,
girar o celular recriaria a Activity. O projeto suporta rotação via ViewModel, mas
para um app de medição que você segura parado, portrait-only é a UX correta.

---

## 6. `proguard-rules.pro` — regras de ofuscação

```proguard
# Mantém os data classes — o R8 não pode renomear campos usados por reflexão
-keep class com.example.ls.data.** { *; }

# Mantém as APIs do TelephonyManager (usadas por reflexão em SignalRepository)
-keep class android.telephony.** { *; }

# Mantém os metadados Kotlin (necessários para data class com named parameters)
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*

# Mantém informações de debug para stack traces legíveis
-keepattributes SourceFile,LineNumberTable
```

---

## 7. Estrutura de arquivos que você vai criar

```
app/src/main/
├── AndroidManifest.xml
└── java/com/example/ls/
    ├── MainActivity.kt
    ├── data/
    │   ├── SignaData.kt
    │   ├── SignalHistory.kt
    │   ├── SignalRepositry.kt
    │   └── FrequencyUtils.kt
    └── ui/
        ├── SignalViewModel.kt
        ├── screens/
        │   ├── MainScreen.kt
        │   ├── HistoryScreen.kt
        │   └── InfoScreen.kt
        ├── components/
        │   ├── Components.kt
        │   ├── SignalGauge.kt
        │   ├── SignalChart.kt
        │   └── CellIdentifiersCard.kt
        └── theme/
            ├── Color.kt
            ├── Theme.kt
            └── Type.kt
```

---

## 8. `MainActivity.kt` — o ponto de entrada

```kotlin
package com.example.ls

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ls.ui.screens.MainScreen
import com.example.ls.ui.theme.SignalPlusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove a borda padrão — o app desenha atrás das barras do sistema
        enableEdgeToEdge()

        // Define o conteúdo Compose
        setContent {
            SignalPlusTheme {
                MainScreen()
            }
        }
    }
}
```

**`enableEdgeToEdge()`:** Faz o app se estender atrás da status bar e da navigation bar.
O `Scaffold` do `MainScreen` depois usa `paddingValues` para garantir que o conteúdo
não fica coberto.

**Por que tão simples?** Toda a lógica foi movida para o ViewModel e os composables.
A Activity é apenas o "ponto de entrada" que liga o Android ao mundo Compose.

---

## 9. Comandos de build

```bash
# Compilar e instalar debug no device conectado
./gradlew installDebug

# Compilar o APK release (na pasta app/build/outputs/apk/release/)
./gradlew assembleRelease

# Rodar todos os testes unitários
./gradlew test

# Rodar testes num device conectado
./gradlew connectedAndroidTest

# Limpar arquivos gerados (use quando tiver erros estranhos)
./gradlew clean

# Ver as dependências do projeto
./gradlew app:dependencies
```

---

## 📌 Resumo

```
build.gradle.kts    → dependências, configurações Android
libs.versions.toml  → versões centralizadas
gradle.properties   → otimizações de build
AndroidManifest.xml → permissões, Activities, configuração do app
proguard-rules.pro  → quais classes preservar no build release
MainActivity.kt     → porta de entrada mínima (habilita Compose)
```

**Próxima aula →** [Camada de Dados](02-dados.md) — implementando SignaData.kt, FrequencyUtils.kt e SignalHistory.kt.
