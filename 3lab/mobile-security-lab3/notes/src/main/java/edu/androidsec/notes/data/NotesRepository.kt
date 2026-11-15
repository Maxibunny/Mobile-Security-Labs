package edu.androidsec.notes.data

import edu.androidsec.notes.data.db.NoteDao
import edu.androidsec.notes.data.db.toDomain
import edu.androidsec.notes.data.db.toEntity
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Варианты сортировки фиксированы; пользователь никогда не вводит SQL. */
enum class NoteSort { TITLE, NEWEST, OLDEST }

data class DeleteTicket(val id: Long, val deadline: Long)

interface NotesRepository {
    fun observeAll(): Flow<List<Note>>
    fun observePending(): Flow<List<DeleteTicket>>
    fun search(text: String, sort: NoteSort): Flow<List<Note>>
    suspend fun get(id: Long): Note?
    suspend fun save(note: Note): Long
    suspend fun requestDelete(id: Long): DeleteTicket?
    suspend fun undoDelete(ticket: DeleteTicket): Boolean
}

/** Единственная точка записи в БД: серверная валидация + отложенное удаление. */
class RoomNotesRepository(
    private val dao: NoteDao,
    scope: CoroutineScope,
    private val onFinalized: (Long, String?) -> Unit = { _, _ -> },
    private val onOrphanedPhoto: (String) -> Unit = {},
    private val onMarkedForDeletion: (Long) -> Unit = {},
) : NotesRepository {
    init {
        // Flow подхватывает tombstone после перезапуска процесса: удаление не теряется и не бывает «наполовину».
        scope.launch {
            dao.observePending().collectLatest { entries ->
                coroutineScope {
                    entries.forEach { entry ->
                        launch {
                            delay((entry.pendingDeleteAt - System.currentTimeMillis()).coerceAtLeast(0))
                            // После DB-DELETE доводим очистку до конца, даже если Flow уже переиздал список.
                            withContext(NonCancellable) {
                                if (dao.finalizeDeletion(entry.id, entry.pendingDeleteAt) == 1) {
                                    val unusedPath = entry.photoPath?.takeIf {
                                        dao.countPhotoReferences(it) == 0
                                    }
                                    runCatching { onFinalized(entry.id, unusedPath) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun observeAll() = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observePending(): Flow<List<DeleteTicket>> = dao.observePending().map { rows ->
        rows.map { DeleteTicket(it.id, it.pendingDeleteAt) }
    }

    override fun search(text: String, sort: NoteSort): Flow<List<Note>> {
        val pattern = escapeLike(text)
        val records = when (sort) {
            NoteSort.TITLE -> dao.searchByTitle(pattern)
            NoteSort.NEWEST -> dao.searchNewest(pattern)
            NoteSort.OLDEST -> dao.searchOldest(pattern)
        }
        return records.map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun get(id: Long) = dao.getById(id)?.toDomain()

    override suspend fun save(note: Note): Long {
        // Даже если UI обойдён, недопустимые данные не попадут в Room.
        val error = NoteValidator.validate(note)
        require(error == null) { error ?: "Некорректная заметка" }
        require(note.pendingDeleteAt == 0L) { "Нельзя изменять удаляемую заметку" }
        val stamped = note.copy(updatedAt = System.currentTimeMillis(), tags = note.tags.trimEnd())
        if (note.isNew) return dao.insert(stamped.toEntity())
        // UPDATE не воскрешает tombstone; прежнее фото удаляем только если никто на него не ссылается.
        val previousPhoto = dao.getById(note.id)?.photoPath
        require(dao.updateActive(note.id, stamped.title, stamped.body, stamped.photoPath,
            stamped.updatedAt, stamped.tags, stamped.isSecret) == 1) { "Заметка не найдена" }
        if (previousPhoto != null && previousPhoto != stamped.photoPath &&
            dao.countPhotoReferences(previousPhoto) == 0) {
            runCatching { onOrphanedPhoto(previousPhoto) }
        }
        return note.id
    }

    override suspend fun requestDelete(id: Long): DeleteTicket? {
        if (id <= 0) return null
        val deadline = System.currentTimeMillis() + 5000L
        return if (dao.markForDeletion(id, deadline) == 1) {
            // Уже показанное напоминание больше не должно оставаться на экране блокировки.
            runCatching { onMarkedForDeletion(id) }
            DeleteTicket(id, deadline)
        } else null
    }

    override suspend fun undoDelete(ticket: DeleteTicket): Boolean {
        if (System.currentTimeMillis() >= ticket.deadline) return false
        return dao.undoDeletion(ticket.id, ticket.deadline) == 1
    }

    companion object {
        /** % _ и обратная косая черта превращаются в литералы, а не wildcard. */
        fun escapeLike(text: String): String = "%" + buildString {
            for (c in text) {
                if (c == '%' || c == '_' || c == '\\') append('\\')
                append(c)
            }
        } + "%"
    }
}
