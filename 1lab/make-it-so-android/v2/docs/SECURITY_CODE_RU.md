# Главные строки безопасности

Путь Kotlin-файлов: `app/src/main/java/com/google/firebase/example/makeitso/`.

## 1. Чем проверяется, вошёл ли пользователь?

Файл: `data/datasource/AuthRemoteDataSource.kt`:

```kotlin
val currentUser: FirebaseUser? get() = auth.currentUser?.takeUnless { it.isAnonymous }
```

`FirebaseAuth.currentUser` — пользователь из Firebase сессии. `?.takeUnless { it.isAnonymous }` возвращает null, если аккаунт анонимный: лабораторная требует явную авторизацию. Это локальная проверка состояния, не серверный доступ.

```kotlin
val currentUserIdFlow: Flow<String?> get() = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { service ->
        trySend(service.currentUser?.takeUnless { it.isAnonymous }?.uid)
    }
    auth.addAuthStateListener(listener)
    awaitClose { auth.removeAuthStateListener(listener) }
}
```

`AuthStateListener` получает изменения сессии (вход/выход), `trySend()` передаёт UID в Flow, `awaitClose()` отписывает слушатель. Это связывает изменение статуса входа с Compose UI. `null` означает «нет допущенного пользователя».

## 2. Почему Back не возвращает в TODO?

Файл: `MainActivity.kt`:

```kotlin
key(uid) {
    val nav = rememberNavController()
    if (uid == null) {
        NavHost(navController = nav, startDestination = SignInRoute, ...)
    } else {
        NavHost(navController = nav, startDestination = TodoListRoute, ...)
    }
}
```

`key(uid)` заставляет Compose пересоздать вложенное состояние при смене UID, в том числе `NavController` и его стек. После `FirebaseAuth.signOut()` UID меняется на `null`: список задач отсутствует в гостевом графе и предыдущий Back stack не используется. Это **защита навигации**, а не защита базы.

## 3. Где настоящий вход Email/Password?

Файл: `data/datasource/AuthRemoteDataSource.kt`:

```kotlin
auth.createUserWithEmailAndPassword(email, password).await()
auth.signInWithEmailAndPassword(email, password).await()
```

Первый вызов создаёт аккаунт, второй выполняет вход. Проверку учётных данных выполняет Firebase сервер. `await()` приостанавливает корутину до завершения операции и выбрасывает ошибку при неуспехе. В приложении нет собственного хранилища паролей.

## 4. Где пароль сохраняется в менеджер Android?

Файл: `security/CredentialAuth.kt`:

```kotlin
manager.createCredential(
    context = activity,
    request = CreatePasswordRequest(id = email, password = password)
)
```

После успешной регистрации вызов передаёт email/password системному провайдеру учётных данных. Провайдер решает, покажет ли запрос сохранения и где будет хранить запись. Это НЕ запись в `SharedPreferences` или Firestore. Пользователь может отказаться — Firebase регистрация от этого не отменяется.

Пароль временно остаётся в параметре функции, пока выполняется код; утверждать, что «пароль вообще никогда не попадает в память приложения», было бы неверно. Правильно: **приложение не сохраняет его в собственное долговременное хранилище**.

## 5. Почему Google и пароль — один системный диалог?

```kotlin
val request = GetCredentialRequest.Builder()
    .addCredentialOption(GetPasswordOption())
    .addCredentialOption(googleOption)
    .build()
val response = manager.getCredential(context = activity, request = request)
```

Два метода включены в ОДИН `GetCredentialRequest`: Android Credential Manager сам показывает подходящие доступные учётные записи. Если какой-то тип недоступен, он может отсутствовать в конкретном диалоге — это зависит от провайдера, настроек и сохранённых credentials.

```kotlin
.setServerClientId(activity.getString(R.string.default_web_client_id))
```

Google ID token должен быть выпущен для **Web OAuth client ID** Firebase-проекта. Ресурс `default_web_client_id` создаётся из `google-services.json` плагином Google Services. Не вставлять сюда Android OAuth client ID.

## 6. Как Google ID token попадает в Firebase?

Файл: `CredentialAuth.kt`:

```kotlin
val google = GoogleIdTokenCredential.createFrom(credential.data)
auth.signInWithGoogleIdToken(google.idToken)
```

Google возвращает удостоверение личности, а не Firebase session. В `AuthRemoteDataSource.kt`:

```kotlin
val credential = GoogleAuthProvider.getCredential(idToken, null)
auth.signInWithCredential(credential).await()
```

Этот вызов обменивает Google удостоверение на Firebase аутентифицированную сессию. Не надо вручную «доверять» JSON декодированного ID token без серверной проверки.

## 7. Почему задачи изолированы?

Файл: `data/datasource/TodoItemRemoteDataSource.kt`:

```kotlin
private fun itemsFor(uid: String): CollectionReference =
    firestore.collection("users").document(uid).collection("todoitems")
```

Каждому UID соответствует отдельный путь. При получении собственных задач ещё проверяется `auth.currentUser != null` и `!isAnonymous`. Подписка через `flatMapLatest` переключает путь при смене UID.

**Внимание:** знание чужого UID само по себе не даёт доступ. Но путь — не гарантия: поддельный APK может передать любую строку `uid`. Гарантию даёт правило на сервере.

## 8. Самое важное правило Firestore — владелец

Файл: `firestore.rules`:

```javascript
function signedIn() {
  return request.auth != null
    && request.auth.token.firebase.sign_in_provider != 'anonymous';
}
function owner(uid) { return signedIn() && request.auth.uid == uid; }
```

`request.auth` выдаётся серверу Firestore после проверки Firebase Auth токена. `request.auth.uid == uid` сопоставляет UID **из токена** с UID **из пути документа**. Злоумышленник не может заставить сервер считать произвольный UID своим просто поменяв параметр URL.

```javascript
match /users/{userId}/todoitems/{itemId} {
  allow get, list: if owner(userId);
  allow create: if owner(userId) && validItem(userId);
  allow update: if owner(userId)
                && resource.data.ownerId == userId
                && validItem(userId);
  allow delete: if owner(userId) && resource.data.ownerId == userId;
}
```

`get` — чтение одного документа; `list` — запрос списка; `create` — новая задача; `update` — изменение; `delete` — удаление. Для всех обязателен правильный пользователь.

## 9. Почему недостаточно просто проверять UID?

В `validItem(uid)`:

```javascript
request.resource.data.keys().hasOnly(['title','priority','completed','flagged','ownerId'])
request.resource.data.keys().hasAll(['title','priority','completed','flagged','ownerId'])
request.resource.data.ownerId == uid
```

`hasOnly` запрещает неожиданные поля (`isAdmin`), `hasAll` требует обязательные поля, `ownerId == uid` исключает подмену владельца. Также проверяются `title` (не пусто, максимум 200), `priority` (целое 0–3), два boolean поля.

При `create`/`update` `request.resource.data` — документ в состоянии **после** планируемой записи. При `update` `resource.data` — **до** записи. Поэтому можно контролировать как новый, так и прежний ownerId.

```javascript
match /{document=**} { allow read, write: if false; }
```

Все не перечисленные явно пути остаются закрытыми. Правила Firestore объединяют разрешения по логике OR между совпадающими match; поэтому это правило **не отменило бы** другие `allow ... if true`. Здесь иных открытых разрешений нет.

## 10. Кто выставляет ownerId при создании задачи?

Файл: `ui/todoitem/TodoItemViewModel.kt`:

```kotlin
val ownerId = authRepository.currentUser?.uid
...
todoItemRepository.create(item.copy(ownerId = ownerId))
```

UID берётся из Firebase Auth, а не из поля ввода. Если пользователь модифицирует приложение и пришлёт иной UID, сервер всё равно проверит `request.auth.uid` и `ownerId`.

## 11. Что значит выход из аккаунта?

В `MainActivity.kt` сначала вызывается:

```kotlin
authRepository.signOut()
```

Это вызывает `FirebaseAuth.signOut()` и меняет `currentUserIdFlow`. Затем:

```kotlin
credentialAuth.clearAfterSignOut()
```

Credential Manager сбрасывает состояние выбора учётных данных у провайдера; **сохранённый системный пароль не удаляется**. Sign-out Firebase означает завершение локального входа в приложение; это НЕ глобальный отзыв всех токенов на всех устройствах. Серверный отзыв refresh-токенов требует отдельных административных механизмов.

## 12. Что делает профиль?

Файл: `ui/settings/SettingsViewModel.kt`:

```kotlin
val currentMethod = user.getIdToken(false).await().signInProvider
```

`signInProvider` — способ, которым установлена Firebase сессия (`password`, `google.com`). `user.providerData` — связанные способы входа; несколько связанных способов **не означают**, что человек вошёл всеми одновременно.


## 13. Типовые вопросы

**«Чем отличается аутентификация от авторизации?»** Аутентификация доказывает личность (Firebase Auth); авторизация проверяет право читать/писать конкретный документ (Firestore Rules).

**«Если взломщик откроет TODO экран в модифицированном APK?»** Он может подделать UI, но сервер откажет чужому UID по `owner(uid)`. Потому проверка должна быть серверной.

**«Где хранится пароль?»** В Firebase для аутентификации на серверной стороне, а сохранённая пользователем копия — у выбранного системного провайдера Credential Manager. В собственную БД приложения не пишется.

**«Почему нельзя `allow read, write: if true`?»** Тогда любой клиент, знающий путь, получит доступ.

**«Зачем тесты Bob → Alice?»** Это отрицательные тесты доступа: подтверждают, что именно сервер блокирует чужие данные, а не только фильтрует отображение.

**«Всё ли гарантированно безопасно?»** Нет: вся система зависит от настройки Firebase проекта, публикации точных правил и защиты устройств/учётных записей. Для производственной системы нужны дополнительные меры (например, политика паролей, обработка ошибок без раскрытия деталей, мониторинг, проверка правил в CI).