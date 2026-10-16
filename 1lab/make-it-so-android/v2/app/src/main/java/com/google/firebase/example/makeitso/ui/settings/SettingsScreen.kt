package com.google.firebase.example.makeitso.ui.settings

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Экран профиля и кнопка выхода из аккаунта.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: Только отображает данные SettingsViewModel и вызывает onSignOut; фактический signOut выполняет Firebase Auth через MainActivity.
// ===========================================================================

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable

@Serializable object SettingsRoute

/** Настройки теперь выполняют требование лабораторной: полный профиль Firebase Auth. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Профиль", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileRow("Способ последнего входа", profile.signedInVia)
                ProfileRow("Почта", profile.email)
                ProfileRow("Имя пользователя", profile.name)
                ProfileRow("Firebase UID", profile.uid)
                ProfileRow("Привязанные провайдеры", profile.linkedProviders)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Вернуться к задачам") }
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Выйти из аккаунта") }
        Text("После выхода TODO и профиль больше не доступны. Сохранённый системный пароль не удаляется.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}
