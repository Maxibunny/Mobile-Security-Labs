// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Системный выбор изображения через Activity Result API без разрешения READ_MEDIA_IMAGES.
// Важный момент: Пользователь выдаёт доступ к конкретному выбранному URI, а не всей галерее.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.photo

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * ipc-2: системный Android Photo Picker. Не просит разрешений на галерею/камеру.
 * Временный content:// URI копируется в частное хранилище существующим PhotoStore.
 * rememberUpdatedState гарантирует актуальный callback после рекомпозиции.
 */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val latestOnPicked by rememberUpdatedState(onPicked)
    // Система возвращает URI лишь выбранного файла; «общий доступ к медиа» не нужен.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) latestOnPicked(uri) // null = пользователь закрыл выбор без фотографии
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
