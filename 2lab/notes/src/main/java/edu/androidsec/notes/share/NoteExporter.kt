// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Формирует одноразовый текстовый файл заметки в cacheDir/exports.
// Важный момент: Путь к файлу не отправляется наружу напрямую: этим занимается FileProvider.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.share

import android.content.Context
import edu.androidsec.notes.domain.Note
import java.io.File

/** Пишет заметку в текстовый файл `cacheDir/exports/<id>.txt` и возвращает его. */
object NoteExporter {
    const val DIR = "exports"

    fun export(context: Context, note: Note): File {
        // Ровно этот каталог разрешён в res/xml/file_paths.xml.
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, "${note.id}.txt")
        file.writeText(
            buildString {
                appendLine(note.title)
                appendLine()
                append(note.body)
            },
        )
        return file
    }
}
