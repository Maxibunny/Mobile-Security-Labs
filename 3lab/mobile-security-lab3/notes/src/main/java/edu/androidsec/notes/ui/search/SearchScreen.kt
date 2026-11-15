package edu.androidsec.notes.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.androidsec.notes.R
import edu.androidsec.notes.appContainer
import edu.androidsec.notes.data.NoteSort
import edu.androidsec.notes.ui.list.NoteRow
import edu.androidsec.notes.ui.list.PendingDeletionNotice

/** SQL не собирается в UI: экран передаёт строку и один из трёх enum-вариантов сортировки. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenNote: (Long) -> Unit) {
    val repo = LocalContext.current.appContainer.notesRepository
    val vm: SearchViewModel = viewModel { SearchViewModel(repo) }
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val found by vm.results.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    PendingDeletionNotice(repo, snackbar)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.search_title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.note_back))
            }
        })
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { vm.query.value = it.take(200) },
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(when (sort) {
                        NoteSort.TITLE -> stringResource(R.string.sort_title)
                        NoteSort.NEWEST -> stringResource(R.string.sort_newest)
                        NoteSort.OLDEST -> stringResource(R.string.sort_oldest)
                    })
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(
                        NoteSort.TITLE to R.string.sort_title,
                        NoteSort.NEWEST to R.string.sort_newest,
                        NoteSort.OLDEST to R.string.sort_oldest,
                    ).forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                            vm.sort.value = value
                            expanded = false
                        })
                    }
                }
            }
            if (found.isEmpty()) Text(stringResource(R.string.search_empty))
            else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(found, key = { it.id }) { note -> NoteRow(note, { onOpenNote(note.id) }) }
            }
        }
    }
}
