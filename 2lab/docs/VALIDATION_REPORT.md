> **Исторический отчёт статической проверки:** составлен раньше реального запуска 21.10.2026. На пользовательском Pixel_8 уже был `PASS=18 FAIL=0` и успешный AndroidTest; см. `00_START_HERE.md` и `evidence/USER_SESSION_2026-10-21.md`.

# Отчёт о проверках лабораторной №2 — редакция v2

**Дата:** 21 октября 2026 г. **Входные данные:** исходная ветка starter, предыдущий проект, предоставленный пользователем вывод Android Studio на задаче `:notes:checkDebugAarMetadata`.

## Реально подтверждённое

- В репозитории присутствует исходный Git-коммит `9f3bf6f`; история исходника сохранена, преподавательский `scripts/check.sh` не изменён.
- По выводу пользователя сборка с AGP 8.13.0 / SDK 36 была блокирована **28 требованиями** сторонних AAR к AGP 9.1+ / API 37. Это относится к выбранным версиям библиотек, а не к содержимому Kotlin классов.
- В новой редакции все библиотеки, объявленные в `gradle/libs.versions.toml`, переведены на ветку совместимости с AGP 8.13.0 и SDK 36; добавлен требуемый для AGP 8 Kotlin Android plugin, удалена фиксация Java 25. Подробности и официальные источники: `docs/BUILD_COMPATIBILITY.md`.
- `python scripts/static_audit.py` проверяет входные точки, экспорт, собственное `signature`-разрешение, области FileProvider, явные immutable PendingIntent, Photo Picker, валидацию ID и версии сборочной среды.
- `bash -n scripts/check.sh scripts/collect_evidence.sh` и разбор XML/TOML выполняются локально.
- **После подготовки прошлой редакции пользователь успешно собрал оба модуля на Windows**: `BUILD SUCCESSFUL in 2m 16s` (`75 actionable tasks: 44 executed, 6 from cache, 25 up-to-date`). Этот факт подтверждён его логом, а не сборкой в текущем контейнере.
- В редакции с автоматизацией добавлены `START_LAB2.cmd`, `CHECK_LAB2.cmd`, `scripts/auto_run_windows.ps1`, инструкция `docs/AUTO_WINDOWS.md`. Проведена структурная статическая проверка этих файлов, исходный `scripts/check.sh` совпадает с прежним побайтно.
- В `notes/src/androidTest` находятся дополнительные проверки Deep Link, заголовков и разрешённого каталога FileProvider. Их код подготовлен, **но они не запускались на Android**.

## Неподтверждённое — не выдавать за PASS

- `:notes:connectedDebugAndroidTest` и новый Windows CMD/PowerShell автосценарий;
- все шаги `bash scripts/check.sh all` на нерутованном эмуляторе;
- содержимое **merged manifest** установленного APK;
- фактический отказ shell/стороннего клиента, открытие deep link и отображение уведомления.

**Повторная локальная попытка:** `./gradlew --version` после перехода на `gradle-8.13-bin.zip` останавливается при загрузке с `java.net.UnknownHostException: services.gradle.org`. В среде нет Android SDK/эмулятора. Это сетевая проблема проверки, не отчёт о компиляции.

## Следующая обязательная проверка на ПК пользователя

1. Android Studio, JDK 17, SDK Platform 36, успешный Gradle Sync.
2. `gradlew.bat :notes:assembleDebug :client:assembleDebug`.
3. Установить приложения на Google Play эмулятор API 36.
4. В Git Bash выполнить `bash scripts/check.sh all` и сохранить настоящие `PASS`/`FAIL` через `bash scripts/collect_evidence.sh`.
5. Запустить `gradlew.bat :notes:connectedDebugAndroidTest`.
6. Проверить клиент с debug-ключом и отдельным ключом, Фото, уведомления, экспорт файла. Команды: `docs/TESTING.md`.
7. Вставить фактические `adb`-выводы в README, как прямо требует преподаватель. **Примерные выводы в качестве доказательства не годятся.**

**Бонус HTTPS App Link:** не заявлен как выполненный; нет принадлежащего пользователю подтверждённого HTTPS-домена с `assetlinks.json` и отпечатком debug/release сертификата.

## Автоматизация Windows

Новый `START_LAB2.cmd` не меняет безопасность компонентов. Он выполняет сборку, обнаружение/пуск AVD, ADB установку `--no-streaming`, тестирование и сохраняет реальные логи. В этой среде Windows PowerShell и Android Emulator недоступны, поэтому **не заявляем** успешный запуск автоматизации. Проверяемость: первый запуск в Windows, журнал `logs/auto-.../summary.txt`, а для преподавательского критерия — `docs/evidence/check-all.txt`. Инструкция: `docs/AUTO_WINDOWS.md`.
