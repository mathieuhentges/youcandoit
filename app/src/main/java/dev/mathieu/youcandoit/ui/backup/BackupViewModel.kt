package dev.mathieu.youcandoit.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mathieu.youcandoit.data.backup.BackupFiles
import dev.mathieu.youcandoit.data.backup.BackupJson
import dev.mathieu.youcandoit.data.backup.NotABackupException
import dev.mathieu.youcandoit.domain.usecase.ExportBackup
import dev.mathieu.youcandoit.domain.usecase.ImportBackup
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How an export or import went, for a snackbar. */
sealed interface BackupMessage {
    data object Exported : BackupMessage
    data class Imported(val tasks: Int) : BackupMessage
    data object NotABackup : BackupMessage
    data object Failed : BackupMessage
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val exportBackup: ExportBackup,
    private val importBackup: ImportBackup,
    private val files: BackupFiles,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {
    private val state = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = state.asStateFlow()

    private var busy = false

    /** Suggested in the file picker: dated, so several exports don't overwrite each other. */
    fun exportFileName(): String = "you-can-do-it-${LocalDate.now(clock.withZone(zone))}.json"

    fun onExport(uri: Uri) = run {
        files.write(uri, BackupJson.encode(exportBackup(), clock.instant()))
        BackupMessage.Exported
    }

    fun onImport(uri: Uri) = run {
        val result = importBackup(BackupJson.decode(files.read(uri)))
        BackupMessage.Imported(result.tasks)
    }

    fun onMessageShown() {
        state.value = null
    }

    private fun run(work: suspend () -> BackupMessage) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            state.value = try {
                work()
            } catch (e: NotABackupException) {
                BackupMessage.NotABackup
            } catch (e: IOException) {
                BackupMessage.Failed
            } catch (e: SecurityException) {
                // The file's app took back access, e.g. a cloud file that's gone offline.
                BackupMessage.Failed
            } finally {
                busy = false
            }
        }
    }
}
