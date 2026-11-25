// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Инструментальный тест парсера: корректный URI, некорректный URI, обычный запуск.
// Важный момент: Валидный, но отсутствующий id проверяется отдельно UI/ViewModel, не самим парсером.
// См. README.md и исходники тестируемого компонента.
// ======================================================================

/*
 * ФАЙЛ: NoteDeepLinkTest.kt
 *
 * Назначение:
 * Проверяет безопасную обработку внешних ссылок вида notes://note/id.
 * Некорректные ссылки не должны приводить к ошибкам приложения.
 *
 * ТЕСТЫ:
 *
 * validId()
 * Проверяет правильное извлечение ID из корректных ссылок.
 * Например, notes://note/1 преобразуется в IncomingNote.Open(1).
 * Наличие заметки в базе этот тест не проверяет.
 *
 * invalidIds()
 * Проверяет отклонение неправильных ссылок:
 * буквы вместо ID, нулевые и отрицательные ID,
 * переполнение числа, лишние сегменты, параметры,
 * фрагменты и неподходящие адреса.
 *
 * regularLaunchDoesNotNavigate()
 * Проверяет, что обычный запуск приложения без deep link
 * не вызывает переход к определённой заметке.
 *
 * ИТОГ:
 * Внешние ссылки обрабатываются предсказуемо,
 * а недопустимый ID не передаётся дальше в навигацию.
 */

package edu.androidsec.notes

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.androidsec.notes.deeplink.IncomingNote
import edu.androidsec.notes.deeplink.NoteDeepLink
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** ipc-3: Android-инструментальные проверки парсера недоверенных VIEW URI. */
@RunWith(AndroidJUnit4::class)
class NoteDeepLinkTest {
    private fun parse(value: String) = NoteDeepLink.parse(
        Intent(Intent.ACTION_VIEW, Uri.parse(value)),
    )

    @Test fun validId() {
        assertEquals(IncomingNote.Open(1), parse("notes://note/1"))
        assertEquals(IncomingNote.Open(999999), parse("notes://note/999999"))
    }

    // Параметр «недоверенный»: SQL и навигация не должны получать мусор или переполнение.
    @Test fun invalidIds() {
        listOf(
            "notes://note/", "notes://note/abc", "notes://note/0", "notes://note/-1",
            "notes://note/1/2", "notes://note/1/", "notes://note/%31",
            "notes://note/9223372036854775808", "notes://note/1?debug=2",
            "notes://note/1#fragment", "notes://note:444/1", "notes://attacker/1",
            "https://note/1", "notes://note/1a",
        ).forEach { assertEquals("URI: $it", IncomingNote.Invalid, parse(it)) }
    }

    @Test fun regularLaunchDoesNotNavigate() {
        assertEquals(IncomingNote.None, NoteDeepLink.parse(null))
        assertEquals(IncomingNote.None, NoteDeepLink.parse(Intent(Intent.ACTION_MAIN)))
    }
}
