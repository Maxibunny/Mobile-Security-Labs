// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Инструментальный тест доступного каталога и схемы content://.
// Важный момент: Тест выполняется внутри приложения, поэтому не заменяет отрицательный ADB-тест чужого UID.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.share.NoteExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** ipc-5: проверка реальной границы каталога, заданной в res/xml/file_paths.xml.
 *  Тест запускается как наше приложение и НЕ подменяет отрицательный adb-тест из check.sh.
 */
@RunWith(AndroidJUnit4::class)
class FileProviderScopeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority = "edu.androidsec.notes.files"

    @Test fun exportUsesContentUriAndExportsOnlyNotesDirectory() {
        val note = Note(id = 101, title = "Заголовок", body = "Текст", photoPath = null)
        val file = NoteExporter.export(context, note)
        val uri = FileProvider.getUriForFile(context, authority, file)
        assertEquals("content", uri.scheme)
        assertEquals(authority, uri.authority)
        assertTrue(uri.path.orEmpty().startsWith("/exports/"))
        context.contentResolver.openInputStream(uri)!!.use { input ->
            assertTrue(input.bufferedReader().readText().contains("Заголовок"))
        }
    }

    // Каталоги рядом с exports/ НЕ становятся публичными из-за ошибочного <root-path>.
    @Test fun siblingCacheAndInternalFileAreNotShareable() {
        val outsideCache = File(context.cacheDir, "private.txt").apply { writeText("not shared") }
        val insideFiles = File(context.filesDir, "private.txt").apply { writeText("not shared") }
        for (file in listOf(outsideCache, insideFiles)) {
            val result = runCatching { FileProvider.getUriForFile(context, authority, file) }
            assertTrue("Cannot export file outside cacheDir/exports: ${file.path}", result.isFailure)
        }
    }
}
