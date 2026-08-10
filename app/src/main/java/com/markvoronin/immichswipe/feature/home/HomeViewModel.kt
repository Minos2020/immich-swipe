package com.markvoronin.immichswipe.feature.home

import kotlin.time.Duration.Companion.milliseconds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markvoronin.immichswipe.data.repository.UserRepository
import com.markvoronin.immichswipe.data.repository.AlbumRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.markvoronin.immichswipe.core.SessionManager
import com.markvoronin.immichswipe.core.AppLogger
import com.markvoronin.immichswipe.data.repository.SessionRepository
import com.markvoronin.immichswipe.data.repository.SwipeDecisionRepository
import com.markvoronin.immichswipe.data.repository.AssetRepository
import com.markvoronin.immichswipe.data.repository.AccountRepository
import com.markvoronin.immichswipe.domain.model.Album

/**
 * ViewModel de l'écran d'accueil.
 */
class HomeViewModel(
    private val sessionRepository: SessionRepository,
    private val albumRepository: AlbumRepository,
    private val swipeDecisionRepository: SwipeDecisionRepository,
    private val assetRepository: AssetRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    
    private val userRepository by lazy { 
        UserRepository(
            SessionManager.api ?: throw IllegalStateException("Session not initialized"),
        )
    }
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Observe les préférences
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
            sessionRepository.includeArchived.collect { include ->
                _uiState.update { it.copy(includeArchived = include) }
                // On rafraîchit les albums si cette option change car les comptes vont changer
                refreshAlbums()
            }
        }

        viewModelScope.launch {
            sessionRepository.sortOrder.collect { order ->
                _uiState.update { it.copy(sortOrder = order) }
            }
        }

        // Applique le mode d'affichage par défaut au démarrage
        viewModelScope.launch {
            val isGrid = sessionRepository.defaultLayoutGrid.first()
            _uiState.update { it.copy(isGridView = isGrid) }
        }

        // SOLUTION : Observe l'état de santé global de la connexion.
        // Puisque SessionManager met à jour son Flow à chaque requête réseau,
        // la pastille réagira à tout (refresh, swipe, vidéo, etc.)
        viewModelScope.launch {
            SessionManager.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }

        // Observe les décisions locales pour mettre à jour les barres de progression
        viewModelScope.launch {
            sessionRepository.sessionConfig.collect { config ->
                config?.let { cfg ->
                    combine(
                        swipeDecisionRepository.getAllDecisionsForUser(cfg.userId),
                        swipeDecisionRepository.getAllAlbumDecisionCounts(cfg.userId)
                    ) { allDecisions, albumStats ->
                        val uniqueDecisions = allDecisions.distinctBy { it.assetId }
                        val treatedMap = albumStats.associateBy { it.albumId }.mapValues { it.value.totalCount }.toMutableMap()
                        val unsyncedMap = albumStats.associateBy { it.albumId }.mapValues { it.value.unsyncedCount }.toMutableMap()
                        
                        // Injection du compte global pour "Tous les médias"
                        treatedMap[Album.VIRTUAL_ALL_ID] = uniqueDecisions.size
                        unsyncedMap[Album.VIRTUAL_ALL_ID] = uniqueDecisions.count { !it.isSynced }
                        
                        // Note: Pour les orphelins, on se base sur les décisions prises spécifiquement sur des orphelins
                        // ou on pourra affiner le calcul plus tard.

                        _uiState.update { 
                            it.copy(
                                albumTreatedCounts = treatedMap,
                                albumUnsyncedChanges = unsyncedMap
                            )
                        }
                    }.collect {}
                }
            }
        }

        // Observe les statistiques globales (Historique + Albums)
        viewModelScope.launch {
            sessionRepository.sessionConfig.collect { config ->
                if (config == null) return@collect

                combine(
                    swipeDecisionRepository.getSyncHistory(config.userId),
                    swipeDecisionRepository.getAllDecisionsForUser(config.userId),
                    _uiState.map { it.albums },
                    _uiState.map { it.albumTreatedCounts }
                ) { history, allDecisions, albums, treatedCounts ->
                    val now = System.currentTimeMillis()
                    val oneWeekAgo = now - (7 * 24 * 60 * 60 * 1000L)
                    
                    val weeklyHistory = history.filter { it.timestamp >= oneWeekAgo }

                    // STATS GLOBALES (Cumul Historique + Décisions locales non synchronisées)
                    // On reprend le cumul de tout ce qui a été fait par le passé
                    val totalDeleted = history.sumOf { it.deletedCount }
                    val totalBytes = history.sumOf { it.bytesSaved }
                    val totalLocked = history.sumOf { it.lockedCount }
                    
                    // Pour KEEP et ARCHIVE, on fait : (Somme de l'historique) + (Nouveaux swipes pas encore synchronisés)
                    val totalKept = history.sumOf { it.keptCount } + allDecisions.count { (it.decision == "KEEP") && !it.isSynced }
                    val totalArchived = history.sumOf { it.archivedCount } + allDecisions.count { (it.decision == "ARCHIVE") && !it.isSynced }
                    
                    // Stats hebdomadaires (basées sur l'activité réelle enregistrée)
                    val weeklyDeleted = weeklyHistory.sumOf { it.deletedCount }
                    val weeklyBytes = weeklyHistory.sumOf { it.bytesSaved }

                    val completedCount = albums.count { album ->
                        val treated = treatedCounts[album.id] ?: 0
                        (treated >= album.assetCount) && (album.assetCount > 0)
                    }

                    StatsUiData(
                        totalDeleted = totalDeleted,
                        totalBytesSaved = totalBytes,
                        totalKept = totalKept,
                        totalArchived = totalArchived,
                        totalLocked = totalLocked,
                        totalAlbums = albums.size,
                        completedAlbums = completedCount,
                        weeklyDeleted = weeklyDeleted,
                        weeklyBytesSaved = weeklyBytes
                    )
                }.collect { newStats ->
                    _uiState.update { it.copy(stats = newStats) }
                }
            }
        }

        // Observe les comptes sauvegardés
        viewModelScope.launch {
            accountRepository.allAccounts.collect { accounts ->
                _uiState.update { it.copy(savedAccounts = accounts) }
            }
        }

        // Observe l'avertissement de backup
        viewModelScope.launch {
            sessionRepository.backupWarningShown.collect { shown ->
                _uiState.update { it.copy(showBackupWarning = !shown) }
            }
        }
    }

    fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                AppLogger.d("Home", "Chargement des données utilisateur et albums")
                val user = userRepository.getCurrentUser()

                val albums = albumRepository.refreshAlbums(_uiState.value.includeArchived)
                AppLogger.i("Home", "Utilisateur chargé: ${user.name}, ${albums.size} albums trouvés")
                _uiState.update { 
                    it.copy(
                        user = user, 
                        albums = albums, 
                        isLoading = false, 
                        error = null
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("Home", "Erreur lors du chargement initial", e)
                _uiState.update { 
                    it.copy(
                        error = e.message ?: "Erreur de chargement", 
                        isLoading = false
                    )
                }
            }
        }
    }

    fun refreshAlbums() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                // On mémorise l'heure de début
                val startTime = System.currentTimeMillis()
                
                // On lance la requête
                val albums = albumRepository.refreshAlbums(_uiState.value.includeArchived)
                
                // On récupère le nombre total de médias pour la collection virtuelle
                val allCount = assetRepository.getTotalAssetCount(_uiState.value.includeArchived)

                // On récupère le nombre d'orphelins directement via l'API
                val orphansCount = assetRepository.getOrphansCount(_uiState.value.includeArchived)

                // On calcule combien de temps a duré la requête
                val duration = System.currentTimeMillis() - startTime
                // On attend le complément pour atteindre au moins 800ms
                if (duration < 800) {
                    delay((800 - duration).milliseconds)
                }

                _uiState.update { 
                    it.copy(
                        albums = albums,
                        allAssetsCount = allCount,
                        orphansCount = orphansCount,
                        isRefreshing = false, 
                        error = null
                    ) 
                }
            } catch (_: Exception) {
                _uiState.update { 
                    it.copy(
                        isRefreshing = false
                    ) 
                }
            }
        }
    }

    fun onTabSelected(tab: HomeTab) {
        val current = _uiState.value.currentTab
        
        // SOLUTION : Rafraîchissement systématique si on revient sur HOME
        if ((tab == HomeTab.HOME) && (current != HomeTab.HOME)) {
            refreshAlbums()
        }

        val nextPrevious = if (tab == HomeTab.SETTINGS) current else _uiState.value.previousTab
        _uiState.update { 
            it.copy(currentTab = tab, previousTab = nextPrevious, showProfilePopup = false)
        }
    }

    fun goBack() {
        val previous = _uiState.value.previousTab
        
        /*// SOLUTION : Rafraîchissement systématique si on revient sur HOME
        if (previous == HomeTab.HOME) {
            refreshAlbums()
        }*/
        
        _uiState.update { it.copy(currentTab = previous) }
    }

    fun onAlbumSelected(album: Album) {
        _uiState.update { 
            it.copy(selectedAlbum = album, currentTab = HomeTab.SWIPE, previousTab = HomeTab.HOME)
        }
    }

    fun toggleProfilePopup(visible: Boolean) {
        _uiState.update { it.copy(showProfilePopup = visible) }
    }

    fun toggleStatsPopup(visible: Boolean) {
        _uiState.update { it.copy(showStatsPopup = visible) }
    }

    fun logout() = viewModelScope.launch {
        val currentUserId = _uiState.value.user?.id
        _uiState.update { it.copy(currentTab = HomeTab.HOME, showProfilePopup = false) }
        
        // Supprime le compte de la base locale
        currentUserId?.let { accountRepository.deleteAccount(it) }
        
        // Déconnexion de la session active
        sessionRepository.clearSession()
    }

    fun removeAccount(userId: String) = viewModelScope.launch {
        val currentUserId = _uiState.value.user?.id
        if (userId == currentUserId) {
            logout()
        } else {
            accountRepository.deleteAccount(userId)
        }
    }

    fun switchAccount(userId: String) = viewModelScope.launch {
        val account = accountRepository.getAccount(userId) ?: return@launch
        AppLogger.i("Home", "Switching to account ${account.userName} ($userId)")
        
        // On met à jour l'heure d'activité
        accountRepository.updateLastActive(userId)
        
        // On sauvegarde la session active (cela va déclencher le re-rendu de MainActivity via AppViewModel)
        sessionRepository.saveSession(
            baseUrl = account.baseUrl,
            token = account.apiKey,
            userId = account.userId
        )
    }

    fun startAddAccount() {
        _uiState.update { it.copy(isLoggingInToAnotherAccount = true, showProfilePopup = false) }
    }

    fun cancelAddAccount() {
        _uiState.update { it.copy(isLoggingInToAnotherAccount = false) }
    }

    fun onSearchQueryChanged(query: String) = _uiState.update { it.copy(searchQuery = query) }
    
    fun updateVirtualNames(id: String, name: String, description: String? = null) {
        _uiState.update { 
            val newNames = it.virtualNames.toMutableMap()
            newNames[id] = name
            val newDescs = it.virtualDescriptions.toMutableMap()
            if (description != null) {
                newDescs[id] = description
            }
            it.copy(virtualNames = newNames, virtualDescriptions = newDescs)
        }
    }

    fun toggleLayoutMode() = _uiState.update { it.copy(isGridView = !it.isGridView) }

    fun toggleCategory(status: AlbumStatus) {
        _uiState.update { state ->
            val newCollapsed = state.collapsedCategories.toMutableSet()
            if (newCollapsed.contains(status)) {
                newCollapsed.remove(status)
            } else {
                newCollapsed.add(status)
            }
            state.copy(collapsedCategories = newCollapsed)
        }
    }

    fun dismissBackupWarning() {
        viewModelScope.launch {
            sessionRepository.saveBackupWarningShown(true)
        }
    }

    private val _resetRequestSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resetRequestSignal = _resetRequestSignal.asSharedFlow()

    fun requestReset() {
        viewModelScope.launch {
            _resetRequestSignal.emit(Unit)
        }
    }

    fun getSessionRepository() = sessionRepository
}
