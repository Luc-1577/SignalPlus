package com.example.ls.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary             = AccentBlue,
    onPrimary           = Color.White,
    primaryContainer    = BgCard,
    onPrimaryContainer  = TextPrimary,
    secondary           = AccentCyan,
    onSecondary         = Color.White,
    tertiary            = AccentPurple,
    background          = BgDeep,
    onBackground        = TextPrimary,
    surface             = BgSurface,
    onSurface           = TextPrimary,
    surfaceVariant      = BgCard,
    onSurfaceVariant    = TextSecondary,
    outline             = BorderDefault,
    outlineVariant      = BorderSubtle,
    error               = QualityVeryPoor,
    onError             = Color.White,
)

private val AppTypography = Typography(
    displayLarge  = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold,      letterSpacing = (-1).sp),
    displaySmall  = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold),
    headlineLarge  = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall  = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge  = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall  = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium,   letterSpacing = 0.1.sp),
    bodyLarge   = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium  = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge  = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium,   letterSpacing = 0.8.sp),
    labelSmall  = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium,   letterSpacing = 1.sp),
)

@Composable
fun SignalPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = AppTypography,
        content     = content,
    )
}