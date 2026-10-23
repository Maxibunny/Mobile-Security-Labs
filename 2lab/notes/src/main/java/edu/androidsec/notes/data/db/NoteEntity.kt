// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Сущность SQLite: описание таблицы notes; преобразование между Room и domain Note.
// Важный момент: БД хранится во внутреннем хранилище приложения; наружу она напрямую не экспортируется.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import edu.androidsec.notes.domain.Note

@Entity(tableName = "notes")
data class NoteEntity(
    // Room создаст уникальный id автоматически; у seed-заметок id задан вручную.
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val body: String,
    @ColumnInfo(name = "photo_path") val photoPath: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/* Перевод объекта хранения в понятную экрану бизнес-модель. */
fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title,
    body = body,
    photoPath = photoPath,
    updatedAt = updatedAt,
)

/* Обратный перевод для сохранения в Room; SQL не попадает в ViewModel. */
fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    body = body,
    photoPath = photoPath,
    updatedAt = updatedAt,
)
