> **Уточнение 23.10.2026:** в `notes/build.gradle.kts` и `client/build.gradle.kts` теперь явно установлен `kotlin.compilerOptions.jvmTarget = JvmTarget.JVM_17`, так как локальный Gradle JDK мог использовать 21 и приводить к конфликту Java 17 / Kotlin 21. Последний предоставленный журнал пользователя подтвердил успешную сборку и запуск исходной функциональной версии; после внесения только комментариев Gradle из среды подготовки ZIP не запускался.

# Совместимость Android Studio: исправление 28 ошибок AAR Metadata

Дата: 21.10.2026. Редакция v2 (после пользовательского лога `:notes:checkDebugAarMetadata`).

## Что было не так

Исходный starter/предыдущий ZIP использовали AGP 9.4.1 и compileSdk 37. Android Studio пользователя поддерживает не выше AGP 8.13.0, поэтому AGP был понижен до 8.13.0, а compileSdk — до 36. Однако новые зависимости остались в каталоге `gradle/libs.versions.toml`:

- `navigation-compose:2.10.2`, `core-ktx:1.19.1`, Lifecycle 2.11.0, Compose 1.12.1 (через BOM 2026.09.00);
- их AAR Metadata потребовали **AGP >= 9.1 и API >= 37**;
- пользовательский лог фиксирует 28 ошибок и завершение на `:notes:checkDebugAarMetadata`.

**Нельзя исправить** это включением `android.suppressUnsupportedCompileSdk`, отключением `checkDebugAarMetadata` или одной заменой `compileSdk` — так теряется гарантия совместимости библиотек.

## Принятый согласованный набор

| Компонент | v2 |
|---|---|
| Android Gradle Plugin | 8.13.0 |
| Gradle Wrapper | 8.13 |
| Gradle JDK | 17 (в Gradle settings) |
| Kotlin Gradle / Compose / Serialization plugins | 2.2.21 |
| Kotlin Android plugin | 2.2.21, подключён **явно** в обоих модулях |
| KSP (Room) | 2.2.21-2.0.4 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Compose BOM | 2025.10.00 |
| Navigation Compose | 2.9.5 |
| Lifecycle | 2.9.4 |
| Activity Compose | 1.11.0 |
| Core KTX | 1.17.0 |
| Room | 2.8.4 |
| Coroutines | 1.10.2 |

Эти версии опубликованы и относятся к более ранней ветке AndroidX без заявленного требования API 37 / AGP 9.1, которое вызвало текущую ошибку. Совместимость оценивается на основе release notes, а окончательно подтверждается **только Gradle сборкой** в среде с SDK и зависимостями.

Источники для перепроверки:
- AGP 8.13, Gradle >=8.13 и JDK >=17: https://developer.android.com/build/releases/agp-8-13-0-release-notes
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- Core KTX 1.17.0: https://developer.android.com/jetpack/androidx/releases/core
- Navigation 2.9.5: https://developer.android.com/jetpack/androidx/releases/navigation
- Lifecycle 2.9.4: https://developer.android.com/jetpack/androidx/releases/lifecycle
- Room 2.8.x: https://developer.android.com/jetpack/androidx/releases/room
- KSP 2.2.21-2.0.4: https://github.com/google/ksp/releases/tag/2.2.21-2.0.4

## Что изменено в репозитории

1. `gradle/libs.versions.toml` — версии AGP, Kotlin, KSP, AndroidX согласованы.
2. `build.gradle.kts`, `notes/build.gradle.kts`, `client/build.gradle.kts` — явное применение `org.jetbrains.kotlin.android` (в AGP 8 нет встроенной поддержки Kotlin, в отличие от AGP 9).
3. `notes/build.gradle.kts`, `client/build.gradle.kts` — compileSdk/targetSdk 36.
4. `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.13.
5. Удалён `gradle/gradle-daemon-jvm.properties`, требовавший Java 25.
6. В README и руководствах изменены требования к сборке.
7. Добавлены инструментальные security-тесты для URI, ContentProvider и области FileProvider.

## Запуск на Windows

Android Studio → **File → Settings → Build, Execution, Deployment → Build Tools → Gradle** → Gradle JDK **17**. SDK Manager → установить **Android SDK Platform 36** и **Android SDK Command-line Tools**. Открывать корневую папку с `settings.gradle.kts`.

```powershell
.\gradlew.bat --stop
.\gradlew.bat :notes:assembleDebug :client:assembleDebug --stacktrace
```

Установить `notes` и `client` на эмулятор Google Play API 36, затем в Git Bash:

```bash
bash scripts/check.sh all
bash scripts/collect_evidence.sh
```

При успешной сборке, но ошибках компиляции, отправьте **первую ошибку выше `BUILD FAILED`** — длинный stacktrace ниже обычно вторичен.

## Ограничения проверки

В среде подготовки нового ZIP отсутствует Android SDK, нет эмулятора и DNS-доступа к `services.gradle.org`. Повторная попытка `./gradlew --version` с обновлённым Gradle 8.13 закончилась **UnknownHostException** ещё до конфигурации проекта. **Позднее пользователь прислал реальный результат сборки двух модулей на Windows:** `BUILD SUCCESSFUL in 2m 16s`, `75 actionable tasks: 44 executed, 6 from cache, 25 up-to-date` (21.10.2026). Это подтверждает сборку его копии проекта с согласованными версиями. Скрипт `check.sh all`, работа на эмуляторе и merged manifest остаются неподтверждёнными до фактического тестирования.
