package com.example.ls.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ls.ui.ExportManagerViewModel
import com.example.ls.ui.components.DestructiveConfirmDialog
import com.example.ls.ui.components.formatFileSize
import com.example.ls.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ExportManagerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // managingExports em SettingsScreen é um boolean local, não uma entrada de
    // back stack — sem isso, o botão/gesto de voltar do sistema não teria nada
    // pra interceptar e fecharia o app inteiro em vez de retornar às Configurações.
    BackHandler(onBack = onBack)

    // shareExport() já não engole a falha em silêncio — sem isso, o botão
    // "Compartilhar" simplesmente não fazia nada quando o arquivo não podia
    // ser aberto, indistinguível de um botão quebrado.
    LaunchedEffect(uiState.shareError) {
        uiState.shareError?.let {
            snackbarHostState.showSnackbar(it)
            vm.dismissShareError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
                }
                Text("Arquivos exportados", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            if (uiState.files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum arquivo exportado ainda.", fontSize = 13.sp, color = TextMuted)
                }
            } else {
                val fmt = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.US) }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.files, key = { it.name }) { file ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)).background(BgCard)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
                                Text(
                                    "${formatFileSize(file.sizeBytes)} · ${fmt.format(Date(file.lastModifiedMs))}",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                )
                            }
                            IconButton(onClick = {
                                vm.shareExport(file.name) { intent ->
                                    // Erro daqui (ex.: nenhum app aceita text/csv) não é o
                                    // mesmo problema de "não conseguiu abrir o arquivo" que
                                    // shareExport() já reporta via shareError — tratado
                                    // aqui, no lugar exato onde acontece, com sua própria
                                    // mensagem, em vez de subir e ser confundido com uma
                                    // falha de leitura de arquivo que nunca ocorreu.
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "Exportar histórico"))
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(context, "Nenhum app de compartilhamento encontrado.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) { Icon(Icons.Default.Share, "Compartilhar", tint = AccentBlue, modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { vm.startRename(file) }) {
                                Icon(Icons.Default.Edit, "Renomear", tint = TextSecondary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { vm.startDelete(file) }) {
                                Icon(Icons.Default.DeleteOutline, "Apagar", tint = QualityVeryPoor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    uiState.renaming?.let { file ->
        // Único estado que continua local: o texto digitado é input puramente
        // efêmero, sem I/O — os erros (nome inválido, colisão) já vivem no
        // ExportManagerUiState do ViewModel.
        var text by remember(file) { mutableStateOf(file.name.removeSuffix(".csv")) }
        AlertDialog(
            onDismissRequest = { vm.cancelRename() },
            title = { Text("Renomear arquivo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        label = { Text("Nome") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = BorderSubtle,
                            focusedLabelColor = AccentBlue,
                            unfocusedLabelColor = TextMuted,
                        ),
                    )
                    uiState.renameError?.let { Text(it, fontSize = 11.sp, color = QualityVeryPoor, modifier = Modifier.padding(top = 6.dp)) }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.confirmRename(text) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                ) { Text("Renomear") }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.cancelRename() }) { Text("Cancelar") }
            },
        )
    }

    uiState.deleting?.let { file ->
        DestructiveConfirmDialog(
            title = "Apagar ${file.name}?",
            text = "Isso apaga permanentemente este arquivo exportado. Não pode ser desfeito.",
            onConfirm = { vm.confirmDelete() },
            onDismiss = { vm.cancelDelete() },
            extraContent = {
                uiState.deleteError?.let {
                    Text(it, fontSize = 11.sp, color = QualityVeryPoor, modifier = Modifier.padding(top = 8.dp))
                }
            },
        )
    }
}
