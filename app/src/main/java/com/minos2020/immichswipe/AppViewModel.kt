package com.minos2020.immichswipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel principal de l'application (niveau Activity).
 * Il s'occupe de l'initialisation et du thème.
 */
class AppViewModel(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        // Au démarrage, on lance l'observation réactive de la session et du thème
        observeSession()
        observeTheme()
        observeDynamicColor()
    }

    private fun observeSession() {
        viewModelScope.launch {
            // On s'abonne au tuyau de la session
            sessionRepository.sessionConfig.collect { config ->
                if (config != null) {
                    // Si on a une config sauvegardée, on initialise le SessionManager
                    SessionManager.initialize(config)

                    // On met à jour l'UI : on est connecté !
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        activeUserId = config.userId
                    )
                } else {
                    // Si on reçoit null, on nettoie tout
                    SessionManager.clear()

                    // SOLUTION : Si on vient d'une version v2, on force le nettoyage complet
                    // pour obliger à une reconnexion propre (multi-compte).
                    sessionRepository.cleanupLegacySession()

                    // On met à jour l'UI : on est déconnecté
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = false,
                        activeUserId = null
                    )
                }
            }
        }
    }

    private fun observeTheme() {
        viewModelScope.launch {
            sessionRepository.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
    }

    private fun observeDynamicColor() {
        viewModelScope.launch {
            sessionRepository.dynamicColor.collect { enabled ->
                _uiState.value = _uiState.value.copy(dynamicColor = enabled)
            }
        }
    }
}
