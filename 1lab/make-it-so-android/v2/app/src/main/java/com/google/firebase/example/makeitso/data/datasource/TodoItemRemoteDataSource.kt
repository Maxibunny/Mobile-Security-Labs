package com.google.firebase.example.makeitso.data.datasource

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Доступ к документам TODO через Firestore по текущему UID.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: Путь /users/{uid}/todoitems/{taskId} выбран для изоляции. Клиентский UID помогает выбрать путь, но право доступа проверяет сервер по firestore.rules.
// ===========================================================================

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.example.makeitso.data.model.TodoItem
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.dataObjects
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Все задачи хранятся в /users/{uid}/todoitems/{id}, а не в общей коллекции.
 * Поэтому список пользователя Б даже из локального кэша не содержит задачи А.
 * Отдельно серверные Firestore Rules запрещают Б читать путь А.
 */
class TodoItemRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun itemsFor(uid: String): CollectionReference =
        // Путь из UID — клиентская маршрутизация запросов. Правила доступа в firestore.rules.
        firestore.collection("users").document(uid).collection("todoitems")

    private fun ownItems(): CollectionReference {
        val user = auth.currentUser
        // Дополнительный барьер в клиенте. Его МОЖНО обойти в модифицированном APK,
        // поэтому самостоятельная серверная проверка owner(uid) ОБЯЗАТЕЛЬНА.
        check(user != null && !user.isAnonymous) { "Требуется аутентификация" }
        return itemsFor(user.uid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTodoItems(currentUserIdFlow: Flow<String?>): Flow<List<TodoItem>> =
        // При смене пользователя прекращает предыдущую подписку и переключается на новый путь.
        currentUserIdFlow.flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else itemsFor(uid).dataObjects()
        }

    suspend fun getTodoItem(itemId: String): TodoItem? =
        ownItems().document(itemId).get().await().toObject()

    suspend fun create(todoItem: TodoItem): String =
        // Firebase Rules сверяют ownerId, типы и диапазоны данных ДО записи на сервере.
        ownItems().add(todoItem).await().id

    suspend fun update(todoItem: TodoItem) {
        ownItems().document(todoItem.id).set(todoItem).await()
    }

    suspend fun delete(itemId: String) {
        ownItems().document(itemId).delete().await()
    }
}
