# Mantém data classes do pacote data (não obfusca)
-keep class com.lucapp.signalmeter.data.** { *; }

# TelephonyManager e classes de célula do Android SDK
-keep class android.telephony.** { *; }

# Metadados do Kotlin (reflexão)
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Compose e Lifecycle
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.compose.**

# Stack traces legíveis nos relatórios de crash
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile