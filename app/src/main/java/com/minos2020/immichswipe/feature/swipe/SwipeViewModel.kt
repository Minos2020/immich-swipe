package com.minos2020.immichswipe.feature.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.core.AppLogger
import com.minos2020.immichswipe.core.CardDisplayMode
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.domain.model.Album
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private fun loadAssetsAndDecisions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                AppLogger.d("Swipe", "Chargement de l'album ${album.albumName} (ID: ${album.id})")
                val config = sessionRepository.sessionConfig.first() ?: return@launch
                val includeArchived = sessionRepository.includeArchived.first()

                // On charge les assets depuis l'API (en passant le userId pour l'album virtuel)
                val assets = assetRepository.getAssetsByAlbum(album.id, includeArchived, config.userId)
                
                // On charge les décisions locales (TOUTES les décisions de l'utilisateur, car elles sont désormais partagées)
                val localDecisions = swipeDecisionRepository.getAllDecisionsForUser(config.userId).first()
                AppLogger.d("Swipe", "${assets.size} assets trouvés, ${localDecisions.size} décisions locales")
                
                // Durée d'expiration en millisecondes
                val lifespanDays = sessionRepository.skipLifespanDays.first()
                val lifespanMs = lifespanDays * 24 * 60 * 60 * 1000L
                val currentTime = System.currentTimeMillis()

                // On nettoie réellement la base de données pour les SKIP expirés
                if (lifespanDays > 0) {
                    swipeDecisionRepository.cleanExpiredSkips(lifespanDays)
                }

                // On transforme la liste de SwipeDecisionEntity en Map<String, SwipeDecision>
                // On filtre les SKIP expirés (au cas où le cleanup prend du temps)
                val decisionMap = mutableMapOf<String, SwipeDecision>()
                val sizeMap = mutableMapOf<String, Long>()

                localDecisions.forEach { entity ->
                    val decision = try {
                        SwipeDecision.valueOf(entity.decision)
                    } catch (e: Exception) {
                        null
                    } ?: return@forEach

                    if (decision == SwipeDecision.SKIP && lifespanDays > 0) {
                        val isExpired = (currentTime - entity.createdAt) > lifespanMs
                        if (isExpired) return@forEach
                    }

                    // On ne met dans l'état de l'UI (Timeline/Pile) que ce qui n'est PAS synchronisé
                    if (!entity.isSynced) {
                        decisionMap[entity.assetId] = decision
                        entity.fileSize?.let { sizeMap[entity.assetId] = it }
                    }
                }

                // Filtrage de la liste des assets pour ne garder que la pile de travail
                // Pile de travail = Assets sans décision OU Assets avec décision NON synchronisée
                // EXCEPTION : Pour l'album virtuel des SKIP synchronisés, on veut justement les voir !
                val isVirtualSkipped = album.id == Album.VIRTUAL_SKIPPED_ID
                
                val syncedIds = if (isVirtualSkipped) {
                    emptySet()
                } else {
                    localDecisions.filter { it.isSynced }.map { it.assetId }.toSet()
                }
                
                val workPileAssets = assets.filter { !syncedIds.contains(it.id) }
                AppLogger.i("Swipe", "Pile de travail: ${workPileAssets.size} assets restants à trier")

                // Dans le cas de l'album virtuel, on veut aussi voir les décisions actuelles (qui sont SKIP et synchronisées)
                // pour que l'utilisateur sache ce qu'il a déjà fait (même si au début ils sont tous SKIP)
                if (isVirtualSkipped) {
                    localDecisions.forEach { entity ->
                        if (entity.decision == SwipeDecision.SKIP.name && entity.isSynced) {
                            decisionMap[entity.assetId] = SwipeDecision.SKIP
                            entity.fileSize?.let { sizeMap[entity.assetId] = it }
                        }
                    }
                }

                // NETTOYAGE : Si on a des décisions locales pour des assets qui n'existent plus
                // dans cet album sur le serveur, on les supprime.
                // Cela peut notamment arriver si des assets présents dans plusieurs albums ont été supprimés dans un des albums.
                // Cela évite les compteurs incohérents (ex: 7/6 triés).
                val serverAssetIds = assets.map { it.id }.toSet()
                val invalidAssetIds = localDecisions.map { it.assetId }.filter { !serverAssetIds.contains(it) }
                
                if (invalidAssetIds.isNotEmpty()) {
                    AppLogger.w("Swipe", "Nettoyage de ${invalidAssetIds.size} décisions orphelines")
                    // Pour le nettoyage au chargement, on est prudent : on ne nettoie que pour CET album
                    // car l'absence dans CET album ne veut pas dire que l'asset est mort sur Immich
                    // (il a pu être simplement retiré de l'album).
                    swipeDecisionRepository.removeDecisions(invalidAssetIds, album.id, config.userId)
                    invalidAssetIds.forEach { 
                        decisionMap.remove(it)
                        sizeMap.remove(it)
                    }
                }

                // On cherche le premier index non traité dans la pile filtrée
                val firstUnprocessedIndex = if (isVirtualSkipped && workPileAssets.isNotEmpty()) {
                    0
                } else {
                    workPileAssets.indexOfFirst { !decisionMap.containsKey(it.id) }
                        .let { if (it == -1) workPileAssets.size else it }
                }

                _uiState.value = _uiState.value.copy(
                    assets = workPileAssets,
                    decisions = decisionMap,
                    assetSizes = sizeMap,
                    history = emptyList(),
                    localFavorites = emptyMap(),
                    currentIndex = firstUnprocessedIndex,
                    isLoading = false
                )
                
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

    fun undo() {
        val currentState = _uiState.value
        val lastAssetIdFromHistory = currentState.history.lastOrNull()

        viewModelScope.launch {
            val config = sessionRepository.sessionConfig.first() ?: return@launch
            if (lastAssetIdFromHistory != null) {
                // 1. LOGIQUE DE SESSION (Historique présent)
                swipeDecisionRepository.removeDecision(lastAssetIdFromHistory, album.id, config.userId)
                
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
                    swipeDecisionRepository.removeDecision(currentAsset.id, album.id, config.userId)
                }
                
                val newDecisions = currentState.decisions.toMutableMap()
                if (currentAsset != null) {
                    newDecisions.remove(currentAsset.id)
                }

                _uiState.value = currentState.copy(
                    currentIndex = previousIndex,
                    decisions = newDecisions
                )
                
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
            swipeDecisionRepository.removeDecision(assetId, album.id, config.userId)
            
            // 2. Mise à jour UI
            val newDecisions = currentState.decisions.toMutableMap()
            newDecisions.remove(assetId)
            
            val newHistory = currentState.history.toMutableList()
            newHistory.remove(assetId)
            
            _uiState.value = currentState.copy(
                decisions = newDecisions,
                history = newHistory
            )
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
                val freshAssets = assetRepository.getAssetsByAlbum(album.id, includeArchived = true, userId = config.userId)
                val freshIds = freshAssets.map { it.id }.toSet()

                // - Identification des succès (ceux qui ont disparu de l'album)
                // Note: LOCK retire l'asset de l'album sur Immich, donc on le traite comme DELETE pour le nettoyage
                val successfullyDisappeared = (toDelete + toLock).filter { !freshIds.contains(it) }
                val failedDeletionsCount = toDelete.size - toDelete.filter { disappeared -> successfullyDisappeared.contains(disappeared) }.size
                
                val successfulKeeps = (toKeep + toArchive + toSkip).filter { freshIds.contains(it) || toSkip.contains(it) }

                // 3. Mise à jour de la base de données locale
                if (successfullyDisappeared.isNotEmpty()) {
                    // On retire de la base locale car ils ne sont plus dans l'album
                    swipeDecisionRepository.removeDecisionsFromAllAlbums(successfullyDisappeared, config.userId)
                }

                if (successfulKeeps.isNotEmpty()) {
                    swipeDecisionRepository.markAsSynced(successfulKeeps, config.userId)
                }

                // 3.5 Enregistrement dans l'historique pour les statistiques
                swipeDecisionRepository.saveSyncHistory(
                    userId = config.userId,
                    deletedCount = toDelete.filter { successfullyDisappeared.contains(it) }.size,
                    bytesSaved = toDelete.filter { successfullyDisappeared.contains(it) }.sumOf { assetSizes[it] ?: 0L },
                    keptCount = toKeep.size,
                    archivedCount = toArchive.size,
                    lockedCount = toLock.filter { successfullyDisappeared.contains(it) }.size,
                    skippedCount = toSkip.size
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
