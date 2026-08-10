package com.minos2020.immichswipe.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.core.SortOrder
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.data.repository.UserRepository
import com.minos2020.immichswipe.data.local.model.DatabaseExport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val sessionRepository: SessionRepository,
    private val swipeDecisionRepository: SwipeDecisionRepository,
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
            sessionRepository.dynamicColor.collect { enabled ->
                _uiState.value = _uiState.value.copy(dynamicColor = enabled)
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
            sessionRepository.downloadButtonPosition.collect { pos ->
                _uiState.value = _uiState.value.copy(downloadButtonPosition = pos)
            }
        }
        viewModelScope.launch {
            sessionRepository.shareButtonPosition.collect { pos ->
                _uiState.value = _uiState.value.copy(shareButtonPosition = pos)
            }
        }
        viewModelScope.launch {
            sessionRepository.showFullscreenButton.collect { show ->
                _uiState.value = _uiState.value.copy(showFullscreenButton = show)
            }
        }
        viewModelScope.launch {
            sessionRepository.showImmichButton.collect { show ->
                _uiState.value = _uiState.value.copy(showImmichButton = show)
            }
        }
        viewModelScope.launch {
            sessionRepository.showCardDisplayButton.collect { show ->
                _uiState.value = _uiState.value.copy(showCardDisplayButton = show)
            }
        }
        viewModelScope.launch {
            sessionRepository.showMuteButton.collect { show ->
                _uiState.value = _uiState.value.copy(showMuteButton = show)
            }
        }
        viewModelScope.launch {
            sessionRepository.showDownloadButton.collect { show ->
                _uiState.value = _uiState.value.copy(showDownloadButton = show)
            }
        }
        viewModelScope.launch {
            sessionRepository.showShareButton.collect { show ->
                _uiState.value = _uiState.value.copy(showShareButton = show)
            }
        }
        viewModelScope.launch {
            sessionRepository.defaultLayoutGrid.collect { isGrid ->
                _uiState.value = _uiState.value.copy(isDefaultLayoutGrid = isGrid)
            }
        }
        viewModelScope.launch {
            sessionRepository.showFavoriteButton.collect { show ->
                _uiState.value = _uiState.value.copy(showFavoriteButton = show)
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
            sessionRepository.sortOrder.collect { order ->
                _uiState.update { it.copy(sortOrder = order) }
            }
        }
        viewModelScope.launch {
            sessionRepository.defaultCardDisplayMode.collect { mode ->
                _uiState.update { it.copy(defaultCardDisplayMode = mode) }
            }
        }
        viewModelScope.launch {
            sessionRepository.showSwipeButtons.collect { show ->
                _uiState.update { it.copy(showSwipeButtons = show) }
            }
        }
        viewModelScope.launch {
            sessionRepository.swapSummaryArchive.collect { swap ->
                _uiState.update { it.copy(swapSummaryArchive = swap) }
            }
        }
        viewModelScope.launch {
            sessionRepository.syncLocalDeletion.collect { sync ->
                _uiState.update { it.copy(syncLocalDeletion = sync) }
            }
        }
        viewModelScope.launch {
            sessionRepository.trashLocalDeletion.collect { trash ->
                _uiState.update { it.copy(trashLocalDeletion = trash) }
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

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveDynamicColor(enabled)
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

    fun setDownloadButtonPosition(pos: IconPosition) {
        viewModelScope.launch {
            sessionRepository.saveDownloadButtonPosition(pos)
        }
    }

    fun setShareButtonPosition(pos: IconPosition) {
        viewModelScope.launch {
            sessionRepository.saveShareButtonPosition(pos)
        }
    }

    fun setShowFullscreenButton(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowFullscreenButton(show) }
    }

    fun setShowImmichButton(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowImmichButton(show) }
    }

    fun setShowCardDisplayButton(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowCardDisplayButton(show) }
    }

    fun setShowMuteButton(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowMuteButton(show) }
    }

    fun setShowDownloadButton(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowDownloadButton(show) }
    }

    fun setShowShareButton(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowShareButton(show) }
    }

    fun setDefaultLayoutGrid(isGrid: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveDefaultLayoutGrid(isGrid)
        }
    }

    fun setShowFavorite(show: Boolean) {
        viewModelScope.launch { sessionRepository.saveShowFavorite(show) }
    }

    fun setAutoNextOnFav(autoNextOnFav: Boolean) {
        viewModelScope.launch { sessionRepository.saveAutoNextOnFav(autoNextOnFav) }
    }

    fun setIncludeArchived(include: Boolean) {
        viewModelScope.launch { sessionRepository.saveIncludeArchived(include) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { sessionRepository.saveSortOrder(order) }
    }

    fun setDefaultCardDisplayMode(mode: com.minos2020.immichswipe.core.CardDisplayMode) {
        viewModelScope.launch {
            sessionRepository.saveDefaultCardDisplayMode(mode)
        }
    }

    fun setShowSwipeButtons(show: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveShowSwipeButtons(show)
        }
    }

    fun setSwapSummaryArchive(swap: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveSwapSummaryArchive(swap)
        }
    }

    fun setSyncLocalDeletion(sync: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveSyncLocalDeletion(sync)
        }
    }

    fun setTrashLocalDeletion(trash: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveTrashLocalDeletion(trash)
        }
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

    fun setShowActionButtonsDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showActionButtonsDialog = show)
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
