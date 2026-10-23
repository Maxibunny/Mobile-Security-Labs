// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Строгий разбор внешней ссылки notes://note/<id> без доверия к значениям URI.
// Важный момент: Парсер проверяет синтаксис, но существование id подтверждается только запросом к Room.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.deeplink

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** Результат разбора недоверенного VIEW Intent. */
sealed interface IncomingNote {
    /** Положительный Long; факт существования в Room отдельно проверяется ViewModel. */
    data class Open(val id: Long) : IncomingNote
    data object Invalid : IncomingNote
    data object None : IncomingNote
}

/**
 * ipc-3, лекция 6 («Deep links»): URI сам по себе не является доказательством права доступа.
 * Разбор не допускает чужие host/scheme, параметры запроса, фрагменты, второй сегмент пути,
 * отрицательные числа и переполнение Long. Даже валидный id проверяется через Room.
 */
object NoteDeepLink {
    const val SCHEME = "notes"
    const val HOST = "note"

    fun uriFor(noteId: Long): Uri = "$SCHEME://$HOST/$noteId".toUri()

    fun parse(intent: Intent?): IncomingNote {
        // Запуск с главного экрана ACTION_MAIN не должен считаться deep link.
        if (intent?.action != Intent.ACTION_VIEW) return IncomingNote.None
        val uri = intent.data ?: return IncomingNote.Invalid
        // Запрещаем лишние части URI: query, fragment, userInfo, порт, чужие host/scheme.
        if (uri.scheme != SCHEME || uri.host != HOST || uri.port != -1 ||
            uri.query != null || uri.fragment != null || uri.userInfo != null
        ) return IncomingNote.Invalid

        // Сравниваем именно encodedPath: "/1/", "/1/2" и "/%31" не совпадут.
        val path = uri.encodedPath ?: return IncomingNote.Invalid
        if (!path.matches(Regex("/[0-9]+"))) return IncomingNote.Invalid
        // toLongOrNull не бросает исключение на переполнении и на мусорном вводе.
        val id = path.substring(1).toLongOrNull() ?: return IncomingNote.Invalid
        return if (id > 0L) IncomingNote.Open(id) else IncomingNote.Invalid
    }
}
