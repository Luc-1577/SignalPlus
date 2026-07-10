package com.example.ls.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ls.ui.components.SectionHeader
import com.example.ls.ui.theme.*

private val POLL_INTERVAL_OPTIONS = listOf(1_000L to "1s", 2_000L to "2s", 5_000L to "5s")

@Composable
fun SettingsScreen(
    pollIntervalMs: Long,
    onPollIntervalChange: (Long) -> Unit,
    onClearPersistedHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
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
