# Защита ЛР №2: каждый пункт задания, угроза → реализация → проверка

Задание: «закрыть точки входа» приложения «Заметки» — оставить наружу только необходимые возможности и дать каждому внешнему вызову ровно нужные права. Здесь приводятся **реальные имена классов текущего проекта**. Контрольные команды — в [TEST_PLAN.md](TEST_PLAN.md).

## Модель угроз (что считается «чужим»)

На Android у каждого приложения отдельный UID и файловый sandbox. В нашем случае доверенный субъект — основное приложение `edu.androidsec.notes`, системные сервисы Android и при выполнении условия цифровой подписи — `edu.androidsec.notes.client`. Посторонние приложения и `adb shell` не должны иметь прямого доступа к private Receiver и файлам; доступ к заголовкам разрешён только сертификату разработчика. Внешняя ссылка и любые extras считаются **недоверенным пользовательским вводом** даже при запуске нашей Activity.

**Различайте две задачи:** ограничить *доступ* к компоненту (`exported`, manifest permission, temporary URI grant) и проверить *данные* после разрешённого входа (`URI`, id, `action`, диапазон). Публичная Activity может принимать ссылку, но не обязана выполнять произвольные действия по ней.

## 1. Манифест и минимальные разрешения

**Файл:** `notes/src/main/AndroidManifest.xml` (смотрите также клиентский `client/src/main/AndroidManifest.xml`). `android:exported=true` означает, что внешний пакет может обратиться к компоненту, **если** дополнительные ограничения позволяют. `false` означает, что чужой пакет не может его вызвать напрямую. У `MainActivity` `true`, иначе launcher/deep link не будут доступны. У `TitlesProvider` `true`, иначе клиент не сможет читать заголовки, **но** компонент защищён signature-разрешением. У `ReminderActionReceiver` и `FileProvider` — `false`.

| Компонент | exported | Другие барьеры | Почему |
|---|---|---|---|
| `.MainActivity` | `true` | Схема `notes://note`, `NoteDeepLink.parse` | Внешняя ссылка обязана открывать заметку |
| `.reminder.ReminderActionReceiver` | `false` | Явный immutable PI, валидация action/id | Чужой broadcast не может «нажать Готово» |
| `androidx.core.content.FileProvider` | `false` | Список путей + временный URI read-grant | Чужой пакет не видит файлов без явного шаринга |
| `.provider.TitlesProvider` | `true` | `android:permission` уровня signature + read-only код | Свой подписанный клиент может запросить заголовки |

`ProfileInstallReceiver` библиотеки удаляется через `tools:node="remove"`, чтобы не добавлять лишний публичный компонент для сценария лабораторной. **Проверяют итоговый merged manifest** после слияния с библиотеками, а не только открытый вами XML.

В Notes запрошено только `POST_NOTIFICATIONS`. Другие разрешения медиа/камеры отсутствуют. Иногда Android Gradle Plugin сам добавляет техническое `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; скрипт преподавателя специально допускает его, это не запрос галереи.

**Демонстрация:** `bash scripts/check.sh manifest` (требуются Android SDK Command-line Tools, поскольку `apkanalyzer` читает установленный APK).

## 2. Уведомления и системный Photo Picker

**Файлы:** `reminder/ReminderPermission.kt`, `photo/PhotoPicker.kt`, `photo/PhotoStore.kt`, `NoteScreen.kt`. Пользователь нажимает «Напомнить», только тогда запускается `rememberNotificationPermission`. Android 13+ требует `POST_NOTIFICATIONS`; перед первым системным запросом показано собственное пояснение. Если пользователь отказал, заметка остаётся доступной; после повторного отказа выводится предложение открыть настройки приложения (`ACTION_APP_NOTIFICATION_SETTINGS`). Число отказов хранится в private SharedPreferences. Ни при первом запуске, ни при выборе фотографии не нужно просить это разрешение.

Для фото вызывается `ActivityResultContracts.PickVisualMedia`: Android выдаёт URI только выбранного изображения. Поток по нему сразу копируется в `filesDir/photos`; приложение **не** получает широкое разрешение читать медиатеку. Отсутствуют `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`, `CAMERA`.

**Демонстрация:** вручную создать заметку → сохранить → «Напомнить» → объяснение/разрешить или отказать; отдельно выбрать фото, проверить отсутствие системного запроса чтения всей галереи. Отказ в уведомлениях не должен мешать редактированию.

## 3. Безопасный deep link `notes://note/<id>`

**Файлы:** `notes/src/main/AndroidManifest.xml`, `deeplink/NoteDeepLink.kt`, `MainActivity.kt`, `ui/navigation/NotesNavHost.kt`, `ui/note/NoteViewModel.kt`, `NoteScreen.kt`.

1. В манифесте указаны `ACTION_VIEW`, `DEFAULT`, `BROWSABLE`, `scheme=notes`, `host=note`. **Не** менять эти идентификаторы.
2. `MainActivity` передаёт `intent` парсеру: действие только `VIEW`; порт, userInfo, query/fragment недопустимы; encodedPath обязан быть `/` + десятичные цифры.
3. `toLongOrNull()` отвергает переполнение `Long`, `id=0`/отрицательные значения отвергаются.
4. `NotesNavHost` делает переход **только** для `IncomingNote.Open(id)`; `Invalid` вызывает Toast.
5. `NoteViewModel.load` ищет id в Room. Если нет записи, выставляет `notFound=true` и UI выводит «Не найдено» без формы редактирования; `save()` повторно запрещает запись в этом состоянии.

**Смысл:** `notes://note/999999` синтаксически допустим, но не обязан обозначать существующую запись. Парсер не может понять это без БД. `notes://note/abc` отвергается ещё до доступа к БД.

**Демонстрация:** `adb shell am start -W -a android.intent.action.VIEW -d "notes://note/1"`; затем `.../999999`, `.../abc`, `.../`. При некорректных ссылках приложение не падает и не открывает чужую запись.

## 4. Уведомление, immutable PendingIntent и закрытый ресивер

**Файлы:** `reminder/ReminderScheduler.kt`, `reminder/ReminderActionReceiver.kt`, `notes/src/main/AndroidManifest.xml`, `NotesApp.kt`.

- NotificationChannel имеет фиксированный id `reminders`; на Android 8+ пользователь управляет уведомлениями канала в настройках.
- По умолчанию через `delay(10_000)` в корутине формируется уведомление; заново проверяется `POST_NOTIFICATIONS`.
- **Открытие карточки**: `Intent(context, MainActivity::class.java)`, `ACTION_VIEW`, `data=notes://note/id`; `PendingIntent.getActivity(..., FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)`.
- **Кнопка «Готово»**: `Intent(context, ReminderActionReceiver::class.java)` с action `edu.androidsec.notes.action.DONE`, EXTRA_NOTE_ID; `PendingIntent.getBroadcast` с такими же флагами.
- `ReminderActionReceiver` объявлен `android:exported="false"`, проверяет правильность action и id. Отменяет `Job` и `NotificationManager`-уведомление с тем же id. Другие приложения не должны вызывать его прямым broadcast.

**Почему IMMUTABLE:** получателю `PendingIntent` нельзя дописать новый `action`, `component`, URI или extras, чтобы превратить нашу операцию в другую. `FLAG_UPDATE_CURRENT` означает, что *владелец* может обновить свой уже созданный PendingIntent.

**Демонстрация чужого вызова:** `adb shell am broadcast -n edu.androidsec.notes/.reminder.ReminderActionReceiver -a edu.androidsec.notes.action.DONE --el edu.androidsec.notes.extra.NOTE_ID 1`. Проверка преподавателя делает также неявный broadcast и смотрит отсутствие строки `DONE received` в Logcat. Системное сообщение `Broadcast completed` **не означает**, что private Receiver выполнил onReceive.

**Честное ограничение:** в текущей версии таймер — корутина внутри процесса. Он может потеряться после уничтожения процесса. Это не фоновая надёжная доставка после перезагрузки телефона.

## 5. Экспорт заметки через FileProvider

**Файлы:** `share/NoteExporter.kt`, `share/ShareNote.kt`, `notes/src/main/res/xml/file_paths.xml`, `AndroidManifest.xml`.

Сначала `NoteExporter` пишет `cacheDir/exports/<id>.txt`. Затем `FileProvider.getUriForFile(context, "edu.androidsec.notes.files", file)` отдаёт `content://edu.androidsec.notes.files/exports/<id>.txt`. В `ACTION_SEND` добавлены `EXTRA_STREAM`, `ClipData`, `FLAG_GRANT_READ_URI_PERMISSION`; chooser переносит временное право получателю. В манифесте: `exported=false`, `grantUriPermissions=true`, путь ограничен **одним каталогом** `exports` внутри cache.

**Почему не `file://`:** абсолютный путь приватной папки не даёт получателю легального доступа, а прямой обмен file URI на новых Android блокируется/небезопасен. При `content://` разрешения выдаёт Android, и FileProvider потоково открывает только разрешённый файл.

**Демонстрация:** поделиться заметкой в файловый/текстовый получатель; другой UID **без** read-grant выполняет `adb shell content read --uri content://edu.androidsec.notes.files/exports/1.txt` и получает `Permission Denial`/`SecurityException` (файл 1 должен существовать; check.sh это проверяет). `FileProviderScopeTest` дополнительно убеждается, что файл вне exports даже изнутри приложения нельзя превратить в URI провайдера.

## 6. Свой клиент: read-only TitlesProvider с signature

**Файлы:** `provider/TitlesProvider.kt`, `client/TitlesClient.kt`, `client/ClientViewModel.kt`, два манифеста, `client/build.gradle.kts`.

Названия **не менять**:

| Назначение | Значение |
|---|---|
| Notes package | `edu.androidsec.notes` |
| Client package | `edu.androidsec.notes.client` |
| Deep link | `notes://note/<id>` |
| FileProvider authority | `edu.androidsec.notes.files` |
| TitlesProvider authority | `edu.androidsec.notes.provider` |
| Provider URI | `content://edu.androidsec.notes.provider/notes` |
| Permission | `edu.androidsec.notes.permission.READ_NOTES` |
| Protection level | `signature` |
| Receiver | `edu.androidsec.notes.reminder.ReminderActionReceiver` |
| Action | `edu.androidsec.notes.action.DONE` |
| Channel | `reminders` |

В Notes объявлено `<permission android:name=... android:protectionLevel="signature"/>`. Провайдер экспортирован, но защищён `android:permission` (обе стороны доступа), а код допускает **только чтение** двух колонок. В клиенте `<uses-permission>` и `<queries><package...>` — второе необходимо для пакетной диагностики, само права не выдаёт. Клиент выполняет `ContentResolver.query(...)`, Binder переносит вызов в процесс Notes, Android проверяет манифестное разрешение. SQL отдаёт только id/title, `body`/`photo_path` не поддерживаются.

Сертификат подписания — идентификатор доверенного разработчика, а не пароль внутри приложения. При стандартной debug-сборке на **одной машине** оба приложения обычно подписываются одним debug keystore. При другом ключе установленный клиент должен увидеть отказ, часто `SecurityException`. Отрицательный тест нельзя заменить `adb shell content query`: shell имеет свой UID и не является клиентом с чужим сертификатом.

**Демонстрация:** показать заголовки в подписанном клиенте и ошибку на специально собранном/данном преподавателем клиенте с другой подписью. `adb shell content query --uri content://edu.androidsec.notes.provider/notes` тоже должен отказать; `insert`, `delete` и `update` не поддерживаются. Скрипт преподавателя проверяет shell-случай; сравнение разных APK — ручная часть.

## 7. README + ADB + скрипт преподавателя

**Файлы:** `README.md`, `docs/TEST_PLAN.md`, `scripts/check.sh`, `docs/evidence/check-all.txt`.

Преподаватель ожидает фактические команды и вывод отказов. Запускайте в корне через Git Bash:

```bash
bash scripts/check.sh all
```

Скрипт разбит на `manifest`, `closed`, `deeplink`, `provider`, проверяет merged manifest, чужой broadcast, файловый read, deep-link запуск, посторонние query/insert/delete. Итого по журналу пользователя 21.10.2026 было `PASS=18 FAIL=0`. После обновления учебных комментариев сценарий нужно при желании повторить: результаты до обновления **не** являются новым запуском.

`docs/evidence/check-all.txt` заполняется только реальной командой или автозапуском. В [TEST_PLAN.md](TEST_PLAN.md) приведены команды ADB и **ожидаемый тип** вывода; их не следует выдавать за фактически полученный лог.

## Бонус [+2]: Verified HTTPS App Links

В `docs/APP_LINK_BONUS.md` — отдельные шаги для `https://` домена и `assetlinks.json`. В этом ZIP **бонус не реализован и не подтверждён**: для проверки нужен реально контролируемый HTTPS-домен и правильный отпечаток сертификата. Нельзя называть кастомный `notes://` URI «верифицированным App Link»: это разные технологии.

## Таблица «угроза / механизм / где проверить»

| Угроза | Контроль | Где доказательство |
|---|---|---|
| Посторонний вызывает private receiver | `exported=false` + immutable PI + action/id guard | `check.sh closed` + Logcat |
| Чужой читает экспортированный файл без разрешения | `FileProvider`, ограниченный path + temporary grant | `check.sh closed`, `FileProviderScopeTest` |
| Ссылка несёт мусорный id | Строгий parse + `Room.get` + `notFound` | `check.sh deeplink`, `NoteDeepLinkTest`, экран |
| Клиент без сертификата получает заголовки | `signature` permission на exported provider | `check.sh provider`, чужой подписанный APK |
| Подписанный клиент пытается получить body | whitelist колонок `_id`, `title` | `TitlesProviderTest` |
| Даже доверенный клиент изменяет заметки | `insert/update/delete` запрещены | `TitlesProviderTest`, `check.sh provider` |
| Приложение требует медиаразрешения без необходимости | Системный Photo Picker, только POST_NOTIFICATIONS | `check.sh manifest`, демонстрация UI |

**Вывод для преподавателя:** неэкспортируемый компонент нельзя вызвать снаружи; экспортируемый провайдер защищён проверкой сертификата в ОС; публичный deep link дополнительно валидируется; временное чтение файла выдаётся только при добровольном экспорте; приложение не получает лишние разрешения.
