package com.google.firebase.example.makeitso.data.injection

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: DI-модуль Hilt: предоставляет FirebaseAuth и FirebaseFirestore.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: @Provides делает зависимости доступными через @Inject; модуль не выполняет вход и сам не проверяет права.
// ===========================================================================

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object FirebaseHiltModule {
    @Provides fun auth(): FirebaseAuth = Firebase.auth

    @Provides fun firestore(): FirebaseFirestore = Firebase.firestore
}