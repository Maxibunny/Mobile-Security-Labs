// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Состояние редактора и действия над заметкой; доступ к данным через репозиторий.
// Важный момент: Проверка notFound повторена в методах ViewModel, не только в разметке UI.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.ui.note

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.R
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.photo.PhotoStore
import edu.androidsec.notes.reminder.ReminderScheduler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Всё, что экран заметки рисует. `noteId == 0` — новая, ещё не сохранённая заметка. */
data class NoteUiState(
    val noteId: Long,
    val isLoading: Boolean = true,
    /** Фактически отсутствующий id нельзя превратить в новую заметку. */
    val notFound: Boolean = false,
    val title: String = "",
    val body: String = "",
    val photoPath: String? = null,
) {
    val isNew: Boolean get() = noteId == 0L

    fun toNote(): Note = Note(id = noteId, title = title, body = body, photoPath = photoPath)
}

/** Одноразовые события для экрана: показать сообщение или закрыть экран. */
sealed interface NoteEvent {
    data class Message(@param:StringRes val resId: Int) : NoteEvent
    data object Close : NoteEvent
}

class NoteViewModel(
    noteId: Long,
    private val repository: NotesRepository,
    private val photoStore: PhotoStore,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    // Изменяемое состояние скрыто в ViewModel; снаружи только read-only StateFlow.
    private val _uiState = MutableStateFlow(NoteUiState(noteId = noteId))
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    // Channel нужен для одноразовых Toast/Close, чтобы не повторять их при перерисовке.
    private val _events = Channel<NoteEvent>(Channel.BUFFERED)
    val events: Flow<NoteEvent> = _events.receiveAsFlow()

    init {
        load(noteId)
    }

    private fun load(noteId: Long) {
        if (noteId == 0L) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            // Ссылка с правильным синтаксисом ещё может указывать на удалённую запись.
            val note = repository.get(noteId)
            if (note == null) {
                // ipc-3: id синтаксически валиден, но отсутствует в Room.
                // Выставляем отдельное состояние ошибки: UI НЕ покажет редактор и Save/Delete.
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    title = note.title,
                    body = note.body,
                    photoPath = note.photoPath,
                )
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update {
        if (it.isLoading || it.notFound) it else it.copy(title = value)
    }

    fun onBodyChange(value: String) = _uiState.update {
        if (it.isLoading || it.notFound) it else it.copy(body = value)
    }

    fun save() {
        // Защита не только на уровне UI: нельзя вставить несуществующий id из deep link.
        // Даже если UI вызовет save программно, не сохраняем несуществующий deep-link id.
        if (_uiState.value.isLoading || _uiState.value.notFound) return
        viewModelScope.launch {
            val id = repository.save(_uiState.value.toNote())
            _uiState.update { it.copy(noteId = id) }
            _events.send(NoteEvent.Message(R.string.note_saved))
        }
    }

    fun delete() {
        val state = _uiState.value
        if (state.isLoading || state.notFound) return
        viewModelScope.launch {
            if (!state.isNew) {
                // Не оставляем «висячее» уведомление или Job после удаления заметки.
                reminderScheduler.cancel(state.noteId)
                repository.delete(state.noteId)
                _events.send(NoteEvent.Message(R.string.note_deleted))
            }
            _events.send(NoteEvent.Close)
        }
    }

    /** Выбор фото вернул `content://`-URI: копируем файл к себе, право на чтение URI временное. */
    fun onPhotoPicked(uri: Uri) {
        if (_uiState.value.isLoading || _uiState.value.notFound) return
        viewModelScope.launch {
            runCatching { photoStore.copyToPrivateStorage(uri) }
                .onSuccess { path -> _uiState.update { it.copy(photoPath = path) } }
                .onFailure {
                    Log.w(TAG, "Cannot store photo", it)
                    _events.send(NoteEvent.Message(R.string.note_photo_failed))
                }
        }
    }

    fun removePhoto() = _uiState.update { if (it.notFound || it.isLoading) it else it.copy(photoPath = null) }

    /** Вызывается экраном, когда разрешение на уведомления уже получено. */
    fun scheduleReminder() {
        val state = _uiState.value
        if (state.isNew || state.isLoading || state.notFound) return
        reminderScheduler.schedule(state.noteId, state.title)
        _events.trySend(NoteEvent.Message(R.string.reminder_scheduled))
    }

    private companion object {
        const val TAG = "NoteViewModel"
    }
}
