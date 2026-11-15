package edu.androidsec.notes.domain

import org.junit.Assert.*
import org.junit.Test

/** JVM: обе стороны валидации проверяются без Android и БД. */
class NoteValidatorTest {
    @Test fun validValuesAndExactBoundaries() {
        assertNull(NoteValidator.validate(Note(title = "Планы 2026!", body = "Текст\nещё\tстрока", tags = "work-2 personal")))
        assertNull(NoteValidator.validate(Note(title = "", body = "", tags = "")))
        assertNull(NoteValidator.titleError("a".repeat(100)))
        assertNull(NoteValidator.titleError("😀".repeat(100))) // Количество Unicode code points.
        assertNull(NoteValidator.bodyError("a".repeat(5000)))
        assertNull(NoteValidator.tagsError("a".repeat(20)))
        assertNull(NoteValidator.tagsError("a bb ccc dddd eeeee"))
        assertNull(NoteValidator.tagsError("one two ")) // Пробел в конце нужен при наборе.
    }

    @Test fun oversizedInputsAreRejected() {
        assertNotNull(NoteValidator.titleError("a".repeat(101)))
        assertNotNull(NoteValidator.titleError("😀".repeat(101)))
        assertNotNull(NoteValidator.bodyError("a".repeat(5001)))
        assertNotNull(NoteValidator.tagsError("a".repeat(21)))
        assertNotNull(NoteValidator.titleError("😀".repeat(101)))
        assertNotNull(NoteValidator.bodyError("😀".repeat(5001)))
        assertNotNull(NoteValidator.tagsError("a".repeat(100_000)))
    }

    @Test fun singleLineAndNonPrintableRules() {
        listOf("one\ntwo", "one\rtwo", "one\u0000two", "one\u2028two").forEach {
            assertNotNull("title $it", NoteValidator.titleError(it))
        }
        assertNotNull(NoteValidator.bodyError("a\u0000b"))
        assertNotNull(NoteValidator.bodyError("a\rb"))
        assertNull(NoteValidator.bodyError("a\nb\tc"))
        assertNull(NoteValidator.titleError("Текст с пунктуацией: -!?"))
    }

    @Test fun invalidTagsAndSixthTag() {
        listOf("тег", "HELLO", "one_1", "a\tb", " a", "a  b", "a\nb", "one,two",
            "one two three four five six", "one two three four five x").forEach {
            assertNotNull("tags $it", NoteValidator.tagsError(it))
        }
        assertNull(NoteValidator.tagsError("a-9"))
        assertNull(NoteValidator.tagsError("1 2 3 4 5"))
    }

    @Test fun validationAppliesToWholeNote() {
        assertNotNull(NoteValidator.validate(Note(title = "x".repeat(101))))
        assertNotNull(NoteValidator.validate(Note(body = "x".repeat(5001))))
        assertNotNull(NoteValidator.validate(Note(tags = "CAPS")))
        assertNotNull(NoteValidator.validate(Note(tags = "one two three four five six")))
    }
}
