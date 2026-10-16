# Шпаргалка

**Аутентификация vs авторизация?** Аутентификация отвечает, кто пользователь; авторизация — какие данные ему доступны.

**Кто проверяет пароль?** Firebase Authentication на сервере. Приложение не хранит пароль в локальной БД, файле или SharedPreferences.

**Зачем Credential Manager?** Это системный API, который сохраняет пароль через доверенного провайдера и предоставляет один диалог для нескольких типов учётных данных.

**Почему Google не вход через обычный пароль?** Credential Manager возвращает Google ID token, который мы передаём в `FirebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))`.

**Что значит Google ID Token?** Подписанное свидетельство об аутентификации Google. Наше приложение его не проверяет вручную: Firebase Auth валидирует токен на стороне сервера.

**Как ограничен экран TODO?** `MainActivity` проверяет `currentUserIdFlow` из Firebase Auth. При `null` доступны только `SignInRoute` и `SignUpRoute`. При выходе навигационный граф пересоздаётся, поэтому Back не возвращает в TODO.

**Защищает ли скрытый экран данные в Firestore?** Нет. Главное ограничение — `firestore.rules`: запрос к `/users/{uid}/todoitems/{id}` разрешён только при `request.auth.uid == uid`. Клиентский экран лишь UX-защита.

**Почему задачи в подпапке users/{uid}?** Изолирует пути данных по пользователю и упрощает проверку на сервере, снижает риск путаницы локального кэша и выборки.

**Можно ли подменить ownerId?** Нет: правила запрещают изменения от чужого UID и проверяют `ownerId == uid` при каждой записи.

**Что происходит после закрытия приложения?** Firebase SDK хранит сессию. Если пользователь вошёл, она восстанавливается, иначе откроется экран входа.

**Что делает signOut?** Удаляет текущую Firebase Auth сессию из SDK, очищает credential provider state и принудительно убирает защищённый навигационный стек. Пароль из системного менеджера не удаляется.

**В чём отличие PasswordCredential от GoogleIdTokenCredential?** Первый предоставляет email и сохранённый пароль для Firebase Email Auth; второй даёт ID-токен Google для Firebase Google Auth.

**Какие файлы главные** `MainActivity.kt`, `security/CredentialAuth.kt`, `data/datasource/AuthRemoteDataSource.kt`, `data/datasource/TodoItemRemoteDataSource.kt`, `firestore.rules`.
