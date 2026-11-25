package edu.androidsec.notes.data

/*
 * ФАЙЛ: NoteDaoSecurityTest.kt
 *
 * Назначение:
 * Проверка безопасности операций с заметками на уровне Room DAO.
 * Здесь исследуется поведение запросов к базе данных.
 *
 * Точные @Test-методы необходимо посмотреть в исходнике.
 */

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import edu.androidsec.notes.data.db.NoteEntity
import edu.androidsec.notes.data.db.NotesDatabase
import edu.androidsec.notes.domain.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Тесты на устройстве: реальный Room/SQLite, в том числе ESCAPE '\'. */
@RunWith(AndroidJUnit4::class)
class NoteDaoSecurityTest {
    private lateinit var db: NotesDatabase
    private lateinit var repo: RoomNotesRepository
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Before fun init() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        repo = RoomNotesRepository(db.noteDao(), scope)
    }

    @After fun close() { scope.cancel(); db.close() }

    @Test fun searchTreatsPercentUnderscoreAndBackslashLiterally() = runBlocking {
        val dao = db.noteDao()
        suspend fun add(title: String, body: String = "") =
            dao.insert(NoteEntity(title = title, body = body, photoPath = null, updatedAt = 10))
        add("100% done")
        add("file_name")
        add("folder\\document")
        add("plain", "content%test")
        assertEquals(setOf("100% done", "plain"), repo.search("%", NoteSort.TITLE).first().map { it.title }.toSet())
        assertEquals(listOf("file_name"), repo.search("_", NoteSort.TITLE).first().map { it.title })
        assertEquals(listOf("folder\\document"), repo.search("\\", NoteSort.TITLE).first().map { it.title })
        assertTrue(repo.search("%' OR 1=1 --", NoteSort.TITLE).first().isEmpty())
    }

    @Test fun eachSortHasItsOwnFixedQuery() = runBlocking {
        val dao = db.noteDao()
        dao.insert(NoteEntity(title = "Zulu", body = "hello", photoPath = null, updatedAt = 1))
        dao.insert(NoteEntity(title = "Alpha", body = "hello", photoPath = null, updatedAt = 3))
        assertEquals(listOf("Alpha", "Zulu"), repo.search("", NoteSort.TITLE).first().map { it.title })
        assertEquals(listOf("Alpha", "Zulu"), repo.search("", NoteSort.NEWEST).first().map { it.title })
        assertEquals(listOf("Zulu", "Alpha"), repo.search("", NoteSort.OLDEST).first().map { it.title })
    }

    @Test fun invalidRepositorySaveCannotReachDatabase() = runBlocking {
        try { repo.save(Note(title = "bad\ntitle")); fail("Must reject") }
        catch (_: IllegalArgumentException) { }
        try { repo.save(Note(tags = "one two three four five six")); fail("Must reject") }
        catch (_: IllegalArgumentException) { }
        assertTrue(db.noteDao().observeAll().first().isEmpty())
    }

    @Test fun secretTitlesAreNotPublishedViaSignatureProviderQuery() = runBlocking {
        repo.save(Note(title = "Секрет", isSecret = true))
        repo.save(Note(title = "Открыто"))
        assertEquals(listOf("Открыто"), db.noteDao().titlesBlocking().map { it.title })
    }

    @Test fun searchByBodyAndAllFixedSorts() = runBlocking {
        val dao = db.noteDao()
        dao.insert(NoteEntity(title = "Z", body = "hello\\world", photoPath = null, updatedAt = 2))
        dao.insert(NoteEntity(title = "A", body = "hello%world", photoPath = null, updatedAt = 3))
        dao.insert(NoteEntity(title = "B", body = "hello_world", photoPath = null, updatedAt = 1))
        assertEquals(listOf("Z"), repo.search("\\", NoteSort.TITLE).first().map { it.title })
        assertEquals(listOf("A"), repo.search("%", NoteSort.NEWEST).first().map { it.title })
        assertEquals(listOf("B"), repo.search("_", NoteSort.OLDEST).first().map { it.title })
    }

    @Test fun undoAfterRepositoryRestartKeepsFullRecord() = runBlocking {
        val id = repo.save(Note(title = "Saved", body = "body", tags = "a-b", isSecret = true,
            photoPath = "/private/photo.jpg"))
        val ticket = requireNotNull(repo.requestDelete(id))
        // Имитируем смерть процесса: отменяем прежние корутины, создаём новый репозиторий.
        scope.cancel()
        val restartedScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val restarted = RoomNotesRepository(db.noteDao(), restartedScope)
        assertNull(restarted.get(id))
        assertTrue(restarted.undoDelete(ticket))
        val restored = requireNotNull(restarted.get(id))
        restartedScope.cancel()
        assertEquals("Saved", restored.title)
        assertEquals("body", restored.body)
        assertEquals("a-b", restored.tags)
        assertTrue(restored.isSecret)
        assertEquals("/private/photo.jpg", restored.photoPath)
    }

    @Test fun interruptedProcessFinalizesDeletionAfterRestart() = runBlocking {
        val id = repo.save(Note(title = "After reboot", body = "saved"))
        val ticket = requireNotNull(repo.requestDelete(id))
        scope.cancel() // Предыдущий репозиторий больше ничего не удалит.
        delay((ticket.deadline - System.currentTimeMillis() + 150L).coerceAtLeast(0L))
        val restartedScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            RoomNotesRepository(db.noteDao(), restartedScope)
            withTimeout(4000L) {
                db.noteDao().observePending().first { pending -> pending.none { it.id == id } }
            }
            assertNull(db.noteDao().getById(id))
        } finally { restartedScope.cancel() }
    }

    @Test fun requestDeleteCannotBeDuplicatedOrUpdatePendingRecord() = runBlocking {
        val id = repo.save(Note(title = "Mutable"))
        val ticket = requireNotNull(repo.requestDelete(id))
        assertNull(repo.requestDelete(id))
        try {
            repo.save(Note(id = id, title = "Should fail"))
            fail("Cannot save a pending deletion")
        } catch (_: IllegalArgumentException) { }
        assertTrue(repo.undoDelete(ticket))
        assertEquals("Mutable", repo.get(id)?.title)
    }

    @Test fun deletionBecomesPermanentAfterFiveSeconds() = runBlocking {
        val id = repo.save(Note(title = "Удалить"))
        val ticket = requireNotNull(repo.requestDelete(id))
        withTimeout(8000L) {
            repo.observePending().first { entries -> entries.none { it.id == id } }
        }
        assertFalse(repo.undoDelete(ticket))
        assertNull(repo.get(id))
    }

    @Test fun undoRestoresEveryFieldAndPreventsStaleTimer() = runBlocking {
        val savedId = repo.save(Note(title = "Secret", body = "private", tags = "a-b", isSecret = true, photoPath = "/private/file.jpg"))
        val before = requireNotNull(repo.get(savedId))
        val ticket = requireNotNull(repo.requestDelete(savedId))
        assertNull(repo.get(savedId))
        assertTrue(repo.observeAll().first().isEmpty())
        assertTrue(repo.undoDelete(ticket))
        assertEquals(before, repo.get(savedId))
        assertEquals(0, db.noteDao().finalizeDeletion(ticket.id, ticket.deadline))
        assertNotNull(repo.get(savedId))
    }
}
