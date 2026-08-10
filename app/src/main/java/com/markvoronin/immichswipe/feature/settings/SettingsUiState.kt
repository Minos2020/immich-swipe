package com.markvoronin.immichswipe.feature.settings

import com.markvoronin.immichswipe.core.AppTheme
import com.markvoronin.immichswipe.core.CardDisplayMode
import com.markvoronin.immichswipe.core.IconPosition
import com.markvoronin.immichswipe.core.PlaybackBehavior
import com.markvoronin.immichswipe.core.SortOrder

enum class DatabaseScope {
    ALL, USER
}

enum class DatabaseAction {
    DELETE, EXPORT, IMPORT
}

/**
 * État de l'écran des paramètres.
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val userName: String = "",
    val playbackBehavior: PlaybackBehavior = PlaybackBehavior.PAUSE_OTHERS,
    val themeMode: AppTheme = AppTheme.SYSTEM,
    val dynamicColor: Boolean = true,
    val fullscreenButtonPosition: IconPosition = IconPosition.TOP_RIGHT,
    val immichButtonPosition: IconPosition = IconPosition.TOP_LEFT,
    val cardDisplayButtonPosition: IconPosition = IconPosition.BOTTOM_LEFT,
    val muteButtonPosition: IconPosition = IconPosition.BOTTOM_RIGHT,
    val downloadButtonPosition: IconPosition = IconPosition.BOTTOM_LEFT,
    val shareButtonPosition: IconPosition = IconPosition.BOTTOM_RIGHT,
    val showFullscreenButton: Boolean = true,
    val showImmichButton: Boolean = true,
    val showCardDisplayButton: Boolean = false,
    val showMuteButton: Boolean = false,
    val showDownloadButton: Boolean = true,
    val showShareButton: Boolean = true,
    val isDefaultLayoutGrid: Boolean = false,
    val showFavoriteButton: Boolean = true,
    val autoNextOnFav: Boolean = true,
    val includeArchived: Boolean = false,
    val sortOrder: SortOrder = SortOrder.CHRONOLOGICAL_DESC,
    val showLogsDialog: Boolean = false,
    val defaultCardDisplayMode: CardDisplayMode = CardDisplayMode.FIT,
    val showSwipeButtons: Boolean = false,
    val swapSummaryArchive: Boolean = false,
    val syncLocalDeletion: Boolean = false,
    val trashLocalDeletion: Boolean = true,
    val showActionButtonsDialog: Boolean = false,
    
    // Database actions
    val pendingDatabaseAction: DatabaseAction? = null,
    val pendingDatabaseScope: DatabaseScope? = null,
    val databaseActionStatus: String? = null
)
