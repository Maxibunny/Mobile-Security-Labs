// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Application создаётся системой до первого экрана; регистрирует канал напоминаний и контейнер.
// Важный момент: Сама по себе Application не является экспортируемым IPC-компонентом.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import edu.androidsec.notes.di.AppContainer
import edu.androidsec.notes.reminder.ReminderScheduler

class NotesApp : Application() {

    // lazy = создавать БД/репозиторий только при первом обращении, не при запуске процесса.
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Android 8+ требует канал для показа уведомлений; id «reminders» неизменяем.
        createReminderChannel()
    }

    /** Канал уведомлений создаётся один раз при старте; id канала фиксирован заданием. */
    private fun createReminderChannel() {
        val channel = NotificationChannel(
            ReminderScheduler.CHANNEL_ID,
            getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_reminders_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** Доступ к контейнеру из Activity, ресивера и провайдера. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as NotesApp).container
