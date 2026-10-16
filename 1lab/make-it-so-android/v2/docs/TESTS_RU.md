# Полная проверка лабораторной: ручные и автоматические тесты

Важно различать **три разные проверки**:

1. **Сборка** — Kotlin/Gradle компилируются, но это не доказывает безопасность.
2. **UI-тесты вручную** — реальные экран входа, Google picker, сохранение пароля, профиль, Back, повторный запуск, смена пользователя.
3. **Тесты Firestore Rules на локальном эмуляторе** — доказывают, что **серверные правила** разрешают владельцу и отказывают другим/анонимам/некорректным документам.

## A. Короткий порядок подготовки на Windows

Сначала проверь файлы:

```powershell
# Терминал Android Studio / PowerShell в каталоге v2
powershell -ExecutionPolicy Bypass -File .\scripts\check-windows.ps1
```

В проекте должен быть `app/google-services.json`, `firestore.rules`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.jar` и `.properties`. Нужны Android Studio, Android SDK 35, JDK 17 и эмулятор с Google Play. `local.properties` Android Studio создаёт самостоятельно.

Проверка сборки:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

**Вторая команда** запускает существующие локальные Android unit tests, но их шаблонный `ExampleUnitTest.kt` **не проверяет Firebase Rules**. Не путай этот тест с основным набором тестов доступа. Если Gradle сообщает об отсутствии SDK, сначала проверь SDK Manager / `local.properties`.

## B. Ручные UI-проверки — все обязательные требования

Используй как минимум два разных тестовых аккаунта, например A (email/password) и B (другой email или Google аккаунт). Не вводи настоящие пароли в отчёты и логи.

| № | Действия | Ожидаемый результат | Где происходит |
|---|---|---|---|
| U01 | Очистить данные приложения, запустить впервые | Только SignIn / SignUp; TODO закрыт | `MainActivity.kt` |
| U02 | Зарегистрировать A | Пользователь A появляется в Firebase Console → Authentication → Users | `SignUpScreen.kt` → `AuthRemoteDataSource.kt` |
| U03 | Подтвердить сохранение пароля | Системный пароль сохраняется у провайдера, Firebase аккаунт уже создан | `CredentialAuth.kt` |
| U04 | Выйти и открыть системный выбор | В одном диалоге доступны подходящие Google/password варианты; конкретный набор зависит от доступных credentials | `CredentialAuth.kt` |
| U05 | Выбрать сохранённый пароль A | Firebase авторизует A, UID тот же | `AuthRemoteDataSource.kt` |
| U06 | Войти через Google | Firebase входит по ID token; в профиле отображается текущий провайдер | `CredentialAuth.kt`, `SettingsViewModel.kt` |
| U07 | Открыть профиль | Видны UID, email, displayName, способ входа, провайдеры | `SettingsScreen.kt` |
| U08 | Создать TODO от имени A | Документ сохраняется в `/users/{UID_A}/todoitems` | `TodoItemViewModel.kt`, Firestore |
| U09 | Выйти из A, войти в B | TODO A не появляются в списке B | `TodoItemRemoteDataSource.kt` |
| U10 | В Firestore Rules Playground выполнить GET чужой задачи от B | DENY на сервере | `firestore.rules` |
| U11 | Сымитировать GET собственной задачи от A | ALLOW при корректном токене пользователя A | `firestore.rules` |
| U12 | При открытом TODO выйти и нажать Android Back | TODO и профиль не восстанавливаются | `MainActivity.kt` |
| U13 | Перезапустить приложение после выхода | Только экран входа | `MainActivity.kt` |
| U14 | Войти снова и перезапустить без выхода | Firebase сохраняет сессию; TODO снова доступен | Firebase Auth, `MainActivity.kt` |

Если Credential Manager не показывает одновременно пароль и Google — проверить сохранённые credentials, Play Services, Google аккаунт, версию провайдера. Не считать отсутствие одного варианта доказательством, что в коде два отдельных запроса: код содержит один `GetCredentialRequest`.

Если UID A и B совпали, ты **не** получил два независимых аккаунта; проверь провайдеры / возможное связывание аккаунтов.

## C. Автоматические тесты Firestore Security Rules

### Что установить

- Node.js LTS + npm (https://nodejs.org/en/download)
- Java JDK (для Firebase Emulator; совместимую версию подскажет текущая Firebase CLI)
- Firebase CLI (https://firebase.google.com/docs/cli)

**Не нужно** ставить npm/CLI для обычного запуска Android-приложения. Эти инструменты нужны только для отдельных автоматических тестов правил.

### Команды (PowerShell из `v2`)

```powershell
node --version
npm --version
java -version
npm install -g firebase-tools
firebase --version
npm --prefix security-tests install
firebase emulators:exec --project demo-makeitso-security-lab --only firestore 'npm --prefix security-tests test'
```

При первом запуске CLI может запросить согласие на скачивание файлов эмулятора. `demo-makeitso-security-lab` — локальный демонстрационный проект; подключать продуктивные учётные данные для этих тестов не нужно.

**Команда с `emulators:exec` автоматически запускает локальный Firestore Emulator, выполняет скрипт тестов и завершает эмулятор.** Запускай её из корня `v2`, чтобы прочитался `firebase.json`.

При успешном результате Node test runner выведет 21 пройденный тест, без проваленных. При отказах `assertFails` — это **успех теста**: мы специально доказываем, что сервер запретил атаку. Если тест «Alice can read» упал, это регрессия, так как законное действие должно пройти.

### Что проверяется (файл `security-tests/firestore.test.cjs`)

| Тест | Ожидаемый результат | Что доказывает |
|---|---|---|
| 01 | ALLOW | Alice читает свою задачу |
| 02 | ALLOW | Alice получает свой список задач |
| 03 | ALLOW | Alice создаёт допустимый документ |
| 04 | ALLOW | Alice меняет completed, не меняя владельца |
| 05 | ALLOW | Alice удаляет свою задачу |
| 06 | DENY | Bob не читает документ Alice |
| 07 | DENY | Bob не перечисляет задачи Alice |
| 08 | DENY | Bob не создаёт задачу в чужом UID |
| 09 | DENY | Bob не редактирует задачу Alice |
| 10 | DENY | Bob не удаляет задачу Alice |
| 11 | DENY | Без Firebase-сессии нельзя читать или писать |
| 12 | DENY | Анонимный Firebase provider не считается разрешённым |
| 13 | DENY | Владелец не может изменить `ownerId` существующей задачи |
| 14 | DENY | Владелец не может создать документ с чужим `ownerId` |
| 15 | DENY | Нельзя добавить неизвестное поле `isAdmin` |
| 16 | DENY | Нельзя пропустить обязательное поле `completed` |
| 17 | DENY | Нельзя записать пустой заголовок |
| 18 | DENY | Нельзя записать заголовок длиннее 200 символов |
| 19 | DENY | Нельзя записать priority вне 0..3 |
| 20 | DENY | Нельзя записать completed строкой вместо bool |
| 21 | DENY | Старая коллекция `todolist` недоступна |

**Как работают тесты:** `before()` поднимает окружение; `beforeEach()` очищает локальную Firestore и наполняет её документом Alice с отключёнными правилами **только в контексте подготовки данных**. Далее `authenticatedContext('bob')` эмулирует токен Bob, а `assertFails(getDoc(...))` доказывает отказ по правилам. Это НЕ взлом Firebase и НЕ запуск действий от имени настоящего Google аккаунта.

### Где искать ошибку теста

- `ECONNREFUSED`: локальный эмулятор не запущен / занят порт / CLI не нашёл окружение.
- `permission-denied` у ALLOW теста: rules слишком строгие или данные не соответствуют `validItem()`.
- DENY тест неожиданно успешен: это потенциальная **дыра в правилах**, проверь `request.auth.uid == uid`, публикацию и `match`.
- `Cannot find module '@firebase/rules-unit-testing'`: в `security-tests` не установлены npm зависимости.

## D. Отдельно: проверка серверной защиты в Firebase Console

Открой Firestore Rules Playground (если он доступен в твоей версии консоли) и смоделируй GET `/users/UID_A/todoitems/ID_ЗАДАЧИ` от имени UID_B. Должен быть **DENY**. Важно: в реальной базе должна быть опубликована та же версия `firestore.rules`, что в локальном проекте. Эмуляторные тесты проверяют **локальную версию файла** и сами по себе не публикуют её в облако.
