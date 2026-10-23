# Пофайловый разбор исходников: что вызывает что

В этом документе описан **фактический код ZIP**, без пересказа несуществующих классов. Все пути относительны папке `2lab`. В самих исходниках на русском языке есть шапки файла, Kotlin KDoc и пояснения рядом с критическими операциями.

## 1. Настройка проекта

| Файл | Что в нём | Зачем нужен на защите |
|---|---|---|
| `settings.gradle.kts` | Подключение Gradle-модулей `:notes`, `:client` и репозиториев | Показывает, что клиент — **другое приложение**, не экран Notes |
| `build.gradle.kts` | Плагины проекта | Не хранит бизнес-логику |
| `gradle/libs.versions.toml` | Версии плагинов, AndroidX, Compose, Room | Единый каталог зависимостей; требуется совместимость SDK/AGP |
| `notes/build.gradle.kts` | `applicationId=edu.androidsec.notes`, min 26 / target и compile 36; Kotlin/Java JVM 17; зависимости Compose и Room | База и интерфейс принадлежат Notes |
| `client/build.gradle.kts` | `applicationId=edu.androidsec.notes.client`; Kotlin/Java 17; опциональная подпись из `client/signing.properties` | Клиент можно собрать тем же или другим сертификатом |
| `gradle.properties` | Параметры Gradle | Не является Android-манифестом |
| `notes/src/main/AndroidManifest.xml` | Доступность всех системных компонентов Notes | Проверяется **merged**-вариант, не только исходный XML |
| `client/src/main/AndroidManifest.xml` | Компоненты и запрос собственного `READ_NOTES` | Отдельная программа получает разрешение только при совпадающей подписи |
| `notes/src/main/res/xml/file_paths.xml` | Один `<cache-path name="exports" path="exports/" />` | Сопоставление `content://.../exports/...` и приватного каталога |

**Важно различать:** `minSdk` — минимальный Android для запуска; `compileSdk` — SDK для сборки; `targetSdk` — режим совместимости/требования платформы. `jvmTarget=17` не означает Android 17, это версия байткода Java/Kotlin.

## 2. Запуск и зависимости (`notes`)

**`MainActivity.kt`** — единственная точка входа UI. Android вызывает `onCreate`; метод получает `intent` и вызывает `NoteDeepLink.parse`. Результат кладёт в `MutableStateFlow<IncomingNote>`. `setContent` создаёт тему и `NotesNavHost`. При `singleTop` новый Intent приходит в `onNewIntent`, где его снова проверяют. Почему важно: публичная Activity не должна напрямую доверять extras/URI.

**`NotesApp.kt`** — `Application`, создаётся системой один раз на процесс. В `onCreate` создаёт NotificationChannel с обязательным именем `reminders`. Свойство `container` лениво создаёт `AppContainer`. Extension `Context.appContainer` даёт Activity, Provider и Receiver доступ к одной инфраструктуре, не создавая БД в каждом классе.

**`di/AppContainer.kt`** — ручной Dependency Injection. Хранит `NotesDatabase`, `RoomNotesRepository`, `PhotoStore`, `ReminderScheduler`. Все объекты получают `applicationContext`, не временную Activity. `appScope` состоит из `SupervisorJob + Dispatchers.Default`, поэтому отложенные уведомления не блокируют UI. Важно: `appScope` прекращает существовать с процессом приложения.

## 3. Данные (`domain/`, `data/`)

**`domain/Note.kt`** — модель заметки с полями `id`, `title`, `body`, `photoPath`, `updatedAt`. `id=0` — ещё не сохранённая заметка. `NoteTitle` содержит только `id`, `title`: используется как «белый список» данных, предназначенных для клиента.

**`data/db/NoteEntity.kt`** — модель таблицы Room `notes`, аннотации `@Entity`, `@PrimaryKey(autoGenerate=true)`, `@ColumnInfo`. `toDomain` и `toEntity` связывают Room с `Note`. Это защищает остальной код от зависимости от формата SQL.

**`data/db/NoteDao.kt`** — интерфейс операций SQLite. `observeAll()` отдаёт `Flow` (список перерисуется после изменения). `getById`, `upsert`, `deleteById` — чтение/сохранение/удаление. **`titlesBlocking()`** делает специальный `SELECT id,title FROM notes ORDER BY id`, без `body`. Синхронный вариант вызван из Binder-потока `ContentProvider.query()`.

**`data/db/NotesDatabase.kt`** — RoomDatabase, файл `notes.db` в частном хранилище приложения. Callback `SeedCallback.onCreate` добавляет три записи `id=1,2,3` только при первоначальном создании БД. Почему id=1 нужен: демонстрационная ссылка `notes://note/1` может открыть существующую запись на чистой установке. После удаления и повторного запуска сиды не появляются заново.

**`data/NotesRepository.kt`** — интерфейс `NotesRepository` и реализация `RoomNotesRepository`. ViewModel не пишет SQL; вызывает `repository.save()` или `get()`. `save` обновляет `updatedAt` и возвращает id, который создал Room. `observeAll` преобразует `NoteEntity` в `Note`.

### Путь сохранения заметки

```text
Пользователь нажал «Сохранить»
  NoteScreen:onSave
    → NoteViewModel.save()
      → NotesRepository.save(note)
        → RoomNotesRepository
          → NoteDao.upsert(NoteEntity)
            → SQLite notes.db
          ← присвоенный id
      ← StateFlow обновился, UI видит актуальный id
```

## 4. Экраны (`ui/`)

**`ui/navigation/Routes.kt`** — типобезопасные `ListRoute`, `NoteRoute(id)` на kotlinx serialization. `NEW_NOTE_ID=0` используется по кнопке `+`, но внешние deep links с 0 парсер отвергает.

**`ui/navigation/NotesNavHost.kt`** — запускает NavHost со списком. Подписывается на `IncomingNote` из Activity: `Open(id)` → `NoteRoute(id)`; `Invalid` → Toast и без перехода; `None` → ничего. `onIncomingConsumed()` сбрасывает событие, чтобы оно не переигрывалось при рекомпозиции.

**`ui/list/NotesListScreen.kt`** — разметка Compose для списка, индикатора ожидания, пустого списка, кнопки «+», карточек и удаления. Получает `NotesListViewModel` из `AppContainer`, собирает `StateFlow` только пока экран активен.

**`ui/list/NotesListViewModel.kt`** — подписывается на `repository.observeAll()`, через `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(...))` делает StateFlow для UI. Метод `delete()` удаляет запись в корутине. В текущем коде удаление *из списка* не вызывает `ReminderScheduler.cancel()` (в отличие от `NoteViewModel.delete()`); это функциональное ограничение, о котором лучше говорить прямо.

**`ui/note/NoteScreen.kt`** — экран редактора. Получает `NoteViewModel` с ключом заметки; через `collectAsStateWithLifecycle()` рисует поля и фото. UI инициирует системный Photo Picker, runtime permission, Share chooser. Если `state.notFound=true`, вместо редактора появляется сообщение и кнопка возврата; нельзя редактировать или сохранить фиктивный id. Для новой заметки кнопки «Напомнить» и «Поделиться» отключены.

**`ui/note/NoteViewModel.kt`** — хранит `NoteUiState` (loading/notFound, текст, фото) и одноразовые `NoteEvent.Message`/`Close`. В `load()` ищет `repository.get(id)`; если null — `notFound=true`. В `save()` повторно проверяет notFound и сохраняет. В `delete()` отменяет напоминание для заметки и удаляет её. В `onPhotoPicked()` копирует URI через PhotoStore. В `scheduleReminder()` запускает планировщик только для существующей сохранённой заметки. Состояние и события разделены, чтобы Toast не повторялся после перерисовки.

**`ui/theme/Theme.kt`** — цвета/тема Compose. На проверку IPC не влияет.

## 5. Deep links (`deeplink/NoteDeepLink.kt`)

`NoteDeepLink.parse(intent)` выполняет «недоверенная строка → строго проверенный id». Требует `ACTION_VIEW`; `scheme=notes`, `host=note`; отсутствие порта, query, fragment, userinfo. `encodedPath` строго соответствует `/[0-9]+` (без `/%31`, `/1/2`, завершающего `/`). `toLongOrNull` безопасно отклоняет переполнение, `id > 0`. Возвращает одно из `Open(id)`, `Invalid`, `None`, **не** открывает БД сам. Затем `NoteViewModel` проверяет, существует ли запись. Поэтому синтаксическая проверка и проверка существования раздельны.

При переходе из уведомления `ReminderScheduler` строит `Intent(ACTION_VIEW)` именно к `MainActivity` и добавляет `notes://note/id`; этот Intent снова проходит тем же парсером.

## 6. Уведомления (`reminder/`)

**`ReminderPermission.kt`** — функция `rememberNotificationPermission(onGranted)` возвращает обработчик кнопки. Если Android ниже API 33 либо разрешение уже выдано — сразу вызывает `onGranted`. Иначе показывает объяснение, запрашивает `POST_NOTIFICATIONS` через ActivityResultLauncher. Счётчик отказов в private SharedPreferences; при повторном отказе показывает диалог с кнопкой перехода в настройки конкретного приложения. При denied запись остаётся доступна.

**`ReminderScheduler.kt`** — `schedule(noteId,title)` отменяет предыдущий `Job` того же id, ждёт 10 секунд и вызывает `showNotification()`. Перед фактическим показом проверяет разрешение снова. У уведомления два действия: карточка открывает MainActivity, кнопка «Готово» вызывает ReminderActionReceiver. Обе операции используют **явные** Intent и `PendingIntent.FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`. `FLAG_UPDATE_CURRENT` позволяет **самому владельцу** обновить уже созданный PI, не давая получателю менять поля. Для разных id различаются requestCode и data.

**`ReminderActionReceiver.kt`** — частный BroadcastReceiver. Фильтрует `ACTION_DONE`, EXTRA_NOTE_ID и диапазон, отменяет Job и уведомление. `android:exported=false` в Manifest не даёт постороннему приложению послать broadcast. Это демонстрируется командой `adb shell am broadcast`; отсутствие реакции в Logcat — ожидаемый результат.

**Ограничение:** планирование — корутина в памяти процесса, не точный системный будильник. После force-stop/убийства процесса запланированная Job может пропасть. В задании достаточно демонстрационного уведомления; для долговечных фоновых заданий применяют иные механизмы.

## 7. Фото (`photo/`)

**`PhotoPicker.kt`** использует `ActivityResultContracts.PickVisualMedia(ImageOnly)`: сам Android показывает выбор, приложению не нужны `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`, `CAMERA`. Отмена выбора возвращает null.

**`PhotoStore.kt`** сразу копирует выбранный `content://` поток в `filesDir/photos/<uuid>.jpg`, записывает локальный путь в заметку и уменьшает картинку для превью с помощью BitmapFactory и `inSampleSize`. Важная деталь: копируется исходное содержимое без перекодирования, поэтому `.jpg` — имя файла, а не гарантия JPEG-формата. Это не влияет на принцип временного URI, но не стоит утверждать, что приложение конвертирует фотографии в JPEG. В текущей версии старые файлы не удаляются автоматически при удалении заметки/фото (функциональное ограничение).

## 8. Экспорт файла (`share/`)

**`NoteExporter.kt`** сохраняет текст заметки (`title`, пустая строка, `body`) в приватный `cacheDir/exports/<id>.txt`. **`ShareNote.kt`** вызывает `FileProvider.getUriForFile` c фиксированным authority `edu.androidsec.notes.files`; создаёт `ACTION_SEND`, передаёт URI как `EXTRA_STREAM` и `ClipData`, указывает MIME `text/plain` и временный `FLAG_GRANT_READ_URI_PERMISSION`. Затем открывает системный chooser. В `file_paths.xml` разрешён только `exports/`, другие файлы `cacheDir`/`filesDir` не сопоставляются. Даже разрешённый каталог не читается чужим UID без конкретного URI-grant.

## 9. Защищённый IPC (`provider/`, `client/`)

**`provider/TitlesProvider.kt`** — `ContentProvider` по `content://edu.androidsec.notes.provider/notes`. Manifest: `exported=true` (иначе иной пакет не обратится) плюс `android:permission=edu.androidsec.notes.permission.READ_NOTES` с `protectionLevel="signature"`. ОС сравнивает сертификаты при выдаче доступа. Код `query()` дополнительно разрешает только путь `/notes`, проекцию `_id/title` и запрещает параметры selection/sort. `MatrixCursor` содержит только разрешённые пары. `insert/update/delete` бросают `UnsupportedOperationException` — провайдер **read-only**.

**`client/TitlesClient.kt`** — через `ContentResolver.query(TITLES_URI, ["_id","title"],...)` запрашивает данные по Binder, закрывает Cursor через `use`. В `diagnostics()` проверяет наличие Notes, `PackageManager.checkPermission` и `checkSignatures`. Это лишь диагностика: настоящее разрешение контролирует ОС на стороне провайдера. При отказе в query клиент получает ошибку.

**`client/ClientViewModel.kt`** — при старте запускает query и сохраняет либо список, либо исключение в `ClientUiState`. **`client/MainActivity.kt`** — Compose UI, который показывает заголовки, ошибку красным, диагностические статусы и «Повторить».

**Почему одной строки `<uses-permission>` мало:** она объявляет *запрос*, а не выдаёт signature-разрешение. Доступ получает лишь APK, подписанный тем же сертификатом, что определивший разрешение Notes. У другого ключа запрос отклоняется; это обязательный ручной тест.

## 10. Инструментальные тесты (`notes/src/androidTest/`)

| Файл | Что проверяет | Чего не доказывает |
|---|---|---|
| `NoteDeepLinkTest.kt` | Парсер принимает корректный id; отвергает мусор, overflow, чужой host, порт, query и т.п. | Что существующий id реально отображается в UI |
| `FileProviderScopeTest.kt` | URI схемы `content://`, чтение из приложения; файлы вне exports недоступны через FileProvider | Что чужой UID получил SecurityException |
| `TitlesProviderTest.kt` | Только `_id/title`, только `/notes`, нельзя `body`, нельзя изменять | Отрицательный тест стороннего APK с другим сертификатом |

В предоставленном пользователем журнале от 21.10.2026 инструментальные тесты выполнялись на Pixel_8 (Android 16); ранее было зафиксировано `10/10 completed, 0 failed`. Проверки на собственном компьютере можно повторить `START_LAB2.cmd` либо `gradlew.bat :notes:connectedDebugAndroidTest`.

## 11. Скрипты и документы

- `START_LAB2.cmd` — запускает полный сценарий; `CHECK_LAB2.cmd` — использует готовые APK; `SELFTEST_LAB2.cmd` — тест механизма команд без Android.
- `scripts/auto_run_windows.ps1` — тайм-ауты и логирование Gradle/ADB, поиск/запуск AVD, установка без streaming, поиск `apkanalyzer`, запуск **неизменённого** `check.sh all`, AndroidTest, восстановление установленного Notes после тестов и попытка открыть экран.
- `scripts/check.sh` — скрипт преподавателя. **Оставлен байт-в-байт без изменений.** Его команды и ожидаемые признаки изложены в `TEST_PLAN.md`.
- `scripts/collect_evidence.sh` — сохраняет настоящий вывод `check.sh` и информацию о подключённом устройстве в `docs/evidence/check-all.txt`.
- `scripts/static_audit.py` — статическая проверка требований к исходникам, **не** доказательство системной безопасности.
- `docs/evidence/` — место для настоящих результатов с компьютера, а не шаблонных `PASS`.

Читайте дальше: [SECURITY_WALKTHROUGH.md](SECURITY_WALKTHROUGH.md) — там те же файлы объясняются в логике пунктов преподавателя.
