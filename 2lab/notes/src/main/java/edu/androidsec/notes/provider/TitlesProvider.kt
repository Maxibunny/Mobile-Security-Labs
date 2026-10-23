// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Внешний Binder IPC: ContentProvider выдаёт только _id и title для клиента.
// Важный момент: Android проверяет signature-разрешение до вызова query; имя пакета и UID логируются, но не авторизуют.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.provider.BaseColumns
import android.util.Log
import edu.androidsec.notes.appContainer

/**
 * ipc-6: read-only Binder IPC. Манифестное signature-разрешение контролирует сам Android
 * ДО вызова query(): проверка в UI или по имени пакета не заменила бы этот барьер.
 * Публичный URI: content://edu.androidsec.notes.provider/notes.
 * Разрешены только две колонки (_id, title) и путь /notes; всё остальное отвергается.
 */
class TitlesProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, PATH_NOTES, MATCH_NOTES)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        // 1. Не принимаем /notes/1, другие пути или неизвестный authority.
        requireNotesUri(uri)
        require(selection == null && selectionArgs == null && sortOrder == null) {
            "Selection and custom sorting are not supported"
        }
        // 2. Даже подписанный клиент не может запросить body или photo_path.
        val columns = projection ?: arrayOf(BaseColumns._ID, COLUMN_TITLE)
        require(columns.all { it == BaseColumns._ID || it == COLUMN_TITLE }) {
            "Only _id and title may be queried"
        }

        // Binder UID нужен для диагностики, но НЕ используется как самодельная авторизация.
        Log.i(TAG, "query: callingUid=${Binder.getCallingUid()}, package=${callingPackage ?: "unknown"}")
        val dao = requireNotNull(context).appContainer.database.noteDao()
        // 3. Сборка результата в памяти, без выдачи наружу соединения Room/SQLite.
        val cursor = MatrixCursor(columns)
        dao.titlesBlocking().forEach { note ->
            cursor.addRow(columns.map { if (it == BaseColumns._ID) note.id else note.title })
        }
        return cursor
    }

    override fun getType(uri: Uri): String {
        requireNotesUri(uri)
        return "vnd.android.cursor.dir/vnd.edu.androidsec.notes.title"
    }

    // Наличие signature-разрешения НЕ означает права на запись: все мутации запрещены.
    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("TitlesProvider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("TitlesProvider is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("TitlesProvider is read-only")

    private fun requireNotesUri(uri: Uri) {
        require(matcher.match(uri) == MATCH_NOTES) { "Unknown URI: $uri" }
    }

    companion object {
        const val TAG = "TitlesProvider"
        const val AUTHORITY = "edu.androidsec.notes.provider"
        const val PERMISSION = "edu.androidsec.notes.permission.READ_NOTES"
        const val PATH_NOTES = "notes"
        const val COLUMN_TITLE = "title"
        private const val MATCH_NOTES = 1
    }
}
