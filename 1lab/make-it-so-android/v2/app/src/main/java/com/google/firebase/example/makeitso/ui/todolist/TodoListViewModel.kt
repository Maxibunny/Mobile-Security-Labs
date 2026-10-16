package com.google.firebase.example.makeitso.ui.todolist

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: ViewModel списка задач; наблюдает изменения Firebase UID.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: Получает поток задач по идентификатору вошедшего пользователя и делегирует обновление репозиторию.
// ===========================================================================

import com.google.firebase.example.makeitso.MainViewModel
import com.google.firebase.example.makeitso.data.model.TodoItem
import com.google.firebase.example.makeitso.data.repository.AuthRepository
import com.google.firebase.example.makeitso.data.repository.TodoItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TodoListViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val todoItemRepository: TodoItemRepository
) : MainViewModel() {
    // Данные поступают лишь для UID текущей сессии, а сервер проверяет ownerId в правилах.
    // Подписка привязана к UID: после выхода список станет пустым.
    val todoItems = todoItemRepository.getTodoItems(authRepository.currentUserIdFlow)

    fun updateItem(item: TodoItem) {
        launchCatching { todoItemRepository.update(item) }
    }
}
