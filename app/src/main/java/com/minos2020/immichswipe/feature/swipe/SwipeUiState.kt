package com.minos2020.immichswipe.feature.swipe

import com.minos2020.immichswipe.domain.model.Asset
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.CardDisplayMode
import com.minos2020.immichswipe.core.SortOrder

/**
 * Les différentes décisions possibles pour un asset.
 */
enum class SwipeDecision {
    KEEP, DELETE, ARCHIVE, LOCK
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
    val fullscreenButtonPosition: IconPosition = IconPosition.TOP_RIGHT,
    val immichButtonPosition: IconPosition = IconPosition.TOP_LEFT,
    val cardDisplayButtonPosition: IconPosition = IconPosition.BOTTOM_LEFT,
    val muteButtonPosition: IconPosition = IconPosition.BOTTOM_RIGHT,
    val showFavoriteButton: Boolean = true,
    val autoNextOnFav: Boolean = true,
    val includeArchived: Boolean = false,
    val sortOrder: SortOrder = SortOrder.CHRONOLOGICAL_DESC,
    val localFavorites: Map<String, Boolean> = emptyMap(), // Map de AssetID -> Nouveau statut favori
    val cardDisplayMode: CardDisplayMode = CardDisplayMode.FIT,
    val showSwipeButtons: Boolean = false,
    val swapSummaryArchive: Boolean = false,
    val isFullscreenMode: Boolean = false,
    val showResetConfirmation: Boolean = false,
    val syncLocalDeletion: Boolean = false,
    val trashLocalDeletion: Boolean = true,
    val localDeletePendingIntent: android.app.PendingIntent? = null
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

    // Statistiques de tri basées sur les décisions locales non synchronisées.
    // 'assets' représente la pile de travail (non synchronisée).
    // 'decisions' contient les actions déjà effectuées sur cette pile.
    val totalCount: Int get() = assets.size
    val processedCount: Int get() = decisions.size
    val remainingCount: Int get() = totalCount - processedCount

    val keptCount: Int get() = decisions.values.count { it == SwipeDecision.KEEP }
    val allKeptCount: Int get() = decisions.values.count { it == SwipeDecision.KEEP || it == SwipeDecision.ARCHIVE || it == SwipeDecision.LOCK }
    val deletedCount: Int get() = decisions.values.count { it == SwipeDecision.DELETE }
    val favoriteCount: Int get() = assets.count { isFavorite(it.id) && isProcessedKeep(it.id) }
    val favoritesAddedCount: Int get() = localFavorites.count { (id, fav) -> fav && !(assets.find { it.id == id }?.isFavorite ?: false) }
    val favoritesRemovedCount: Int get() = localFavorites.count { (id, fav) -> !fav && (assets.find { it.id == id }?.isFavorite ?: false) }
    val archiveCount: Int get() = decisions.values.count { it == SwipeDecision.ARCHIVE }
    val lockedCount: Int get() = decisions.values.count { it == SwipeDecision.LOCK }

    // Calcul de l'état "traité" (Gardé)
    private fun isProcessedKeep(assetId: String): Boolean {
        val d = decisions[assetId]
        return d == SwipeDecision.KEEP || d == SwipeDecision.ARCHIVE || d == SwipeDecision.LOCK
    }
    
    private fun getEffectiveSize(assetId: String): Long {
        return assetSizes[assetId] ?: assets.find { it.id == assetId }?.exifInfo?.fileSizeInBytes ?: 0L
    }

    /**
     * Calcule la taille moyenne des assets dont le poids est connu.
     */
    private val averageKnownSize: Long get() {
        val knownSizes = assetSizes.values.filter { it > 0 }
        return if (knownSizes.isEmpty()) 0L else knownSizes.sum() / knownSizes.size
    }
    
    // Calcul des poids (en bytes)
    val keptSize: Long get() = assets.filter { decisions[it.id] == SwipeDecision.KEEP }.sumOf { getEffectiveSize(it.id) }
    val deletedSize: Long get() = assets.filter { decisions[it.id] == SwipeDecision.DELETE }.sumOf { getEffectiveSize(it.id) }
    val favoriteSize: Long get() = assets.filter { isFavorite(it.id) && isProcessedKeep(it.id) }.sumOf { getEffectiveSize(it.id) }
    val archiveSize: Long get() = assets.filter { decisions[it.id] == SwipeDecision.ARCHIVE }.sumOf { getEffectiveSize(it.id) }
    val lockedSize: Long get() = assets.filter { decisions[it.id] == SwipeDecision.LOCK }.sumOf { getEffectiveSize(it.id) }
    
    /**
     * Taille restante : Somme des tailles connues + estimation (moyenne) pour les inconnues.
     */
    val remainingSize: Long get() {
        val unprocessed = assets.filter { !decisions.containsKey(it.id) }
        val avg = averageKnownSize
        return unprocessed.sumOf { asset ->
            val size = getEffectiveSize(asset.id)
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
