package com.google.firebase.example.makeitso.ui.signup

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Клиентские проверки формата email и сложности пароля.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: Клиентская валидация улучшает UX, но не заменяет проверку аккаунта в Firebase или правила Firestore.
// ===========================================================================

import android.util.Patterns
import java.util.regex.Pattern

// Ограничение UI: >=8, латинские строчная/заглавная буквы и цифра;
// сервер Firebase отдельно реализует собственную политику аутентификации.
private const val MIN_PASSWORD_LENGTH = 8
private const val PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=\\S+$).{4,}$"

fun String.isValidEmail(): Boolean {
    return this.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

fun String.isValidPassword(): Boolean {
    return this.isNotBlank() &&
            this.length >= MIN_PASSWORD_LENGTH &&
            Pattern.compile(PASSWORD_PATTERN).matcher(this).matches()
}