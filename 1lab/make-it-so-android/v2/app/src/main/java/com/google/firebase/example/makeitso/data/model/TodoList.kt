package com.google.firebase.example.makeitso.data.model

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Устаревшая модель будущих нескольких списков из исходного приложения.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: В лабораторной используется TodoItem; не путать model TodoList с реально отображаемым экраном TodoListScreen.
// ===========================================================================

import com.google.firebase.firestore.DocumentId

//TODO: Start using this data class when we add multiple lists feature
data class TodoList(
    @DocumentId val id: String = "",
    val title: String = "",
    val owner: String = "",
    val editors: List<String> = emptyList(),
    val viewers: List<String> = emptyList()
)