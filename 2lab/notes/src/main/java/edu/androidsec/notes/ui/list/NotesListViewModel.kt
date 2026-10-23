// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: ViewModel списка подписывается на Flow Room и выполняет удаление в корутине.
// Важный момент: StateFlow сохраняет состояние при рекомпозиции и не блокирует UI-поток.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.domain.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesListViewModel(
    private val repository: NotesRepository,
) : ViewModel() {

    /** Список заметок из Room; `null` — список ещё загружается. */
    val notes: StateFlow<List<Note>?> = repository.observeAll()
        // stateIn превращает Flow в доступное Compose состояние; WhileSubscribed экономит работу.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun delete(id: Long) {
        // SQL выполняется асинхронно через suspend-функцию, не блокируя основной поток.
        viewModelScope.launch { repository.delete(id) }
    }
}
