package edu.androidsec.notes.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.domain.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Flow Room обновляет список сразу после пометки на удаление и после отмены. */
class NotesListViewModel(private val repository: NotesRepository) : ViewModel() {
    val notes = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Note>())

    fun delete(id: Long) { viewModelScope.launch { repository.requestDelete(id) } }
}
