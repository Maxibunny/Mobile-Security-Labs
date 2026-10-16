package com.google.firebase.example.makeitso.data.datasource

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Непосредственные вызовы Firebase Authentication.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: AuthStateListener выдаёт текущий UID; Firebase сервер проверяет email/password или Google credential. Нет собственных файлов паролей.
// ===========================================================================

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Только Firebase хранит состояние сессии. Пароли/ID-токены не пишутся в файлы приложения. */
class AuthRemoteDataSource @Inject constructor(private val auth: FirebaseAuth) {
    // Не считаем анонимную сессию полноценным входом для этой лабораторной.
    val currentUser: FirebaseUser? get() = auth.currentUser?.takeUnless { it.isAnonymous }

    // Listener срабатывает при старте, входе и выходе, включая после перезапуска процесса.
    val currentUserIdFlow: Flow<String?> get() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { service ->
            trySend(service.currentUser?.takeUnless { it.isAnonymous }?.uid)
        }
        // Подписка; awaitClose ниже убирает слушатель, чтобы избежать утечек.
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun signUp(email: String, password: String) {
        // Лабораторная запрещает анонимное открытие TODO: создаём обычного пользователя.
        // await завершится только после успешного ответа Firebase или выдаст исключение.
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        // Firebase Auth проверяет подписанный Google ID token на сервере.
        // Обмен GOOGLE ID token на Firebase credential. Сам токен здесь не сохраняется.
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    // Удаляет локальное состояние входа Firebase; серверные правила далее видят неавторизованного клиента.
    fun signOut() = auth.signOut()

    suspend fun deleteAccount() {
        val user = currentUser ?: error("Пользователь не авторизован")
        user.delete().await()
    }
}
