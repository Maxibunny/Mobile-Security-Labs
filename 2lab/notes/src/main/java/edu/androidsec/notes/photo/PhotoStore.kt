// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Копирование выбранного изображения во внутреннюю папку и загрузка уменьшенного превью.
// Важный момент: Выданный Photo Picker временный URI не используется как постоянная ссылка в БД.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Хранит копии выбранных фото во внутреннем хранилище приложения (`filesDir/photos`).
 * Временное право на чтение `content://`-URI из выбора фото живёт недолго,
 * поэтому файл копируется сразу, а в базе хранится путь к копии.
 */
class PhotoStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    /** Копирует содержимое [uri] в приватный файл и возвращает его абсолютный путь. */
    suspend fun copyToPrivateStorage(uri: Uri): String = withContext(Dispatchers.IO) {
        // Уникальное имя уменьшает риск перезаписать старую фотографию другой заметки.
        val target = File(dir, "${UUID.randomUUID()}.jpg")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri")
        // use гарантирует закрытие обоих потоков, даже при ошибке копирования.
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        target.absolutePath
    }

    /** Декодирует файл с уменьшением до разумного размера для превью. */
    suspend fun loadPreview(path: String, maxSide: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        // Сначала считываем только размеры (без декодирования полной картинки в RAM).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        const val DIR = "photos"
    }
}
