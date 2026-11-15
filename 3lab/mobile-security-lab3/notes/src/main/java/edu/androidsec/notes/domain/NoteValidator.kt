package edu.androidsec.notes.domain

/** Единая политика ввода: используется и в Compose, и перед записью в Room. */
object NoteValidator {
    const val TITLE_LIMIT = 100
    const val BODY_LIMIT = 5000
    const val MAX_TAGS = 5
    const val TAG_LIMIT = 20

    /** Проверяем кодовые точки Unicode: эмодзи не считаются двумя символами. */
    fun titleError(title: String): String? = when {
        // Быстрый отказ от огромной вставки до полного обхода Unicode.
        title.length > TITLE_LIMIT * 2 ||
            title.codePointCount(0, title.length) > TITLE_LIMIT -> "Заголовок: не более 100 символов"
        title.codePoints().anyMatch { !printable(it) } -> "Заголовок: только печатные символы, без переноса строки"
        else -> null
    }

    fun bodyError(body: String): String? = when {
        body.length > BODY_LIMIT * 2 ||
            body.codePointCount(0, body.length) > BODY_LIMIT -> "Текст: не более 5000 символов"
        body.codePoints().anyMatch { it != '\n'.code && it != '\t'.code && !printable(it) } ->
            "Текст: запрещены управляющие и непечатаемые символы"
        else -> null
    }

    fun tagsError(tags: String): String? {
        if (tags.isEmpty()) return null // Теги необязательны.
        // Пять тегов по 20 символов + максимум пять разделителей, включая конечный.
        if (tags.length > MAX_TAGS * (TAG_LIMIT + 1)) return "Теги: не более 5 по 20 символов"
        if (tags.any { it != ' ' && it !in 'a'..'z' && it !in '0'..'9' && it != '-' })
            return "Теги: допустимы только a-z, 0-9, дефис и пробел"
        if (tags.startsWith(' ') || "  " in tags)
            return "Теги: без начальных и повторных пробелов"
        val parts = tags.split(' ').filter { it.isNotEmpty() } // Последний пробел допускается при вводе.
        if (parts.size > MAX_TAGS) return "Теги: не более 5"
        if (parts.any { it.length !in 1..TAG_LIMIT }) return "Каждый тег: от 1 до 20 символов"
        return null
    }

    /** Возвращает ошибку первого недопустимого поля; null означает валидную запись. */
    fun validate(note: Note): String? =
        titleError(note.title) ?: bodyError(note.body) ?: tagsError(note.tags)

    private fun printable(codePoint: Int): Boolean {
        if (Character.isLetterOrDigit(codePoint)) return true
        // Белый список категорий Unicode: буквы/цифры выше, далее знаки и обычные пробелы.
        return when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(), Character.SPACE_SEPARATOR.toInt(),
            Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(), Character.MATH_SYMBOL.toInt(),
            Character.CURRENCY_SYMBOL.toInt(), Character.MODIFIER_SYMBOL.toInt(),
            Character.OTHER_SYMBOL.toInt() -> true
            else -> false
        }
    }
}
