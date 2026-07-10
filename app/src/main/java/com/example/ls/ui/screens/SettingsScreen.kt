package com.example.ls.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ls.ui.components.SectionHeader
import com.example.ls.ui.theme.*

private val POLL_INTERVAL_OPTIONS = listOf(1_000L to "1s", 2_000L to "2s", 5_000L to "5s")

/**
 * Piso de segurança para o intervalo personalizado — evita 0/negativo (loop
 * sem delay, batendo no rádio sem parar). Documentação oficial do Android não
 * publica um número exato de rate-limit para CellInfo, só diz que é
 * "rate-limited" e "não garantido"; fontes de código do AOSP e temporizadores
 * 3GPP (T_RESELECTION > 1s, ciclos de DRX em idle ~320ms–2.56s) sugerem que
 * abaixo de ~1s dificilmente há dado mais fresco — mas deixamos o usuário
 * escolher valores menores se quiser, com esse aviso.
 */
private const val MIN_CUSTOM_POLL_INTERVAL_MS = 200L

@Composable
fun SettingsScreen(
    pollIntervalMs: Long,
    onPollIntervalChange: (Long) -> Unit,
    onClearPersistedHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text("Configurações", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)).padding(16.dp),
            ) {
                SectionHeader("Intervalo de leitura")
                Text(
                    "Com que frequência o app lê o sinal. Intervalos maiores economizam bateria.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    POLL_INTERVAL_OPTIONS.forEach { (ms, label) ->
                        val selected = ms == pollIntervalMs
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) AccentBlue else BgCardAlt)
                                .border(1.dp, if (selected) AccentBlue else BorderSubtle, RoundedCornerShape(12.dp))
                                .clickable { onPollIntervalChange(ms) }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) TextPrimary else TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.filter(Char::isDigit).take(6) },
                        label = { Text("Personalizado (ms)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = BorderSubtle,
                            focusedLabelColor = AccentBlue,
                            unfocusedLabelColor = TextMuted,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            customText.toLongOrNull()
                                ?.coerceAtLeast(MIN_CUSTOM_POLL_INTERVAL_MS)
                                ?.let(onPollIntervalChange)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    ) { Text("Aplicar") }
                }
                Text(
                    "Mínimo $MIN_CUSTOM_POLL_INTERVAL_MS ms. Abaixo de ~1000ms o rádio normalmente não " +
                        "entrega leituras mais novas (limitação do próprio Android/modem) — o app vai " +
                        "continuar consultando, mas pode repetir o mesmo valor.",
                    fontSize = 10.sp,
                    color = TextDim,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val isCustomActive = POLL_INTERVAL_OPTIONS.none { it.first == pollIntervalMs }
                if (isCustomActive) {
                    Text(
                        "Intervalo atual: $pollIntervalMs ms",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)).padding(16.dp),
            ) {
                SectionHeader("Dados")
                Text(
                    "Todas as leituras de sinal ficam salvas permanentemente para exportação, mesmo depois de fechar o app.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                OutlinedButton(onClick = { showConfirmDelete = true }) {
                    Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Apagar histórico salvo")
                }
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Apagar histórico salvo?") },
            text = { Text("Isso apaga permanentemente todas as leituras salvas. Não pode ser desfeito.") },
            confirmButton = {
                Button(
                    onClick = { onClearPersistedHistory(); showConfirmDelete = false },
                    colors = ButtonDefaults.buttonColors(containerColor = QualityVeryPoor),
                ) { Text("Apagar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}
