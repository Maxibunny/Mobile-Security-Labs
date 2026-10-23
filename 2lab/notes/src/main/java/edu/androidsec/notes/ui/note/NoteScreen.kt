// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Compose-редактор заметки: поля, фото, сохранение, напоминание, экспорт.
// Важный момент: Отсутствующий id не показывает редактор: нельзя создать запись из неверной ссылки.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.ui.note

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.androidsec.notes.R
import edu.androidsec.notes.appContainer
import edu.androidsec.notes.photo.PhotoStore
import edu.androidsec.notes.photo.rememberPhotoPicker
import edu.androidsec.notes.reminder.rememberNotificationPermission
import edu.androidsec.notes.share.ShareNote

@Composable
fun NoteScreen(noteId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    // Ключ отличает состояние редактора заметки 1 от состояния заметки 2.
    val viewModel: NoteViewModel = viewModel(key = "note-$noteId") {
        NoteViewModel(noteId, container.notesRepository, container.photoStore, container.reminderScheduler)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Системные диалоги (разрешение, выбор фото) живут в UI; результат уходит во ViewModel.
    // Разрешение проверяем при нажатии «Напомнить», а не при открытии приложения.
    val requestNotificationPermission = rememberNotificationPermission(onGranted = viewModel::scheduleReminder)
    val pickPhoto = rememberPhotoPicker(onPicked = viewModel::onPhotoPicked)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NoteEvent.Message -> Toast.makeText(context, event.resId, Toast.LENGTH_SHORT).show()
                NoteEvent.Close -> onBack()
            }
        }
    }

    NoteContent(
        state = state,
        photoStore = container.photoStore,
        onBack = onBack,
        onDelete = viewModel::delete,
        onTitleChange = viewModel::onTitleChange,
        onBodyChange = viewModel::onBodyChange,
        onAttachPhoto = pickPhoto,
        onRemovePhoto = viewModel::removePhoto,
        onSave = viewModel::save,
        onRemind = requestNotificationPermission,
        onShare = { ShareNote.shareNoteAsFile(context, state.toNote()) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteContent(
    state: NoteUiState,
    photoStore: PhotoStore,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onAttachPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
    onRemind: () -> Unit,
    onShare: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(
                        when {
                            state.notFound -> R.string.note_not_found
                            state.isNew -> R.string.note_title_new
                            else -> R.string.note_title_edit
                        }
                    ))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.note_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDelete, enabled = !state.isLoading && !state.notFound) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.note_delete))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                // ipc-3: для отсутствующего id показываем ошибку, а не пустую форму.
                // Кнопок сохранения, удаления, экспорта и напоминания здесь нет.
                // Отсутствующий id НЕ создаёт новую запись: редактор скрыт.
                state.notFound -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.note_not_found), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.note_not_found_hint))
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.note_back)) }
                }
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = onTitleChange,
                        label = { Text(stringResource(R.string.note_field_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.body,
                        onValueChange = onBodyChange,
                        label = { Text(stringResource(R.string.note_field_body)) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.photoPath?.let { NotePhoto(path = it, photoStore = photoStore) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onAttachPhoto) { Text(stringResource(R.string.note_attach_photo)) }
                        if (state.photoPath != null) {
                            OutlinedButton(onClick = onRemovePhoto) { Text(stringResource(R.string.note_remove_photo)) }
                        }
                    }

                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.note_save))
                    }
                    OutlinedButton(onClick = onRemind, enabled = !state.isNew, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.note_remind))
                    }
                    // Экспорт разрешён только для уже сохранённой заметки с id>0.
                    OutlinedButton(onClick = onShare, enabled = !state.isNew, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.note_share))
                    }
                    if (state.isNew) {
                        Text(stringResource(R.string.note_save_first), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotePhoto(path: String, photoStore: PhotoStore) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = photoStore.loadPreview(path)
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = stringResource(R.string.note_photo_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )
    }
}
