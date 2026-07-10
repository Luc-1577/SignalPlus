package com.example.ls

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ls.ui.screens.MainScreen
import com.example.ls.ui.theme.SignalMeterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O app é dark-only (SignalMeterTheme sempre usa darkColorScheme, sem
        // isSystemInDarkTheme()). O default de enableEdgeToEdge() (SystemBarStyle.auto)
        // decide a cor dos ícones da status/navigation bar com base no tema do SISTEMA,
        // não no tema do app — em um aparelho com sistema em modo claro isso resultaria
        // em ícones escuros sobre o fundo sempre escuro do app, quase invisíveis.
        // Forçamos SystemBarStyle.dark() para garantir ícones claros sempre.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            SignalMeterTheme {
                MainScreen()
            }
        }
    }
}