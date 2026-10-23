> **Навигация (редакция для защиты 23.10.2026):** этот файл оставлен как дополнительный материал. Последовательный учебный разбор и актуальный статус — в [`00_START_HERE.md`](00_START_HERE.md), [`CODE_GUIDE.md`](CODE_GUIDE.md) и [`TEST_PLAN.md`](TEST_PLAN.md).

# Бонус [+2]: проверяемая HTTPS Android App Link

**Текущий статус:** НЕ ВКЛЮЧЕНА. Нельзя честно заявить `autoVerify` без подтверждённого домена и сертификата. `notes://note/<id>` уже работает как обязательный пункт. Ниже процедура, которая превращает его в HTTPS-вариант после публикации.

## 1. Публичный домен на GitHub Pages

Создайте GitHub Pages репозиторий `<YOUR_USERNAME>.github.io` и включите **Settings → Pages → Deploy from a branch**, ветка `main`, корневая директория `/`. Домен будет `https://<YOUR_USERNAME>.github.io` (подставьте свой аккаунт GitHub).

В корне сайта добавьте файл `.nojekyll` и папку `.well-known/` с файлом `assetlinks.json`, чтобы GitHub Pages не исключил её из публикации.

## 2. SHA-256 настоящего сертификата

Для локальной debug-сборки на своём ПК:

```bash
keytool -list -v -keystore "$HOME/.android/debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

В Windows Git Bash аналогично `"$USERPROFILE/.android/debug.keystore"` (если такой путь существует). Из вывода возьмите точный `SHA256: XX:...:XX`. Для релизного APK берите **релизный** сертификат; debug-отпечаток от другой машины не подойдёт. Никогда не публикуйте приватные ключи.

## 3. assetlinks.json — ОБРАЗЕЦ, не готовый публичный файл

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "edu.androidsec.notes",
      "sha256_cert_fingerprints": ["REPLACE_WITH_REAL_SHA256_FINGERPRINT"]
    }
  }
]
```

Замените fingerprint и опубликуйте по **точному URL** `https://<YOUR_USERNAME>.github.io/.well-known/assetlinks.json`. Через браузер убедитесь, что сервер отдаёт JSON с кодом HTTP 200, а не HTML 404 или redirect. Формат URL должен соответствовать **тому же** домену, что в манифесте.

## 4. Дополнительный intent-filter в MainActivity

После получения реально работающего домена **добавьте ещё один** `<intent-filter>` рядом с существующим `notes://`. Не изменяйте исходный фильтр:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="YOUR_USERNAME.github.io" android:pathPrefix="/note/" />
</intent-filter>
```

В `NoteDeepLink.parse()` добавьте обработку **только этого точного host и https**, сохранив строгий контроль `/note/<id>` и проверку существования заметки. Вариант простой и безопасный:

```kotlin
val isCustom = uri.scheme == SCHEME && uri.host == HOST
val isVerifiedHost = uri.scheme == "https" && uri.host == "YOUR_USERNAME.github.io"
if (!isCustom && !isVerifiedHost) return IncomingNote.Invalid
val path = uri.encodedPath ?: return IncomingNote.Invalid
val idPath = if (isCustom) path else path.removePrefix("/note")
if (!idPath.matches(Regex("/[0-9]+"))) return IncomingNote.Invalid
val id = idPath.substring(1).toLongOrNull() ?: return IncomingNote.Invalid
```

Пример — **фрагмент**, а не готовая замена всей функции: оставьте проверки port, userInfo, query, fragment из основного парсера.

## 5. Переустановка APK и настоящая проверка Android

Соберите Notes с фильтром, затем:

```bash
adb install -r notes/build/outputs/apk/debug/notes-debug.apk
adb shell pm verify-app-links --re-verify edu.androidsec.notes
adb shell pm get-app-links edu.androidsec.notes
adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://YOUR_USERNAME.github.io/note/1'
```

Для бонуса нужны одновременно: **verified** для вашего host в `pm get-app-links`, переход к Notes по HTTPS, правильная заметка №1 и отрицательные кейсы без падения. Сохраните **реальный** вывод в `docs/evidence/`.

Первоисточник: https://developer.android.com/training/app-links/verify-android-applinks
