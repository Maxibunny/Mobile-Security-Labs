// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Типизированные маршруты Compose Navigation: ListRoute и NoteRoute(id).
// Важный момент: id=0 используется только для создания новой записи из UI, не допускается внешним deep link.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.ui.navigation

import kotlinx.serialization.Serializable

/** Типобезопасные маршруты Navigation Compose. */

@Serializable
// Это стартовый экран приложения и цель возврата из редактора.
data object ListRoute

/** Экран заметки; `id == NEW_NOTE_ID` — новая заметка. */
@Serializable
data class NoteRoute(val id: Long) {
    companion object {
        const val NEW_NOTE_ID = 0L
    }
}
