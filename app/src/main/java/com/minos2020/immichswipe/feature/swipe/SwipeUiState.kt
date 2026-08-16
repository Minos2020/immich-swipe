package com.minos2020.immichswipe.feature.swipe

import com.minos2020.immichswipe.domain.model.Asset
import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.CardDisplayMode
import com.minos2020.immichswipe.core.SwipeSortOrder
import com.minos2020.immichswipe.core.SwipeSortPriority

/**
 * Les différentes décisions possibles pour un asset.
 */
enum class SwipeDecision {
    KEEP, DELETE, SKIP, ARCHIVE, LOCK
}

/**
 * État de la session de tri (Swipe).
 */
data class SwipeUiState(
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val showSuccessAnimation: Boolean = false,
    val showSummary: Boolean = false,
    val albumName: String = "",
    val assets: List<Asset> = emptyList(),
    val currentIndex: Int = 0,
    val decisions: Map<String, SwipeDecision> = emptyMap(),
    val assetSizes: Map<String, Long> = emptyMap(), // Map de AssetID -> Taille connue (persitée ou chargée)
    val history: List<String> = emptyList(), // Liste des IDs swipés pour l'undo
    val error: String? = null,
    val playbackBehavior: PlaybackBehavior = PlaybackBehavior.PAUSE_OTHERS,
    val isSwipeInverted: Boolean = false,
    val fullscreenButtonPosition: IconPosition = IconPosition.TOP_RIGHT,
    val immichButtonPosition: IconPosition = IconPosition.TOP_LEFT,
    val cardDisplayButtonPosition: IconPosition = IconPosition.TOP_RIGHT,
    val muteButtonPosition: IconPosition = IconPosition.BOTTOM_RIGHT,
    val isMuted: Boolean = false,
    val showResetConfirmation: Boolean = false, // Popup de confirmation de reset
    val skipLifespanDays: Long = 0L,
    val showFavoriteButton: Boolean = true,
    val showArchiveButton: Boolean = true,
    val showLockButton: Boolean = true,
    val showKeepDeleteButtons: Boolean = true,
    val showAddToAlbumButton: Boolean = true,
    val showShareButton: Boolean = true,
    val autoNextOnFav: Boolean = true,
    val includeArchived: Boolean = false,
    val localFavorites: Map<String, Boolean> = emptyMap(), // Map de AssetID -> Nouveau statut favori
    val cardDisplayMode: CardDisplayMode = CardDisplayMode.FILL,
    val sortOrder: SwipeSortOrder = SwipeSortOrder.DATE_DESC,
    val sortPriority: SwipeSortPriority = SwipeSortPriority.NONE,
    val shareButtonPosition: IconPosition = IconPosition.TOP_LEFT,

    // États pour l'ajout à un album
    val showAlbumSelection: Boolean = false,
    val availableAlbums: List<Album> = emptyList(),
    val albumSearchQuery: String = "",
    val isAddingToAlbum: Boolean = false,

    // Statistiques pré-calculées pour le Review Screen (Performance)
    val summaryDeletedAssets: List<Asset> = emptyList(),
    val summaryCounts: Map<SwipeDecision, Int> = emptyMap(),
    val summarySizes: Map<SwipeDecision, Long> = emptyMap()
) {
    val currentAsset: Asset? get() = assets.getOrNull(currentIndex)
    
    /**
     * Retourne si un asset est favori en tenant compte des modifs locales.
     */
    fun isFavorite(assetId: String): Boolean {
        return localFavorites[assetId] ?: assets.find { it.id == assetId }?.isFavorite ?: false
    }

    /**
     * Retourne si un asset est archivé en tenant compte des modifs locales.
     */
    fun isArchived(assetId: String): Boolean {
        return decisions[assetId] == SwipeDecision.ARCHIVE || (assets.find { it.id == assetId }?.isArchived ?: false)
    }

    /**
     * Retourne si un asset est verrouillé en tenant compte des modifs locales.
     */
    fun isLocked(assetId: String): Boolean {
        return decisions[assetId] == SwipeDecision.LOCK || (assets.find { it.id == assetId }?.isLocked ?: false)
    }

    // Statistiques de tri
    val totalCount: Int get() = assets.size
    val processedCount: Int get() = decisions.size
    val remainingCount: Int get() = totalCount - processedCount

    val keptCount: Int get() = summaryCounts[SwipeDecision.KEEP] ?: 0
    val deletedCount: Int get() = summaryCounts[SwipeDecision.DELETE] ?: 0
    val skippedCount: Int get() = summaryCounts[SwipeDecision.SKIP] ?: 0
    val archiveCount: Int get() = summaryCounts[SwipeDecision.ARCHIVE] ?: 0
    val lockedCount: Int get() = summaryCounts[SwipeDecision.LOCK] ?: 0
    val allKeptCount: Int get() = keptCount + archiveCount + lockedCount
    
    // Calcul des poids (en bytes)
    val keptSize: Long get() = summarySizes[SwipeDecision.KEEP] ?: 0L
    val deletedSize: Long get() = summarySizes[SwipeDecision.DELETE] ?: 0L
    val skippedSize: Long get() = summarySizes[SwipeDecision.SKIP] ?: 0L
    val archiveSize: Long get() = summarySizes[SwipeDecision.ARCHIVE] ?: 0L
    val lockedSize: Long get() = summarySizes[SwipeDecision.LOCK] ?: 0L
    
    /**
     * Taille restante : Somme des tailles connues + estimation (moyenne) pour les inconnues.
     */
    val remainingSize: Long get() {
        val unprocessed = assets.filter { !decisions.containsKey(it.id) }
        val knownSizes = assetSizes.values.filter { it > 0 }
        val avg = if (knownSizes.isEmpty()) 0L else knownSizes.sum() / knownSizes.size
        
        return unprocessed.sumOf { asset ->
            val size = assetSizes[asset.id] ?: asset.exifInfo?.fileSizeInBytes ?: 0L
            if (size > 0) size else avg
        }
    }

    /**
     * Indique si la taille "Restant" contient des estimations.
     */
    val isRemainingEstimated: Boolean get() = assets.any { !decisions.containsKey(it.id) && (assetSizes[it.id] ?: 0L) == 0L }

    // Progression (0.0f à 1.0f)
    val progress: Float get() = if (totalCount > 0) processedCount.toFloat() / totalCount else 0f
}
