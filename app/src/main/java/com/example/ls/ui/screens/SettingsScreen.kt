package com.example.ls.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ls.data.AUTOMATIC_SUBSCRIPTION_ID
import com.example.ls.data.SettingsRepository
import com.example.ls.data.SimOption
import com.example.ls.ui.components.CustomNumericField
import com.example.ls.ui.components.DestructiveConfirmDialog
import com.example.ls.ui.components.SectionHeader
import com.example.ls.ui.components.SelectableChipRow
import com.example.ls.ui.components.SettingsCard
import com.example.ls.ui.theme.*

private val POLL_INTERVAL_OPTIONS = listOf(1_000L to "1s", 2_000L to "2s", 5_000L to "5s")

// Piso de segurança para o intervalo personalizado — evita 0/negativo (loop
// sem delay, batendo no rádio sem parar). Vive em SettingsRepository (não só
// aqui) pra ser reforçado também na camada de persistência, não só neste
// botão. Documentação oficial do Android não publica um número exato de
// rate-limit para CellInfo, só diz que é "rate-limited" e "não garantido";
// fontes de código do AOSP e temporizadores 3GPP (T_RESELECTION > 1s, ciclos
// de DRX em idle ~320ms–2.56s) sugerem que abaixo de ~1s dificilmente há dado
// mais fresco — mas deixamos o usuário escolher valores menores se quiser,
// com esse aviso.
private val MIN_CUSTOM_POLL_INTERVAL_MS = SettingsRepository.MIN_POLL_INTERVAL_MS

// 0 = SettingsRepository.RETENTION_KEEP_FOREVER — não referenciado direto pra
// manter a lista de opções como constante top-level simples, igual
// POLL_INTERVAL_OPTIONS acima.
private val RETENTION_OPTIONS = listOf(7 to "7 dias", 30 to "30 dias", 90 to "90 dias", 0 to "Para sempre")

@Composable
fun SettingsScreen(
    pollIntervalMs: Long,
    onPollIntervalChange: (Long) -> Unit,
    onClearPersistedHistory: () -> Unit,
    modifier: Modifier = Modifier,
    availableSims: List<SimOption> = emptyList(),
    selectedSubscriptionId: Int = AUTOMATIC_SUBSCRIPTION_ID,
    onSelectSubscription: (Int) -> Unit = {},
    towerTriangulationEnabled: Boolean = false,
    onToggleTowerTriangulation: (Boolean) -> Unit = {},
    onClearTowerEstimates: () -> Unit = {},
    historyRetentionDays: Int = SettingsRepository.DEFAULT_HISTORY_RETENTION_DAYS,
    onHistoryRetentionDaysChange: (Int) -> Unit = {},
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    var showConfirmClearTowers by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var customRetentionText by remember { mutableStateOf("") }
    var managingExports by remember { mutableStateOf(false) }

    if (managingExports) {
        ExportManagerScreen(onBack = { managingExports = false }, modifier = modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text("Configurações", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        if (availableSims.size >= 2) {
            item {
                SettingsCard {
                    SectionHeader("Chip para monitorar")
                    Text(
                        "Este aparelho tem ${availableSims.size} chips ativos. Escolha qual chip o app deve ler.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                    val autoIsEffective = availableSims.none { it.subscriptionId == selectedSubscriptionId }
                    val chipOptions = listOf(AUTOMATIC_SUBSCRIPTION_ID to "Automático") +
                        availableSims.map { it.subscriptionId to it.displayName }
                    SelectableChipRow(
                        options = chipOptions,
                        isSelected = { id -> if (id == AUTOMATIC_SUBSCRIPTION_ID) autoIsEffective else id == selectedSubscriptionId },
                        onSelect = onSelectSubscription,
                    )
                }
            }
        }
        item {
            SettingsCard {
                SectionHeader("Intervalo de leitura")
                Text(
                    "Com que frequência o app lê o sinal. Intervalos maiores economizam bateria.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                SelectableChipRow(
                    options = POLL_INTERVAL_OPTIONS,
                    isSelected = { it == pollIntervalMs },
                    onSelect = onPollIntervalChange,
                )
                Spacer(Modifier.height(14.dp))
                CustomNumericField(
                    text = customText,
                    onTextChange = { customText = it },
                    label = "Personalizado (ms)",
                    onApply = { raw ->
                        val clamped = raw.coerceAtLeast(MIN_CUSTOM_POLL_INTERVAL_MS)
                        // Sem isso, digitar um valor abaixo do piso (ex.: "50")
                        // aplicava 200ms de verdade mas o campo continuava
                        // mostrando "50" — só o rótulo "Intervalo atual" abaixo
                        // (fácil de não notar) revelava o valor real aplicado.
                        customText = clamped.toString()
                        onPollIntervalChange(clamped)
                    },
                )
                Text(
                    "Mínimo $MIN_CUSTOM_POLL_INTERVAL_MS ms. Abaixo de ~1000ms o aparelho normalmente não " +
                        "entrega leituras mais novas, então o valor pode se repetir.",
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
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionHeader("Estimar posição das torres")
                        Text(
                            "Usa sua localização para estimar aos poucos onde cada torre está. " +
                                "Desligado por padrão.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = towerTriangulationEnabled,
                        onCheckedChange = onToggleTowerTriangulation,
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
                    )
                }
                OutlinedButton(
                    onClick = { showConfirmClearTowers = true },
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Apagar estimativas de posição")
                }
            }
        }
        item {
            SettingsCard {
                SectionHeader("Dados")
                Text(
                    "Leituras de sinal ficam salvas para exportação, mesmo depois de fechar o app. " +
                        "Sem um limite, o histórico cresce sem parar e ocupa cada vez mais espaço. " +
                        "Escolha por quanto tempo manter:",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                SelectableChipRow(
                    options = RETENTION_OPTIONS,
                    isSelected = { it == historyRetentionDays },
                    onSelect = onHistoryRetentionDaysChange,
                    verticalPadding = 10.dp,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))
                CustomNumericField(
                    text = customRetentionText,
                    onTextChange = { customRetentionText = it },
                    label = "Personalizado (dias)",
                    maxDigits = 5,
                    onApply = { raw ->
                        // Mínimo 1: 0 é a sentinela de "Para sempre", já coberto
                        // pelo chip — um campo "personalizado" com 0 teria o
                        // mesmo efeito por um caminho diferente, confuso.
                        val clamped = raw.toInt().coerceAtLeast(1)
                        customRetentionText = clamped.toString()
                        onHistoryRetentionDaysChange(clamped)
                    },
                )
                val isCustomRetentionActive = RETENTION_OPTIONS.none { it.first == historyRetentionDays }
                if (isCustomRetentionActive) {
                    Text(
                        "Retenção atual: $historyRetentionDays dias",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                OutlinedButton(
                    onClick = { showConfirmDelete = true },
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Apagar histórico salvo")
                }
            }
        }
        item {
            SettingsCard {
                SectionHeader("Arquivos exportados")
                Text(
                    "Veja, renomeie ou apague os CSVs já exportados pelo botão \"Exportar\" do Histórico.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                OutlinedButton(onClick = { managingExports = true }) {
                    Icon(Icons.Default.Folder, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Gerenciar exportações")
                }
            }
        }
    }

    if (showConfirmDelete) {
        DestructiveConfirmDialog(
            title = "Apagar histórico salvo?",
            text = "Isso apaga permanentemente todas as leituras salvas. Não pode ser desfeito.",
            onConfirm = { onClearPersistedHistory(); showConfirmDelete = false },
            onDismiss = { showConfirmDelete = false },
        )
    }

    if (showConfirmClearTowers) {
        DestructiveConfirmDialog(
            title = "Apagar estimativas de posição?",
            text = "Isso apaga permanentemente as posições de torres já estimadas. Não pode ser desfeito.",
            onConfirm = { onClearTowerEstimates(); showConfirmClearTowers = false },
            onDismiss = { showConfirmClearTowers = false },
        )
    }
}
