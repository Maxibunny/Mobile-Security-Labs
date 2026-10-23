// ======================= РАЗБОР ДЛЯ ЗАЩИТЫ ЛР №2 =======================
// Назначение: Compose-обёртка для контекстного запроса POST_NOTIFICATIONS и объясняющих диалогов.
// Важный момент: Повторный отказ не ломает заметки: вместо навязчивого запроса предлагаются настройки.
// Подробные связи: docs/CODE_GUIDE.md и docs/SECURITY_WALKTHROUGH.md.
// ======================================================================
package edu.androidsec.notes.reminder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import edu.androidsec.notes.R

private const val PREFS_NAME = "notification_permission"
private const val KEY_DENIALS = "denials"

/**
 * ipc-2 («Runtime permissions»): минимальный запрос при нажатии «Напомнить».
 * 1) До API 33 — без диалога ОС. 2) Уже выдано — сразу напомнить.
 * 3) Перед запросом — объяснение; после первого отказа только сообщение.
 * 4) После второго отказа — предлагаем системные настройки вместо бесконечных запросов.
 * Число отказов хранится в PRIVATE SharedPreferences (это не секрет и не разрешение).
 */
@Composable
fun rememberNotificationPermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestOnGranted by rememberUpdatedState(onGranted)
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Callback вызывается только после ответа ОС; при denied не планируем напоминание.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            prefs.edit().putInt(KEY_DENIALS, 0).apply()
            latestOnGranted()
        } else {
            // Счётчик локальный, нужен только для выбора UX: снова просить или показать Settings.
            val denials = prefs.getInt(KEY_DENIALS, 0) + 1
            prefs.edit().putInt(KEY_DENIALS, denials).apply()
            Toast.makeText(context, R.string.permission_denied_toast, Toast.LENGTH_LONG).show()
            if (denials >= 2) showSettings = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.permission_rationale_title)) },
            text = { Text(stringResource(R.string.permission_rationale_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.permission_rationale_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text(stringResource(R.string.permission_rationale_cancel))
                }
            },
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(stringResource(R.string.permission_settings_title)) },
            text = { Text(stringResource(R.string.permission_settings_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showSettings = false
                    // Экран настроек именно нашего пакета, а не общий список приложений.
                    val settings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(settings)
                }) { Text(stringResource(R.string.permission_settings_open)) }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text(stringResource(R.string.permission_settings_cancel))
                }
            },
        )
    }

    // Эту лямбду экран вызывает только после нажатия кнопки «Напомнить».
    return {
        when {
            context.hasNotificationPermission() -> {
                prefs.edit().putInt(KEY_DENIALS, 0).apply()
                latestOnGranted()
            }
            prefs.getInt(KEY_DENIALS, 0) >= 2 -> showSettings = true
            else -> {
                // Перед первым запросом объясняем цель сами. При повторном учитываем
                // рекомендацию Android shouldShowRequestPermissionRationale.
                val systemWantsRationale = context.findActivity()?.let { activity ->
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity, Manifest.permission.POST_NOTIFICATIONS,
                    )
                } ?: false
                if (prefs.getInt(KEY_DENIALS, 0) == 0 || systemWantsRationale) {
                    showRationale = true
                } else {
                    // На прошивках без rationale повторно запрашиваем один раз;
                    // результат всё равно увеличит счётчик и предложит настройки.
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** Compose LocalContext может быть ContextThemeWrapper, разворачиваем до Activity. */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
