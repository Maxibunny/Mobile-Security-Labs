// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Принимает кнопку «Готово» от собственного уведомления и закрывает его.
// Важный момент: exported=false и проверка ACTION_DONE/id ограничивают поверхность вызова.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import edu.androidsec.notes.appContainer

/**
 * ipc-4: доступен только приложению через явный immutable PendingIntent. exported=false.
 * Проверяем action и id, чтобы даже внутренний ошибочный вызов не закрыл чужой объект.
 * По условию тег лога и строка должны оставаться точными: их использует check.sh.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Защита на двух уровнях: система не экспортирует компонент + проверяем payload.
        if (intent.action != ACTION_DONE || !intent.hasExtra(EXTRA_NOTE_ID)) return
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId <= 0 || noteId > Int.MAX_VALUE) return

        // Снимаем отложенную job и уже показанное уведомление одним noteId.
        context.appContainer.reminderScheduler.cancel(noteId)
        NotificationManagerCompat.from(context).cancel(noteId.toInt())
        Log.i(TAG, "DONE received for note $noteId")
    }

    companion object {
        const val TAG = "ReminderActionReceiver"
        const val ACTION_DONE = "edu.androidsec.notes.action.DONE"
        const val EXTRA_NOTE_ID = "edu.androidsec.notes.extra.NOTE_ID"
    }
}
