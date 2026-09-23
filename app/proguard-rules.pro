# android.telephony.** e androidx.compose.**/androidx.lifecycle.** não têm
# regras -keep aqui de propósito: são classes de plataforma/AARs que já
# publicam suas próprias consumer-rules.pro (Compose/Lifecycle) ou nunca
# entram no dex do app (android.telephony é API de plataforma) — um -keep
# manual por cima delas só anula o shrink/ofuscação do R8 na maior fatia do
# APK, contrariando o isMinifyEnabled=true do release.

# Metadados do Kotlin (reflexão)
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Stack traces legíveis nos relatórios de crash
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile