package edu.androidsec.notes.ui.note

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.R
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteValidator
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

/** Данные из Room + состояние формы; ввод сам хранится в Compose TextFieldState. */
data class NoteUiState(
    val noteId: Long,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    val body: String = "",
    val tags: String = "",
    val isSecret: Boolean = false,
    /** Исходное сохранённое значение: снимки остаются запрещены до подтверждения снятия секретности. */
    val storedSecret: Boolean = false,
    val photoPath: String? = null,
    val saveError: String? = null,
    val isSaving: Boolean = false,
) {
    val isNew: Boolean get() = noteId == 0L
    fun toNote() = Note(id = noteId, title = title, body = body,
        photoPath = photoPath, tags = tags, isSecret = isSecret)
}

sealed interface NoteEvent {
    data class Message(@param:StringRes val resId: Int) : NoteEvent
    /** Сохранённый id нужен экрану новой заметки после пересоздания процесса. */
    data class Saved(val id: Long) : NoteEvent
    data object Close : NoteEvent
}

class NoteViewModel(
    noteId: Long,
    private val repository: NotesRepository,
    private val photoStore: PhotoStore,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NoteUiState(noteId = noteId))
    // Новые фото удаляем при отмене редактирования, если они ещё не привязаны к БД.
    private val draftPhotos = mutableSetOf<String>()
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()
    private val _events = Channel<NoteEvent>(Channel.BUFFERED)
    val events: Flow<NoteEvent> = _events.receiveAsFlow()

    init { load(noteId) }

    private fun load(id: Long) {
        if (id == 0L) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            val note = repository.get(id)
            if (note == null) _uiState.update { it.copy(isLoading = false, notFound = true) }
            else _uiState.update { it.copy(isLoading = false,
                title = note.title, body = note.body, tags = note.tags,
                isSecret = note.isSecret, storedSecret = note.isSecret, photoPath = note.photoPath) }
        }
    }

    fun onSecretChange(value: Boolean) = _uiState.update {
        if (it.isLoading || it.notFound || it.isSaving) it else it.copy(isSecret = value)
    }

    fun save(title: String, body: String, tags: String) {
        val state = _uiState.value
        if (state.isLoading || state.notFound || state.isSaving) return
        val note = state.toNote().copy(title = title, body = body, tags = tags)
        val error = NoteValidator.validate(note)
        if (error != null) {
            _uiState.update { it.copy(saveError = error) }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            // Репозиторий независимо повторяет проверку и не пропускает обход интерфейса.
            runCatching {
                val wasSecret = if (note.isNew) false else repository.get(note.id)?.isSecret
                val id = repository.save(note)
                // Не оставляем старое уведомление с заголовком после превращения в секретную.
                if (note.isSecret && wasSecret == false && !note.isNew) reminderScheduler.cancel(id)
                id
            }
                .onSuccess { id ->
                    note.photoPath?.let(draftPhotos::remove)
                    _uiState.update { it.copy(
                        noteId = id, title = title, body = body, tags = tags.trimEnd(),
                        storedSecret = note.isSecret, saveError = null, isSaving = false,
                    ) }
                    _events.send(NoteEvent.Saved(id))
                }
                .onFailure { error ->
                    Log.w("NoteViewModel", "Save failed", error)
                    _uiState.update { it.copy(isSaving = false,
                        saveError = error.message ?: "Не удалось сохранить заметку") }
                }
        }
    }

    fun delete() {
        val state = _uiState.value
        if (state.isLoading || state.notFound || state.isSaving) return
        viewModelScope.launch {
            // Ошибка Room не должна завершать корутину необработанным исключением.
            runCatching { state.isNew || repository.requestDelete(state.noteId) != null }
                .onSuccess { deleted ->
                    if (deleted) _events.send(NoteEvent.Close)
                }
                .onFailure { error ->
                    Log.w("NoteViewModel", "Delete failed", error)
                    _uiState.update { it.copy(saveError = error.message ?: "Не удалось удалить заметку") }
                }
        }
    }

    fun onPhotoPicked(uri: Uri) {
        if (_uiState.value.isLoading || _uiState.value.notFound || _uiState.value.isSaving) return
        viewModelScope.launch {
            runCatching { photoStore.copyToPrivateStorage(uri) }
                .onSuccess { path ->
                    val state = _uiState.value
                    if (state.isSaving || state.notFound) {
                        photoStore.deleteOwned(path)
                    } else {
                        // Заменённое до сохранения фото не должно оставаться в private storage.
                        state.photoPath?.let { old ->
                            if (draftPhotos.remove(old)) photoStore.deleteOwned(old)
                        }
                        draftPhotos.add(path)
                        _uiState.update { it.copy(photoPath = path) }
                    }
                }
                .onFailure {
                    Log.w("NoteViewModel", "Cannot store photo", it)
                    _events.send(NoteEvent.Message(R.string.note_photo_failed))
                }
        }
    }

    fun removePhoto() {
        val state = _uiState.value
        if (state.notFound || state.isLoading || state.isSaving) return
        state.photoPath?.let { old ->
            if (draftPhotos.remove(old)) photoStore.deleteOwned(old)
        }
        _uiState.update { it.copy(photoPath = null) }
    }

    override fun onCleared() {
        super.onCleared()
        // Если Save был прерван навигацией после DB-commit, не удаляем фото, уже связанное с записью.
        // Лучше оставить временную копию, чем разрушить сохранённую заметку при гонке.
        if (!_uiState.value.isSaving) draftPhotos.forEach(photoStore::deleteOwned)
        draftPhotos.clear()
    }

    fun scheduleReminder() {
        val state = _uiState.value
        if (state.isNew || state.isLoading || state.notFound || state.isSaving) return
        viewModelScope.launch {
            val saved = repository.get(state.noteId) ?: return@launch
            // При несохранённом включении секретности также не показываем заголовок.
            val secret = saved.isSecret || _uiState.value.isSecret
            reminderScheduler.schedule(saved.id, saved.title, isSecret = secret)
            _events.send(NoteEvent.Message(R.string.reminder_scheduled))
        }
    }
}
