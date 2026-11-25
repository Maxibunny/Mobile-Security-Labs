package edu.androidsec.notes.data

/*
 * ФАЙЛ: NotesMigrationTest.kt
 *
 * Назначение:
 * Проверка миграции базы Room со старой версии на новую.
 *
 * Основная цель лабораторной:
 * убедиться, что новые поля tags и is_secret добавляются
 * без потери заметок, созданных в предыдущей версии.
 *
 * Точные @Test-методы необходимо посмотреть в исходнике.
 */

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.androidsec.notes.data.db.NotesDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Создаёт БД в формате v1, затем открывает v2 через зарегистрированную Migration. */
@RunWith(AndroidJUnit4::class)
class NotesMigrationTest {
    @Test fun migrationKeepsExistingNotesAndSetsNewDefaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-${System.nanoTime()}.db"
        context.deleteDatabase(dbName)
        val old = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        old.execSQL("""CREATE TABLE IF NOT EXISTS notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL, body TEXT NOT NULL, photo_path TEXT,
            updated_at INTEGER NOT NULL)""")
        old.execSQL("INSERT INTO notes (id, title, body, photo_path, updated_at) VALUES (42, 'Старая', 'Данные', '/tmp/old.jpg', 12345)")
        old.version = 1
        old.close()

        val upgraded = Room.databaseBuilder(context, NotesDatabase::class.java, dbName)
            .addMigrations(NotesDatabase.MIGRATION_1_2).build()
        try {
            val saved = requireNotNull(upgraded.noteDao().getById(42))
            assertEquals("Старая", saved.title)
            assertEquals("Данные", saved.body)
            assertEquals("/tmp/old.jpg", saved.photoPath)
            assertEquals(12345L, saved.updatedAt)
            assertEquals("", saved.tags)
            assertFalse(saved.isSecret)
            assertEquals(0L, saved.pendingDeleteAt)
        } finally {
            upgraded.close()
            context.deleteDatabase(dbName)
        }
    }
}
