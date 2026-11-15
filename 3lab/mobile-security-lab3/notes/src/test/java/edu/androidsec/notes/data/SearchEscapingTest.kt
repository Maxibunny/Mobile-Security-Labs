package edu.androidsec.notes.data

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
