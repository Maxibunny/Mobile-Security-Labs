package edu.androidsec.notes.ui.navigation

import kotlinx.serialization.Serializable

/** Типизированные маршруты — входящие Uri никогда не становятся SQL-запросами. */
@Serializable data object ListRoute
@Serializable data object SearchRoute
@Serializable data class NoteRoute(val id: Long) {
    companion object { const val NEW_NOTE_ID = 0L }
}
