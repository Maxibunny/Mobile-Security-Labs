package edu.androidsec.notes.data

/*
 * ФАЙЛ: SearchEscapingTest.kt
 *
 * Назначение:
 * Проверка экранирования специальных символов SQL LIKE.
 *
 * В лабораторной важно, чтобы символы %, _ и \
 * воспринимались как обычный текст при поиске,
 * а не как специальные шаблоны SQL.
 *
 * Точные @Test-методы необходимо посмотреть в исходнике.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

/** Проверяет формирование только параметра LIKE, без конкатенации SQL. */
class SearchEscapingTest {
    @Test fun wildcardsBecomeLiterals() {
        assertEquals("%100\\% ready%", RoomNotesRepository.escapeLike("100% ready"))
        assertEquals("%a\\_b%", RoomNotesRepository.escapeLike("a_b"))
        assertEquals("%a\\\\b%", RoomNotesRepository.escapeLike("a\\b"))
    }
}
