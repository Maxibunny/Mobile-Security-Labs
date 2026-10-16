package com.google.firebase.example.makeitso.ui.settings

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Получает сведения профиля из FirebaseUser и ID-токена.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: signInProvider обозначает последний способ входа; providerData — связанные провайдеры. Это разные понятия.
// ===========================================================================

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.example.makeitso.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ProfileInfo(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val signedInVia: String = "Определяется…",
    val linkedProviders: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _profile = MutableStateFlow(ProfileInfo())
    val profile = _profile.asStateFlow()

    init { refreshProfile() }

    fun refreshProfile() = viewModelScope.launch {
        val user = auth.currentUser ?: return@launch
        // token.signInProvider содержит именно метод последнего входа,
        // а user.providerData — связанные способы входа (они могут различаться).
        // getIdToken(false) возвращает токен Firebase, а signInProvider — метод последнего входа.
        val currentMethod = try { user.getIdToken(false).await().signInProvider } catch (_: Exception) { null }
        val providerLabel = when (currentMethod) {
            "google.com" -> "Google"
            "password" -> "Email и пароль"
            else -> "Не удалось определить (${currentMethod ?: "нет данных"})"
        }
        _profile.value = ProfileInfo(
            uid = user.uid,
            email = user.email ?: "Не указана",
            name = user.displayName ?: "Не указано",
            signedInVia = providerLabel,
            linkedProviders = user.providerData.map { it.providerId }.filter { it != "firebase" }.distinct().joinToString(", ").ifBlank { "Не указаны" }
        )
    }
}
