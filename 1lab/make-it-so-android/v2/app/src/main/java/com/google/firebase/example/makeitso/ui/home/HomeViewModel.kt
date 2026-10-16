package com.google.firebase.example.makeitso.ui.home

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: ViewModel устаревшего HomeScreen.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: Не участвует в цепочке авторизации и TODO; состояние загрузки является демонстрационным.
// ===========================================================================

import com.google.firebase.example.makeitso.MainViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : MainViewModel() {
    private val _isLoadingUser = MutableStateFlow(true)
    val isLoadingUser: StateFlow<Boolean>
        get() = _isLoadingUser.asStateFlow()
}