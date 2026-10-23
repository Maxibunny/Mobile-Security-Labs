// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: DAO описывает SQL-операции и позволяет Room создать реализацию автоматически.
// Важный момент: Провайдер использует только titlesBlocking(): SELECT id,title, без текста заметки.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import edu.androidsec.notes.domain.NoteTitle
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // Flow автоматически отдаёт обновлённый список после изменения таблицы Room.
    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    // REPLACE: сохранение существующей записи с тем же первичным ключом заменяет её.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Синхронный запрос заголовков: вызывается из `ContentProvider.query()`,
     * который работает в binder-потоке, а не в главном.
     */
    // Минимальная проекция: содержимое body и путь к фото не уходят в IPC.
    @Query("SELECT id, title FROM notes ORDER BY id")
    fun titlesBlocking(): List<NoteTitle>
}
