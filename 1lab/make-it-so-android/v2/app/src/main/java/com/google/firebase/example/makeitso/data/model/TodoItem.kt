package com.google.firebase.example.makeitso.data.model

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Структура документа задачи, сериализуемого Firebase Firestore.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: ownerId обозначает владельца. @DocumentId подставляет ID документа при чтении и не должен рассматриваться как средство контроля доступа.
// ===========================================================================

import com.google.firebase.firestore.DocumentId

data class TodoItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val priority: Int = Priority.NONE.value,
    val completed: Boolean = false,
    val flagged: Boolean = false,
    val ownerId: String = ""
)

val TodoItem.isHighPriority: Boolean
    get() = this.priority == Priority.HIGH.value

val TodoItem.isMediumPriority: Boolean
    get() = this.priority == Priority.MEDIUM.value

val TodoItem.isLowPriority: Boolean
    get() = this.priority == Priority.LOW.value

val TodoItem.isNonePriority: Boolean
    get() = this.priority == Priority.NONE.value

