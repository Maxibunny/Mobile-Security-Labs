package com.google.firebase.example.makeitso.data.model

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Модель сообщения об ошибке для пользовательского интерфейса.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: Содержит строку или идентификатор ресурса; это не средство авторизации и не журнал секретов.
// ===========================================================================

import androidx.annotation.StringRes

sealed class ErrorMessage {
    class StringError(val message: String) : ErrorMessage()
    class IdError(@StringRes val message: Int) : ErrorMessage()
}
