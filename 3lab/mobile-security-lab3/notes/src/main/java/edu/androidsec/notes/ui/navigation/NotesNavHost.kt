// ЛР2: Навигация между списком и редактором; обрабатывает уже разобранные IncomingNote.
// Проверка: Invalid показывает сообщение; входящие значения после обработки сбрасываются.
package edu.androidsec.notes.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import edu.androidsec.notes.R
import edu.androidsec.notes.deeplink.IncomingNote
import edu.androidsec.notes.ui.list.NotesListScreen
import edu.androidsec.notes.ui.note.NoteScreen
import edu.androidsec.notes.ui.search.SearchScreen
import kotlinx.coroutines.flow.StateFlow

/**
 * Граф навигации. [incoming] — результат разбора входящего интента Activity
 * (deep link или нажатие на уведомление); после обработки вызывается [onIncomingConsumed].
 */
@Composable
fun NotesNavHost(
    incoming: StateFlow<IncomingNote>,
    onIncomingConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val incomingValue by incoming.collectAsStateWithLifecycle()

    // На каждое новое значение IncomingNote выполняем один навигационный эффект.
    LaunchedEffect(incomingValue) {
        when (val value = incomingValue) {
            is IncomingNote.Open -> {
                navController.navigate(NoteRoute(value.id))
                onIncomingConsumed()
            }

            // Никакая noteId не навигируется; пользователь увидит уведомление об ошибке.
            IncomingNote.Invalid -> {
                Toast.makeText(context, R.string.deeplink_invalid, Toast.LENGTH_LONG).show()
                onIncomingConsumed()
            }

            IncomingNote.None -> Unit
        }
    }

    NavHost(navController = navController, startDestination = ListRoute) {
        composable<ListRoute> {
            NotesListScreen(
                onOpenNote = { id -> navController.navigate(NoteRoute(id)) },
                onSearch = { navController.navigate(SearchRoute) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = { id -> navController.navigate(NoteRoute(id)) },
            )
        }
        composable<NoteRoute> { entry ->
            val route = entry.toRoute<NoteRoute>()
            NoteScreen(
                noteId = route.id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
