package edu.androidsec.notes.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.data.NoteSort
import edu.androidsec.notes.data.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Сортировка передаётся enum, а репозиторий выбирает готовый @Query. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repository: NotesRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(NoteSort.NEWEST)
    val results = combine(query, sort) { q, s -> q to s }
        .flatMapLatest { (q, s) -> repository.search(q, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
