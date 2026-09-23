package com.example.ls.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ls.data.CsvFormatter
import com.example.ls.util.CsvExportUtil
import com.example.ls.util.ExportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado único da tela de exportações (listar/renomear/apagar/compartilhar) —
 *  mesma convenção de estado-como-StateFlow já usada em SignalUiState, em vez
 *  de eventos avulsos: tanto o erro de "nome inválido" (síncrono) quanto o de
 *  "já existe um arquivo com esse nome" (só sai depois de I/O) vivem aqui, não
 *  como uma checagem local separada na tela. */
data class ExportManagerUiState(
    val files: List<ExportedFile> = emptyList(),
    val renaming: ExportedFile? = null,
    val renameError: String? = null,
    val deleting: ExportedFile? = null,
    val deleteError: String? = null,
    val shareError: String? = null,
)

/**
 * ExportManagerScreen era a única tela do app que gerenciava seu próprio
 * estado local e chamava CsvExportUtil direto, fora do padrão
 * Hardware → Repository → ViewModel → UI documentado no CLAUDE.md. Ao
 * contrário do split de SignalViewModel (pacote ui.delegate, classes comuns —
 * necessário lá porque a triangulação de torres pega carona no loop de poll
 * e depende de tab/foreground compartilhados), esta tela não tem nenhum
 * acoplamento cruzado: é screen-scoped de verdade, então um ViewModel comum
 * (via `viewModel()`, sem NavHost — resolve pro ViewModelStore da Activity e
 * sobrevive ao toggle booleano de managingExports em SettingsScreen) é a
 * forma certa aqui.
 */
class ExportManagerViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(ExportManagerUiState())
    val uiState: StateFlow<ExportManagerUiState> = _uiState.asStateFlow()

    init { reload() }

    private fun reload() {
        viewModelScope.launch {
            val files = CsvExportUtil.listExports(getApplication())
            _uiState.value = _uiState.value.copy(files = files)
        }
    }

    /** Devolve o Intent pronto via [onReady] — quem chama ainda faz o
     *  `context.startActivity(...)` (precisa de um Context de Activity pra um
     *  chooser suave), mas a leitura do arquivo em si (File I/O, não precisa
     *  de Activity) roda aqui com `getApplication()`. Ao contrário da versão
     *  anterior desta função, uma falha aqui não é mais engolida em silêncio —
     *  igual confirmRename()/confirmDelete() já fazem, agora fica visível em
     *  [ExportManagerUiState.shareError] em vez do botão "Compartilhar"
     *  simplesmente não fazer nada. */
    fun shareExport(name: String, onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            // try/catch cobre só a leitura do arquivo (I/O) — não mais a chamada
            // a onReady() abaixo. onReady() é quem faz context.startActivity() do
            // lado de fora; se ela lançar (ex.: ActivityNotFoundException, sem
            // nenhum app pra abrir o chooser), esse catch pegava e reportava
            // "não foi possível abrir o arquivo", mascarando que o arquivo abriu
            // sem problema — o erro era outro, na hora de compartilhar, não de ler.
            val intent = try {
                withContext(Dispatchers.IO) {
                    CsvExportUtil.shareIntentFor(getApplication(), name)
                }
            } catch (_: Exception) {
                null
            }
            if (intent != null) {
                _uiState.value = _uiState.value.copy(shareError = null)
                onReady(intent)
            } else {
                _uiState.value = _uiState.value.copy(shareError = "Não foi possível abrir o arquivo para compartilhar.")
            }
        }
    }

    fun dismissShareError() {
        _uiState.value = _uiState.value.copy(shareError = null)
    }

    fun startRename(file: ExportedFile) {
        _uiState.value = _uiState.value.copy(renaming = file, renameError = null)
    }

    fun cancelRename() {
        _uiState.value = _uiState.value.copy(renaming = null, renameError = null)
    }

    fun confirmRename(newDisplayName: String) {
        val file = _uiState.value.renaming ?: return
        if (CsvFormatter.sanitizeExportFileName(newDisplayName).isEmpty()) {
            _uiState.value = _uiState.value.copy(renameError = "Nome inválido.")
            return
        }
        viewModelScope.launch {
            val result = CsvExportUtil.renameExport(getApplication(), file.name, newDisplayName)
            // Guard: o usuário pode ter cancelado este diálogo e aberto o de
            // OUTRO arquivo enquanto este I/O ainda estava em voo — sem checar
            // que `renaming` ainda é este MESMO arquivo, o resultado desta
            // chamada fecharia (ou mostraria erro) no diálogo do arquivo errado.
            if (_uiState.value.renaming?.name != file.name) return@launch
            when (result) {
                CsvExportUtil.RenameResult.SUCCESS -> {
                    _uiState.value = _uiState.value.copy(renaming = null, renameError = null)
                    reload()
                }
                CsvExportUtil.RenameResult.NAME_TAKEN ->
                    _uiState.value = _uiState.value.copy(renameError = "Já existe um arquivo com esse nome.")
                CsvExportUtil.RenameResult.FAILED ->
                    _uiState.value = _uiState.value.copy(renameError = "Não foi possível renomear o arquivo.")
            }
        }
    }

    fun startDelete(file: ExportedFile) {
        _uiState.value = _uiState.value.copy(deleting = file, deleteError = null)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(deleting = null, deleteError = null)
    }

    fun confirmDelete() {
        val file = _uiState.value.deleting ?: return
        viewModelScope.launch {
            val deleted = CsvExportUtil.deleteExport(getApplication(), file.name)
            // Mesmo guard de confirmRename(): o diálogo pode já ser sobre outro
            // arquivo quando este I/O termina.
            if (_uiState.value.deleting?.name != file.name) return@launch
            if (deleted) {
                _uiState.value = _uiState.value.copy(deleting = null, deleteError = null)
                reload()
            } else {
                _uiState.value = _uiState.value.copy(deleteError = "Não foi possível apagar o arquivo.")
            }
        }
    }
}
