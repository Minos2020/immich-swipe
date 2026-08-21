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
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.core.AppLogger
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.data.local.entity.SwipeDecisionEntity
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
    private val assetRepository: AssetRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    
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
            sessionRepository.includeArchived.distinctUntilChanged().collect { include ->
                _uiState.update { it.copy(includeArchived = include) }
                // On rafraîchit les albums uniquement si cette option a REELLEMENT changé
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
                    // On observe includeArchived pour relancer le flux de statistiques SQL
                    _uiState.map { it.includeArchived }.distinctUntilChanged().collectLatest { includeArchived ->
                        swipeDecisionRepository.getAllAlbumDecisionCounts(config.userId, includeArchived).collect { albumStats ->
                            val treatedMap = albumStats.associate { it.albumId to it.totalCount }.toMutableMap()
                            val unsyncedMap = albumStats.associate { it.albumId to it.unsyncedCount }.toMutableMap()

                            _uiState.update { 
                                it.copy(
                                    albumTreatedCounts = treatedMap,
                                    albumUnsyncedChanges = unsyncedMap
                                )
                            }
                        }
                    }
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
                val user = userRepository.getCurrentUser()
                // SOLUTION : Migration des anciennes données (v3 -> v4) vers l'ID utilisateur réel
                swipeDecisionRepository.migrateLegacyDecisions(user.id)

                val albums = albumRepository.refreshAlbums(_uiState.value.includeArchived)
                val allCount = assetRepository.getTotalAssetCount(_uiState.value.includeArchived)
                val orphansCount = assetRepository.getOrphansCount(_uiState.value.includeArchived)

                AppLogger.i("Home", "Utilisateur chargé: ${user.name}, ${albums.size} albums trouvés")
                
                // Nettoyage des SKIP expirés dès le démarrage
                val lifespan = sessionRepository.skipLifespanDays.first()
                val removedSkips = swipeDecisionRepository.cleanExpiredSkips(user.id, lifespan)
                if (removedSkips > 0) {
                    AppLogger.i("Home", "Nettoyage : $removedSkips SKIP expirés supprimés")
                }

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
                if (e is CancellationException) throw e
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

                // Nettoyage des SKIP expirés au rafraîchissement manuel
                val lifespan = sessionRepository.skipLifespanDays.first()
                val removedSkips = swipeDecisionRepository.cleanExpiredSkips(config!!.userId, lifespan)
                if (removedSkips > 0) {
                    AppLogger.i("Home", "Nettoyage : $removedSkips SKIP expirés supprimés")
                }

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
                if (e is CancellationException) throw e
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

    fun setShowLogoutConfirmation(visible: Boolean) {
        _uiState.update { it.copy(showLogoutConfirmation = visible) }
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

    fun requestAlbumAction(album: Album, action: AlbumAction) {
        _uiState.update { it.copy(pendingAlbum = album, pendingAlbumAction = action) }
    }

    fun dismissAlbumAction() {
        _uiState.update { it.copy(pendingAlbum = null, pendingAlbumAction = null) }
    }

    /**
     * Exécute la réinitialisation locale d'un album.
     */
    fun resetAlbumDecisions(albumId: String) {
        viewModelScope.launch {
            try {
                val userId = sessionRepository.sessionConfig.first()?.userId ?: return@launch
                swipeDecisionRepository.removeDecisionsForAlbum(albumId, userId)
                AppLogger.i("Home", "Réinitialisation locale de l'album $albumId")
                dismissAlbumAction()
                refreshAlbums() // Pour mettre à jour les compteurs
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e("Home", "Erreur lors du reset de l'album", e)
            }
        }
    }

    /**
     * Marque tous les médias d'un album comme KEEP (localement).
     */
    fun markAlbumAsKeep(album: com.minos2020.immichswipe.domain.model.Album) {
        viewModelScope.launch {
            try {
                val userId = sessionRepository.sessionConfig.first()?.userId ?: return@launch
                _uiState.update { it.copy(isRefreshing = true) }
                
                // On récupère TOUS les assets de l'album via le repository
                // .last() attend que le flux de chargement incrémental soit terminé
                val allAssetsList: List<com.minos2020.immichswipe.domain.model.Asset> = 
                    assetRepository.getAssetsByAlbum(album.id, includeArchived = true, userId = userId).last()
                
                // Préparation du batch pour insertion massive (plus performant)
                val timestamp = System.currentTimeMillis()
                val newDecisions = allAssetsList.map { asset ->
                    SwipeDecisionEntity(
                        assetId = asset.id,
                        albumId = album.id,
                        userId = userId,
                        decision = "KEEP",
                        fileSize = asset.exifInfo?.fileSizeInBytes,
                        createdAt = timestamp,
                        isSynced = true, // On marque directement comme synchronisé (tri terminé)
                        wasSyncedSkip = false
                    )
                }
                
                swipeDecisionRepository.importData(newDecisions, emptyList())
                
                AppLogger.i("Home", "Album ${album.albumName} marqué comme KEEP (${allAssetsList.size} médias)")
                _uiState.update { it.copy(isRefreshing = false) }
                dismissAlbumAction()
                refreshAlbums()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e("Home", "Erreur lors du marquage KEEP de l'album", e)
                _uiState.update { it.copy(isRefreshing = false) }
            }
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
                _uiState.update { it.copy(isDiscovering = true) }
                // On attend un peu que l'UI initiale soit affichée
                delay(2000)
                
                // On récupère la liste des albums réels
                val albumsToScan = _uiState.value.albums.toMutableList()
                val orphansCount = _uiState.value.orphansCount
                val includeArchived = _uiState.value.includeArchived
                
                if (albumsToScan.isEmpty() && orphansCount == 0) {
                    // Bibliothèque vide : on nettoie les collections virtuelles
                    assetRepository.updateAllAssetsMapping(userId)
                    val removedCount = swipeDecisionRepository.removeGhostDecisions(userId)
                    if (removedCount > 0) {
                        AppLogger.i("Home", "Nettoyage : $removedCount décisions obsolètes supprimées")
                    }
                    return@launch
                }

                // On ajoute manuellement la collection des Orphelins à scanner
                if (orphansCount > 0) {
                    val virtualOrphans = Album(
                        id = Album.VIRTUAL_ORPHANS_ID,
                        albumName = _uiState.value.virtualNames[Album.VIRTUAL_ORPHANS_ID] ?: "Orphans",
                        assetCount = orphansCount,
                        albumThumbnailAssetId = null
                    )
                    albumsToScan.add(virtualOrphans)
                }
                // albumsToScan.size-1 pour ne pas inclure la collection Orphans dans le compte (plus simple pour l'utilisateur)
                AppLogger.i("Home", "Démarrage de la découverte des albums (${albumsToScan.size-1} à scanner)" +
                        " [Archives: $includeArchived]")

                // On récupère la vérité absolue du serveur (totaux réels incluant archives) 
                // pour savoir si on doit lancer un scan de mise à jour du cache.
                val exhaustiveAlbums = albumRepository.refreshAlbums(includeArchived = true)
                val serverTotals = exhaustiveAlbums.associate { it.id to it.assetCount }

                var hasChanges = false
                for (album in albumsToScan) {
                    val localCount = albumRepository.getMappingCount(album.id, userId)
                    val serverTotal = serverTotals[album.id] ?: album.assetCount

                    // Si notre cache local est déjà complet par rapport au serveur, on passe.
                    if (localCount == serverTotal) continue

                    hasChanges = true
                    AppLogger.d("Home", "Scan de l'album : ${album.albumName} ($localCount -> $serverTotal)")
                    // On force includeArchived = true pour avoir un cache exhaustif (Solution A)
                    assetRepository.getAssetsByAlbum(album.id, includeArchived = true, userId = userId).collect {}
                    
                    // Petite pause pour ne pas saturer le serveur
                    delay(500)
                }

                if (hasChanges) {
                    // Une fois tous les albums et orphelins indexés, on reconstruit la collection globale
                    AppLogger.d("Home", "Mise à jour de la collection 'Tous les médias'")
                    assetRepository.updateAllAssetsMapping(userId)

                    // Enfin, on nettoie les décisions pour les photos disparues du serveur
                    val removedCount = swipeDecisionRepository.removeGhostDecisions(userId)
                    if (removedCount > 0) {
                        AppLogger.d("Home", "Nettoyage des décisions obsolètes : $removedCount  supprimées")
                    }
                } else {
                    AppLogger.d("Home", "Aucun changement détecté sur les albums")
                }

                AppLogger.i("Home", "Découverte des albums terminée\n--------------------------------------")
            } catch (e: Exception) {
                if (e is CancellationException) {
                    AppLogger.d("Home", "Découverte annulée par une autre requête")
                } else {
                    AppLogger.e("Home", "Erreur lors de la découverte", e)
                }
            } finally {
                _uiState.update { it.copy(isDiscovering = false) }
            }
        }
    }

    fun getSessionRepository() = sessionRepository
}
