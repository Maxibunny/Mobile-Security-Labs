package com.google.firebase.example.makeitso.ui.signin

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Открытый Compose-экран авторизации.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: Форма передаёт email/password в callback signInWithEmail. Системная кнопка вызывает signInFromCredentialManager через MainActivity.
// ===========================================================================

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable

@Serializable object SignInRoute

/** Открытый экран. Проверка пароля всегда в Firebase, а выбор учётной записи — в Android. */
@Composable
fun SignInScreen(
    openSignUpScreen: () -> Unit,
    signInWithEmail: (String, String) -> Unit,
    signInFromCredentialManager: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Безопасная аутентификация", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Make It So · Лабораторная №1")
        Spacer(Modifier.height(32.dp))
        Button(onClick = signInFromCredentialManager, modifier = Modifier.fillMaxWidth()) {
            Text("Продолжить с Google / сохранённым паролем")
        }
        Text("Google и сохранённые пароли выбираются в одном системном окне", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        Text("Или войти вручную")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { signInWithEmail(email.trim(), password) }, modifier = Modifier.fillMaxWidth(), enabled = email.isNotBlank() && password.isNotBlank()) {
            Text("Войти по Email")
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = openSignUpScreen) { Text("Нет аккаунта? Зарегистрироваться") }
    }
}
