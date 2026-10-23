// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Откладывает уведомление на 10 секунд и создаёт два независимых явных PendingIntent.
// Важный момент: Оба PendingIntent IMMUTABLE; отложенная корутина не переживает уничтожение процесса.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.reminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import edu.androidsec.notes.MainActivity
import edu.androidsec.notes.R
import edu.androidsec.notes.deeplink.NoteDeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ipc-4: два явных PendingIntent, оба FLAG_IMMUTABLE. RequestCode и data привязаны к заметке:
 * действие «Готово» для id=1 не перезапишет содержимое PendingIntent для id=2.
 * Для лабораторной задержка идёт в scope процесса — после force-stop напоминание исчезнет.
 * Промышленный вариант требует WorkManager/AlarmManager (здесь не нужен).
 */
class ReminderScheduler(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    // id заметки -> отменяемое задание; память процесса, не постоянное расписание ОС.
    private val jobs = HashMap<Long, Job>()

    fun schedule(noteId: Long, title: String, delayMillis: Long = DEFAULT_DELAY_MILLIS) {
        if (noteId <= 0L || noteId > Int.MAX_VALUE) return // Notification id имеет тип Int
        val text = title.ifBlank { context.getString(R.string.note_untitled) }
        synchronized(jobs) {
            jobs.remove(noteId)?.cancel()
            // Если пользователь повторно устанавливает напоминание, прежняя карточка
            // больше не должна показывать устаревший заголовок.
            NotificationManagerCompat.from(context).cancel(noteId.toInt())
            // delay без блокировки UI; повторное напоминание заменяет предыдущее.
            jobs[noteId] = scope.launch {
                delay(delayMillis)
                showNotification(noteId, text)
            }
        }
    }

    fun cancel(noteId: Long) {
        synchronized(jobs) { jobs.remove(noteId)?.cancel() }
        // При удалении заметки или выборе «Готово» закрываем и уже показанное уведомление.
        if (noteId in 1..Int.MAX_VALUE.toLong()) {
            NotificationManagerCompat.from(context).cancel(noteId.toInt())
        }
    }

    @SuppressLint("MissingPermission") // Выше по стеку permission запрошено; здесь повторная проверка.
    private fun showNotification(noteId: Long, title: String) {
        if (!context.hasNotificationPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted, reminder for note $noteId dropped")
            return
        }

        // Прямое указание MainActivity: другой пакет не может стать получателем.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = NoteDeepLink.uriFor(noteId)
        }
        // getActivity открывает заметку; нельзя заменить data/action со стороны получателя.
        val contentPendingIntent = PendingIntent.getActivity(
            context, noteId.toInt(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Отдельный, явно адресованный нашему неэкспортированному BroadcastReceiver.
        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DONE
            data = NoteDeepLink.uriFor(noteId) // дополнительная уникальность PendingIntent
            putExtra(ReminderActionReceiver.EXTRA_NOTE_ID, noteId)
        }
        // getBroadcast отправляет action только нашему private Receiver.
        val donePendingIntent = PendingIntent.getBroadcast(
            context, noteId.toInt(), doneIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.reminder_action_done), donePendingIntent)
            .build()

        // Номер уведомления равен id заметки; его можно отменить тем же id.
        NotificationManagerCompat.from(context).notify(noteId.toInt(), notification)
    }

    companion object {
        const val TAG = "ReminderScheduler"
        const val CHANNEL_ID = "reminders" // зафиксирован заданием
        const val DEFAULT_DELAY_MILLIS = 10_000L
    }
}
