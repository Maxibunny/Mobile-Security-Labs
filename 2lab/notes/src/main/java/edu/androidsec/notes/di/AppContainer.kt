// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Ручное внедрение зависимостей (DI): единые экземпляры БД, репозитория, фото и напоминаний.
// Важный момент: Контекст приложения живёт дольше Activity; не храним ссылку на Activity во избежание утечек.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.di

import android.content.Context
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.data.RoomNotesRepository
import edu.androidsec.notes.data.db.NotesDatabase
import edu.androidsec.notes.photo.PhotoStore
import edu.androidsec.notes.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Ручной DI-контейнер: один экземпляр на процесс, живёт в [edu.androidsec.notes.NotesApp].
 * Сторонние DI-библиотеки в лабораторной не используются, чтобы все зависимости были читаемы.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // SupervisorJob: сбой одного напоминания не отменяет все остальные задания процесса.
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: NotesDatabase by lazy { NotesDatabase.create(appContext) }

    // ViewModel знает интерфейс репозитория, а реализация знает DAO.
    val notesRepository: NotesRepository by lazy { RoomNotesRepository(database.noteDao()) }

    val photoStore: PhotoStore by lazy { PhotoStore(appContext) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext, appScope) }
}
