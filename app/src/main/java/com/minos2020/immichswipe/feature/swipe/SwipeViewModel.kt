package com.minos2020.immichswipe.feature.swipe

import kotlin.time.Duration.Companion.milliseconds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.minos2020.immichswipe.core.CardDisplayMode
import com.minos2020.immichswipe.core.SortOrder
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.domain.model.Album

/**
 * ViewModel de l'écran de tri (Swipe).
 */
class SwipeViewModel(
    private val assetRepository: AssetRepository,
    private val sessionRepository: SessionRepository,
    private val swipeDecisionRepository: SwipeDecisionRepository,
    private val album: Album
) : ViewModel() {

    private val _uiState = MutableStateFlow(SwipeUiState(albumName = album.albumName))
    val uiState: StateFlow<SwipeUiState> = _uiState.asStateFlow()

    init {
        loadAssetsAndDecisions()
        observePlaybackBehavior()
        observeFullscreenButtonPosition()
        observeImmichButtonPosition()
        observeCardDisplayButtonPosition()
        observeMuteButtonPosition()
        observeButtonVisibility()
        observeAutoNextOnFav()
        observeSortOrder()
        observeSwapSummaryArchive()
        observeSyncLocalDeletion()
        observeTrashLocalDeletion()
    }

    private fun observePlaybackBehavior() {
        viewModelScope.launch {
            sessionRepository.playbackBehavior.collect { behavior ->
                _uiState.update { it.copy(playbackBehavior = behavior) }
            }
        }
    }

    private fun observeFullscreenButtonPosition() {
        viewModelScope.launch {
            sessionRepository.fullscreenButtonPosition.collect { pos ->
                _uiState.update { it.copy(fullscreenButtonPosition = pos) }
            }
        }
    }

    private fun observeImmichButtonPosition() {
        viewModelScope.launch {
            sessionRepository.immichButtonPosition.collect { pos ->
                _uiState.update { it.copy(immichButtonPosition = pos) }
            }
        }
    }

    private fun observeCardDisplayButtonPosition() {
        viewModelScope.launch {
            sessionRepository.cardDisplayButtonPosition.collect { pos ->
                _uiState.update { it.copy(cardDisplayButtonPosition = pos) }
            }
        }
    }

    private fun observeMuteButtonPosition() {
        viewModelScope.launch {
            sessionRepository.muteButtonPosition.collect { pos ->
                _uiState.update { it.copy(muteButtonPosition = pos) }
            }
        }
    }

    private fun observeButtonVisibility() {
        viewModelScope.launch {
            sessionRepository.showSwipeButtons.collect { show ->
                _uiState.update { it.copy(showSwipeButtons = show) }
            }
        }
    }

    private fun observeAutoNextOnFav() {
        viewModelScope.launch {
            sessionRepository.autoNextOnFav.collect { autoNext ->
                _uiState.update { it.copy(autoNextOnFav = autoNext) }
            }
        }
    }

    private fun observeSortOrder() {
        viewModelScope.launch {
            sessionRepository.sortOrder.collect { order ->
                val previousOrder = _uiState.value.sortOrder
                _uiState.update { it.copy(sortOrder = order) }
                
                // Si l'ordre a réellement changé, on recharge tout
                if (previousOrder != order) {
                    loadAssetsAndDecisions()
                }
            }
        }
    }

    private fun observeSwapSummaryArchive() {
        viewModelScope.launch {
            sessionRepository.swapSummaryArchive.collect { swap ->
                _uiState.update { it.copy(swapSummaryArchive = swap) }
            }
        }
    }

    private fun observeSyncLocalDeletion() {
        viewModelScope.launch {
            sessionRepository.syncLocalDeletion.collect { sync ->
                _uiState.update { it.copy(syncLocalDeletion = sync) }
            }
        }
    }

    private fun observeTrashLocalDeletion() {
        viewModelScope.launch {
            sessionRepository.trashLocalDeletion.collect { trash ->
                _uiState.update { it.copy(trashLocalDeletion = trash) }
            }
        }
    }

    fun setSortOrder(order: SortOrder) = viewModelScope.launch {
        sessionRepository.saveSortOrder(order)
    }

    /**
     * Retente le chargement des données si une erreur a eu lieu.
     */
    fun retryLoading() {
        loadAssetsAndDecisions()
    }

    // On garde en mémoire les décisions qui étaient déjà synchronisées au début de la session
    private var initialSyncedDecisions = mapOf<String, SwipeDecision>()
    private var currentShuffleSeed: Long? = null

    private fun loadAssetsAndDecisions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                AppLogger.d("Swipe", "Chargement de l'album ${album.albumName} (ID: ${album.id})")
                val config = sessionRepository.sessionConfig.first() ?: return@launch
                val includeArchived = sessionRepository.includeArchived.first()
                val currentSortOrder = sessionRepository.sortOrder.first()

                // Si on est en mode SHUFFLE mais qu'on n'a pas encore de seed, on en crée un
                if (currentSortOrder == SortOrder.SHUFFLED && currentShuffleSeed == null) {
                    currentShuffleSeed = System.currentTimeMillis()
                }

                // On charge les assets depuis l'API
                val assets = assetRepository.getAssetsByAlbum(
                    album.id,
                    includeArchived,
                    config.userId,
                    sortOrder = currentSortOrder,
                    shuffleSeed = currentShuffleSeed
                )
                val albumAssetIds = assets.map { it.id }.toSet()

                // On charge TOUTES les décisions locales de l'utilisateur (partagées entre albums)
                val localDecisions = swipeDecisionRepository.getAllDecisionsForUser(config.userId).first()
                    .filter { albumAssetIds.contains(it.assetId) }

                AppLogger.d("Swipe", "${assets.size} assets trouvés, ${localDecisions.size} décisions locales pour cet album")
                
                // On mémorise l'état synchronisé pour calculer les deltas lors de la synchronisation.
                initialSyncedDecisions = localDecisions
                    .filter { it.isSynced }
                    .associate { entity ->
                        val decision = try { SwipeDecision.valueOf(entity.decision) } catch (_: Exception) { SwipeDecision.KEEP }
                        entity.assetId to decision
                    }

                // On transforme la liste de SwipeDecisionEntity en Map<String, SwipeDecision>
                val decisionMap = mutableMapOf<String, SwipeDecision>()
                val sizeMap = mutableMapOf<String, Long>()

                localDecisions.forEach { entity ->
                    val decision = try {
                        SwipeDecision.valueOf(entity.decision)
                    } catch (e: Exception) {
                        null
                    } ?: return@forEach

                    // On met toutes les décisions dans l'état de l'UI (même synchronisées)
                    // pour que les tags "KEEP" soient conservés visuellement.
                    decisionMap[entity.assetId] = decision
                    
                    // On garde toujours la taille connue de l'asset
                    entity.fileSize?.let { sizeMap[entity.assetId] = it }
                }

                // On conserve TOUS les assets de l'album pour afficher la progression complète
                val workPileAssets = assets

                // On cherche le premier index non traité (celui qui n'a aucune décision en base)
                val firstUnprocessedIndex = workPileAssets.indexOfFirst { !decisionMap.containsKey(it.id) }
                        .let { if (it == -1) workPileAssets.size else it }

                _uiState.update {
                    it.copy(
                        assets = workPileAssets,
                        decisions = decisionMap,
                        assetSizes = sizeMap,
                        history = emptyList(),
                        localFavorites = emptyMap(),
                        currentIndex = firstUnprocessedIndex,
                        isLoading = false
                    )
                }
                
                // On charge les détails de l'asset actuel
                if (firstUnprocessedIndex < workPileAssets.size) {
                    loadAssetDetail(workPileAssets[firstUnprocessedIndex].id, firstUnprocessedIndex)
                }
            } catch (e: Exception) {
                AppLogger.e("Swipe", "Erreur lors du chargement de l'album", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Erreur lors du chargement des photos"
                )
            }
        }
    }

    private fun loadAssetDetail(assetId: String, index: Int) {
        viewModelScope.launch {
            try {
                val detail = assetRepository.getAssetDetail(assetId)
                val currentAssets = _uiState.value.assets.toMutableList()
                if (index < currentAssets.size && currentAssets[index].id == assetId) {
                    currentAssets[index] = detail
                    
                    val newSizes = _uiState.value.assetSizes.toMutableMap()
                    detail.exifInfo?.fileSizeInBytes?.let { newSizes[assetId] = it }

                    _uiState.update { it.copy(assets = currentAssets, assetSizes = newSizes) }
                }
            } catch (_: Exception) {}
        }
    }

    fun onSwipe(decision: SwipeDecision) {
        val currentState = _uiState.value
        val currentAsset = currentState.currentAsset ?: return
        
        viewModelScope.launch {
            val config = sessionRepository.sessionConfig.first() ?: return@launch
            
            // 1. Sauvegarde locale Room
            swipeDecisionRepository.saveDecision(
                assetId = currentAsset.id,
                albumId = album.id,
                userId = config.userId,
                decision = decision.name,
                fileSize = currentAsset.exifInfo?.fileSizeInBytes
            )

            // 2. Mise à jour UI
            val newDecisions = currentState.decisions.toMutableMap()
            newDecisions[currentAsset.id] = decision

            val newHistory = currentState.history.toMutableList()
            newHistory.add(currentAsset.id)

            // On avance vers le prochain non traité
            val nextIndex = currentState.assets.indices.firstOrNull { i ->
                i > currentState.currentIndex && !newDecisions.containsKey(currentState.assets[i].id)
            } ?: currentState.assets.size

            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    decisions = newDecisions,
                    history = newHistory
                )
            }
            
            // Pré-chargement du prochain asset si besoin
            if (nextIndex < currentState.assets.size) {
                loadAssetDetail(currentState.assets[nextIndex].id, nextIndex)
            }
        }
    }

    fun toggleFavorite() {
        val currentState = _uiState.value
        val currentAsset = currentState.currentAsset ?: return
        
        val currentFav = currentState.isFavorite(currentAsset.id)
        val newFavorites = currentState.localFavorites.toMutableMap()
        newFavorites[currentAsset.id] = !currentFav
        
        _uiState.update { it.copy(localFavorites = newFavorites) }
        if (currentState.autoNextOnFav) {
            onSwipe(SwipeDecision.KEEP) // Avance à la suivante
        }
    }

    fun toggleArchive() {
        onSwipe(SwipeDecision.ARCHIVE)
    }

    fun toggleLock() {
        onSwipe(SwipeDecision.LOCK)
    }

    fun toggleDisplayMode() {
        val nextMode = if (_uiState.value.cardDisplayMode == CardDisplayMode.FILL) {
            CardDisplayMode.FIT
        } else {
            CardDisplayMode.FILL
        }
        _uiState.update { it.copy(cardDisplayMode = nextMode) }
    }

    fun undo() {
        val currentState = _uiState.value
        val lastAssetIdFromHistory = currentState.history.lastOrNull()

        viewModelScope.launch {
            val config = sessionRepository.sessionConfig.first() ?: return@launch
            if (lastAssetIdFromHistory != null) {
                // On vérifie si l'action annulée était une décision fraîche ou une modification d'un état synchronisé
                val previouslySynced = initialSyncedDecisions[lastAssetIdFromHistory]
                
                if (previouslySynced == null) {
                    // C'était une nouvelle décision : on supprime totalement de la base locale
                    swipeDecisionRepository.removeDecision(lastAssetIdFromHistory, config.userId)
                } else {
                    // C'était la modification d'un état déjà synchronisé : on restaure l'ancien état
                    swipeDecisionRepository.saveDecision(
                        assetId = lastAssetIdFromHistory,
                        albumId = album.id,
                        userId = config.userId,
                        decision = previouslySynced.name,
                        isSynced = true
                    )
                }
                
                val newDecisions = currentState.decisions.toMutableMap()
                if (previouslySynced == null) {
                    newDecisions.remove(lastAssetIdFromHistory)
                } else {
                    newDecisions[lastAssetIdFromHistory] = previouslySynced
                }

                val newHistory = currentState.history.toMutableList()
                newHistory.removeAt(newHistory.size - 1)

                val previousIndex = currentState.assets.indexOfFirst { it.id == lastAssetIdFromHistory }

                _uiState.update {
                    it.copy(
                        currentIndex = if (previousIndex != -1) previousIndex else currentState.currentIndex,
                        decisions = newDecisions,
                        history = newHistory
                    )
                }
                
                if (previousIndex != -1) {
                    loadAssetDetail(lastAssetIdFromHistory, previousIndex)
                }
            }
        }
    }

    /**
     * Permet de sauter directement à un asset précis (via la timeline).
     */
    fun onMoveToAsset(index: Int) {
        if (index in _uiState.value.assets.indices) {
            _uiState.update { it.copy(currentIndex = index) }
            loadAssetDetail(_uiState.value.assets[index].id, index)
        }
    }

    /**
     * Affiche ou cache l'écran de résumé.
     */
    fun toggleSummary(visible: Boolean) {
        _uiState.update { it.copy(showSummary = visible) }
    }

    /**
     * Affiche ou cache le dialogue de confirmation de reset.
     */
    fun toggleResetConfirmation(visible: Boolean) {
        _uiState.update { it.copy(showResetConfirmation = visible) }
    }

    /**
     * Réinitialise toutes les décisions pour l'album actuel.
     */
    fun resetAlbumDecisions() {
        val currentState = _uiState.value
        val assetIds = currentState.assets.map { it.id }
        
        viewModelScope.launch {
            try {
                val config = sessionRepository.sessionConfig.first() ?: return@launch
                
                // 1. Supprimer de la base Room
                swipeDecisionRepository.removeDecisions(assetIds, config.userId)
                
                // 2. Recharger les données pour repartir de zéro
                _uiState.update { it.copy(showResetConfirmation = false) }
                loadAssetsAndDecisions()
                
                AppLogger.i("Swipe", "Album ${album.albumName} réinitialisé avec succès")
            } catch (e: Exception) {
                AppLogger.e("Swipe", "Erreur lors du reset de l'album", e)
            }
        }
    }

    /**
     * Active ou désactive le mode plein écran.
     */
    fun toggleFullscreen(enabled: Boolean) {
        _uiState.update { it.copy(isFullscreenMode = enabled) }
    }

    /**
     * Annule une décision spécifique (utilisé depuis le résumé).
     */
    fun undoSpecificDecision(assetId: String) {
        val currentState = _uiState.value
        viewModelScope.launch {
            val config = sessionRepository.sessionConfig.first() ?: return@launch
            
            val previouslySynced = initialSyncedDecisions[assetId]
            if (previouslySynced == null) {
                swipeDecisionRepository.removeDecision(assetId, config.userId)
            } else {
                swipeDecisionRepository.saveDecision(
                    assetId = assetId,
                    albumId = album.id,
                    userId = config.userId,
                    decision = previouslySynced.name,
                    isSynced = true
                )
            }
            
            val newDecisions = currentState.decisions.toMutableMap()
            if (previouslySynced == null) {
                newDecisions.remove(assetId)
            } else {
                newDecisions[assetId] = previouslySynced
            }
            
            val newHistory = currentState.history.toMutableList()
            newHistory.remove(assetId)
            
            _uiState.update {
                it.copy(
                    decisions = newDecisions,
                    history = newHistory
                )
            }
        }
    }

    /**
     * Applique les décisions (Suppression sur Immich) et marque les assets comme traités localement.
     */
    fun applyChanges() {
        val currentState = _uiState.value
        val decisions = currentState.decisions
        
        // On ne synchronise que ce qui a changé par rapport à l'état initial
        val unsyncedDecisions = decisions.filter { (id, decision) ->
            initialSyncedDecisions[id] != decision
        }
        
        val toDeleteIds = unsyncedDecisions.filter { it.value == SwipeDecision.DELETE }.keys.toList()
        val toArchive = unsyncedDecisions.filter { it.value == SwipeDecision.ARCHIVE }.keys.toList()
        val toLock = unsyncedDecisions.filter { it.value == SwipeDecision.LOCK }.keys.toList()
        val toKeep = unsyncedDecisions.filter { it.value == SwipeDecision.KEEP }.keys.toList()
        
        // Gestion des favoris (toujours synchronisés car ils sont volatiles dans l'UI)
        val toFavorite = currentState.localFavorites.filter { it.value }.keys.toList()
        val toUnfavorite = currentState.localFavorites.filter { !it.value }.keys.toList()

        if (toDeleteIds.isEmpty() && toArchive.isEmpty() && toLock.isEmpty() && toKeep.isEmpty() && toFavorite.isEmpty() && toUnfavorite.isEmpty()) {
            AppLogger.d("Swipe", "Aucun changement à synchroniser")
            _uiState.update { it.copy(showSummary = false) }
            return
        }

        viewModelScope.launch {
            AppLogger.i("Swipe", "Application des changements : DELETE(${toDeleteIds.size}), ARCHIVE(${toArchive.size}), LOCK(${toLock.size}), KEEP(${toKeep.size})")
            _uiState.update { it.copy(isSyncing = true) }
            try {
                val config = sessionRepository.sessionConfig.first() ?: return@launch
                
                // 0. Préparation de la suppression locale (si activée)
                var pendingIntent: android.app.PendingIntent? = null
                if (currentState.syncLocalDeletion && toDeleteIds.isNotEmpty()) {
                    val assetsToDelete = currentState.assets.filter { toDeleteIds.contains(it.id) }
                    val localUris = assetRepository.findLocalUris(assetsToDelete)
                    if (localUris.isNotEmpty()) {
                        pendingIntent = if (currentState.trashLocalDeletion) {
                            assetRepository.createLocalTrashRequest(localUris, true)
                        } else {
                            assetRepository.createLocalDeleteRequest(localUris)
                        }
                    }
                }

                // 1. Appels API
                if (toDeleteIds.isNotEmpty()) assetRepository.deleteAssets(toDeleteIds)
                if (toFavorite.isNotEmpty()) assetRepository.updateAssets(toFavorite, isFavorite = true)
                if (toUnfavorite.isNotEmpty()) assetRepository.updateAssets(toUnfavorite, isFavorite = false)
                if (toArchive.isNotEmpty()) assetRepository.updateAssets(toArchive, visibility = "archive")
                if (toLock.isNotEmpty()) assetRepository.updateAssets(toLock, visibility = "locked")

                // 2. Vérification et mise à jour de la base locale
                val freshAssets = assetRepository.getAssetsByAlbum(album.id, includeArchived = true, userId = config.userId)
                val freshIds = freshAssets.map { it.id }.toSet()

                // - Identification des succès (ceux qui ont disparu de l'album)
                val successfullyDisappeared = (toDeleteIds + toLock).filter { !freshIds.contains(it) }
                
                val successfulKeeps = (toKeep + toArchive).filter { freshIds.contains(it) }

                // 3. Mise à jour de la base de données locale
                if (successfullyDisappeared.isNotEmpty()) {
                    swipeDecisionRepository.removeDecisions(successfullyDisappeared, config.userId)
                }

                if (successfulKeeps.isNotEmpty()) {
                    swipeDecisionRepository.markAsSynced(successfulKeeps, config.userId)
                }

                // 4. Statistiques de session (delta)
                var deltaKeep = toKeep.size
                var deltaArchive = toArchive.size

                (toKeep + toArchive + toDeleteIds + toLock).forEach { id ->
                    initialSyncedDecisions[id]?.let { previous ->
                        when (previous) {
                            SwipeDecision.KEEP -> deltaKeep--
                            SwipeDecision.ARCHIVE -> deltaArchive--
                            else -> {}
                        }
                    }
                }

                swipeDecisionRepository.saveSyncHistory(
                    userId = config.userId,
                    deletedCount = successfullyDisappeared.count { toDeleteIds.contains(it) },
                    bytesSaved = toDeleteIds.filter { successfullyDisappeared.contains(it) }.sumOf { currentState.assetSizes[it] ?: 0L },
                    keptCount = deltaKeep,
                    archivedCount = deltaArchive,
                    lockedCount = successfullyDisappeared.count { toLock.contains(it) }
                )

                // Mise à jour de l'état local pour refléter la synchronisation
                val currentAssetId = currentState.currentAsset?.id
                val newSyncedDecisions = initialSyncedDecisions.toMutableMap()
                successfulKeeps.forEach { id -> newSyncedDecisions[id] = decisions[id] ?: SwipeDecision.KEEP }
                successfullyDisappeared.forEach { newSyncedDecisions.remove(it) }
                initialSyncedDecisions = newSyncedDecisions

                _uiState.update { 
                    val filteredAssets = it.assets.filter { asset -> !successfullyDisappeared.contains(asset.id) }
                    // Recalcul de l'index pour éviter les sauts lors du filtrage
                    val newIndex = if (currentAssetId != null) {
                        val foundIndex = filteredAssets.indexOfFirst { a -> a.id == currentAssetId }
                        if (foundIndex != -1) foundIndex else it.currentIndex.coerceAtMost(filteredAssets.size)
                    } else {
                        it.currentIndex.coerceAtMost(filteredAssets.size)
                    }

                    it.copy(
                        assets = filteredAssets,
                        currentIndex = newIndex,
                        isSyncing = false,
                        showSuccessAnimation = true,
                        showSummary = false,
                        localFavorites = emptyMap(),
                        localDeletePendingIntent = pendingIntent
                    )
                }
                
                delay(2500.milliseconds)
                _uiState.update { it.copy(showSuccessAnimation = false) }

            } catch (e: Exception) {
                AppLogger.e("Swipe", "Erreur lors de la synchronisation", e)
                _uiState.update { it.copy(isSyncing = false, error = "Erreur synchro: ${e.message}") }
            }
        }
    }

    /**
     * Une fois que l'Intent de suppression locale a été traité par l'UI, on le vide.
     */
    fun onLocalDeleteIntentHandled() {
        _uiState.update { it.copy(localDeletePendingIntent = null) }
    }

    /**
     * Calcule l'index du prochain asset à afficher en arrière-plan.
     * Priorité aux non-traités, sinon le suivant dans la liste.
     */
    fun getNextUnprocessedIndex(): Int {
        val state = _uiState.value
        val assets = state.assets
        val decisions = state.decisions
        val current = state.currentIndex

        // 1. Chercher le prochain non-traité après
        for (i in (current + 1) until assets.size) {
            if (!decisions.containsKey(assets[i].id)) return i
        }
        
        // 2. Chercher le prochain non-traité avant
        for (i in 0 until current) {
            if (!decisions.containsKey(assets[i].id)) return i
        }

        // 3. Si tout est traité, on affiche simplement la carte suivante dans la liste
        if (current + 1 < assets.size) {
            return current + 1
        }
        
        return -1
    }
}
