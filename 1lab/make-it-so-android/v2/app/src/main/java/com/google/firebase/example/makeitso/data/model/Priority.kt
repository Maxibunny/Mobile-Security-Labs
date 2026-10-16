package com.google.firebase.example.makeitso.data.model

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Справочник приоритетов задач.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: value=0..3 — допустимые значения, которые отдельно проверяются сервером в firestore.rules.
// ===========================================================================

import androidx.compose.ui.graphics.Color
import com.google.firebase.example.makeitso.ui.theme.LightGreen
import com.google.firebase.example.makeitso.ui.theme.LightOrange
import com.google.firebase.example.makeitso.ui.theme.LightRed

enum class Priority(val title: String, val selectedColor: Color, val value: Int) {
    NONE("None", Color.White, 0),
    LOW("Low", LightGreen, 1),
    MEDIUM("Medium", LightOrange, 2),
    HIGH("High", LightRed, 3)
}