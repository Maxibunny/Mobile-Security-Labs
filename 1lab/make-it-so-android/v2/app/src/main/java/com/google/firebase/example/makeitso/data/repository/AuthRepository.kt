package com.google.firebase.example.makeitso.data.repository

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Репозиторий авторизации: интерфейс между UI и удалённым Firebase источником.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: MainActivity и SettingsViewModel вызывают этот класс, а он делегирует операции AuthRemoteDataSource.
// ===========================================================================

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.example.makeitso.data.datasource.AuthRemoteDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Прослойка между UI и Firebase, не сохраняющая пароли. */
class AuthRepository @Inject constructor(private val remote: AuthRemoteDataSource) {
    val currentUser: FirebaseUser? get() = remote.currentUser
    val currentUserIdFlow: Flow<String?> get() = remote.currentUserIdFlow

    suspend fun signIn(email: String, password: String) = remote.signIn(email, password)
    suspend fun signUp(email: String, password: String) = remote.signUp(email, password)
    suspend fun signInWithGoogleIdToken(token: String) = remote.signInWithGoogleIdToken(token)
    fun signOut() = remote.signOut()
    suspend fun deleteAccount() = remote.deleteAccount()
}
