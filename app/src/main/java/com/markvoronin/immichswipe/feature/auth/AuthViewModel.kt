package com.markvoronin.immichswipe.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markvoronin.immichswipe.core.AppLogger
import com.markvoronin.immichswipe.core.SessionConfig
import com.markvoronin.immichswipe.core.SessionManager
import com.markvoronin.immichswipe.data.repository.AccountRepository
import com.markvoronin.immichswipe.data.repository.AuthRepository
import com.markvoronin.immichswipe.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel gérant la logique de l'écran de connexion.
 * Il délègue les appels réseau au AuthRepository.
 */
class AuthViewModel(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // On observe la session : si elle devient null (déconnexion), 
        // on réinitialise l'état du formulaire de login.
        observeSessionReset()
    }

    private fun observeSessionReset() {
        viewModelScope.launch {
            sessionRepository.sessionConfig.collect { config ->
                if (config == null) {
                    resetState()
                }
            }
        }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(baseUrl = value)
    }

    fun onApiKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(apiKey = value)
    }

    /**
     * Remet l'état à zéro (utile après une déconnexion).
     */
    private fun resetState() {
        _uiState.value = AuthUiState()
    }

    /**
     * Tente de connecter l'utilisateur.
     */
    fun login() {
        viewModelScope.launch {
            val baseUrl = _uiState.value.baseUrl.trim()
            val apiKey = _uiState.value.apiKey.trim()

            AppLogger.i("Auth", "Tentative de connexion à $baseUrl")

            // Validation basique
            if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                _uiState.value = _uiState.value.copy(error = AuthError.EmptyFields)
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // 1. On demande au Repository de vérifier les identifiants
                val user = authRepository.checkCredentials(baseUrl, apiKey)
                AppLogger.i("Auth", "Identifiants valides. Utilisateur: ${user.name} (${user.id})")

                // 2. Si on arrive ici, c'est que la connexion a réussi !
                val config = SessionConfig(baseUrl = baseUrl, apiKey = apiKey, userId = user.id)
                SessionManager.initialize(config)

                // 3. On sauvegarde le compte dans la base locale et la session active
                accountRepository.saveAccount(baseUrl, apiKey, user)
                sessionRepository.saveSession(baseUrl = baseUrl, token = apiKey, userId = user.id)

                _uiState.value = _uiState.value.copy(isLoading = false, success = true)

            } catch (e: Exception) {
                val error = when (e) {
                    is java.net.UnknownHostException -> AuthError.Dns
                    is java.net.SocketTimeoutException -> AuthError.Timeout
                    is java.net.ConnectException -> AuthError.Refused
                    is retrofit2.HttpException -> {
                        when (e.code()) {
                            401 -> AuthError.Auth
                            403 -> AuthError.Forbidden
                            404 -> AuthError.NotFound
                            else -> AuthError.Server(e.code())
                        }
                    }
                    is javax.net.ssl.SSLHandshakeException -> AuthError.Ssl
                    else -> AuthError.Unknown(e.localizedMessage)
                }

                AppLogger.e("Auth", "Échec de connexion : $error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error
                )
            }
        }
    }
}
