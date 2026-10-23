// Корневой Gradle-файл: объявления плагинов, реальные настройки — в notes/ и client/.
// Корневой файл сборки: плагины объявлены здесь, применяются в модулях.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
