package com.minos2020.immichswipe.feature.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.core.AppLogger
import com.minos2020.immichswipe.core.CardDisplayMode
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.domain.model.Asset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
        observeSwipeInversion()
        observeFullscreenButtonPosition()
        observeImmichButtonPosition()
        observeCardDisplayButtonPosition()
        observeMuteButtonPosition()
        observeSkipLifespan()
        observeButtonVisibility()
        observeAutoNextOnFav()
        observeIncludeArchived()
        observeDefaultCardDisplayMode()
    }

    private fun observeDefaultCardDisplayMode() {
        viewModelScope.launch {
            sessionRepository.defaultCardDisplayMode.collect { mode ->
                _uiState.value = _uiState.value.copy(cardDisplayMode = mode)
            }
        }
    }

    private fun observeIncludeArchived() {
        viewModelScope.launch {
            sessionRepository.includeArchived.collect { include ->
                _uiState.value = _uiState.value.copy(includeArchived = include)
            }
        }
    }

    private fun observeAutoNextOnFav() {
        viewModelScope.launch {
            sessionRepository.autoNextOnFav.collect { autoNextOnFav ->
                _uiState.value = _uiState.value.copy(autoNextOnFav = autoNextOnFav)
            }
        }
    }

    private fun observeButtonVisibility() {
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
    }

    private fun observeSkipLifespan() {
        viewModelScope.launch {
            sessionRepository.skipLifespanDays.collect { days ->
                _uiState.value = _uiState.value.copy(skipLifespanDays = days)
            }
        }
    }

    /**
     * Retente le chargement des données si une erreur a eu lieu.
     */
    fun retryLoading() {
        if (!_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(error = null)
            // On ajoute un petit délai pour éviter de spammer le serveur en cas de crash en boucle
            viewModelScope.launch {
                delay(500)
                loadAssetsAndDecisions()
            }
        }
    }

    private fun observePlaybackBehavior() {
        viewModelScope.launch {
            sessionRepository.playbackBehavior.collect { behavior ->
                _uiState.value = _uiState.value.copy(playbackBehavior = behavior)
            }
        }
    }

    private fun observeSwipeInversion() {
        viewModelScope.launch {
            sessionRepository.swipeInverted.collect { inverted ->
                _uiState.value = _uiState.value.copy(isSwipeInverted = inverted)
            }
        }
    }

    private fun observeFullscreenButtonPosition() {
        viewModelScope.launch {
            sessionRepository.fullscreenButtonPosition.collect { pos ->
                _uiState.value = _uiState.value.copy(fullscreenButtonPosition = pos)
            }
        }
    }

    private fun observeImmichButtonPosition() {
        viewModelScope.launch {
            sessionRepository.immichButtonPosition.collect { pos ->
                _uiState.value = _uiState.value.copy(immichButtonPosition = pos)
            }
        }
    }

    private fun observeCardDisplayButtonPosition() {
        viewModelScope.launch {
            sessionRepository.cardDisplayButtonPosition.collect { pos ->
                _uiState.value = _uiState.value.copy(cardDisplayButtonPosition = pos)
            }
        }
    }

    private fun observeMuteButtonPosition() {
        viewModelScope.launch {
            sessionRepository.muteButtonPosition.collect { pos ->
                _uiState.value = _uiState.value.copy(muteButtonPosition = pos)
            }
        }
    }

    // On garde en mémoire les décisions qui étaient déjà synchronisées au début de la session
    private var initialSyncedDecisions = mapOf<String, SwipeDecision>()
    private var lastLoadedUserId: String? = null

    private fun loadAssetsAndDecisions() {
        viewModelScope.launch {
            try {
                val config = sessionRepository.sessionConfig.first() ?: return@launch
                
                // SECURITÉ ANTI-FUITE : Si on change d'utilisateur, on vide tout l'état précédent immédiatement
                if (lastLoadedUserId != null && lastLoadedUserId != config.userId) {
                    AppLogger.w("Swipe", "Changement d'utilisateur détecté, purge de l'état de tri")
                    _uiState.value = SwipeUiState(albumName = album.albumName)
                }
                lastLoadedUserId = config.userId

                _uiState.value = _uiState.value.copy(isLoading = true)
                val includeArchived = sessionRepository.includeArchived.first()

                // 1. On charge d'abord TOUTES les décisions locales de l'utilisateur
                val allDecisionsFromDb = swipeDecisionRepository.getAllDecisionsForUser(config.userId).first()
                
                // Durée d'expiration des SKIP
                val lifespanDays = sessionRepository.skipLifespanDays.first()
                val lifespanMs = lifespanDays * 24 * 60 * 60 * 1000L
                val currentTime = System.currentTimeMillis()

                // Filtrage des SKIP expirés pour qu'ils retournent dans la pile de tri
                val localDecisions = allDecisionsFromDb.filter { entity ->
                    if (lifespanDays > 0 && entity.decision == SwipeDecision.SKIP.name) {
                        val isExpired = (currentTime - entity.createdAt) > lifespanMs
                        !isExpired
                    } else {
                        true
                    }
                }
                
                // Nettoyage de la base de données pour les SKIP expirés
                if (lifespanDays > 0) {
                    swipeDecisionRepository.cleanExpiredSkips(lifespanDays)
                }

                // 2. On mémorise l'état synchronisé pour calculer les deltas lors de la synchronisation.
                initialSyncedDecisions = localDecisions
                    .filter { it.isSynced || it.wasSyncedSkip }
                    .associate { entity ->
                        val decision = if (entity.wasSyncedSkip) SwipeDecision.SKIP 
                                      else try { SwipeDecision.valueOf(entity.decision) } catch (_: Exception) { SwipeDecision.SKIP }
                        entity.assetId to decision
                    }

                // 3. On prépare la Map des décisions locales (non synchronisées)
                val allLocalDecisions = mutableMapOf<String, SwipeDecision>()
                val sizeMap = mutableMapOf<String, Long>()
                localDecisions.forEach { entity ->
                    if (!entity.isSynced) {
                        try { allLocalDecisions[entity.assetId] = SwipeDecision.valueOf(entity.decision) } catch(_: Exception) {}
                    }

                    // On garde toujours la taille connue de l'asset
                    entity.fileSize?.let { sizeMap[entity.assetId] = it }
                }

                // 4. On lance le chargement incrémental des assets
                AppLogger.d("Swipe", "Chargement de l'album ${album.albumName} (ID: ${album.id})")
                assetRepository.getAssetsByAlbum(album.id, includeArchived, config.userId).collect { allAssetsFound ->
                    val currentState = _uiState.value
                    
                    // Filtrage de la pile de travail (Exclut les synchronisés sauf pour l'album SKIP)
                    val isVirtualSkipped = album.id == Album.VIRTUAL_SKIPPED_ID
                    val syncedIds = if (isVirtualSkipped) emptySet() else initialSyncedDecisions.keys
                    val workPile = allAssetsFound.filter { !syncedIds.contains(it.id) }
                    val workPileIds = workPile.map { it.id }.toSet()

                    // FUSION INTELLIGENTE : On garde les décisions de la session actuelle 
                    // et on ajoute celles du pool initial pour les nouveaux assets découverts
                    val mergedDecisions = currentState.decisions.toMutableMap()
                    val mergedSizes = currentState.assetSizes.toMutableMap()

                    workPileIds.forEach { id ->
                        // On n'ajoute la décision de la DB que si on n'a pas encore touché à la photo cette session
                        if (!mergedDecisions.containsKey(id)) {
                            allLocalDecisions[id]?.let { mergedDecisions[id] = it }
                        }
                        // Idem pour les tailles de fichiers
                        if (!mergedSizes.containsKey(id)) {
                            sizeMap[id]?.let { mergedSizes[id] = it }
                        }
                    }

                    // Nettoyage : on ne garde que les décisions des assets présents dans la pile
                    mergedDecisions.keys.retainAll(workPileIds)

                    // Recherche du premier index non traité (si on ne l'a pas encore trouvé)
                    var newIndex = currentState.currentIndex
                    var newIsLoading = currentState.isLoading
                    
                    if (newIsLoading || currentState.assets.isEmpty()) {
                        val firstFound = workPile.indexOfFirst { !mergedDecisions.containsKey(it.id) }
                        if (firstFound != -1) {
                            newIndex = firstFound
                            newIsLoading = false 
                        } else if (workPile.isNotEmpty()) {
                            // Si tout est déjà traité dans ce qui a été chargé, on se met à la fin
                            newIndex = workPile.size
                        }
                    }

                    _uiState.value = currentState.copy(
                        assets = workPile,
                        decisions = mergedDecisions,
                        assetSizes = mergedSizes,
                        currentIndex = newIndex,
                        isLoading = newIsLoading
                    )

                    updateSummaryStats()

                    // Anticipation : charge les détails de l'asset actuel si besoin
                    if (!newIsLoading && newIndex < workPile.size) {
                        loadAssetDetail(workPile[newIndex].id, newIndex)
                    }
                }
                
                // SÉCURITÉ : Une fois que le flux est terminé (tout est chargé), 
                // on force l'arrêt du loader quoi qu'il arrive.
                _uiState.value = _uiState.value.copy(isLoading = false)

            } catch (e: Exception) {
                AppLogger.e("Swipe", "Erreur lors du chargement de l'album", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Erreur lors du chargement des photos"
                )
            }
        }
    }

    /**
     * Recalcule les statistiques du résumé en une seule passe pour optimiser les performances.
     */
    private fun updateSummaryStats() {
        val state = _uiState.value
        val decisions = state.decisions
        val assetSizes = state.assetSizes
        val workPile = state.assets
        
        val counts = mutableMapOf<SwipeDecision, Int>()
        val sizes = mutableMapOf<SwipeDecision, Long>()
        val deletedAssets = mutableListOf<Asset>()

        // Calcul de la taille moyenne pour les assets dont le poids est inconnu
        val knownSizes = assetSizes.values.filter { it > 0 }
        val avgSize = if (knownSizes.isEmpty()) 0L else knownSizes.sum() / knownSizes.size

        workPile.forEach { asset ->
            val decision = decisions[asset.id] ?: return@forEach
            
            // Mise à jour des compteurs
            counts[decision] = (counts[decision] ?: 0) + 1
            
            // Mise à jour des tailles (on utilise la taille réelle ou la moyenne)
            val size = assetSizes[asset.id] ?: asset.exifInfo?.fileSizeInBytes ?: avgSize
            sizes[decision] = (sizes[decision] ?: 0L) + size

            // Liste spécifique pour la grille de suppression
            if (decision == SwipeDecision.DELETE) {
                deletedAssets.add(asset)
            }
        }

        _uiState.value = state.copy(
            summaryCounts = counts,
            summarySizes = sizes,
            summaryDeletedAssets = deletedAssets
        )
    }

    private fun loadAssetDetail(assetId: String, index: Int) {
        viewModelScope.launch {
            try {
                val detail = assetRepository.getAssetDetail(assetId)
                val currentAssets = _uiState.value.assets.toMutableList()
                if (index < currentAssets.size) {
                    currentAssets[index] = detail
                    val newSizes = _uiState.value.assetSizes.toMutableMap()
                    detail.exifInfo?.fileSizeInBytes?.let { newSizes[assetId] = it }
                    _uiState.value = _uiState.value.copy(
                        assets = currentAssets,
                        assetSizes = newSizes
                    )
                }
            } catch (e: Exception) {
                // Erreur silencieuse pour les détails
                android.util.Log.e("SWIPE_VM", "Erreur details asset: ${e.message}")
            }
        }
    }

    fun onSwipe(decision: SwipeDecision) {
        val currentState = _uiState.value
        val currentAsset = currentState.currentAsset ?: return
        val currentSize = currentState.assetSizes[currentAsset.id] ?: currentAsset.exifInfo?.fileSizeInBytes

        // 1. Sauvegarde en base locale (Room)
        viewModelScope.launch {
            val config = sessionRepository.sessionConfig.first() ?: return@launch
            swipeDecisionRepository.saveDecision(
                assetId = currentAsset.id,
                albumId = album.id,
                userId = config.userId,
                decision = decision.name,
                fileSize = currentSize,
                isSynced = false // Toujours false au départ, même pour SKIP
            )
        }

        // 2. Mise à jour de l'UI
        val newDecisions = currentState.decisions.toMutableMap()
        newDecisions[currentAsset.id] = decision

        val newSizes = currentState.assetSizes.toMutableMap()
        currentSize?.let { newSizes[currentAsset.id] = it }

        val newHistory = currentState.history.toMutableList()
        newHistory.add(currentAsset.id)

        // Trouver le prochain asset à afficher
        val assets = currentState.assets
        var nextIndex = -1

        // 1. Chercher d'abord la prochaine photo NON TRAITÉE après l'actuelle
        for (i in (currentState.currentIndex + 1) until assets.size) {
            if (!newDecisions.containsKey(assets[i].id)) {
                nextIndex = i
                break
            }
        }

        // 2. Si rien trouvé après, chercher une photo NON TRAITÉE depuis le début (boucle)
        if (nextIndex == -1) {
            for (i in 0 until currentState.currentIndex) {
                if (!newDecisions.containsKey(assets[i].id)) {
                    nextIndex = i
                    break
                }
            }
        }

        // 3. Si TOUT est traité (Mode Revue), on passe simplement au suivant dans l'ordre de la liste
        if (nextIndex == -1) {
            if (currentState.currentIndex + 1 < assets.size) {
                nextIndex = currentState.currentIndex + 1
            } else {
                nextIndex = assets.size // Fin réelle de l'album
            }
        }

        _uiState.value = currentState.copy(
            currentIndex = nextIndex,
            decisions = newDecisions,
            assetSizes = newSizes,
            history = newHistory
        )
        
        updateSummaryStats()

        // Anticipation : charge les détails du prochain
        if (nextIndex < assets.size) {
            loadAssetDetail(assets[nextIndex].id, nextIndex)
        }
    }

    fun toggleFavorite() {
        val currentState = _uiState.value
        val currentAsset = currentState.currentAsset ?: return
        
        val newFavorites = currentState.localFavorites.toMutableMap()
        val currentStatus = currentState.isFavorite(currentAsset.id)
        newFavorites[currentAsset.id] = !currentStatus
        
        _uiState.value = currentState.copy(localFavorites = newFavorites)
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
        val nextMode = if (_uiState.value.cardDisplayMode == com.minos2020.immichswipe.core.CardDisplayMode.FILL) {
            CardDisplayMode.FIT
        } else {
            CardDisplayMode.FILL
        }
        _uiState.value = _uiState.value.copy(cardDisplayMode = nextMode)
    }

    fun toggleMute() {
        _uiState.value = _uiState.value.copy(isMuted = !_uiState.value.isMuted)
    }

    fun undo() {
        val currentState = _uiState.value
        val lastAssetIdFromHistory = currentState.history.lastOrNull()

        viewModelScope.launch {
            val config = sessionRepository.sessionConfig.first() ?: return@launch
            if (lastAssetIdFromHistory != null) {
                // 1. LOGIQUE DE SESSION (Historique présent)
                swipeDecisionRepository.removeDecision(lastAssetIdFromHistory, config.userId)
                
                val newDecisions = currentState.decisions.toMutableMap()
                newDecisions.remove(lastAssetIdFromHistory)

                val newHistory = currentState.history.toMutableList()
                newHistory.removeAt(newHistory.size - 1)

                val previousIndex = currentState.assets.indexOfFirst { it.id == lastAssetIdFromHistory }

                _uiState.value = currentState.copy(
                    currentIndex = if (previousIndex != -1) previousIndex else currentState.currentIndex,
                    decisions = newDecisions,
                    history = newHistory
                )
                
                updateSummaryStats()
                
                if (previousIndex != -1) {
                    loadAssetDetail(lastAssetIdFromHistory, previousIndex)
                }
            } else if (currentState.currentIndex > 0) {
                // 2. LOGIQUE DE REMONTÉE (Historique vide, on recule manuellement)
                // "annule l'asset affiché actuellement, puis passe au précédent"
                val currentAsset = currentState.currentAsset
                val previousIndex = currentState.currentIndex - 1
                val previousAssetId = currentState.assets[previousIndex].id

                // On nettoie UNIQUEMENT l'actuel
                if (currentAsset != null) {
                    swipeDecisionRepository.removeDecision(currentAsset.id, config.userId)
                }
                
                val newDecisions = currentState.decisions.toMutableMap()
                if (currentAsset != null) {
                    newDecisions.remove(currentAsset.id)
                }

                _uiState.value = currentState.copy(
                    currentIndex = previousIndex,
                    decisions = newDecisions
                )
                
                updateSummaryStats()
                
                loadAssetDetail(previousAssetId, previousIndex)
            }
        }
    }

    /**
     * Permet de sauter directement à un asset précis (via la timeline).
     */
    fun onMoveToAsset(index: Int) {
        if (index in 0 until _uiState.value.assets.size) {
            _uiState.value = _uiState.value.copy(currentIndex = index)
            loadAssetDetail(_uiState.value.assets[index].id, index)
        }
    }

    /**
     * Affiche ou cache l'écran de résumé.
     */
    fun toggleSummary(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showSummary = visible)
    }

    /**
     * Annule une décision spécifique (utilisé depuis le résumé).
     */
    fun undoSpecificDecision(assetId: String) {
        val currentState = _uiState.value
        viewModelScope.launch {
            val config = sessionRepository.sessionConfig.first() ?: return@launch
            // 1. Suppression base Room
            swipeDecisionRepository.removeDecision(assetId, config.userId)
            
            // 2. Mise à jour UI
            val newDecisions = currentState.decisions.toMutableMap()
            newDecisions.remove(assetId)
            
            val newHistory = currentState.history.toMutableList()
            newHistory.remove(assetId)
            
            _uiState.value = currentState.copy(
                decisions = newDecisions,
                history = newHistory
            )
            updateSummaryStats()
        }
    }

    /**
     * Applique les décisions (Suppression sur Immich) et marque les assets comme traités localement.
     */
    fun applyChanges() {
        val currentState = _uiState.value
        val decisions = currentState.decisions
        val assetSizes = currentState.assetSizes
        
        val toDelete = decisions.filter { it.value == SwipeDecision.DELETE }.keys.toList()
        val toArchive = decisions.filter { it.value == SwipeDecision.ARCHIVE }.keys.toList()
        val toLock = decisions.filter { it.value == SwipeDecision.LOCK }.keys.toList()
        val toKeep = decisions.filter { it.value == SwipeDecision.KEEP }.keys.toList()
        val toSkip = decisions.filter { it.value == SwipeDecision.SKIP }.keys.toList()
        
        // Gestion des favoris
        val toFavorite = currentState.localFavorites.filter { it.value }.keys.toList()
        val toUnfavorite = currentState.localFavorites.filter { !it.value }.keys.toList()

        viewModelScope.launch {
            AppLogger.i("Swipe", "Application des changements : DELETE(${toDelete.size}), ARCHIVE(${toArchive.size}), LOCK(${toLock.size}), KEEP(${toKeep.size}), SKIP(${toSkip.size})")
            _uiState.value = _uiState.value.copy(isSyncing = true)
            try {
                val config = sessionRepository.sessionConfig.first() ?: return@launch
                // 1. Appels API
                if (toDelete.isNotEmpty()) assetRepository.deleteAssets(toDelete)
                if (toFavorite.isNotEmpty()) assetRepository.updateAssets(toFavorite, isFavorite = true)
                if (toUnfavorite.isNotEmpty()) assetRepository.updateAssets(toUnfavorite, isFavorite = false)
                if (toArchive.isNotEmpty()) assetRepository.updateAssets(toArchive, visibility = "archive")
                if (toLock.isNotEmpty()) assetRepository.updateAssets(toLock, visibility = "locked")

                // 2. Vérification et mise à jour de la base locale
                val freshAssets = assetRepository.getAssetsByAlbum(album.id, includeArchived = true, userId = config.userId).last()
                val freshIds = freshAssets.map { it.id }.toSet()

                // - Identification des succès (ceux qui ont disparu de l'album)
                // Note: LOCK retire l'asset de l'album sur Immich, donc on le traite comme DELETE pour le nettoyage
                val successfullyDisappeared = (toDelete + toLock).filter { !freshIds.contains(it) }
                val failedDeletionsCount = toDelete.size - toDelete.filter { disappeared -> successfullyDisappeared.contains(disappeared) }.size
                
                val successfulKeeps = (toKeep + toArchive + toSkip).filter { freshIds.contains(it) || toSkip.contains(it) }

                // 3. Mise à jour de la base de données locale
                if (successfullyDisappeared.isNotEmpty()) {
                    // On retire de la base locale car ils ne sont plus dans l'album
                    swipeDecisionRepository.removeDecisions(successfullyDisappeared, config.userId)
                }

                if (successfulKeeps.isNotEmpty()) {
                    swipeDecisionRepository.markAsSynced(successfulKeeps, config.userId)
                }

                // 3.5 Enregistrement dans l'historique avec calcul de DELTAS pour éviter les doublons
                val deltaDelete = toDelete.filter { successfullyDisappeared.contains(it) }.size
                val deltaLock = toLock.filter { successfullyDisappeared.contains(it) }.size
                
                // Pour KEEP, ARCHIVE, SKIP : on soustrait l'ancienne valeur si elle existait déjà dans l'historique
                var deltaKeep = toKeep.size
                var deltaArchive = toArchive.size
                var deltaSkip = toSkip.size

                (toKeep + toArchive + toSkip + toDelete + toLock).forEach { id ->
                    val previous = initialSyncedDecisions[id]
                    if (previous != null) {
                        // L'asset avait déjà une décision synchronisée : on annule l'ancienne catégorie
                        when (previous) {
                            SwipeDecision.KEEP -> deltaKeep--
                            SwipeDecision.ARCHIVE -> deltaArchive--
                            SwipeDecision.SKIP -> deltaSkip--
                            else -> {}
                        }
                    }
                }

                swipeDecisionRepository.saveSyncHistory(
                    userId = config.userId,
                    deletedCount = deltaDelete,
                    bytesSaved = toDelete.filter { successfullyDisappeared.contains(it) }.sumOf { assetSizes[it] ?: 0L },
                    keptCount = deltaKeep,
                    archivedCount = deltaArchive,
                    lockedCount = deltaLock,
                    skippedCount = deltaSkip
                )

                AppLogger.i("Swipe", "Synchronisation réussie. ${successfullyDisappeared.size} supprimés/verrouillés, ${successfulKeeps.size} gardés localement.")

                // 4. Feedback utilisateur et rechargement
                if (failedDeletionsCount > 0) {
                    AppLogger.w("Swipe", "$failedDeletionsCount échecs de suppression détectés après vérification")
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        showSummary = false,
                        error = "Attention : $failedDeletionsCount photos n'ont pas pu être supprimées. Vérifiez votre connexion ou vos droits."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        showSummary = false,
                        showSuccessAnimation = true
                    )
                    delay(2500)
                    _uiState.value = _uiState.value.copy(showSuccessAnimation = false)
                }
                
                loadAssetsAndDecisions()
                
            } catch (e: Exception) {
                AppLogger.e("Swipe", "Échec lors de l'application des changements", e)
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    error = "Erreur lors de la synchronisation : ${e.message}"
                )
            }
        }
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
