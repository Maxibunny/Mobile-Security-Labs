package edu.androidsec.notes.domain

/** Модель заметки; 0 в id означает ещё не сохранённую запись. */
data class Note(
    val id: Long = 0L,
    val title: String = "",
    val body: String = "",
    val photoPath: String? = null,
    val updatedAt: Long = 0L,
    val tags: String = "",
    val isSecret: Boolean = false,
    /** 0 — активна, иначе время окончательного удаления (миллисекунды Unix). */
    val pendingDeleteAt: Long = 0L,
) {
    val isNew: Boolean get() = id == 0L
}

/** Публичная минимальная проекция для signature ContentProvider. */
data class NoteTitle(val id: Long, val title: String)
