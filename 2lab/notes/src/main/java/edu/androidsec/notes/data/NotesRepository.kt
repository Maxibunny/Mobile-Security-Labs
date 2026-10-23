// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Репозиторий отделяет ViewModel от Room: чтение списка, поиск, сохранение и удаление.
// Важный момент: Все экраны работают с Note, а не с курсором SQL или открытым ContentProvider.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.data

import edu.androidsec.notes.data.db.NoteDao
import edu.androidsec.notes.data.db.toDomain
import edu.androidsec.notes.data.db.toEntity
import edu.androidsec.notes.domain.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Порт доступа к заметкам. ViewModel-и зависят только от него. */
interface NotesRepository {
    fun observeAll(): Flow<List<Note>>
    suspend fun get(id: Long): Note?
    /** Сохраняет заметку и возвращает её id (для новой заметки — только что выданный). */
    suspend fun save(note: Note): Long
    suspend fun delete(id: Long)
}

class RoomNotesRepository(private val dao: NoteDao) : NotesRepository {

    // map преобразует NoteEntity в Note, сохраняя реактивный Flow.
    override fun observeAll(): Flow<List<Note>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): Note? = dao.getById(id)?.toDomain()

    override suspend fun save(note: Note): Long {
        // Новая метка времени поддерживает сортировку списка по свежести изменений.
        val stamped = note.copy(updatedAt = System.currentTimeMillis())
        val rowId = dao.upsert(stamped.toEntity())
        return if (note.isNew) rowId else note.id
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
