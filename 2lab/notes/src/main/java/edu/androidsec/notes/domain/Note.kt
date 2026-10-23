// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Модель бизнес-логики заметки и безопасная сокращённая модель заголовков.
// Важный момент: NoteTitle не содержит body/photoPath: не передавать лишние данные через IPC.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.domain

/** Заметка. `id == 0` означает ещё не сохранённую заметку. */
data class Note(
    val id: Long = 0L,
    val title: String = "",
    val body: String = "",
    /** Абсолютный путь к копии фото во внутреннем хранилище приложения или null. */
    val photoPath: String? = null,
    val updatedAt: Long = 0L,
) {
    // Ноль выбран как внутренний маркер новой записи; внешние URI с id=0 отвергаются.
    val isNew: Boolean get() = id == 0L
}

/** Пара «идентификатор — заголовок» для списка и для провайдера заголовков. */
data class NoteTitle(
    val id: Long,
    val title: String,
)
