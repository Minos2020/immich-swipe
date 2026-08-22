package com.minos2020.immichswipe.data.repository

import android.content.Context
import com.minos2020.immichswipe.core.AppLogger
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.SessionConfig
import com.minos2020.immichswipe.core.CardDisplayMode
import com.minos2020.immichswipe.core.SwipeSortOrder
import com.minos2020.immichswipe.core.SwipeSortPriority
import com.minos2020.immichswipe.core.ImmichOpenMode
import com.minos2020.immichswipe.data.datastore.SessionDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Repository gérant la persistence de la session utilisateur.
 * C'est la Source Unique de Vérité (SSOT) pour l'état de connexion.
 */
class SessionRepository(context: Context) {

    private val dataStore = SessionDataStore(context)

    /**
     * Expose la configuration de session actuelle sous forme de Flow.
     * Si l'un des deux éléments (URL ou Clé) est manquant, émet null.
     */
    val sessionConfig: Flow<SessionConfig?> = combine(
        dataStore.getBaseUrl(),
        dataStore.getApiKey(),
        dataStore.getUserId()
    ) { url, key, userId ->
        if (url != null && key != null && userId != null) {
            SessionConfig(url, key, userId)
        } else {
            null
        }
    }

    /**
     * Expose le comportement de lecture actuel.
     * Par défaut: PAUSE_OTHERS.
     */
    val playbackBehavior: Flow<PlaybackBehavior> = dataStore.getAudioFocusMode().map { modeString ->
        if (modeString == null) return@map PlaybackBehavior.PAUSE_OTHERS
        try {
            PlaybackBehavior.valueOf(modeString)
        } catch (e: Exception) {
            PlaybackBehavior.PAUSE_OTHERS
        }
    }

    /**
     * Expose le thème actuel.
     */
    val themeMode: Flow<AppTheme> = dataStore.getThemeMode().map {
        it?.let { try { AppTheme.valueOf(it) } catch(e: Exception) { AppTheme.SYSTEM } } ?: AppTheme.SYSTEM
    }

    /**
     * Expose l'inversion du swipe.
     */
    val swipeInverted: Flow<Boolean> = dataStore.isSwipeInverted()

    /**
     * Expose la position de l'icône plein écran.
     */
    val fullscreenButtonPosition: Flow<IconPosition> = dataStore.getFullscreenIconPosition().map {
        it?.let { try { IconPosition.valueOf(it) } catch(e: Exception) { IconPosition.TOP_RIGHT } } ?: IconPosition.TOP_RIGHT
    }

    /**
     * Expose la position de l'icône Immich.
     */
    val immichButtonPosition: Flow<IconPosition> = dataStore.getImmichIconPosition().map {
        it?.let { try { IconPosition.valueOf(it) } catch(e: Exception) { IconPosition.TOP_LEFT } } ?: IconPosition.TOP_LEFT
    }

    /**
     * Expose la position de l'icône de mode d'affichage.
     */
    val cardDisplayButtonPosition: Flow<IconPosition> = dataStore.getCardDisplayIconPosition().map {
        it?.let { try { IconPosition.valueOf(it) } catch(e: Exception) { IconPosition.BOTTOM_LEFT } } ?: IconPosition.BOTTOM_LEFT
    }

    /**
     * Expose la position de l'icône de sourdine (vidéo).
     */
    val muteButtonPosition: Flow<IconPosition> = dataStore.getMuteIconPosition().map {
        it?.let { try { IconPosition.valueOf(it) } catch(e: Exception) { IconPosition.BOTTOM_RIGHT } } ?: IconPosition.BOTTOM_RIGHT
    }

    val shareButtonPosition: Flow<IconPosition> = dataStore.getShareIconPosition().map {
        it?.let { try { IconPosition.valueOf(it) } catch(e: Exception) { IconPosition.TOP_LEFT } } ?: IconPosition.TOP_LEFT
    }

    val rotateButtonPosition: Flow<IconPosition> = dataStore.getRotateIconPosition().map {
        it?.let { try { IconPosition.valueOf(it) } catch(e: Exception) { IconPosition.BOTTOM_RIGHT } } ?: IconPosition.BOTTOM_RIGHT
    }

    /**
     * Expose la préférence du mode d'affichage par défaut.
     */
    val defaultLayoutGrid: Flow<Boolean> = dataStore.isDefaultLayoutGrid()

    /**
     * Expose la durée de vie des SKIP (en jours). 0 = Jamais.
     */
    val skipLifespanDays: Flow<Long> = dataStore.getSkipLifespan()

    val showFavoriteButton: Flow<Boolean> = dataStore.isShowFavorite()
    val showArchiveButton: Flow<Boolean> = dataStore.isShowArchive()
    val showLockButton: Flow<Boolean> = dataStore.isShowLock()
    val showKeepDeleteButtons: Flow<Boolean> = dataStore.isShowKeepDelete()
    val showAddToAlbumButton: Flow<Boolean> = dataStore.isShowAddToAlbum()
    val showShareButton: Flow<Boolean> = dataStore.isShowShare()
    val showFullscreenButton: Flow<Boolean> = dataStore.isShowFullscreen()
    val showImmichButton: Flow<Boolean> = dataStore.isShowImmich()
    val showCardDisplayButton: Flow<Boolean> = dataStore.isShowCardDisplay()
    val showMuteButton: Flow<Boolean> = dataStore.isShowMute()
    val showRotateButton: Flow<Boolean> = dataStore.isShowRotate()
    val syncRotate: Flow<Boolean> = dataStore.isSyncRotate()
    val autoNextOnFav: Flow<Boolean> = dataStore.isAutoNextOnFav()
    val includeArchived: Flow<Boolean> = dataStore.isIncludeArchived()

    /**
     * Expose le mode d'ouverture d'Immich.
     */
    val immichOpenMode: Flow<ImmichOpenMode> = dataStore.getImmichOpenMode().map {
        it?.let { try { ImmichOpenMode.valueOf(it) } catch(e: Exception) { ImmichOpenMode.APP } } ?: ImmichOpenMode.APP
    }

    /**
     * Expose si l'appui long pour ouvrir sur le web est activé.
     */
    val immichLongPressWeb: Flow<Boolean> = dataStore.isImmichLongPressWeb()

    /**
     * Expose le mode d'affichage par défaut des cartes.
     */
    val defaultCardDisplayMode: Flow<CardDisplayMode> = dataStore.getDefaultCardDisplayMode().map {
        it?.let { try { CardDisplayMode.valueOf(it) } catch(e: Exception) { CardDisplayMode.FILL } } ?: CardDisplayMode.FILL
    }

    /**
     * Expose l'ordre de tri actuel pour le swipe.
     */
    val swipeSortOrder: Flow<SwipeSortOrder> = dataStore.getSwipeSortOrder().map {
        it?.let { try { SwipeSortOrder.valueOf(it) } catch(e: Exception) { SwipeSortOrder.DATE_DESC } } ?: SwipeSortOrder.DATE_DESC
    }

    /**
     * Expose la priorité de tri actuelle pour le swipe.
     */
    val swipeSortPriority: Flow<SwipeSortPriority> = dataStore.getSwipeSortPriority().map {
        it?.let { try { SwipeSortPriority.valueOf(it) } catch(e: Exception) { SwipeSortPriority.NONE } } ?: SwipeSortPriority.NONE
    }

    /**
     * Sauvegarde une nouvelle session. 
     * Grâce au Flow ci-dessus, tous les observateurs seront notifiés automatiquement.
     */
    suspend fun saveSession(baseUrl: String, token: String, userId: String) {
        dataStore.saveSession(baseUrl, token, userId)
    }

    /**
     * Sauvegarde la préférence de lecture.
     */
    suspend fun savePlaybackBehavior(behavior: PlaybackBehavior) {
        dataStore.saveAudioFocusMode(behavior.name)
    }

    /**
     * Sauvegarde le thème.
     */
    suspend fun saveThemeMode(theme: AppTheme) {
        dataStore.saveThemeMode(theme.name)
    }

    /**
     * Sauvegarde l'inversion du swipe.
     */
    suspend fun saveSwipeInverted(inverted: Boolean) {
        dataStore.saveSwipeInverted(inverted)
    }

    /**
     * Sauvegarde la position de l'icône plein écran.
     */
    suspend fun saveFullscreenButtonPosition(pos: IconPosition) {
        dataStore.saveFullscreenIconPosition(pos.name)
    }

    /**
     * Sauvegarde la position de l'icône Immich.
     */
    suspend fun saveImmichButtonPosition(pos: IconPosition) {
        dataStore.saveImmichIconPosition(pos.name)
    }

    /**
     * Sauvegarde la position de l'icône de mode d'affichage.
     */
    suspend fun saveCardDisplayButtonPosition(pos: IconPosition) {
        dataStore.saveCardDisplayIconPosition(pos.name)
    }

    /**
     * Sauvegarde la position de l'icône de sourdine (vidéo).
     */
    suspend fun saveMuteButtonPosition(pos: IconPosition) {
        dataStore.saveMuteIconPosition(pos.name)
    }

    suspend fun saveShareButtonPosition(pos: IconPosition) {
        dataStore.saveShareIconPosition(pos.name)
    }

    suspend fun saveRotateButtonPosition(pos: IconPosition) {
        dataStore.saveRotateIconPosition(pos.name)
    }

    /**
     * Sauvegarde le mode d'affichage par défaut.
     */
    suspend fun saveDefaultLayoutGrid(isGrid: Boolean) {
        dataStore.saveDefaultLayoutGrid(isGrid)
    }

    /**
     * Sauvegarde la durée de vie des SKIP.
     */
    suspend fun saveSkipLifespan(days: Long) {
        dataStore.saveSkipLifespan(days)
    }

    suspend fun saveShowFavorite(show: Boolean) { dataStore.saveShowFavorite(show) }
    suspend fun saveShowArchive(show: Boolean) { dataStore.saveShowArchive(show) }
    suspend fun saveShowLock(show: Boolean) { dataStore.saveShowLock(show) }
    suspend fun saveShowKeepDelete(show: Boolean) { dataStore.saveShowKeepDelete(show) }
    suspend fun saveShowAddToAlbum(show: Boolean) { dataStore.saveShowAddToAlbum(show) }
    suspend fun saveShowShare(show: Boolean) { dataStore.saveShowShare(show) }
    suspend fun saveShowFullscreen(show: Boolean) { dataStore.saveShowFullscreen(show) }
    suspend fun saveShowImmich(show: Boolean) { dataStore.saveShowImmich(show) }
    suspend fun saveShowCardDisplay(show: Boolean) { dataStore.saveShowCardDisplay(show) }
    suspend fun saveShowMute(show: Boolean) { dataStore.saveShowMute(show) }
    suspend fun saveShowRotate(show: Boolean) { dataStore.saveShowRotate(show) }
    suspend fun saveSyncRotate(sync: Boolean) { dataStore.saveSyncRotate(sync) }
    suspend fun saveAutoNextOnFav(autoNextOnFav: Boolean) { dataStore.saveAutoNextOnFav(autoNextOnFav) }
    suspend fun saveIncludeArchived(include: Boolean) { dataStore.saveIncludeArchived(include) }

    suspend fun saveImmichOpenMode(mode: ImmichOpenMode) {
        dataStore.saveImmichOpenMode(mode.name)
    }

    suspend fun saveImmichLongPressWeb(enabled: Boolean) {
        dataStore.saveImmichLongPressWeb(enabled)
    }

    suspend fun saveDefaultCardDisplayMode(mode: CardDisplayMode) {
        dataStore.saveDefaultCardDisplayMode(mode.name)
    }

    /**
     * Sauvegarde l'ordre de tri pour le swipe.
     */
    suspend fun saveSwipeSortOrder(order: SwipeSortOrder) {
        dataStore.saveSwipeSortOrder(order.name)
    }

    /**
     * Sauvegarde la priorité de tri pour le swipe.
     */
    suspend fun saveSwipeSortPriority(priority: SwipeSortPriority) {
        dataStore.saveSwipeSortPriority(priority.name)
    }

    /**
     * Vérifie si une session partielle existe (ancienne version) et la nettoie.
     */
    suspend fun cleanupLegacySession() {
        val url = dataStore.getBaseUrl().first()
        val key = dataStore.getApiKey().first()
        val userId = dataStore.getUserId().first()

        if ((url != null || key != null) && userId == null) {
            dataStore.clearSession()
            AppLogger.i("Auth","User ID was missing from the session config, probably due to to upgrading from room v2" +
                    "A reconnexion is required.")
        }
    }

    /**
     * Supprime la session actuelle (Déconnexion).
     */
    suspend fun clearSession() {
        dataStore.clearSession()
    }
}
