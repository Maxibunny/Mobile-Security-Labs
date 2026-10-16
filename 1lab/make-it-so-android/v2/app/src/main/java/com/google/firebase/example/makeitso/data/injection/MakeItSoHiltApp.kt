package com.google.firebase.example.makeitso.data.injection

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Application-класс для инициализации Hilt.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: AndroidManifest.xml указывает этот класс в android:name; без него @AndroidEntryPoint и @HiltViewModel не получат зависимости.
// ===========================================================================

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MakeItSoHiltApp: Application() {}