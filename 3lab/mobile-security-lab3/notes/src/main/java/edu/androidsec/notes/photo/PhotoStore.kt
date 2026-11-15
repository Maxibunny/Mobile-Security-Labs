// ЛР2: Копирование выбранного изображения во внутреннюю папку и загрузка уменьшенного превью.
// Проверка: Выданный Photo Picker временный URI не используется как постоянная ссылка в БД.
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
        try {
            input.use { source -> target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val length = source.read(buffer)
                    if (length < 0) break
                    total += length
                    if (total > MAX_PHOTO_BYTES) throw IOException("Photo exceeds 20 MB")
                    output.write(buffer, 0, length)
                }
            } }
            target.absolutePath
        } catch (t: Throwable) {
            target.delete() // Частично скопированный файл не оставляем.
            throw t
        }
    }

    /** Декодирует файл с уменьшением до разумного размера для превью. */
    suspend fun loadPreview(path: String, maxSide: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        // Битый/удалённый старый файл не должен обрушить экран заметки.
        runCatching {
            if (maxSide <= 0) return@runCatching null
            val file = File(path).canonicalFile
            if (file.parentFile != dir.canonicalFile || !file.isFile) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                sample *= 2 // Не декодируем многомегапиксельное фото целиком.
            }
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /** Удаляем только файл, созданный нами, и только после безотзывного удаления заметки. */
    fun deleteOwned(path: String?) {
        if (path == null) return
        runCatching {
            val root = dir.canonicalFile
            val file = File(path).canonicalFile
            if (file.parentFile == root && file.isFile) file.delete()
        }
    }

    companion object {
        const val DIR = "photos"
        private const val MAX_PHOTO_BYTES = 20L * 1024L * 1024L
    }
}
