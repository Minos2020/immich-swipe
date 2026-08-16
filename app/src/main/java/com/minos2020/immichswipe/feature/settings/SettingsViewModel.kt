package com.minos2020.immichswipe.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.core.AppLogger
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class SettingsViewModel(
    private val sessionRepository: SessionRepository,
    private val swipeDecisionRepository: SwipeDecisionRepository
) : ViewModel() {

    private fun getUserRepository(): UserRepository? {
        return SessionManager.api?.let { UserRepository(it) }
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        loadUserData()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            // SÉCURITÉ : On attend que le SessionManager soit prêt (max 3 secondes)
            var attempts = 0
            while (SessionManager.api == null && attempts < 30) {
                delay(100)
                attempts++
            }

            try {
                val userRepo = getUserRepository() ?: throw IllegalStateException("Session not initialized after wait")
                val user = userRepo.getCurrentUser()
                _uiState.update { it.copy(userName = user.name ?: "") }
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Erreur chargement user: ${e.message}")
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            sessionRepository.playbackBehavior.collect { behavior ->
                _uiState.update { it.copy(playbackBehavior = behavior) }
            }
        }
        viewModelScope.launch {
            sessionRepository.themeMode.collect { theme ->
                _uiState.update { it.copy(themeMode = theme) }
            }
        }
        viewModelScope.launch {
            sessionRepository.swipeInverted.collect { inverted ->
                _uiState.update { it.copy(isSwipeInverted = inverted) }
            }
        }
        viewModelScope.launch {
            sessionRepository.fullscreenButtonPosition.collect { pos ->
                _uiState.update { it.copy(fullscreenButtonPosition = pos) }
            }
        }
        viewModelScope.launch {
            sessionRepository.immichButtonPosition.collect { pos ->
                _uiState.update { it.copy(immichButtonPosition = pos) }
            }
        }
        viewModelScope.launch {
            sessionRepository.cardDisplayButtonPosition.collect { pos ->
                _uiState.update { it.copy(cardDisplayButtonPosition = pos) }
            }
        }
        viewModelScope.launch {
            sessionRepository.muteButtonPosition.collect { pos ->
                _uiState.update { it.copy(muteButtonPosition = pos) }
            }
        }
        viewModelScope.launch {
            sessionRepository.defaultLayoutGrid.collect { isGrid ->
                _uiState.update { it.copy(isDefaultLayoutGrid = isGrid) }
            }
        }
        viewModelScope.launch {
            sessionRepository.skipLifespanDays.collect { days ->
                _uiState.update { it.copy(skipLifespanDays = days) }
            }
        }
        viewModelScope.launch {
            sessionRepository.showFavoriteButton.collect { show ->
                _uiState.update { it.copy(showFavoriteButton = show) }
            }
        }
        viewModelScope.launch {
            sessionRepository.showArchiveButton.collect { show ->
                _uiState.update { it.copy(showArchiveButton = show) }
            }
        }
        viewModelScope.launch {
            sessionRepository.showLockButton.collect { show ->
                _uiState.update { it.copy(showLockButton = show) }
            }
        }
        viewModelScope.launch {
            sessionRepository.showKeepDeleteButtons.collect { show ->
                _uiState.update { it.copy(showKeepDeleteButtons = show) }
            }
        }
        viewModelScope.launch {
            sessionRepository.showAddToAlbumButton.collect { show ->
                _uiState.update { it.copy(showAddToAlbumButton = show) }
            }
        }
        viewModelScope.launch {
            sessionRepository.autoNextOnFav.collect { autoNextOnFav ->
                _uiState.update { it.copy(autoNextOnFav = autoNextOnFav) }
            }
        }
        viewModelScope.launch {
            sessionRepository.includeArchived.collect { include ->
                _uiState.update { it.copy(includeArchived = include) }
            }
        }
        viewModelScope.launch {
            sessionRepository.defaultCardDisplayMode.collect { mode ->
                _uiState.update { it.copy(defaultCardDisplayMode = mode) }
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

    fun setShowKeepDelete(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowKeepDelete(show) }
    }

    fun setShowAddToAlbum(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowAddToAlbum(show) }
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
            _uiState.update { it.copy(showSkipLifespanWarning = days) }
        } else {
            // Sinon, on applique directement
            viewModelScope.launch {
                sessionRepository.saveSkipLifespan(days)
                val userId = SessionManager.getUserId()
                // Nettoyage au cas où (même si peu probable que ça supprime en augmentant)
                if (days > 0 && userId != null) swipeDecisionRepository.cleanExpiredSkips(userId, days)
            }
        }
    }

    fun confirmSkipLifespanChange() {
        val targetDays = _uiState.value.showSkipLifespanWarning ?: return
        viewModelScope.launch {
            sessionRepository.saveSkipLifespan(targetDays)
            val userId = SessionManager.getUserId()
            // On lance un nettoyage immédiat si une durée a été définie
            if (targetDays > 0 && userId != null) {
                swipeDecisionRepository.cleanExpiredSkips(userId, targetDays)
            }
            _uiState.update { it.copy(showSkipLifespanWarning = null) }
        }
    }

    fun dismissSkipLifespanWarning() {
        _uiState.update { it.copy(showSkipLifespanWarning = null) }
    }

    fun logout() {
        viewModelScope.launch {
            sessionRepository.clearSession()
        }
    }

    // --- Gestion de la Base de Données ---

    fun requestDatabaseAction(action: DatabaseAction, scope: DatabaseScope) {
        _uiState.update { it.copy(
            pendingDatabaseAction = action,
            pendingDatabaseScope = scope
        ) }
    }

    fun dismissDatabaseConfirmation() {
        _uiState.update { it.copy(
            pendingDatabaseAction = null,
            pendingDatabaseScope = null
        ) }
    }

    fun executeDelete(scope: DatabaseScope) {
        viewModelScope.launch {
            try {
                val userId = SessionManager.getUserId()
                if (scope == DatabaseScope.ALL) {
                    swipeDecisionRepository.clearAllData()
                } else {
                    userId?.let { swipeDecisionRepository.clearUserData(it) }
                }
                dismissDatabaseConfirmation()
                _uiState.update { it.copy(databaseActionStatus = "Données supprimées avec succès") }
                AppLogger.i("Database", "Suppression des données locales (scope:${scope.name})")
            } catch (e: Exception) {
                _uiState.update { it.copy(databaseActionStatus = "Erreur lors de la suppression: ${e.message}") }
                AppLogger.e("Database", "Échec de la suppression des données", e)
            }
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

                _uiState.update { it.copy(databaseActionStatus = "Export terminé (${decisions.size} décisions)") }
                AppLogger.i("Database", "Export réussi : ${decisions.size} décisions enregistrées (scope:${scope.name})")
            } catch (e: Exception) {
                _uiState.update { it.copy(databaseActionStatus = "Erreur export: ${e.message}") }
                AppLogger.e("Database", "Échec de l'export", e)
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

                _uiState.update { it.copy(databaseActionStatus = "Import réussi (${export.swipeDecisions.size} décisions)") }
                AppLogger.i("Database", "Import réussi : ${export.swipeDecisions.size} décisions importées")
            } catch (e: Exception) {
                _uiState.update { it.copy(databaseActionStatus = "Erreur import: ${e.message}") }
                AppLogger.e("Database", "Échec de l'import", e)
            } finally {
                dismissDatabaseConfirmation()
            }
        }
    }

    fun clearDatabaseActionStatus() {
        _uiState.update { it.copy(databaseActionStatus = null) }
    }

    fun setShowLogs(show: Boolean) {
        _uiState.update { it.copy(showLogsDialog = show) }
    }

    fun setShowClearLogsConfirmation(show: Boolean) {
        _uiState.update { it.copy(showClearLogsConfirmation = show) }
    }

    fun clearLogs() {
        AppLogger.clearLogs()
        _uiState.update { it.copy(
            showClearLogsConfirmation = false,
            showLogsDialog = false
        ) }
    }

    fun getLogs(): String {
        return AppLogger.getLogs()
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
