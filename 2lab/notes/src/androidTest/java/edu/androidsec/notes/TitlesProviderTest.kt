// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Инструментальный тест разрешённых колонок, пути и запрета модификации.
// Важный момент: Проверка чужого сертификата отдельно проводится другим APK на защите.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes

import android.content.ContentValues
import android.net.Uri
import android.provider.BaseColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** ipc-6: проверяем только чтение двух колонок и отказ на операции изменения данных. */
@RunWith(AndroidJUnit4::class)
class TitlesProviderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val uri get() = Uri.parse("content://edu.androidsec.notes.provider/notes")

    @Test fun titlesReadableWithinSameApp() {
        context.contentResolver.query(uri, arrayOf(BaseColumns._ID, "title"), null, null, null)!!.use {
            assertEquals(2, it.columnCount)
            assertEquals(BaseColumns._ID, it.getColumnName(0))
            assertEquals("title", it.getColumnName(1))
            // После переустановки будут три seed-записи; но на рабочем устройстве
            // пользователь мог удалить их. Тест безопасности от этого не зависит.
            while (it.moveToNext()) {
                assertTrue(it.getLong(0) > 0)
                assertNotNull(it.getString(1))
            }
        }
    }

    // Запрет поля body проверяем независимо от разрешения READ_NOTES.
    @Test fun secretColumnsNotReadable() {
        val result = runCatching {
            context.contentResolver.query(uri, arrayOf("body"), null, null, null)?.close()
        }
        assertTrue("body must not be accessible", result.isFailure)
    }

    @Test fun onlyExactNotesPathIsSupported() {
        val wrongPath = Uri.parse("content://edu.androidsec.notes.provider/notes/1")
        val result = runCatching {
            context.contentResolver.query(wrongPath, arrayOf("title"), null, null, null)?.close()
        }
        assertTrue("only /notes is allowed", result.isFailure)
    }

    @Test fun deleteAndUpdateAreForbidden() {
        val updated = runCatching {
            context.contentResolver.update(uri, ContentValues().apply { put("title", "hack") }, null, null)
        }
        val deleted = runCatching { context.contentResolver.delete(uri, null, null) }
        assertTrue("provider must reject update", updated.isFailure)
        assertTrue("provider must reject delete", deleted.isFailure)
    }

    @Test fun insertIsForbidden() {
        val result = runCatching {
            context.contentResolver.insert(uri, ContentValues().apply { put("title", "hack") })
        }
        assertTrue("provider must be read-only", result.isFailure)
    }
}
