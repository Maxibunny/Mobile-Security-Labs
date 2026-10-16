package com.google.firebase.example.makeitso.security

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Связующее звено Android Credential Manager и Firebase Auth.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: Системный провайдер хранит пароль; приложение временно получает выбранные credentials и сразу передаёт их Firebase. Google ID token не является паролем и не сохраняется в нашем коде.
// ===========================================================================

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.example.makeitso.R
import com.google.firebase.example.makeitso.data.repository.AuthRepository

/**
 * Credential Manager показывает системный экран выбора учётной записи.
 * Никаких SharedPreferences/Room/файлов с email+password не создаётся.
 * Firebase хранит собственную сессию в своей библиотеке, пароль же остаётся у провайдера CM.
 */
class CredentialAuth(private val activity: Activity, private val auth: AuthRepository) {
    // API AndroidX обеспечивает системный интерфейс и делегирует хранение провайдеру.
    private val manager = CredentialManager.create(activity)

    suspend fun signInFromSystemPicker() {
        // ОДИН запрос с ДВУМЯ вариантами: сохранённый пароль и Google ID token.
        // Это принципиально отличается от двух отдельных системных диалогов.
        val googleOption = GetGoogleIdOption.Builder()
            // WEB OAuth client ID из google-services.json (НЕ Android client ID).
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            // false позволяет выбрать и новый Google-аккаунт, ранее не связанный с приложением.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            // Две опции в ОДНОМ запросе → один системный диалог выбора credentials.
            .addCredentialOption(GetPasswordOption())
            .addCredentialOption(googleOption)
            .build()

        // Только после выбора получаем credential, НЕ сохраняем его в SharedPreferences.
        val response = manager.getCredential(context = activity, request = request)
        when (val credential = response.credential) {
            // id обычно email, password отправляется Firebase по защищённому SDK.
            is PasswordCredential -> auth.signIn(credential.id, credential.password)
            is CustomCredential -> {
                if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    error("Неподдерживаемый тип учётной записи: ${credential.type}")
                }
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                // Google ID token проверит Firebase сервер, клиенту не надо декодировать ему доверие.
                auth.signInWithGoogleIdToken(google.idToken)
            }
            else -> error("Неподдерживаемый тип учётной записи")
        }
    }

    suspend fun offerToSavePassword(email: String, password: String) {
        // Только после успешной регистрации Firebase.
        // Системный менеджер может предложить сохранить пароль; отказ пользователя допустим.
        manager.createCredential(
            context = activity,
            // Пароль передаётся провайдеру; в нашей БД/файлах он не записывается.
            request = CreatePasswordRequest(id = email, password = password)
        )
    }

    suspend fun clearAfterSignOut() {
        // Не удаляет сохранённые пароли, но снимает автоподбор предыдущей сессии у провайдера.
        // Сбрасываем предпочтённую учётку системы после signOut, не удаляя её пароль.
        manager.clearCredentialState(ClearCredentialStateRequest())
    }
}
