package net.igng.mcstatus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.igng.mcstatus.data.AppSettings
import net.igng.mcstatus.data.SettingsRepository
import net.igng.mcstatus.data.ThemeAccent
import net.igng.mcstatus.data.TicketRepository
import net.igng.mcstatus.data.MathCaptcha
import net.igng.mcstatus.data.SavedAccount

data class LoginUiState(val loading: Boolean = false, val captcha: MathCaptcha? = null, val accountName: String? = null, val error: String? = null)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val ticketRepository: TicketRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setVibrationEnabled(enabled)
        }
    }

    fun setUseSystemAccent(enabled: Boolean) {
        viewModelScope.launch {
            repository.setUseSystemAccent(enabled)
        }
    }

    fun setAccent(accent: ThemeAccent) {
        viewModelScope.launch {
            repository.setAccent(accent)
        }
    }
    private val _loginState = kotlinx.coroutines.flow.MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState
    private var restoreGeneration = 0
    init { refreshCaptcha() }
    fun refreshCaptcha() { viewModelScope.launch { runCatching { ticketRepository.captcha() }.onSuccess { _loginState.value = _loginState.value.copy(captcha = it, error = null) }.onFailure { _loginState.value = _loginState.value.copy(error = "无法加载人机验证") } } }
    fun login(identifier: String, password: String, duration: String, captcha: MathCaptcha, answer: String) { viewModelScope.launch { _loginState.value = _loginState.value.copy(loading = true, error = null); runCatching { ticketRepository.login(identifier, password, duration, captcha, answer) }.onSuccess { result -> val account = SavedAccount(result.user.id, result.user.username, result.user.nickname, result.sessionToken); repository.saveAccount(account); _loginState.value = LoginUiState(accountName = account.displayName) }.onFailure { _loginState.value = _loginState.value.copy(loading = false, error = it.message ?: "登录失败"); refreshCaptcha() } } }
    fun restoreSavedSession(token: String?) {
        if (token.isNullOrBlank()) return
        val generation = ++restoreGeneration
        viewModelScope.launch {
            runCatching { ticketRepository.me(token) }.onSuccess { result ->
                if (generation != restoreGeneration || settings.value.sessionToken != token) return@onSuccess
                val account = SavedAccount(result.user.id, result.user.username, result.user.nickname, token)
                // Only migrate a legacy single-account token. Verification must never change the active account.
                if (settings.value.accounts.isEmpty()) repository.saveAccount(account)
                _loginState.value = _loginState.value.copy(accountName = account.displayName, error = null)
            }
        }
    }
    fun switchAccount(account: SavedAccount) { viewModelScope.launch { restoreGeneration++; repository.switchAccount(account.userId); _loginState.value = _loginState.value.copy(accountName = account.displayName, error = null) } }
    fun logout() { viewModelScope.launch { val token = settings.value.sessionToken; if (!token.isNullOrBlank()) runCatching { ticketRepository.logout(token) }; repository.clearActiveAccount(); _loginState.value = LoginUiState(); refreshCaptcha() } }
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val ticketRepository: TicketRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(repository, ticketRepository) as T
    }
}
