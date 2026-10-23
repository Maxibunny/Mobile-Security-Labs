> **Навигация (редакция для защиты 23.10.2026):** этот файл оставлен как дополнительный материал. Последовательный учебный разбор и актуальный статус — в [`00_START_HERE.md`](00_START_HERE.md), [`CODE_GUIDE.md`](CODE_GUIDE.md) и [`TEST_PLAN.md`](TEST_PLAN.md).

# Тестирование: запуск, проверки, реальные артефакты

## 0. Что проверено и чего пока нет

**Статически:** синтаксис XML, структура проекта, сохранность `scripts/check.sh`, имена компонентов/authority, отсутствие опасных `uses-permission`, наличия реализации вместо заглушек. **Не проверено автоматически:** Gradle/Kotlin-компиляция, итоговый merged manifest, runtime-поведение Android и наличие уведомления на конкретном телефоне. Никаких вымышленных PASS и adb-логов в репозитории нет.

Предпочтительный стенд из стартового задания: **Pixel 6, Google Play API 36**. Не запускайте тесты отказов от имени root: root может обойти обычную модель прав и дать ложный результат.

## 1. Установка приложений

PowerShell в корне:

```powershell
.\gradlew.bat :notes:assembleDebug :client:assembleDebug
adb devices
adb install -r notes/build/outputs/apk/debug/notes-debug.apk
adb install -r client/build/outputs/apk/debug/client-debug.apk
adb shell am start -n edu.androidsec.notes/.MainActivity
```

Если одновременно Android Studio и командная строка используют разные `debug.keystore`, сравните подписи — клиент может не получить READ_NOTES. При ошибке `INSTALL_FAILED_UPDATE_INCOMPATIBLE` после смены ключа **удалите только конфликтующий клиент**, затем установите его заново.

## 2. Полный автоматический чек-лист

Откройте **Git Bash** в корне:

```bash
bash scripts/check.sh all
```

Ожидаемый формат конца лога — `ИТОГО: PASS=N FAIL=0` и код процесса 0 (конкретное N зависит от версии проверочного скрипта). Нельзя объявить тест пройденным, пока не увидите эту строку **на собственном эмуляторе**. Лог сохраняется так:

```bash
bash scripts/collect_evidence.sh
```

Файл результата: `docs/evidence/check-all.txt`. Команды и сырой вывод всех отрицательных проверок преподавателя уже будут внутри. Скопируйте соответствующие выдержки в раздел 5 README для выполнения буквального требования Moodle.

## 3. Проверка deep link вручную

```bash
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/1'
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/999999'
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/abc'
adb shell am start -W -a android.intent.action.VIEW -d 'notes://note/'
adb logcat -d -s AndroidRuntime:E
```

Проверьте экран: `1` — редактор существующей заметки; `999999` — текст «Заметка не найдена», нет полей редактирования и кнопки Save; `abc`, пустой id — безопасная ошибка, без перехода в редактор. `FATAL EXCEPTION` отсутствует.

## 4. Уведомления и permission

Для воспроизводимого теста разрешений удобно очистить данные Notes **только после сохранения важных заметок**:

```bash
adb shell pm clear edu.androidsec.notes
```

После очистки приложение получает три стартовые записи. Запустите:

```bash
adb shell am start -n edu.androidsec.notes/.MainActivity
```

Откройте заметку 1 и нажмите «Напомнить через 10 секунд». На Android 13+ до системного диалога появится объяснение. Нажмите «Не разрешать» — приложение не падает. Повторите — после второго отказа предложит открыть настройки. Включите уведомления и вновь нажмите «Напомнить»: через ~10 с должна появиться карточка.

**Проверка безопасной «Готово»:**

```bash
adb logcat -c
adb shell am broadcast -n edu.androidsec.notes/.reminder.ReminderActionReceiver -a edu.androidsec.notes.action.DONE --el edu.androidsec.notes.extra.NOTE_ID 1
adb logcat -d -s ReminderActionReceiver:I
```

Чужой интент не должен создавать логи `DONE received`. Затем создайте настоящее уведомление, нажмите в нём «Готово» и проверьте тот же tag. Теперь сообщение `DONE received for note 1` **должно** появиться, уведомление исчезнет.

**Важно:** напоминания реализованы в памяти процесса, как предусмотрено заготовкой. Между постановкой и 10-секундным срабатыванием не делайте `force-stop`.

## 5. Photo Picker и FileProvider

Фото: создайте `photo.jpg` и поместите его на эмулятор (команды из starter):

```bash
adb push photo.jpg /sdcard/Pictures/photo.jpg
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/photo.jpg
```

Откройте редактирование заметки → «Прикрепить фото». Покажется **системный** выбор, затем превью в заметке. В Settings → Apps → Notes → Permissions не должно быть доступа ко всей галерее.

Файл: нажмите «Поделиться файлом», выберите приложение, которое умеет получить `text/plain` вложение. Убедитесь, что текст заметки действительно виден. Затем проверьте **снаружи**:

```bash
adb shell content read --uri content://edu.androidsec.notes.files/exports/1.txt
```

Должен быть **отказ**, даже если `1.txt` был ранее создан в каталоге `exports/` через Share. Это различие — основная цель временного grant.

## 6. TitlesProvider: клиент с той же и с другой подписью

### Одинаковая подпись

Соберите оба debug-APK **на одном ПК** обычным Gradle и установите оба. Откройте Client; убедитесь, что диагностика сообщает доступ и отображает 3 стартовых заголовка (если БД ещё не менялась).

### Другая подпись

Генерируем отдельный ключ; **не коммитим** его в Git:

```bash
keytool -genkeypair -keystore other.jks -storepass other123 -keypass other123 -alias other -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Other, O=Test, C=RU'
```

В Git Bash для `-Psigning.storeFile` подставьте абсолютный путь к `other.jks`. Например, из корня в Git Bash:

```bash
OTHER_KEY="$(pwd -W)/other.jks"
./gradlew.bat :client:assembleDebug "-Psigning.storeFile=$OTHER_KEY" -Psigning.storePassword=other123 -Psigning.keyAlias=other -Psigning.keyPassword=other123
adb uninstall edu.androidsec.notes.client
adb install client/build/outputs/apk/debug/client-debug.apk
```

`-Psigning.storeFile` в starter обрабатывает **только модуль client**, модуль Notes остаётся подписан прежним debug-ключом. После запуска Client должно быть **SecurityException** вместо списка заголовков. На Android система решает по **сертификату**, а не по имени клиента.

Восстановление клиента с debug-подписью (после сборки с кастомным ключом очищайте инкрементальный результат, чтобы не установить прежний APK по ошибке):

```bash
./gradlew.bat :client:clean :client:assembleDebug
adb uninstall edu.androidsec.notes.client
adb install client/build/outputs/apk/debug/client-debug.apk
```

В Windows PowerShell аналогичная сборка — `./gradlew.bat` и абсолютный путь `C:/Users/.../other.jks` в параметре. Используйте **не** real production keystore: `other123` только демонстрационный пароль для тестового ключа.

### Негативные тесты shell

```bash
adb shell content query --uri content://edu.androidsec.notes.provider/notes
adb shell content insert --uri content://edu.androidsec.notes.provider/notes --bind title:s:hacked
adb shell content delete --uri content://edu.androidsec.notes.provider/notes
```

Нужно получить `SecurityException`/`Permission Denial`; если удаётся читать/изменять данные — защита не работает. Для доверенного клиента `insert/update/delete` в любом случае недоступны на уровне реализации провайдера.

## 7. Инструментальные тесты (добавлены к starter)

В Android Studio: Run → Edit Configurations → Android Instrumented Tests, модуль `notes`, либо терминал:

```powershell
.\gradlew.bat :notes:connectedDebugAndroidTest
```

Файлы: `notes/src/androidTest/java/edu/androidsec/notes/NoteDeepLinkTest.kt`, `TitlesProviderTest.kt`. Они проверяют URI-парсер, выбор разрешённых колонок и запрет записи внутри приложения. Они **не заменяют** внешние shell-тесты и проверку подписи клиента.

## 8. Что предоставить преподавателю

В репозитории: полный исходный код + исходная история + README, реальный `docs/evidence/check-all.txt` и скопированный в README вывод adb для пунктов 3–6. При защите показать: невалидные URI, два PendingIntent, разные сертификаты, временный FileProvider grant, манифест после слияния.

Шаблон фиксации реального результата вручную (замените, НЕ оставляйте placeholder):

```text
Дата / эмулятор / API:
Команда: adb shell content query --uri ...
Вывод (точно как в терминале):
...
Итог: PASS / FAIL
```

## 8. Инструментальные тесты в новом ZIP

Для Google Play эмулятора API 36, в PowerShell:

```powershell
.\gradlew.bat :notes:connectedDebugAndroidTest
```

- `NoteDeepLinkTest`: корректные и поддельные URI (отрицательные, слишком длинные, чужая схема/host, фрагменты, query).
- `TitlesProviderTest`: только `_id` и `title`, отказ `insert`, `update`, `delete`, чужие URI и секретные колонки.
- `FileProviderScopeTest`: экспортируемая заметка читается самим приложением, файл вне `cacheDir/exports/` нельзя превратить в share-URI.

**Важно:** инструментальный тест из пакета приложения не доказывает, что доступ для чужого UID закрыт. Для этого используйте `scripts/check.sh all` на обычном нерутованном эмуляторе и проверку клиента, подписанного **другим** ключом.
