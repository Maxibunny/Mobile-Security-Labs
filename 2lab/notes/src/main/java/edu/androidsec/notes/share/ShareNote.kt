// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Экспортирует заметку в системный ACTION_SEND chooser как content:// URI.
// Важный момент: FLAG_GRANT_READ_URI_PERMISSION даёт временный доступ только получателю выбранного файла.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import edu.androidsec.notes.R
import edu.androidsec.notes.domain.Note
import java.io.File

/**
 * ipc-5: NoteExporter пишет в cacheDir/exports; FileProvider выдаёт URI на один файл.
 * Получатель ACTION_SEND получает временный read-grant, но не доступ ко всему cacheDir.
 * При отсутствии гранта adb shell content read обязан получать Permission Denial.
 */
object ShareNote {
    const val AUTHORITY = "edu.androidsec.notes.files"

    fun shareNoteAsFile(context: Context, note: Note): File {
        val file = NoteExporter.export(context, note)
        // Преобразует приватный File в URI, который не раскрывает абсолютный путь на диске.
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            // URI передаём потоком, а не через file:// (запрещённый для внешнего шаринга).
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "note-${note.id}", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_chooser_title)).apply {
            // Сохраняем URI-грант и для chooser (нужен некоторым реализациям Android).
            // Совместимость с Android Sharesheet: грант должен дойти до конечного получателя.
            clipData = sendIntent.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return file // сохраняем сигнатуру, которую использует экран и существующий код
    }
}
