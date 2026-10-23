// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Чтение ContentProvider из другого приложения через ContentResolver.query.
// Важный момент: Системное signature-разрешение проверяется при каждом обращении; диагностика не выдаёт право.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.client

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Диагностика окружения: выдано ли разрешение и совпадают ли подписи. */
data class Diagnostics(
    val notesInstalled: Boolean,
    val permissionGranted: Boolean,
    val signatureMatches: Boolean,
)

/** Обращается к провайдеру заголовков модуля :notes. */
class TitlesClient(private val context: Context) {

    suspend fun queryTitles(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            // Binder обращается к другой программе; Android проверяет signature на входе.
            val cursor = context.contentResolver.query(
                TITLES_URI,
                arrayOf(BaseColumns._ID, COLUMN_TITLE),
                null,
                null,
                null,
            ) ?: throw NullCursorException()
            // Курсор закрываем обязательно: Binder-ресурсы не должны утекать.
            cursor.use {
                val titleIndex = it.getColumnIndexOrThrow(COLUMN_TITLE)
                buildList {
                    while (it.moveToNext()) add(it.getString(titleIndex))
                }
            }
        }
    }

    fun diagnostics(): Diagnostics {
        val pm = context.packageManager
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(NOTES_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(NOTES_PACKAGE, 0)
            }
        }.isSuccess
        // Диагностика UI: не является заменой проверки манифеста на стороне провайдера.
        val granted = pm.checkPermission(READ_NOTES_PERMISSION, context.packageName) ==
            PackageManager.PERMISSION_GRANTED
        val sameSignature = installed &&
            pm.checkSignatures(NOTES_PACKAGE, context.packageName) == PackageManager.SIGNATURE_MATCH
        return Diagnostics(installed, granted, sameSignature)
    }

    class NullCursorException : RuntimeException("query() returned null")

    companion object {
        const val NOTES_PACKAGE = "edu.androidsec.notes"
        const val READ_NOTES_PERMISSION = "edu.androidsec.notes.permission.READ_NOTES"
        const val COLUMN_TITLE = "title"
        val TITLES_URI: Uri = "content://edu.androidsec.notes.provider/notes".toUri()
    }
}
