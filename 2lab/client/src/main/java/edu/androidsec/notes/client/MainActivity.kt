// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: UI демонстрационного клиента отображает заголовки или точную причину отказа.
// Важный момент: Никакого обхода разрешений: чтение идёт только через ContentResolver.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Единственный экран клиента: при старте запрашивает заголовки заметок у провайдера
 * `content://edu.androidsec.notes.provider/notes` и показывает либо список,
 * либо текст исключения красным.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ClientScreen()
                }
            }
        }
    }
}

@Composable
private fun ClientScreen() {
    val appContext = LocalContext.current.applicationContext
    val viewModel: ClientViewModel = viewModel { ClientViewModel(TitlesClient(appContext)) }
    // Состояние наполняется после Binder query к чужому ContentProvider.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ClientContent(state = state, onRetry = viewModel::load)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientContent(state: ClientUiState, onRetry: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.client_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.client_subtitle),
                style = MaterialTheme.typography.bodySmall,
            )

            state.diagnostics?.let { DiagnosticsBlock(it) }

            when {
                state.isLoading -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.client_loading))
                }

                // Подпись не совпала/нет доступа: показываем отказ, а не скрываем его.
                state.error != null -> {
                    Text(
                        text = stringResource(R.string.client_error_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Red,
                    )
                    Text(
                        text = if (state.error.startsWith(TitlesClient.NullCursorException::class.java.simpleName)) {
                            stringResource(R.string.client_null_cursor)
                        } else {
                            state.error
                        },
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                state.titles.isEmpty() -> Text(stringResource(R.string.client_empty))

                else -> {
                    Text(
                        text = stringResource(R.string.client_titles_header),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.titles.forEachIndexed { index, title ->
                        Text(text = "${index + 1}. $title", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.client_retry))
            }
        }
    }
}

@Composable
private fun DiagnosticsBlock(diagnostics: Diagnostics) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!diagnostics.notesInstalled) {
            Text(stringResource(R.string.client_notes_not_installed), style = MaterialTheme.typography.bodySmall)
            return
        }
        Text(
            text = stringResource(
                if (diagnostics.permissionGranted) R.string.client_permission_granted
                else R.string.client_permission_denied,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                if (diagnostics.signatureMatches) R.string.client_signature_match
                else R.string.client_signature_mismatch,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
