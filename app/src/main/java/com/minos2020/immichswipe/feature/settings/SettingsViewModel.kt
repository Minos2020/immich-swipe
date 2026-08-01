package com.minos2020.immichswipe.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.data.repository.UserRepository
import com.minos2020.immichswipe.data.local.model.DatabaseExport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val sessionRepository: SessionRepository,
    private val swipeDecisionRepository: SwipeDecisionRepository
) : ViewModel() {

    private val userRepository by lazy {
        UserRepository(
            SessionManager.api ?: throw IllegalStateException("Session not initialized")
        )
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserData()
        observeSettings()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                _uiState.value = _uiState.value.copy(userName = user.name ?: "")
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Erreur chargement user: ${e.message}")
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            sessionRepository.playbackBehavior.collect { behavior ->
                _uiState.value = _uiState.value.copy(playbackBehavior = behavior)
            }
        }
        viewModelScope.launch {
            sessionRepository.themeMode.collect { theme ->
                _uiState.value = _uiState.value.copy(themeMode = theme)
            }
        }
        viewModelScope.launch {
            sessionRepository.swipeInverted.collect { inverted ->
                _uiState.value = _uiState.value.copy(isSwipeInverted = inverted)
            }
        }
        viewModelScope.launch {
            sessionRepository.fullscreenButtonPosition.collect { pos ->
                _uiState.value = _uiState.value.copy(fullscreenButtonPosition = pos)
            }
        }
        viewModelScope.launch {
            sessionRepository.immichButtonPosition.collect { pos ->
                _uiState.value = _uiState.value.copy(immichButtonPosition = pos)
            }
        }
        viewModelScope.launch {
            sessionRepository.cardDisplayButtonPosition.collect { pos ->
                _uiState.value = _uiState.value.copy(cardDisplayButtonPosition = pos)
            }
        }
        viewModelScope.launch {
            sessionRepository.muteButtonPosition.collect { pos ->
                _uiState.value = _uiState.value.copy(muteButtonPosition = pos)
            }
        }
        viewModelScope.launch {
            sessionRepository.defaultLayoutGrid.collect { isGrid ->
                _uiState.value = _uiState.value.copy(isDefaultLayoutGrid = isGrid)
            }
        }
        viewModelScope.launch {
            sessionRepository.skipLifespanDays.collect { days ->
                _uiState.value = _uiState.value.copy(skipLifespanDays = days)
            }
        }
        viewModelScope.launch {
            sessionRepository.showFavoriteButton.collect { show ->
                _uiState.value = _uiState.value.copy(showFavoriteButton = show)
            }
        }
        viewModelScope.launch {
            sessionRepository.showArchiveButton.collect { show ->
                _uiState.value = _uiState.value.copy(showArchiveButton = show)
            }
        }
        viewModelScope.launch {
            sessionRepository.showLockButton.collect { show ->
                _uiState.value = _uiState.value.copy(showLockButton = show)
            }
        }
        viewModelScope.launch {
            sessionRepository.autoNextOnFav.collect { autoNextOnFav ->
                _uiState.value = _uiState.value.copy(autoNextOnFav = autoNextOnFav)
            }
        }
        viewModelScope.launch {
            sessionRepository.includeArchived.collect { include ->
                _uiState.value = _uiState.value.copy(includeArchived = include)
            }
        }
        viewModelScope.launch {
            sessionRepository.defaultCardDisplayMode.collect { mode ->
                _uiState.value = _uiState.value.copy(defaultCardDisplayMode = mode)
            }
        }
    }

    fun setPlaybackBehavior(behavior: PlaybackBehavior) {
        viewModelScope.launch {
            sessionRepository.savePlaybackBehavior(behavior)
        }
    }

    fun setThemeMode(theme: AppTheme) {
        viewModelScope.launch {
            sessionRepository.saveThemeMode(theme)
        }
    }

    fun setSwipeInverted(inverted: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveSwipeInverted(inverted)
        }
    }

    fun setFullscreenButtonPosition(pos: IconPosition) {
        viewModelScope.launch {
            sessionRepository.saveFullscreenButtonPosition(pos)
        }
    }

    fun setImmichButtonPosition(pos: IconPosition) {
        viewModelScope.launch {
            sessionRepository.saveImmichButtonPosition(pos)
        }
    }

    fun setCardDisplayButtonPosition(pos: IconPosition) {
        viewModelScope.launch {
            sessionRepository.saveCardDisplayButtonPosition(pos)
        }
    }

    fun setMuteButtonPosition(pos: IconPosition) {
        viewModelScope.launch {
            sessionRepository.saveMuteButtonPosition(pos)
        }
    }

    fun setDefaultLayoutGrid(isGrid: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveDefaultLayoutGrid(isGrid)
        }
    }

    fun setShowFavorite(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowFavorite(show) }
    }

    fun setShowArchive(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowArchive(show) }
    }

    fun setShowLock(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowLock(show) }
    }

    fun setAutoNextOnFav(autoNextOnFav: Boolean) {
        viewModelScope.launch { sessionRepository.saveAutoNextOnFav(autoNextOnFav) }
    }

    fun setIncludeArchived(include: Boolean) {
        viewModelScope.launch { sessionRepository.saveIncludeArchived(include) }
    }

    fun setDefaultCardDisplayMode(mode: com.minos2020.immichswipe.core.CardDisplayMode) {
        viewModelScope.launch {
            sessionRepository.saveDefaultCardDisplayMode(mode)
        }
    }

    fun requestSkipLifespanChange(days: Long) {
        val currentDays = _uiState.value.skipLifespanDays
        
        // On détermine si on doit afficher l'alerte :
        // 1. Si on passe de "Jamais" (0) à une durée limitée -> Alerte (car on réduit l'infini)
        // 2. Si on réduit une durée existante (ex: de 30j à 7j) -> Alerte
        val isReduction = (currentDays == 0L && days > 0L) || (currentDays > 0L && days > 0L && days < currentDays)
        
        if (isReduction) {
            _uiState.value = _uiState.value.copy(showSkipLifespanWarning = days)
        } else {
            // Sinon, on applique directement
            viewModelScope.launch {
                sessionRepository.saveSkipLifespan(days)
                // Nettoyage au cas où (même si peu probable que ça supprime en augmentant)
                if (days > 0) swipeDecisionRepository.cleanExpiredSkips(days)
            }
        }
    }

    fun confirmSkipLifespanChange() {
        val targetDays = _uiState.value.showSkipLifespanWarning ?: return
        viewModelScope.launch {
            sessionRepository.saveSkipLifespan(targetDays)
            // On lance un nettoyage immédiat si une durée a été définie
            if (targetDays > 0) {
                swipeDecisionRepository.cleanExpiredSkips(targetDays)
            }
            _uiState.value = _uiState.value.copy(showSkipLifespanWarning = null)
        }
    }

    fun dismissSkipLifespanWarning() {
        _uiState.value = _uiState.value.copy(showSkipLifespanWarning = null)
    }

    fun logout() {
        viewModelScope.launch {
            sessionRepository.clearSession()
        }
    }

    // --- Gestion de la Base de Données ---

    fun requestDatabaseAction(action: DatabaseAction, scope: DatabaseScope) {
        _uiState.value = _uiState.value.copy(
            pendingDatabaseAction = action,
            pendingDatabaseScope = scope
        )
    }

    fun dismissDatabaseConfirmation() {
        _uiState.value = _uiState.value.copy(
            pendingDatabaseAction = null,
            pendingDatabaseScope = null
        )
    }

    fun executeDelete(scope: DatabaseScope) {
        viewModelScope.launch {
            val userId = SessionManager.getUserId()
            if (scope == DatabaseScope.ALL) {
                swipeDecisionRepository.clearAllData()
            } else {
                userId?.let { swipeDecisionRepository.clearUserData(it) }
            }
            dismissDatabaseConfirmation()
            _uiState.value = _uiState.value.copy(databaseActionStatus = "Données supprimées avec succès")
        }
    }

    fun exportDatabase(scope: DatabaseScope, outputStream: java.io.OutputStream) {
        viewModelScope.launch {
            try {
                val userId = SessionManager.getUserId()
                val decisions = if (scope == DatabaseScope.ALL) {
                    swipeDecisionRepository.getAllDecisionsRaw()
                } else {
                    userId?.let { swipeDecisionRepository.getAllDecisionsForUserRaw(it) } ?: emptyList()
                }
                val history = if (scope == DatabaseScope.ALL) {
                    swipeDecisionRepository.getAllSyncHistoryRaw()
                } else {
                    userId?.let { swipeDecisionRepository.getAllSyncHistoryForUserRaw(it) } ?: emptyList()
                }

                val export = DatabaseExport(
                    swipeDecisions = decisions,
                    syncHistory = history,
                    scope = scope.name,
                    userId = if (scope == DatabaseScope.USER) userId else null
                )

                val json = com.google.gson.Gson().toJson(export)
                outputStream.use { it.write(json.toByteArray()) }

                _uiState.value = _uiState.value.copy(databaseActionStatus = "Export terminé (${decisions.size} décisions)")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(databaseActionStatus = "Erreur export: ${e.message}")
            } finally {
                dismissDatabaseConfirmation()
            }
        }
    }

    fun importDatabase(inputStream: java.io.InputStream) {
        viewModelScope.launch {
            try {
                val json = inputStream.bufferedReader().use { it.readText() }
                val export = com.google.gson.Gson().fromJson(json, DatabaseExport::class.java)

                swipeDecisionRepository.importData(export.swipeDecisions, export.syncHistory)

                _uiState.value = _uiState.value.copy(databaseActionStatus = "Import réussi (${export.swipeDecisions.size} décisions)")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(databaseActionStatus = "Erreur import: ${e.message}")
            } finally {
                dismissDatabaseConfirmation()
            }
        }
    }

    fun clearDatabaseActionStatus() {
        _uiState.value = _uiState.value.copy(databaseActionStatus = null)
    }

    fun setShowLogs(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLogsDialog = show)
    }

    fun clearLogs() {
        com.minos2020.immichswipe.core.AppLogger.clearLogs()
    }

    fun getLogs(): String {
        return com.minos2020.immichswipe.core.AppLogger.getLogs()
    }
}

class SettingsViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val swipeDecisionRepository: SwipeDecisionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(sessionRepository, swipeDecisionRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
