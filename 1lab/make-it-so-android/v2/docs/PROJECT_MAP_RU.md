# Карта проекта: что где находится и что мной сделано

## 1. Что получено от FirebaseExtended, что добавлено
### Новые для лабораторной файлы (отсутствуют в исходном v2)

| Файл | Назначение |
|---|---|
| `security/CredentialAuth.kt` | Credential Manager: сохранить пароль, единый выбор Password/Google, Google ID token, сброс состояния провайдера при выходе |
| `firestore.rules` | Правила Firestore: только собственный UID, проверка полей, запрет анонимного доступа |
| `firebase.json` | Конфигурация локального эмулятора Firebase для автотестов |
| `security-tests/firestore.test.cjs` | 21 тест разрешённых/запрещённых запросов к Firestore Rules |
| `security-tests/package.json` | Node.js зависимости тестов |
| `scripts/check-windows.ps1` | Диагностика наличия файлов/Java и Firebase package, не меняет среду |

`app/google-services.json` создан **Firebase Console**. Это локальная конфигурация приложения, сохраняемая в проекте и игнорируемая Git. Не путать с ключом сервисного аккаунта Firebase Admin SDK.

### Изменённые файлы исходного v2 (функциональность лабораторной)

| Файл | Что изменялось при реализации безопасности |
|---|---|
| `MainActivity.kt` | Вместо автоматического допуска анонимной сессии: разные NavHost для гостя и вошедшего; регистрация, Credential Manager, выход |
| `data/datasource/AuthRemoteDataSource.kt` | Firebase Auth Email/Password и Google credential, поток UID, signOut, отказ анонимным пользователям |
| `data/repository/AuthRepository.kt` | Методы авторизации, делегирование Firebase datasource |
| `data/datasource/TodoItemRemoteDataSource.kt` | Перенос задач в `/users/{uid}/todoitems`, методы чтения и записи только на собственном пути |
| `ui/signin/SignInScreen.kt` | Открытая форма входа и системная кнопка Credential Manager |
| `ui/signup/SignUpScreen.kt` | Регистрация с подтверждением пароля |
| `ui/settings/SettingsScreen.kt` | Профиль, провайдеры входа, кнопка выхода |
| `ui/settings/SettingsViewModel.kt` | Чтение FirebaseUser и способа входа по ID-токену |
| `ui/todolist/TodoListScreen.kt` | Экран списка задач с текущим потоком данных |
| `ui/todolist/TodoListViewModel.kt` | Подписка на задачи по UID текущей сессии |
| `app/build.gradle.kts` | Credential Manager / Google ID / Firebase зависимости и плагины |
| `app/src/main/AndroidManifest.xml` | Настройки приложения (Hilt, INTERNET, backup) |

Ранее существовавшие исходные `ui/signin/SignInViewModel.kt` и `ui/signup/SignUpViewModel.kt` **удалены при реализации лабораторной**: их функции входа/регистрации теперь выполняются через MainActivity и AuthRepository.

### Существовавшие файлы оригинального проекта, которые теперь снабжены объяснением

| Файл | Ответственность |
|---|---|
| `MainViewModel.kt` | Обработка исключений из корутин и показ ошибок |
| `data/model/TodoItem.kt` | Структура TODO-документа с `ownerId` |
| `data/model/Priority.kt` | Допустимые числовые приоритеты 0–3 |
| `data/model/ErrorMessage.kt` | Варианты ошибок для Snackbar |
| `data/model/TodoList.kt`, `data/model/User.kt` | Старые модели будущих функций оригинала; не участвуют в текущем Firebase Auth |
| `data/repository/TodoItemRepository.kt` | Посредник между TodoItemViewModel и Firestore datasource |
| `ui/todoitem/TodoItemViewModel.kt` | Подставляет UID в новые задачи, загружает/изменяет/удаляет их |
| `ui/todoitem/TodoItemScreen.kt` | Форма редактирования задачи; не должна решать вопросы прав доступа |
| `ui/shared/*` | Общие Compose-кнопки, переключатели, панель, индикатор загрузки |
| `ui/theme/*` | Тема, цвета, шрифты (не безопасность) |
| `data/injection/FirebaseHiltModule.kt` | Даёт FirebaseAuth и FirebaseFirestore через Hilt |
| `data/injection/MakeItSoHiltApp.kt` | Инициализация Hilt Application |
| `ui/home/HomeScreen.kt` и `HomeViewModel.kt` | Неиспользуемый демонстрационный Home экран шаблона |
| `data/datasource/TodoListRemoteDataSource.kt`, `data/repository/TodoListRepository.kt` | Неиспользуемая заготовка «несколько списков»; обращается к коллекции `todolist`, закрытой серверными правилами |

## 2. Поток запроса: от нажатия до Firebase

**Регистрация:** `SignUpScreen` (поля ввода) → callback `MainActivity` (проверка формата) → `AuthRepository.signUp()` → `AuthRemoteDataSource.signUp()` → `FirebaseAuth.createUserWithEmailAndPassword()` → Firebase сервер. Если успешно, `MainActivity` вызывает `CredentialAuth.offerToSavePassword()` → системный Credential Manager.

**Выбор системной учётной записи:** `SignInScreen` (кнопка) → `MainActivity` → `CredentialAuth.signInFromSystemPicker()` → единый `GetCredentialRequest` с `GetPasswordOption` и `GetGoogleIdOption`. PasswordCredential → Firebase email/password. Google ID token → `GoogleAuthProvider.getCredential()` → `FirebaseAuth.signInWithCredential()`.

**Открытие защищённого экрана:** FirebaseAuth AuthStateListener → `AuthRemoteDataSource.currentUserIdFlow` → `MainActivity` → `uid != null` → новый NavHost только с `TodoListRoute`, `TodoItemRoute`, `SettingsRoute`.

**Чтение задач:** `TodoListScreen` → `TodoListViewModel.todoItems` → `TodoItemRepository.getTodoItems()` → `TodoItemRemoteDataSource.getTodoItems()` → Firestore `/users/{uid}/todoitems` → `firestore.rules` (на сервере). Ответ возвращается в Flow, а потом в Compose UI.

**Сохранение задачи:** `TodoItemScreen` → `TodoItemViewModel.saveItem()` (для новой: `ownerId = currentUser.uid`) → `TodoItemRepository` → `TodoItemRemoteDataSource` → Firestore сервер → правила `owner(uid)` и `validItem(uid)` → запись или `PERMISSION_DENIED`.

**Выход:** `SettingsScreen` → `MainActivity.onSignOut` → `AuthRepository.signOut()` → `FirebaseAuth.signOut()` → поток UID становится null → `key(uid)` пересоздаёт NavHost. Потом `CredentialAuth.clearAfterSignOut()` сбрасывает состояние системного выбора, но НЕ стирает сохранённый пароль.


## 3. Что знать в первую очередь

1. `firestore.rules` — основной серверный барьер от чужих задач.
2. `security/CredentialAuth.kt` — единый Credential Manager, Google токен.
3. `data/datasource/AuthRemoteDataSource.kt` — Firebase, AuthStateListener и signOut.
4. `MainActivity.kt` — закрытый навигационный граф и очистка Back stack.
5. `data/datasource/TodoItemRemoteDataSource.kt` — путь текущего UID.
6. `ui/todoitem/TodoItemViewModel.kt` — кто устанавливает ownerId.
7. `security-tests/firestore.test.cjs` — что доказывает защиту на сервере.

«Интерфейс скрывает TODO до входа, но это НЕ основной контроль безопасности: подделать клиент можно. Сервер проверяет токен пользователя и правила доступа для каждого Firestore-запроса».
