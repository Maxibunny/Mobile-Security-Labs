# Лабораторная работа №2 — «Разрешения, компоненты и IPC»

---

## 1. Быстрый запуск в Android Studio (Windows 10/11)

1. Распакуйте ZIP целиком, не удаляя папки `gradle/`, `client/`, `notes/`, `scripts/` и скрытую папку `.git/`.
2. Запустите **Android Studio → Open** и выберите **папку проекта**, в которой находится `settings.gradle.kts`. Не открывайте только `notes/`.
3. Дождитесь **Gradle Sync**. Первый запуск требует интернета: Gradle Wrapper (`gradle-8.13`), плагины и зависимости скачиваются автоматически. Установите **Gradle JDK = 17** в **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**; файл с принудительным требованием Java 25 удалён.
4. В **Tools → SDK Manager → SDK Platforms** установите Android SDK Platform **API 36** (нужна для `compileSdk=36`). В **SDK Tools** включите **Android SDK Command-line Tools (latest)**, **Android SDK Platform-Tools**, **Android Emulator** и нужные SDK Build-Tools.
5. В **Tools → Device Manager → Create device** выберите **Pixel 6**, образ **Google Play API 36** или **API 34+**, завершите создание и запустите эмулятор. Образ Google Play предпочтительнее для отрицательных security-тестов: на обычных образах с `adb root` проверки могут быть непрезентативными.
6. В выпадающем списке конфигураций Android Studio выберите приложение **notes**, затем ▶ **Run**. Должны открыться три стартовые заметки; откройте первую.
7. Для проверки signature-разрешения выберите конфигурацию **client** и ▶ **Run** **без изменения debug-подписи**. Клиент показывает заголовки из Notes, если сертификаты совпадают.

Если конфигураций нет, дождитесь Gradle Sync либо создайте **Run → Edit Configurations → + → Android App**, модуль `notes` (затем аналогично `client`). Не создавайте новый Android-проект поверх исходников.

**Сборка без кнопки Run (PowerShell в корне проекта):**

```powershell
.\gradlew.bat :notes:assembleDebug :client:assembleDebug
```

**Результирующие APK:**

- `notes/build/outputs/apk/debug/notes-debug.apk`
- `client/build/outputs/apk/debug/client-debug.apk`

**Android:** `minSdk=26`, `targetSdk=36`, `compileSdk=36`; **AGP 8.13.0, Gradle 8.13, Kotlin 2.2.21, Java 17**. В отличие от `starter`, версии библиотек приведены к API 36 и AGP 8.13.0; подробности в `docs/BUILD_COMPATIBILITY.md`.

## 2. Что проверить внутри приложения

1. «Заметки»: присутствуют стартовые записи 1, 2, 3. Запись можно открыть, изменить, сохранить и удалить.
2. «Прикрепить фото»: открывается **системный Photo Picker**; выбранный снимок копируется в закрытое хранилище Notes. Не появится запрос доступа ко всем фотографиям.
3. «Напомнить через 10 секунд»: только в этот момент объясняется и запрашивается `POST_NOTIFICATIONS` (Android 13+). При отказе запись остаётся доступна. После двух отказов предлагаются системные настройки уведомлений.
4. После разрешения через 10 секунд появляется уведомление. Нажатие **на карточку уведомления** открывает конкретную заметку; **«Готово»** отменяет уведомление.
5. «Поделиться файлом»: откроется системный выбор получателя; в другом приложении можно прочитать текстовый файл заметки (через `content://`, не `file://`).
6. В **client** с той же подписью доступен список заголовков; при другой подписи — отказ/`SecurityException`.
7. `notes://note/1` открывает существующую заметку. `notes://note/999999` показывает «Заметка не найдена» и **не** даёт сохранить фиктивную запись; `abc` и пустой id не ведут к падению.

## 3. Фиксированные идентификаторы — НЕ МЕНЯТЬ

| Сущность | Идентификатор |
|---|---|
| Notes applicationId | `edu.androidsec.notes` |
| Client applicationId | `edu.androidsec.notes.client` |
| Deep link | `notes://note/<id>` |
| FileProvider authority | `edu.androidsec.notes.files` |
| TitlesProvider authority | `edu.androidsec.notes.provider` |
| TitlesProvider path | `/notes` |
| Signature permission | `edu.androidsec.notes.permission.READ_NOTES` |
| Receiver | `edu.androidsec.notes.reminder.ReminderActionReceiver` |
| Receiver action | `edu.androidsec.notes.action.DONE` |
| Notification channel | `reminders` |

## 4. Проверка (Windows: Git Bash)

Подготовьте эмулятор с установленным `:notes`, включите **Android SDK Command-line Tools**, откройте **Git Bash** в корне проекта и выполните:

```bash
bash scripts/check.sh all
```

Подпроверки: `manifest`, `closed`, `deeplink`, `provider`:

```bash
bash scripts/check.sh manifest
bash scripts/check.sh closed
bash scripts/check.sh deeplink
bash scripts/check.sh provider
```

Скрипт **не изменяли**, чтобы не подогнать критерии. Он проверяет **установленный** APK, а не просто отредактированный манифест. Если `adb` не видит эмулятор, проверьте `adb devices`. Если не найден `apkanalyzer`, установите Command-line Tools (latest). `ANDROID_HOME` обычно не обязателен, если SDK в `C:/Users/<пользователь>/AppData/Local/Android/Sdk`.

Сохранить настоящий лог одним действием:

```bash
bash scripts/collect_evidence.sh
```

Он запишет `docs/evidence/check-all.txt`, включая время, список устройств, результаты `PASS/FAIL` и **сырой вывод ADB**. Он ничего не подделывает. При провале сначала исправьте проблему, заново **Run** модуль `notes`, затем перезапустите скрипт.

**Реальные результаты `bash scripts/check.sh all`:** на эмуляторе пользователя 21.10.2026 было `ИТОГО: PASS=18 FAIL=0` (см. выдержку в `docs/evidence/USER_SESSION_2026-10-21.md`). Полный сырой вывод новой редакции следует получить при повторном запуске `scripts/collect_evidence.sh`.

## 5. ADB: команды для пунктов 3–6 и требования к выводу

Проверять в терминале Android Studio/PowerShell (если `adb` добавлен в PATH) или Git Bash. Ниже именно **ожидаемая категория результата**, не выдуманный реальный вывод.

### Deep link / пункт 3

```bash
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/1'
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/999999'
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/abc'
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/'
adb logcat -d -s AndroidRuntime:E
```

Ожидается запуск `edu.androidsec.notes/.MainActivity` без `FATAL EXCEPTION`; `999999` даёт экран ошибки, `abc`/пустой id — сообщение об ошибке. **Deep link намеренно публичен**, поэтому говорить о `Permission Denial` для самой ссылки неправильно: защита заключается в строгой валидации и отказе создавать несуществующую запись.

### Receiver / пункт 4

```bash
adb logcat -c
adb shell am broadcast -n edu.androidsec.notes/.reminder.ReminderActionReceiver -a edu.androidsec.notes.action.DONE --el edu.androidsec.notes.extra.NOTE_ID 1
adb shell am broadcast -a edu.androidsec.notes.action.DONE -p edu.androidsec.notes --el edu.androidsec.notes.extra.NOTE_ID 1
adb logcat -d -s ReminderActionReceiver:I
```

Ожидается отсутствие `DONE received for note 1` в logcat для **чужих** broadcast. Для нажатия «Готово» из уведомления эта строка, напротив, должна появиться — это легальный вызов через PendingIntent.

### FileProvider / пункт 5

```bash
adb shell content read --uri content://edu.androidsec.notes.files/exports/1.txt
```

Ожидается `Permission Denial` / `SecurityException` / `not exported`; если текст файла спокойно печатается — это ошибка. Через стандартное «Поделиться файлом» файл должен открываться получателем с временным read-grant.

### TitlesProvider / пункт 6

```bash
adb shell content query --uri content://edu.androidsec.notes.provider/notes
adb shell content insert --uri content://edu.androidsec.notes.provider/notes --bind title:s:hacked
adb shell content delete --uri content://edu.androidsec.notes.provider/notes
```

Ожидается `SecurityException` / `Permission Denial` для постороннего `shell`. Даже свой клиент не может вызвать `insert/delete` — эти методы дополнительно кидают `UnsupportedOperationException`. Подробный тест клиента с другим сертификатом: [docs/TESTING.md](docs/TESTING.md).

> Чтобы выполнить обязательное требование задания «команда и **её вывод**», после выполнения на своём эмуляторе **вставьте настоящие текстовые результаты** в этот раздел (не только ожидаемый результат) или дайте ссылки на `docs/evidence/check-all.txt` и дополнительные файлы с сырым выводом. Для защиты преподавателю требуется именно подтверждённый эксперимент, а не шаблон.

## 6. Как устроен проект

- `notes/src/main/AndroidManifest.xml` — границы IPC и разрешения.
- `notes/src/main/java/edu/androidsec/notes/MainActivity.kt` — единственная Activity, маршрутизация входных интентов.
- `deeplink/NoteDeepLink.kt` — разбор `VIEW` URI.
- `ui/navigation/` — типобезопасная Compose-навигация.
- `ui/note/` — экран и ViewModel, Room-проверка существования id.
- `reminder/` — runtime permission, уведомление, безопасные PendingIntent, приватный Receiver.
- `photo/` — системный Photo Picker и приватная копия файла.
- `share/` + `res/xml/file_paths.xml` — сохранение TXT и выдача одного каталога через FileProvider.
- `provider/TitlesProvider.kt` — строго ограниченный интерфейс read-only IPC.
- `data/db/`, `data/`, `di/` — Room, репозиторий, зависимости.
- `client/` — **неизменённый** клиент заготовки (чтение списка + диагностика подписи).
- `scripts/check.sh` — **неизменённый** чек-лист преподавателя.
- `notes/src/androidTest/` — добавленные инструментальные тесты URI, read-only провайдера и файлового каталога FileProvider.

Дополнительно:

- [docs/BUILD_COMPATIBILITY.md](docs/BUILD_COMPATIBILITY.md) — разбор вашей ошибки 28 AAR Metadata и таблица совместимости.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — связи классов и путь запроса от UI до базы.
- [docs/SECURITY.md](docs/SECURITY.md) — каждое решение и его угроза.
- [docs/TESTING.md](docs/TESTING.md) — сценарии ручной и автоматической проверки.
- [docs/DEFENSE.md](docs/DEFENSE.md) — вопросы/короткие ответы для защиты по каждому `ipc-*`.
- [docs/VALIDATION_REPORT.md](docs/VALIDATION_REPORT.md) — реальные проверки, выполненные при подготовке ZIP, и ограничение сборки.
- [docs/APP_LINK_BONUS.md](docs/APP_LINK_BONUS.md) — реальное включение бонуса с GitHub Pages, доменом, SHA-256 и `assetlinks.json`.
