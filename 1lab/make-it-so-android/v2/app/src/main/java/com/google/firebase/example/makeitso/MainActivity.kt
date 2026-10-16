package com.google.firebase.example.makeitso

// ===========================================================================
// НАЗНАЧЕНИЕ ФАЙЛА: Главная Activity: создаёт Compose UI и выбирает допустимый навигационный граф по Firebase UID.
// ПОТОК ДАННЫХ / БЕЗОПАСНОСТЬ: Точка входа: AndroidManifest.xml → MainActivity.onCreate(). При отсутствии UID создаётся ТОЛЬКО SignIn/SignUp. Защита на клиенте не заменяет серверные firestore.rules.
// ===========================================================================

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.example.makeitso.data.repository.AuthRepository
import com.google.firebase.example.makeitso.security.CredentialAuth
import com.google.firebase.example.makeitso.ui.settings.SettingsRoute
import com.google.firebase.example.makeitso.ui.settings.SettingsScreen
import com.google.firebase.example.makeitso.ui.signin.SignInRoute
import com.google.firebase.example.makeitso.ui.signin.SignInScreen
import com.google.firebase.example.makeitso.ui.signup.SignUpRoute
import com.google.firebase.example.makeitso.ui.signup.SignUpScreen
import com.google.firebase.example.makeitso.ui.signup.isValidEmail
import com.google.firebase.example.makeitso.ui.signup.isValidPassword
import com.google.firebase.example.makeitso.ui.theme.MakeItSoTheme
import com.google.firebase.example.makeitso.ui.todoitem.TodoItemRoute
import com.google.firebase.example.makeitso.ui.todoitem.TodoItemScreen
import com.google.firebase.example.makeitso.ui.todolist.TodoListRoute
import com.google.firebase.example.makeitso.ui.todolist.TodoListScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialCancellationException
import javax.inject.Inject

/**
 * Корневой охранник маршрутов: при null UID создаётся только граф авторизации.
 * При изменении UID NavHost пересоздаётся, поэтому Back не возвращает к TODO.
 * Дополнительная, ГЛАВНАЯ проверка доступа выполняется на сервере в firestore.rules.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authRepository: AuthRepository
    private val credentialAuth by lazy { CredentialAuth(this, authRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        setContent {
            val snackbar = remember { SnackbarHostState() }
            val authChanges = remember { authRepository.currentUserIdFlow }
// Источник правды для UI — поток AuthStateListener, а не флаг isLoggedIn в памяти.
            val uid = authChanges.collectAsStateWithLifecycle(
                initialValue = authRepository.currentUser?.uid
            ).value

            MakeItSoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                        // Важно: отдельные навигационные графы. Сторона аутентификации
                        // физически не содержит маршрутов TODO / Profile.
// КЛЮЧЕВАЯ СТРОКА №1: при смене UID удаляем старый NavController и его Back stack.
                        key(uid) {
                            val nav = rememberNavController()
// КЛЮЧЕВАЯ СТРОКА №2: без сессии есть только два экрана входа/регистрации.
                            if (uid == null) {
                                NavHost(navController = nav, startDestination = SignInRoute, modifier = Modifier.padding(padding)) {
                                    composable<SignInRoute> {
                                        SignInScreen(
                                            openSignUpScreen = { nav.navigate(SignUpRoute) },
                                            signInWithEmail = { email, password ->
                                                lifecycleScope.launch {
                                                    try { authRepository.signIn(email, password) }
                                                    catch (e: Exception) { snackbar.showSnackbar("Вход: ${e.localizedMessage ?: "ошибка"}") }
                                                }
                                            },
                                            signInFromCredentialManager = {
                                                lifecycleScope.launch {
                                                    try { credentialAuth.signInFromSystemPicker() }
                                                    catch (_: GetCredentialCancellationException) { /* пользователь закрыл диалог */ }
                                                    catch (e: Exception) { snackbar.showSnackbar("Credential Manager: ${e.localizedMessage ?: "ошибка"}") }
                                                }
                                            }
                                        )
                                    }
// Пользователь вводит пароль в поле Compose; MainActivity вызывает Firebase,
// и только ПОСЛЕ успеха предлагает его системному провайдеру сохранить.
                                    composable<SignUpRoute> {
                                        SignUpScreen(
                                            onBack = { nav.popBackStack() },
                                            onSignUp = { email, password, confirm ->
                                                // Activity-scoped coroutine не отменяется, когда сменится
                                                // граф авторизации: сохранение пароля должно успеть завершиться.
                                                lifecycleScope.launch {
                                                    when {
                                                        !email.isValidEmail() -> snackbar.showSnackbar(getString(R.string.invalid_email))
                                                        !password.isValidPassword() -> snackbar.showSnackbar(getString(R.string.invalid_password))
                                                        password != confirm -> snackbar.showSnackbar(getString(R.string.passwords_do_not_match))
                                                        else -> try {
// СНАЧАЛА Firebase создаёт аккаунт, только затем Credential Manager.
                                                            authRepository.signUp(email, password)
                                                            try {
                                                                credentialAuth.offerToSavePassword(email, password)
                                                            } catch (_: CreateCredentialCancellationException) {
                                                                // Отказ от сохранения не отменяет регистрацию.
                                                            } catch (e: Exception) {
                                                                snackbar.showSnackbar("Аккаунт создан, но сохранить пароль не удалось: ${e.localizedMessage ?: "ошибка"}")
                                                            }
                                                        } catch (e: Exception) {
                                                            snackbar.showSnackbar("Регистрация: ${e.localizedMessage ?: "ошибка"}")
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                NavHost(navController = nav, startDestination = TodoListRoute, modifier = Modifier.padding(padding)) {
                                    composable<TodoListRoute> {
                                        TodoListScreen(
                                            openSettingsScreen = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
                                            openTodoItemScreen = { itemId -> nav.navigate(TodoItemRoute(itemId)) { launchSingleTop = true } }
                                        )
                                    }
                                    composable<SettingsRoute> {
                                        SettingsScreen(
                                            onBack = { nav.popBackStack() },
                                            onSignOut = {
                                                lifecycleScope.launch {
// Главный выход: Firebase теряет локальную авторизованную сессию.
                                                    authRepository.signOut()
                                                    // Закрыть состояние провайдера Credential Manager,
                                                    // не удаляя сохранённый в Google Password Manager пароль.
                                                    try { credentialAuth.clearAfterSignOut() }
                                                    catch (_: Exception) { /* Firebase сессия уже завершена */ }
                                                }
                                            }
                                        )
                                    }
                                    composable<TodoItemRoute> {
                                        TodoItemScreen(
                                            openTodoListScreen = { nav.popBackStack() },
                                            showErrorSnackbar = { error ->
                                                val msg = when(error) {
                                                    is com.google.firebase.example.makeitso.data.model.ErrorMessage.StringError -> error.message
                                                    is com.google.firebase.example.makeitso.data.model.ErrorMessage.IdError -> getString(error.message)
                                                }
                                                lifecycleScope.launch { snackbar.showSnackbar(msg) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
