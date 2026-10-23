// Модуль :client — отдельный APK для демонстрации signature IPC через ContentResolver.
// По умолчанию обе debug-сборки подписываются одной локальной debug.keystore.
// custom signing используется только для ОТРИЦАТЕЛЬНОГО теста с другим сертификатом.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Подпись клиента другим ключом (для проверки signature-разрешения).
// Параметры передаются через -P или через файл client/signing.properties (он в .gitignore):
//   signing.storeFile, signing.storePassword, signing.keyAlias, signing.keyPassword
// Без них клиент подписывается стандартным debug-ключом (~/.android/debug.keystore),
// тем же, которым Android Studio подписывает :notes.
val signingProps = Properties().apply {
    val f = rootProject.file("client/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingProp(name: String): String? =
    (project.findProperty(name) as String?) ?: signingProps.getProperty(name)

val customStoreFile: String? = signingProp("signing.storeFile")

// Фиксируем цель Kotlin независимо от версии JDK, выбранной Android Studio.
// Это должно совпадать с android.compileOptions (Java 17) в обоих модулях.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "edu.androidsec.notes.client"
    compileSdk = 36

    defaultConfig {
        applicationId = "edu.androidsec.notes.client"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (customStoreFile != null) {
            create("custom") {
                storeFile = rootProject.file(customStoreFile)
                storePassword = signingProp("signing.storePassword")
                keyAlias = signingProp("signing.keyAlias")
                keyPassword = signingProp("signing.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (customStoreFile != null) {
                signingConfig = signingConfigs.getByName("custom")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (customStoreFile != null) {
                signingConfigs.getByName("custom")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
}
