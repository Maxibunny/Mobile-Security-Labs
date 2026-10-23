> **Навигация (редакция для защиты 23.10.2026):** этот файл оставлен как дополнительный материал. Последовательный учебный разбор и актуальный статус — в [`00_START_HERE.md`](00_START_HERE.md), [`CODE_GUIDE.md`](CODE_GUIDE.md) и [`TEST_PLAN.md`](TEST_PLAN.md).

# Архитектура — объяснение исходников лабораторной №2

## Два приложения в одном Gradle-проекте

`settings.gradle.kts` подключает `:notes` и `:client`. `:notes` — владелец записей, `:client` — потребитель IPC, не имеющий прямого доступа к файлам Room. Пакеты различаются, но debug-сборки на одной машине по умолчанию подписываются одним `debug.keystore`. Именно цифровая подпись, **не** совпадение имени пакета, определяет допуск клиента.

## Данные и DI

```text
NotesApp (Application)
  └─ AppContainer (создаётся лениво 1 раз на процесс)
       ├─ NotesDatabase (Room) -> NoteDao -> таблица notes
       ├─ RoomNotesRepository (реализация NotesRepository)
       ├─ PhotoStore (копии фотографий в приватном хранилище)
       ├─ ReminderScheduler (CoroutineScope уровня приложения)
       └─ appScope (SupervisorJob + Dispatchers.Default)

MainActivity (принимает Intent)
  └─ NoteDeepLink.parse(Intent) -> IncomingNote.Open/Invalid/None
       └─ NotesNavHost (Compose Navigation)
            ├─ NotesListScreen -> NotesListViewModel -> NotesRepository
            └─ NoteScreen -> NoteViewModel -> NotesRepository
                   ├─ PhotoStore
                   └─ ReminderScheduler
```

`NoteUiState` — текущий снимок состояния экрана в `StateFlow`; Compose подписывается на него через `collectAsStateWithLifecycle`. `NoteEvent` передаётся через `Channel` для одноразовых сообщений и перехода назад, чтобы Toast не повторялся после перерисовки. У `NoteUiState` три важных режима: `isLoading`, `notFound`, нормальный редактор. `noteId == 0` означает создание; id из URL обязан быть положительным.

`NotesDatabase.SeedCallback` добавляет три стартовые записи с id 1/2/3 при **первом создании** БД. Если удалили 1 и хотите снова проверить стартовую заметку, переустановите приложение **с очисткой данных** или создайте новую заметку; при обычном обновлении Room уже не повторяет seed.

## Сценарий: заметка по ссылке

```text
adb am start ACTION_VIEW notes://note/1
  -> MainActivity (android:exported=true)
  -> NoteDeepLink.parse (строгий URI; потенциально чужой ввод)
  -> IncomingNote.Open(1)
  -> NotesNavHost.navigate(NoteRoute(1))
  -> NoteViewModel.load(1)
  -> NotesRepository.get(1) -> Room NoteDao.getById(1)
       ├─ есть запись: NoteUiState с текстом, кнопками редактирования
       └─ нет записи: notFound=true, только текст ошибки + «Назад»
```

Важно: `VIEW` ссылку может отправить **любое** приложение. Экспорт MainActivity разрешён **осознанно**, а недоверенный параметр не используется непосредственно для вставки в базу. Ссылки `notes://note/1?x=1`, `notes://note/%31`, `notes://note/1/2`, переполнения Long и отрицательные значения отвергаются.

## Сценарий: напоминание

```text
NoteScreen, кнопка «Напомнить»
  -> rememberNotificationPermission
  -> [разрешение получено] NoteViewModel.scheduleReminder()
  -> ReminderScheduler.schedule()
  -> CoroutineScope delay(10 секунд)
  -> NotificationManagerCompat.notify()
      ├─ content PendingIntent -> MainActivity, VIEW, notes://note/id
      └─ action «Готово» PendingIntent -> ReminderActionReceiver
            -> проверка action + note id
            -> ReminderScheduler.cancel(id)
            -> NotificationManager.cancel(id), Log.i
```

`PendingIntent.FLAG_IMMUTABLE` делает вложенный Intent недоступным для изменения **получателем**. `FLAG_UPDATE_CURRENT` позволяет **создателю** обновить свой Intent, не делая его изменяемым для чужих приложений. Явный компонент исключает ошибочный выбор чужого Receiver. `exported=false` добавляет системную защиту от вызовов из других UID.

Текущий планировщик демонстрационный: после уничтожения процесса/force-stop задержка теряется. Это сознательное ограничение `starter`, не дефект IPC. `reminders` — неизменяемый ID канала уведомлений.

## Сценарий: фотография

`NoteScreen` -> `rememberPhotoPicker` -> `ActivityResultContracts.PickVisualMedia(ImageOnly)` -> временный `content://` URI -> `NoteViewModel.onPhotoPicked` -> `PhotoStore.copyToPrivateStorage` -> сохранённый private path. Приложение не видит остальные фотографии и не просит `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`, `CAMERA`.

## Сценарий: файл через ACTION_SEND

`NoteScreen` -> `ShareNote.shareNoteAsFile` -> `NoteExporter.export` записывает `cacheDir/exports/<id>.txt` -> `FileProvider.getUriForFile` -> `content://edu.androidsec.notes.files/exports/<id>.txt` -> `ACTION_SEND` / `EXTRA_STREAM` / `ClipData` / `FLAG_GRANT_READ_URI_PERMISSION` -> системный chooser -> приложение-получатель. В `file_paths.xml` **ровно один** корень `cache-path exports/`, а провайдер `exported=false`.

## Сценарий: заголовки между приложениями

```text
ClientViewModel
  -> TitlesClient.queryTitles() [Dispatchers.IO]
  -> ContentResolver.query(content://edu.androidsec.notes.provider/notes)
  -> проверка ОС: подпись клиента + READ_NOTES (protectionLevel=signature)
      ├─ нет права -> SecurityException, query не выполняется
      └─ есть право -> TitlesProvider.query()
             -> UriMatcher сверяет /notes
             -> отклоняет любые колонки кроме _id/title
             -> NoteDao.titlesBlocking() [binder thread]
             -> MatrixCursor(_id, title)
             -> TitlesClient превращает Cursor в List<String>
```

Клиент не получает `body` или путь фотографии. `getCallingUid`/`callingPackage` нужны для **диагностики**, не служат самостоятельной авторизацией. Попытки `insert/update/delete` запрещены и разрешением в манифесте, и исключениями в коде.

## Как ориентироваться по файлам

| Файл | Ответственность |
|---|---|
| `AndroidManifest.xml` | декларативный контроль компонентов/permission |
| `MainActivity.kt` | получение URL или действия уведомления, жизненный цикл |
| `deeplink/NoteDeepLink.kt` | синтаксическая валидация URL |
| `ui/navigation/NotesNavHost.kt` | переход между списком и заметкой |
| `ui/note/NoteViewModel.kt` | UI-state, проверка id, сохранение/удаление |
| `ui/note/NoteScreen.kt` | пользовательские действия, диалоги Android |
| `reminder/ReminderPermission.kt` | runtime-разрешение и настройка отказов |
| `reminder/ReminderScheduler.kt` | уведомление, два защищённых PendingIntent |
| `reminder/ReminderActionReceiver.kt` | действие «Готово» |
| `photo/PhotoPicker.kt` | доступ к одному выбранному снимку |
| `photo/PhotoStore.kt` | приватная копия выбранного изображения (из starter) |
| `share/NoteExporter.kt` | содержимое TXT (из starter) |
| `share/ShareNote.kt` | FileProvider URI и временный grant |
| `provider/TitlesProvider.kt` | read-only content IPC |
| `client/.../TitlesClient.kt` | проверка доступа с подписью клиента (из starter) |
| `data/db/NoteDao.kt` | SQL для Room |
| `data/NotesRepository.kt` | слой абстракции над Room |
| `di/AppContainer.kt` | конструктор зависимостей |
