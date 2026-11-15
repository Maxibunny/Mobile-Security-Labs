package edu.androidsec.notes.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import edu.androidsec.notes.domain.Note

/** Схема v2: новые поля имеют SQL DEFAULT для совместимости с БД v1. */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val body: String,
    @ColumnInfo(name = "photo_path") val photoPath: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(defaultValue = "''") val tags: String = "",
    @ColumnInfo(name = "is_secret", defaultValue = "0") val isSecret: Boolean = false,
    @ColumnInfo(name = "pending_delete_at", defaultValue = "0") val pendingDeleteAt: Long = 0L,
)

fun NoteEntity.toDomain() = Note(id, title, body, photoPath, updatedAt, tags, isSecret, pendingDeleteAt)
fun Note.toEntity() = NoteEntity(id, title, body, photoPath, updatedAt, tags, isSecret, pendingDeleteAt)
