> **Навигация (редакция для защиты 23.10.2026):** этот файл оставлен как дополнительный материал. Последовательный учебный разбор и актуальный статус — в [`00_START_HERE.md`](00_START_HERE.md), [`CODE_GUIDE.md`](CODE_GUIDE.md) и [`TEST_PLAN.md`](TEST_PLAN.md).

# Security review — лабораторная №2

Это сопоставление изменений со **спецификацией задания**, комментариями `TODO(ipc-*)` в ветке `starter` и платформенными возможностями Android. Это не утверждение о независимом аудите или фактически пройденном испытании на устройстве.

| Требование | Реализация | Зачем | Как проверить |
|---|---|---|---|
| ipc-1 Манифест | POST_NOTIFICATIONS; `exported` у компонентов; скрытые receiver/FileProvider; public MainActivity + permission-protected TitlesProvider; удалён библиотечный ProfileInstallReceiver | уменьшение доступных извне точек входа | `bash scripts/check.sh manifest`, merged manifest |
| ipc-2 Разрешения | `ReminderPermission` объясняет причину и спрашивает разрешение в момент действия; после 2 отказов предлагает Settings; `PhotoPicker` использует `PickVisualMedia` | принцип минимальных привилегий | ручной тест API 33+, манифест |
| ipc-3 Ссылки | строгая `NoteDeepLink.parse`; ViewModel проверяет запись в Room, `notFound` не показывает редактор | чужой VIEW не создаст фиктивную заметку | 4 `am start`, UI и logcat |
| ipc-4 Уведомление | два explicit immutable PendingIntent, закрытый Receiver, проверка action/id | ограничение подмены и внешних broadcast | `check.sh closed` + кнопка «Готово» |
| ipc-5 TXT | cacheDir/exports, `FileProvider`, content URI, read grant | не передавать `file://` и не раскрывать весь cacheDir | `content read` от shell отклонён, chooser работает |
| ipc-6 Заголовки | `signature` permission, `android:permission` на экспортированном provider, `UriMatcher`, `MatrixCursor`, исключения при записи | доступ только тому же сертификату и только на чтение | `check.sh provider`, два варианта клиента |

## Почему `android:permission`, а не только `readPermission`

`android:readPermission` само по себе не закрыло бы `insert`/`delete`. В этом проекте применено `android:permission=edu.androidsec.notes.permission.READ_NOTES`: одинаковое требование для чтения **и** записи, плюс методы изменения данных выбрасывают `UnsupportedOperationException` даже для доверенного клиента. `scripts/check.sh` поддерживает именно такой вариант.

## Почему `exported=true` не всегда уязвимость

Public MainActivity должна принимать внешние VIEW/LAUNCHER; public TitlesProvider должен обслуживать второй applicationId. Но у TitlesProvider реальную проверку делает **ОС** по сертификату. FileProvider и Receiver не нуждаются в экспортировании: доступ выдаётся через временный URI-grant или immutable PendingIntent.

## Почему `FLAG_IMMUTABLE` совместим с `FLAG_UPDATE_CURRENT`

`IMMUTABLE` запрещает стороннему получателю изменять extras/компонент внутри PendingIntent, но не запрещает **автору** обновлять ранее созданный PendingIntent с `UPDATE_CURRENT`. В Intents жёстко указаны наши классы, requestCode различается по id.

## Почему нет дополнительных разрешений

Фото выбирается через системный сервис. TXT пишется в `cacheDir` без доступа к внешнему хранилищу. Room живёт в private storage. Клиенту не нужен INTERNET. Единственная runtime-потребность Notes — `POST_NOTIFICATIONS` на API 33+.

## Остаточные ограничения

1. **Автоматический `check.sh` и merged manifest:** требуют реально установленного APK. AndroidX-зависимости могут добавить компоненты, и их `exported` нужно проверить **после слияния**, а не по одному исходному XML.
2. **Напоминания:** задержка привязана к процессу; остановка приложения отменяет ожидающие уведомления. В условиях работы допустимо.
3. **Signature permission:** даёт доступ любому приложению, подписанному тем же сертификатом. Поэтому ключ подписи — граница доверия; утечка ключа критична.
4. **Файловый URI:** временный grant разрешает чтение конкретного файла получателем, но не защищает файл **от самого получателя**. Нужно выбирать доверенное приложение для отправки приватной заметки.
5. **Несуществующие ссылки:** синтаксически корректная ссылка на id, которого нет, открывает экран ошибки «Заметка не найдена», а не редактор. Внешне она не запрещена на уровне intent-filter — так требует deep link.
6. **Подписи релизов:** обе debug-сборки на одном компьютере обычно используют один ключ. Если собрать на разных машинах, ключи могут отличаться; тогда даже легитимный клиент получит отказ.
7. **Бонус HTTPS:** не подключён без контролируемого публичного домена и реального assetlinks.json.

## Полезные первоисточники

- Android Photo Picker: https://developer.android.com/training/data-storage/shared/photo-picker
- Android FileProvider: https://developer.android.com/reference/androidx/core/content/FileProvider
- Android Permissions security: https://developer.android.com/agents/skills/security/android-permissions-security/skill
- Android manifest `<provider>`: https://developer.android.com/guide/topics/manifest/provider-element
- Android PendingIntent: https://developer.android.com/reference/android/app/PendingIntent
- Android App Links: https://developer.android.com/training/app-links/verify-android-applinks
- Исходный проект преподавателя: https://github.com/vaimon/lab2-notes/tree/starter
