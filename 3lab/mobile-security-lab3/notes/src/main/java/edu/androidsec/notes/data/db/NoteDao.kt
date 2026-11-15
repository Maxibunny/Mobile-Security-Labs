package edu.androidsec.notes.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import edu.androidsec.notes.domain.NoteTitle
import kotlinx.coroutines.flow.Flow

/** Статические SQL-запросы. Ни имя колонки, ни SQL не строятся из пользовательского ввода. */
@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE pending_delete_at = 0 ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE pending_delete_at != 0 ORDER BY pending_delete_at ASC")
    fun observePending(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id AND pending_delete_at = 0")
    suspend fun getById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity): Long

    // Существующую заметку обновляем лишь пока она активна; таймер удаления не сбрасываем.
    @Query("""UPDATE notes SET title=:title, body=:body, photo_path=:photoPath,
        updated_at=:updatedAt, tags=:tags, is_secret=:secret
        WHERE id=:id AND pending_delete_at = 0""")
    suspend fun updateActive(id: Long, title: String, body: String, photoPath: String?,
        updatedAt: Long, tags: String, secret: Boolean): Int

    // Одним UPDATE прячем запись; исходные поля и фото остаются для Undo.
    @Query("UPDATE notes SET pending_delete_at = :deadline WHERE id = :id AND pending_delete_at = 0")
    suspend fun markForDeletion(id: Long, deadline: Long): Int

    @Query("UPDATE notes SET pending_delete_at = 0 WHERE id = :id AND pending_delete_at = :deadline")
    suspend fun undoDeletion(id: Long, deadline: Long): Int

    // Защита от гонки: устаревший таймер не сможет удалить восстановленную заметку.
    @Query("SELECT COUNT(*) FROM notes WHERE photo_path = :path")
    suspend fun countPhotoReferences(path: String): Int

    @Query("DELETE FROM notes WHERE id = :id AND pending_delete_at = :deadline")
    suspend fun finalizeDeletion(id: Long, deadline: Long): Int

    // LIKE-параметр привязан Room; ESCAPE '\' делает %, _ и \ обычными символами после экранирования.
    @Query("""SELECT * FROM notes WHERE pending_delete_at = 0
        AND (title LIKE :pattern ESCAPE '\' OR body LIKE :pattern ESCAPE '\')
        ORDER BY title COLLATE NOCASE ASC, id ASC""")
    fun searchByTitle(pattern: String): Flow<List<NoteEntity>>

    @Query("""SELECT * FROM notes WHERE pending_delete_at = 0
        AND (title LIKE :pattern ESCAPE '\' OR body LIKE :pattern ESCAPE '\')
        ORDER BY updated_at DESC, id DESC""")
    fun searchNewest(pattern: String): Flow<List<NoteEntity>>

    @Query("""SELECT * FROM notes WHERE pending_delete_at = 0
        AND (title LIKE :pattern ESCAPE '\' OR body LIKE :pattern ESCAPE '\')
        ORDER BY updated_at ASC, id ASC""")
    fun searchOldest(pattern: String): Flow<List<NoteEntity>>

    // Стороннему клиенту отдаём только заголовки; удаляемые заметки уже не показываем.
    @Query("SELECT id, title FROM notes WHERE pending_delete_at = 0 AND is_secret = 0 ORDER BY id")
    fun titlesBlocking(): List<NoteTitle>
}
