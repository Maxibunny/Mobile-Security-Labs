// ЛР2: Ручное внедрение зависимостей (DI): единые экземпляры БД, репозитория, фото и напоминаний.
// Проверка: Контекст приложения живёт дольше Activity; не храним ссылку на Activity во избежание утечек.
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
    val notesRepository: NotesRepository by lazy {
        RoomNotesRepository(
            dao = database.noteDao(), scope = appScope,
            onFinalized = { id, path ->
                runCatching { reminderScheduler.cancel(id) }
                photoStore.deleteOwned(path)
            },
            onOrphanedPhoto = photoStore::deleteOwned,
            onMarkedForDeletion = reminderScheduler::cancel,
        )
    }

    val photoStore: PhotoStore by lazy { PhotoStore(appContext) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext, appScope) { id -> notesRepository.get(id) } }
}
