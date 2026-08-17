package com.minos2020.immichswipe.feature.settings

import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.core.CardDisplayMode
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.PlaybackBehavior

enum class DatabaseScope {
    ALL, USER
}

enum class DatabaseAction {
    DELETE, EXPORT, IMPORT, CLEAR_CACHE
}

/**
 * État de l'écran des paramètres.
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val userName: String = "",
    val playbackBehavior: PlaybackBehavior = PlaybackBehavior.PAUSE_OTHERS,
    val themeMode: AppTheme = AppTheme.SYSTEM,
    val isSwipeInverted: Boolean = false,
    val fullscreenButtonPosition: IconPosition = IconPosition.TOP_RIGHT,
    val immichButtonPosition: IconPosition = IconPosition.TOP_LEFT,
    val cardDisplayButtonPosition: IconPosition = IconPosition.BOTTOM_LEFT,
    val muteButtonPosition: IconPosition = IconPosition.BOTTOM_RIGHT,
    val isDefaultLayoutGrid: Boolean = false,
    val skipLifespanDays: Long = 0L,
    val showSkipLifespanWarning: Long? = null, // Contient la valeur cible si le dialogue est affiché
    val showCustomSkipDialog: Boolean = false, // Gère l'affichage du slider personnalisé
    val showFavoriteButton: Boolean = true,
    val showArchiveButton: Boolean = true,
    val showLockButton: Boolean = true,
    val showKeepDeleteButtons: Boolean = true,
    val showAddToAlbumButton: Boolean = true,
    val showShareButton: Boolean = true,
    val showFullscreenButton: Boolean = true,
    val showImmichButton: Boolean = true,
    val showCardDisplayButton: Boolean = true,
    val showMuteButton: Boolean = true,
    val showRotateButton: Boolean = true,
    val syncRotate: Boolean = true,
    val autoNextOnFav: Boolean = true,
    val includeArchived: Boolean = false,
    val showLogsDialog: Boolean = false,
    val showClearLogsConfirmation: Boolean = false,
    val defaultCardDisplayMode: CardDisplayMode = CardDisplayMode.FILL,
    val shareButtonPosition: IconPosition = IconPosition.TOP_LEFT,
    val rotateButtonPosition: IconPosition = IconPosition.BOTTOM_RIGHT,
    
    // Database actions
    val pendingDatabaseAction: DatabaseAction? = null,
    val pendingDatabaseScope: DatabaseScope? = null,
    val databaseActionStatus: String? = null
)
