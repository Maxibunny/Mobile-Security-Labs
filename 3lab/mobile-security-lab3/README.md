Команды в **Terminal** (корень проекта):

```powershell
# Windows (PowerShell)
.\gradlew.bat :notes:assembleDebug :client:assembleDebug
.\gradlew.bat :notes:testDebugUnitTest
.\gradlew.bat :notes:connectedDebugAndroidTest
```

## Проверка
| Требование | Что проверить |
|---|---|
| **Ввод** | Создай заметку: заголовок до 100 символов, тело до 5000; теги — не более пяти, по 1–20 символов `[a-z0-9-]`, разделитель — пробел. Слишком длинный или неправильный ввод отклоняется целиком, под полем есть пояснение. |
| **Репозиторий и тесты** | Неверная заметка не сохраняется даже вне UI. Запусти `:notes:testDebugUnitTest`; тесты и валидатор: `notes/src/test`, `domain/NoteValidator.kt`, `data/NotesRepository.kt`. |
| **Миграция Room** | Установи предыдущее приложение ЛР2, создай заметку, затем установи новую сборку **поверх** (та же подпись и `applicationId`), не очищая данные. Записи остаются; схема обновляется 1→2. |
| **Поиск** | Кнопка поиска в списке: ввод по заголовку и тексту; проверь три сортировки и буквальный поиск `%`, `_`, `\`. |
| **Удаление** | Удали из списка и из редактора; в течение **5 сек.** нажми «Отменить». После отмены запись со всеми данными возвращается. Без отмены — удаляется. Проверь закрытие и повторное открытие приложения в течение задержки. |
| **Секретная заметка** | Отметь «Секретная», сохрани. Список показывает заголовок без тела. При открытой заметке скриншот/запись экрана должны блокироваться (`FLAG_SECURE`), для обычной заметки — работать. |
| **Буфер обмена** | «Копировать текст» в секретной заметке помечает ClipData как чувствительные данные. |
| **Напоминание** | Секретная заметка не раскрывает заголовок в уведомлении. |

Тесты Room и миграции находятся в `notes/src/androidTest`; для них нужен Android-эмулятор. Дополнительная проверка без Android SDK: `python scripts/static_and_sql_checks.py` (проверяет XML, TOML, SQL и часть структуры, **не** заменяет APK-сборку).

## 3. Где какой код (краткая карта)

Все пути начинаются с `notes/src/main/java/edu/androidsec/notes/`.

| Файл | За что отвечает |
|---|---|
| `ui/note/NoteScreen.kt` | Форма, `TextFieldState`, `InputTransformation`, подсказки, `FLAG_SECURE`, буфер обмена |
| `domain/NoteValidator.kt` | Единые правила заголовка, тела и тегов |
| `ui/note/NoteViewModel.kt` | Загрузка и сохранение, фото, удаление, напоминания |
| `data/NotesRepository.kt` | Повторная проверка, безопасный поиск, отложенное удаление и отмена |
| `data/db/NotesDatabase.kt` | Room v2 и миграция `MIGRATION_1_2`, без стирания данных |
| `data/db/NoteDao.kt` | Три готовых SQL-запроса с `LIKE :pattern ESCAPE '\'` |
| `data/db/NoteEntity.kt` | Колонки таблицы, включая `tags`, `is_secret`, `pending_delete_at` |
| `ui/search/SearchScreen.kt`, `SearchViewModel.kt` | Поиск и выбор сортировки |
| `ui/list/NotesListScreen.kt`, `PendingDeletionNotice.kt` | Список, скрытие секретного текста, Snackbar «Отменить» |
| `reminder/ReminderScheduler.kt` | Напоминания и сокрытие заголовка секретной заметки |
| `photo/`, `share/` | Системный выбор фото, защищённый файловый обмен |
| `deeplink/`, `provider/`, `reminder/ReminderActionReceiver.kt` | IPC из ЛР2: ссылки, провайдер заголовков, ресивер |
| `ui/navigation/NotesNavHost.kt`, `di/AppContainer.kt` | Навигация и создание зависимостей |

**Общий путь данных:** Compose `Screen` → `ViewModel` → `NotesRepository` → `NoteDao` → Room/SQLite. UI лишь принимает действия; повторная валидация в Repository защищает базу даже при обходе экрана.

## 4. Проверка безопасности ЛР2 (сохранена)

**Фиксированные идентификаторы — не изменять:**

| Объект | Значение |
|---|---|
| `notes` / `client` applicationId | `edu.androidsec.notes` / `edu.androidsec.notes.client` |
| Deep link | `notes://note/<id>` |
| FileProvider | `edu.androidsec.notes.files` |
| TitlesProvider | `edu.androidsec.notes.provider`, путь `/notes` |
| Signature-разрешение | `edu.androidsec.notes.permission.READ_NOTES` |
| Ресивер | `edu.androidsec.notes.reminder.ReminderActionReceiver` |
| Действие ресивера | `edu.androidsec.notes.action.DONE` |
| Канал уведомлений | `reminders` |

После запуска `notes` на эмуляторе открой **Git Bash** (Windows) или терминал (macOS/Linux), выполни:

```bash
 & "C:\Program Files\Git\bin\bash.exe" scripts/check.sh all
# отдельные группы: manifest, closed, deeplink, provider
```

Скрипт использует `adb` и `apkanalyzer` из Android SDK. В ЛР2 проверь: системный Photo Picker; разрешение `POST_NOTIFICATIONS` по запросу; уведомление и кнопку «Готово»; `notes://note/1` и некорректные ссылки; «Поделиться файлом» через FileProvider; чтение заголовков в `client` при одинаковой подписи и отказ при разной. `scripts/check.sh` проверяет установленный APK, а не исходники.

**Про Git:** если сдаёшь ссылку на репозиторий, копируй обновлённые файлы **в существующий clone ветки `starter`**, сохрани `.git` и историю коммитов. Не загружай `local.properties`, `*.jks`, `client/signing.properties`.

**Сборка:** исправлен неверный `import androidx.compose.foundation.text.input.edit` в `NoteScreen.kt`: `edit { ... }` — метод `TextFieldState`, отдельный импорт не нужен. Сборку и тесты необходимо подтвердить в Android Studio.
