# Практическая проверка на Windows и демонстрация преподавателю

Все команды ниже выполняются **на вашем ПК** с установленным Android SDK и запущенным эмулятором. Из этого ZIP нельзя подтвердить состояние устройства удалённо. Смотрите различие между **фактическим** результатом и **ожидаемым**.

## 0. Самый простой запуск

1. В Проводнике Windows откройте распакованную папку `2lab`.
2. Дважды щёлкните `START_LAB2.cmd` — полный путь: Gradle → эмулятор → APK → `check.sh all` → AndroidTest → открытие программ. Для повторного быстрого цикла: `CHECK_LAB2.cmd`.
3. Если возникает ошибка, в `logs/auto-<время>/summary.txt` указана причина; стандартный вывод команд хранится там же.
4. Если в Android Studio при ручной сборке ошибка **Java 17 / Kotlin 21**, в этом ZIP `notes/build.gradle.kts` и `client/build.gradle.kts` явно устанавливают `jvmTarget=JVM_17`. Сделайте **Sync Project with Gradle Files**. Сборку в этой среде после учебных комментариев не выполняли.
5. Никаких `adb uninstall`, Wipe Data или удаления личных заметок скрипт в штатном сценарии не требует. Переустановка `adb install -r` сохраняет данные при совпадающей подписи; инструментальные тесты могут менять состояние тестового приложения.

## 1. Как открыть терминал без опыта

**PowerShell:** Проводник (`Win+E`) → папка `2lab` → нажать `Ctrl+L` → вписать `powershell` → Enter. **Git Bash:** правая кнопка в пустой области папки → `Git Bash Here` (на Windows 11 может потребоваться «Показать дополнительные параметры»). `scripts/check.sh` требует **Git Bash**, не PowerShell.

Проверить, что эмулятор доступен:

```powershell
adb devices
```

**Ожидаемо (пример, не лог конкретного запуска):**

```text
List of devices attached
emulator-5554    device
```

Если обычное `adb` не найдено — запустите автоматические CMD-файлы, они находят SDK; либо откройте SDK Manager для проверки `platform-tools`.

## 2. Установка без зависания streaming

PowerShell из корня, если нужен ручной запуск:

```powershell
.\gradlew.bat :notes:assembleDebug :client:assembleDebug
adb install --no-streaming -r ".\notes\build\outputs\apk\debug\notes-debug.apk"
adb install --no-streaming -r ".\client\build\outputs\apk\debug\client-debug.apk"
```

Ожидаемые признаки: `BUILD SUCCESSFUL`, а каждая установка печатает `Performing Push Install` и `Success`. Если `adb devices` показывает `offline`, сперва дождитесь полного запуска эмулятора или выполните Cold Boot Now в Device Manager.

## 3. Проверка на соответствие критериям

В Git Bash в корне:

```bash
bash scripts/check.sh all
```

В конце должен быть настоящий вывод `ИТОГО: PASS=18 FAIL=0` **если** версия, устройство и окружение проходят ровно те же проверки, что и ранее. Сам по себе этот текст не гарантирован, не подставляйте его вручную. Чтобы вывести всё в файл и не потерять возвращаемый код:

```bash
bash scripts/collect_evidence.sh
```

Полученный файл: `docs/evidence/check-all.txt`. Не редактируйте вывод `PASS/FAIL`. Если `apkanalyzer не найден`, установите **Android SDK Command-line Tools (latest)** в SDK Manager (или запустите `START_LAB2.cmd`, в нём предусмотрена контролируемая загрузка SDK Tools).

По частям:

```bash
bash scripts/check.sh manifest
bash scripts/check.sh closed
bash scripts/check.sh deeplink
bash scripts/check.sh provider
```

## 4. Что именно вводить через ADB (пункты 3–6)

### 4.1. Deep link (3)

```bash
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/1'
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/999999'
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/abc'
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/'
```

**Ожидается:** первая ссылка открывает заметку id=1, если она существует; 999999 безопасно сообщает об отсутствии; `abc` и пустое id не дают доступа и не вызывают `FATAL EXCEPTION`. Не меняйте схему на `https` — бонус отдельный.

### 4.2. Закрытый receiver (4)

```bash
adb logcat -c
adb shell am broadcast -n edu.androidsec.notes/.reminder.ReminderActionReceiver -a edu.androidsec.notes.action.DONE --el edu.androidsec.notes.extra.NOTE_ID 1
adb shell am broadcast -a edu.androidsec.notes.action.DONE -p edu.androidsec.notes --el edu.androidsec.notes.extra.NOTE_ID 1
adb logcat -d -s ReminderActionReceiver:I
```

**Ожидается:** от постороннего shell вызова **нет** строки `DONE received for note 1`. Не следует считать успехом одно лишь `Broadcast completed`: Android может сообщить о завершении обработки команды, при этом закрытый ресивер не исполняется.

В UI для положительного теста: открыть сохранённую заметку → «Напомнить» → разрешить уведомления → дождаться карточки → нажать «Готово» → уведомление исчезает. Обратите внимание: это проверяет работу *нашего* PendingIntent, а не возможность внешнего broadcast.

### 4.3. FileProvider (5)

```bash
adb shell content read --uri content://edu.androidsec.notes.files/exports/1.txt
```

**Ожидается:** `SecurityException` / `Permission Denial` / `not exported`, **а не** содержимое заметки. Положительный сценарий: открыть сохранённую заметку → «Поделиться файлом» → выбрать подходящий получатель → получатель читает вложенный `.txt`.

### 4.4. ContentProvider заголовков (6)

```bash
adb shell content query --uri content://edu.androidsec.notes.provider/notes
adb shell content insert --uri content://edu.androidsec.notes.provider/notes --bind title:s:hacked
adb shell content delete --uri content://edu.androidsec.notes.provider/notes
```

**Ожидается:** отказ (`SecurityException` / `Permission Denial`; для модификаций допустимо `UnsupportedOperationException`), потому что shell не владеет signature-разрешением. Приведённые названия исключений — **ориентиры**, а не сфабрикованные логи.

### 4.5. Разные цифровые подписи — обязательный ручной тест

**Положительный случай:** `notes` и `client`, собранные на одном компьютере стандартным debug keystore, как правило имеют совпадающий сертификат → клиент показывает заголовки. Для чистого теста убедитесь, что оба APK собраны одной средой/пользователем.

**Отрицательный случай:** клиент, подписанный другим сертификатом, должен получить отказ в `ContentResolver.query` (обычно `SecurityException`). Не заменяйте этот тест отключением разрешения в UI: это не одно и то же. В `client/build.gradle.kts` предусмотрена опциональная настройка своего хранилища через `client/signing.properties` либо Gradle `-Psigning.*`. **Пароли, keystore и signing.properties не добавляйте в Git/ZIP для сдачи.** Устанавливать два разных сертификата под одним `applicationId` поверх друг друга без предварительного удаления нельзя: Android блокирует обновление с несовпадающим сертификатом. Для защиты проще показать успешный клиент и затем установить отдельный APK преподавателя с отличающейся подписью/именем пакета.

### 4.6. POST_NOTIFICATIONS / Photo Picker (2)

1. Свежая установка/сброс разрешения уведомлений → открыть приложение: системный диалог не возникает сам.
2. Открыть **сохранённую** заметку → «Напомнить»: виден поясняющий диалог, затем системный запрос.
3. Отказать: заметки продолжают создаваться и редактироваться. Повторить запрос, проверить поведение при втором отказе и предложение настроек.
4. «Прикрепить фото»: системный Photo Picker, не диалог на чтение всей галереи.
5. Разрешить уведомления и проверить карточку/кнопку «Готово».

## 5. Instrumentation tests

```powershell
.\gradlew.bat :notes:connectedDebugAndroidTest
```

Предыдущий лог с эмулятора Pixel_8 / Android 16: был успешный `BUILD SUCCESSFUL`; в более раннем запуске зафиксировано `Tests 10/10 completed (0 failed)`. После добавления документационных комментариев тесты не перезапускались здесь; после выполнения сохраните свой новый stdout, если преподаватель требует подтверждение именно текущей редакции.

## 6. Где реальные журналы и как их не перепутать

| Место | Что это | Степень подтверждения |
|---|---|---|
| `docs/evidence/check-all.txt` | Дословный stdout от `check.sh all` на вашей машине | Реальный, если файл создан запуском |
| `logs/auto-.../security-check.stdout.txt` | Весь stdout преподавательского сценария | Реальный, если создан запуском |
| `logs/auto-.../summary.txt` | Сводка автосценария и ошибки команд | Реальный, если создан запуском |
| `notes/build/reports/androidTests/` | Отчёт инструмента Android Gradle Test | Реальный после запуска |
| `docs/evidence/USER_SESSION_2026-10-21.md` | **Выдержка из присланного пользователем журнала предыдущей редакции** | Историческая, не подмена `check-all.txt` |
| Этот документ | Методика и ожидаемые признаки | **Не является** журналом устройства |

Для README преподаватель просит **команду и её реальный вывод** по пунктам 3–6. В `README.md` приведены команды и указаны файлы с доказательствами, но если преподаватель захочет распечатанный stdout, приложите сохранённый `docs/evidence/check-all.txt`. Не вставляйте в отчёт пример ожидаемого вывода как будто он получен от ADB.
