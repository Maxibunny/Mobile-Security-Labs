package edu.androidsec.notes.ui.note

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.os.PersistableBundle
import android.view.WindowManager
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
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import edu.androidsec.notes.domain.NoteValidator
import edu.androidsec.notes.photo.PhotoStore
import edu.androidsec.notes.photo.rememberPhotoPicker
import edu.androidsec.notes.reminder.rememberNotificationPermission
import edu.androidsec.notes.share.ShareNote

@Composable
fun NoteScreen(noteId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    // После первого сохранения нового документа запоминаем реальный id в SavedState.
    // Иначе восстановление процесса для маршрута id=0 создало бы дубликат.
    var savedId by rememberSaveable(noteId) { mutableStateOf(0L) }
    val actualId = if (noteId == 0L && savedId > 0L) savedId else noteId
    val vm: NoteViewModel = viewModel(key = "note-$actualId") {
        NoteViewModel(actualId, container.notesRepository, container.photoStore, container.reminderScheduler)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    // TextFieldState + метка инициализации переживают поворот и восстановление процесса.
    val titleState = rememberTextFieldState()
    val bodyState = rememberTextFieldState()
    val tagsState = rememberTextFieldState()
    var initialized by rememberSaveable(noteId) { mutableStateOf(false) }
    // Несохранённый переключатель тоже восстанавливается после уничтожения процесса.
    var draftSecret by rememberSaveable(noteId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.isLoading, state.notFound, draftSecret) {
        if (!state.isLoading && !state.notFound && draftSecret != null &&
            !state.isSaving && state.isSecret != draftSecret) {
            vm.onSecretChange(draftSecret!!)
        }
    }
    var titleRejected by remember(noteId) { mutableStateOf<String?>(null) }
    var bodyRejected by remember(noteId) { mutableStateOf<String?>(null) }
    var tagsRejected by remember(noteId) { mutableStateOf<String?>(null) }

    // Загружаем поля ровно раз. Программное edit не заменяет пользовательскую InputTransformation.
    LaunchedEffect(state.isLoading, state.notFound) {
        if (!state.isLoading && !state.notFound && !initialized) {
            titleState.edit { replace(0, length, state.title) }
            bodyState.edit { replace(0, length, state.body) }
            tagsState.edit { replace(0, length, state.tags) }
            initialized = true
        }
    }

    val titleFilter = remember(noteId) { InputTransformation {
        val error = NoteValidator.titleError(asCharSequence().toString())
        if (error != null) revertAllChanges() // Не обрезаем и не исправляем ввод.
        titleRejected = error
    } }
    val bodyFilter = remember(noteId) { InputTransformation {
        val error = NoteValidator.bodyError(asCharSequence().toString())
        if (error != null) revertAllChanges()
        bodyRejected = error
    } }
    val tagsFilter = remember(noteId) { InputTransformation {
        val error = NoteValidator.tagsError(asCharSequence().toString())
        if (error != null) revertAllChanges()
        tagsRejected = error
    } }

    // Пока существующая заметка загружается, экран тоже защищён: нет кадра с секретом до установки флага.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity, actualId, state.isSecret, state.storedSecret,
        state.isLoading, state.notFound, draftSecret) {
        val secured = (actualId > 0L && state.isLoading) ||
            ((state.isSecret || state.storedSecret || draftSecret == true) && !state.notFound)
        if (secured) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secured) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val requestPermission = rememberNotificationPermission(onGranted = vm::scheduleReminder)
    val pickPhoto = rememberPhotoPicker(onPicked = vm::onPhotoPicked)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is NoteEvent.Message -> Toast.makeText(context, event.resId, Toast.LENGTH_SHORT).show()
                is NoteEvent.Saved -> {
                    // Смена key загрузит сохранённую строку, а rememberTextFieldState удержит ввод.
                    if (noteId == 0L) savedId = event.id
                    Toast.makeText(context, R.string.note_saved, Toast.LENGTH_SHORT).show()
                }
                NoteEvent.Close -> onBack()
            }
        }
    }

    NoteContent(
        state = state, title = titleState, body = bodyState, tags = tagsState,
        titleFilter = titleFilter, bodyFilter = bodyFilter, tagsFilter = tagsFilter,
        titleError = titleRejected, bodyError = bodyRejected, tagsError = tagsRejected,
        photoStore = container.photoStore, onBack = onBack, onDelete = vm::delete,
        onSecretChange = { value -> draftSecret = value; vm.onSecretChange(value) }, onAttachPhoto = pickPhoto,
        onRemovePhoto = vm::removePhoto,
        onSave = { vm.save(titleState.text.toString(), bodyState.text.toString(), tagsState.text.toString()) },
        onRemind = requestPermission,
        onShare = {
            runCatching {
                ShareNote.shareNoteAsFile(context, state.toNote().copy(
                    title = titleState.text.toString(), body = bodyState.text.toString(),
                    tags = tagsState.text.toString()))
            }.onFailure { Toast.makeText(context, R.string.note_share_failed, Toast.LENGTH_SHORT).show() }
        },
        onCopy = { copySensitive(context, bodyState.text.toString()) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteContent(
    state: NoteUiState,
    title: TextFieldState, body: TextFieldState, tags: TextFieldState,
    titleFilter: InputTransformation, bodyFilter: InputTransformation, tagsFilter: InputTransformation,
    titleError: String?, bodyError: String?, tagsError: String?,
    photoStore: PhotoStore, onBack: () -> Unit, onDelete: () -> Unit,
    onSecretChange: (Boolean) -> Unit,
    onAttachPhoto: () -> Unit, onRemovePhoto: () -> Unit,
    onSave: () -> Unit, onRemind: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(when {
                state.notFound -> R.string.note_not_found
                state.isNew -> R.string.note_title_new
                else -> R.string.note_title_edit
            })) },
            navigationIcon = { IconButton(onClick = onBack, enabled = !state.isSaving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.note_back))
            } },
            actions = { IconButton(onClick = onDelete, enabled = !state.isLoading && !state.notFound && !state.isSaving) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.note_delete))
            } },
        )
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.notFound -> Column(Modifier.align(Alignment.Center).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.note_not_found), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.note_not_found_hint))
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.note_back)) }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        state = title, inputTransformation = titleFilter, enabled = !state.isSaving,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        label = { Text(stringResource(R.string.note_field_title)) },
                        isError = titleError != null, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(titleError ?: stringResource(R.string.title_rule),
                        color = if (titleError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        state = body, inputTransformation = bodyFilter, enabled = !state.isSaving,
                        label = { Text(stringResource(R.string.note_field_body)) },
                        isError = bodyError != null, modifier = Modifier.fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 4, maxHeightInLines = 12),
                    )
                    Text(bodyError ?: stringResource(R.string.body_rule),
                        color = if (bodyError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        state = tags, inputTransformation = tagsFilter, enabled = !state.isSaving,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        label = { Text(stringResource(R.string.note_field_tags)) },
                        isError = tagsError != null, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(tagsError ?: stringResource(R.string.tags_rule),
                        color = if (tagsError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.note_secret), modifier = Modifier.weight(1f))
                        Switch(checked = state.isSecret, onCheckedChange = onSecretChange, enabled = !state.isSaving)
                    }
                    state.photoPath?.let { NotePhoto(it, photoStore) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onAttachPhoto, enabled = !state.isSaving) { Text(stringResource(R.string.note_attach_photo)) }
                        if (state.photoPath != null) OutlinedButton(onClick = onRemovePhoto, enabled = !state.isSaving) {
                            Text(stringResource(R.string.note_remove_photo))
                        }
                    }
                    if (state.saveError != null) Text(state.saveError, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.note_save))
                    }
                    OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.note_copy))
                    }
                    OutlinedButton(onClick = onRemind, enabled = !state.isNew && !state.isSaving,
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.note_remind)) }
                    OutlinedButton(onClick = onShare, enabled = !state.isNew && !state.isSaving,
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.note_share)) }
                    if (state.isNew) Text(stringResource(R.string.note_save_first),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun NotePhoto(path: String, photoStore: PhotoStore) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) { value = photoStore.loadPreview(path) }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = stringResource(R.string.note_photo_description),
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(220.dp))
    }
}

/** EXTRA_IS_SENSITIVE: Android скрывает предпросмотр буфера обмена (но это не шифрование). */
private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("Заметка", text)
    clip.description.extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    Toast.makeText(context, R.string.note_copied, Toast.LENGTH_SHORT).show()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
