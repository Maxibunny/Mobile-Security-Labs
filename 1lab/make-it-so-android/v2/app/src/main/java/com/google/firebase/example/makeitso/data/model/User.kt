package com.google.firebase.example.makeitso.data.model

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Устаревшая модель профиля из шаблона.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: Текущий профиль собирается из FirebaseUser в SettingsViewModel; поле isAnonymous здесь НЕ управляет реальной сессией Firebase.
// ===========================================================================

import com.google.firebase.firestore.DocumentId

//TODO: Start using this data class when we add profile features to the Settings screen
data class User(
    @DocumentId val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val isAnonymous: Boolean = true
)