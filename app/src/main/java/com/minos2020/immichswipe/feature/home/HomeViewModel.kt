package com.minos2020.immichswipe.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minos2020.immichswipe.data.repository.UserRepository
import com.minos2020.immichswipe.data.repository.AlbumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.core.AppLogger
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.domain.model.Album

/**
 * ViewModel de l'écran d'accueil.
 */
class HomeViewModel(
    private val sessionRepository: SessionRepository,
    private val albumRepository: AlbumRepository,
    private val swipeDecisionRepository: SwipeDecisionRepository,
    private val assetRepository: AssetRepository
) : ViewModel() {
    
    private fun getUserRepository(): UserRepository {
        return UserRepository(
            SessionManager.api ?: throw IllegalStateException("Session not initialized")
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
                if (config != null) {
                    combine(
                        swipeDecisionRepository.getAllDecisionsForUser(config.userId),
                        swipeDecisionRepository.getAllAlbumDecisionCounts(config.userId)
                    ) { allDecisions, albumStats ->
                        val uniqueDecisions = allDecisions.distinctBy { it.assetId }
                        val treatedMap = albumStats.associate { it.albumId to it.totalCount }.toMutableMap()
                        val unsyncedMap = albumStats.associate { it.albumId to it.unsyncedCount }.toMutableMap()
                        
                        // Injection du compte global pour "Tous les médias"
                        treatedMap[Album.VIRTUAL_ALL_ID] = uniqueDecisions.size
                        unsyncedMap[Album.VIRTUAL_ALL_ID] = uniqueDecisions.count { !it.isSynced && !it.wasSyncedSkip }
                        
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

        // Observe le nombre de SKIP synchronisés pour l'album virtuel
        viewModelScope.launch {
            sessionRepository.sessionConfig.collect { config ->
                if (config != null) {
                    combine(
                        swipeDecisionRepository.getSyncedSkipCount(config.userId),
                        sessionRepository.includeArchived
                    ) { count, _ ->
                        count // Imprecision accepted for now
                    }.collect { adjustedCount ->
                        _uiState.update { it.copy(syncedSkipCount = adjustedCount) }
                    }
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
                    
                    // Pour KEEP, ARCHIVE et SKIP, on fait : (Somme de l'historique) + (Nouveaux swipes pas encore synchronisés)
                    val totalKept = history.sumOf { it.keptCount } + allDecisions.count { it.decision == "KEEP" && !it.isSynced }
                    val totalArchived = history.sumOf { it.archivedCount } + allDecisions.count { it.decision == "ARCHIVE" && !it.isSynced }
                    val totalSkipped = history.sumOf { it.skippedCount } + allDecisions.count { it.decision == "SKIP" && !it.isSynced }
                    
                    // Stats hebdomadaires (basées sur l'activité réelle enregistrée)
                    val weeklyDeleted = weeklyHistory.sumOf { it.deletedCount }
                    val weeklyBytes = weeklyHistory.sumOf { it.bytesSaved }

                    val completedCount = albums.count { album ->
                        val treated = treatedCounts[album.id] ?: 0
                        treated >= album.assetCount && album.assetCount > 0
                    }

                    StatsUiData(
                        totalDeleted = totalDeleted,
                        totalBytesSaved = totalBytes,
                        totalKept = totalKept,
                        totalArchived = totalArchived,
                        totalLocked = totalLocked,
                        totalSkipped = totalSkipped,
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
    }

    fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                AppLogger.d("Home", "Chargement des données utilisateur et albums")
                val user = getUserRepository().getCurrentUser()
                // SOLUTION : Migration des anciennes données (v3 -> v4) vers l'ID utilisateur réel
                swipeDecisionRepository.migrateLegacyDecisions(user.id)

                val albums = albumRepository.refreshAlbums(_uiState.value.includeArchived)
                val allCount = assetRepository.getTotalAssetCount(_uiState.value.includeArchived)
                val orphansCount = assetRepository.getOrphansCount(_uiState.value.includeArchived)

                AppLogger.i("Home", "Utilisateur chargé: ${user.name}, ${albums.size} albums trouvés")
                _uiState.update { 
                    it.copy(
                        user = user, 
                        albums = albums,
                        allAssetsCount = allCount,
                        orphansCount = orphansCount,
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
                val config = sessionRepository.sessionConfig.first()
                // On mémorise l'heure de début
                val startTime = System.currentTimeMillis()
                
                // On lance la requête
                val albums = albumRepository.refreshAlbums(_uiState.value.includeArchived)
                
                // On récupère le nombre total de médias pour la collection virtuelle
                val allCount = assetRepository.getTotalAssetCount(_uiState.value.includeArchived)

                // On récupère le nombre d'orphelins directement via l'API
                val orphansCount = assetRepository.getOrphansCount(_uiState.value.includeArchived)

                // En cas de refresh manuel, on force le redémarrage de la tâche de découverte
                // pour s'assurer que les compteurs d'albums sont à jour (indexation)
                if (config != null) {
                    launchDiscoveryTask(config.userId)
                }

                // On calcule combien de temps a duré la requête
                val duration = System.currentTimeMillis() - startTime
                // On attend le complément pour atteindre au moins 800ms
                if (duration < 800) {
                    delay(800 - duration)
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
            } catch (e: Exception) {
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
        if (tab == HomeTab.HOME && current != HomeTab.HOME) {
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

    fun setPlaybackBehavior(behavior: PlaybackBehavior) = viewModelScope.launch {
        sessionRepository.savePlaybackBehavior(behavior)
    }

    fun setThemeMode(theme: AppTheme) = viewModelScope.launch {
        sessionRepository.saveThemeMode(theme)
    }

    fun logout() = viewModelScope.launch {
        discoveryJob?.cancel()
        _uiState.value = HomeUiState() // Reset COMPLET de l'état
        sessionRepository.clearSession()
        AppLogger.i("Auth","Déconnexion de l'utilisateur")
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

    private var discoveryJob: kotlinx.coroutines.Job? = null

    /**
     * Lance une tâche de fond qui parcourt les albums pour indexer les photos.
     * Cela permet de synchroniser les compteurs entre "Tous les médias" et les albums.
     */
    private fun launchDiscoveryTask(userId: String) {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            try {
                // On attend un peu que l'UI initiale soit affichée
                delay(2000)
                
                // On récupère la liste des albums réels
                val albumsToScan = _uiState.value.albums.toMutableList()
                val orphansCount = _uiState.value.orphansCount
                
                if (albumsToScan.isEmpty() && orphansCount == 0) return@launch

                // On ajoute manuellement la collection des Orphelins à scanner
                // car elle nécessite aussi une indexation pour la synchro inter-collections.
                if (orphansCount > 0) {
                    val virtualOrphans = Album(
                        id = Album.VIRTUAL_ORPHANS_ID,
                        albumName = _uiState.value.virtualNames[Album.VIRTUAL_ORPHANS_ID] ?: "Orphans",
                        assetCount = orphansCount,
                        albumThumbnailAssetId = null
                    )
                    albumsToScan.add(virtualOrphans)
                }

                val includeArchived = _uiState.value.includeArchived
                AppLogger.i("Home", "Démarrage de la découverte des albums (${albumsToScan.size} à scanner)" +
                        " [Archives: $includeArchived]")

                for (album in albumsToScan) {
                    // Si le nombre de mappings locaux est différent du nombre serveur, on rescanne
                    val count = albumRepository.getMappingCount(album.id, userId)
                    if (count == album.assetCount && album.assetCount > 0) continue


                    AppLogger.d("Home", "Scan de l'album : ${album.albumName} ($count -> ${album.assetCount})")
                    // fetchAllAssets va automatiquement remplir la table album_assets avec le userId
                    assetRepository.getAssetsByAlbum(album.id, includeArchived = includeArchived, userId = userId)
                    
                    // Petite pause pour ne pas saturer le serveur
                    delay(500)
                }
                AppLogger.i("Home", "Découverte des albums terminée\n")
            } catch (e: Exception) {
                AppLogger.e("Home", "Erreur lors de la découverte", e)
            }
        }
    }

    fun getSessionRepository() = sessionRepository
}
