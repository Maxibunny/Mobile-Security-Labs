package edu.androidsec.notes.ui.list

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.androidsec.notes.R
import edu.androidsec.notes.data.DeleteTicket
import edu.androidsec.notes.data.NotesRepository
import kotlinx.coroutines.withTimeoutOrNull

/** Отмена доступна и на списке, и на поиске. Несколько быстрых удалений отменяются вместе. */
@Composable
fun PendingDeletionNotice(repository: NotesRepository, snackbar: SnackbarHostState) {
    val pendingFlow = remember(repository) { repository.observePending() }
    val pending by pendingFlow.collectAsStateWithLifecycle(initialValue = emptyList<DeleteTicket>())
    val deletedOne = stringResource(R.string.note_deleted)
    val deletedMany = stringResource(R.string.notes_deleted_many, pending.size)
    val undo = stringResource(R.string.delete_undo)

    // Изменение набора сбрасывает прежний Snackbar; срок не продлеваем.
    LaunchedEffect(pending) {
        val now = System.currentTimeMillis()
        val active = pending.filter { it.deadline > now }
        if (active.isNotEmpty()) {
            val remaining = active.minOf { it.deadline - now }
            val result = withTimeoutOrNull(remaining) {
                snackbar.showSnackbar(
                    if (active.size == 1) deletedOne else deletedMany,
                    undo, duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                // Каждую запись возвращает SQL UPDATE с проверкой своего deadline.
                active.forEach { repository.undoDelete(it) }
            }
        }
    }
}
