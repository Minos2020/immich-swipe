package com.markvoronin.immichswipe.data.repository

import android.content.Context
import com.markvoronin.immichswipe.core.AppLogger
import com.markvoronin.immichswipe.core.AppTheme
import com.markvoronin.immichswipe.core.IconPosition
import com.markvoronin.immichswipe.core.PlaybackBehavior
import com.markvoronin.immichswipe.core.SessionConfig
import com.markvoronin.immichswipe.core.CardDisplayMode
import com.markvoronin.immichswipe.core.SortOrder
import com.markvoronin.immichswipe.data.datastore.SessionDataStore
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
     * Expose si les couleurs dynamiques (Material You) sont activées.
     */
    val dynamicColor: Flow<Boolean> = dataStore.isDynamicColor()

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
     * Expose la position de l'icône mute.
     */
    val muteButtonPosition: Flow<IconPosition> = dataStore.getMuteIconPosition().map {
        it?.let { try { IconPosition.valueOf(it) } catch(e: Exception) { IconPosition.BOTTOM_RIGHT } } ?: IconPosition.BOTTOM_RIGHT
    }

    /**
     * Expose la position de l'icône download.
     */
    val downloadButtonPosition: Flow<IconPosition> = dataStore.getDownloadIconPosition().map {
        it?.let { try { IconPosition.valueOf(it) } catch(e: Exception) { IconPosition.BOTTOM_LEFT } } ?: IconPosition.BOTTOM_LEFT
    }

    /**
     * Expose la position de l'icône share.
     */
    val shareButtonPosition: Flow<IconPosition> = dataStore.getShareIconPosition().map {
        it?.let { try { IconPosition.valueOf(it) } catch(e: Exception) { IconPosition.BOTTOM_RIGHT } } ?: IconPosition.BOTTOM_RIGHT
    }

    val showFullscreenButton: Flow<Boolean> = dataStore.isShowFullscreenIcon()
    val showImmichButton: Flow<Boolean> = dataStore.isShowImmichIcon()
    val showCardDisplayButton: Flow<Boolean> = dataStore.isShowCardDisplayIcon()
    val showMuteButton: Flow<Boolean> = dataStore.isShowMuteIcon()
    val showDownloadButton: Flow<Boolean> = dataStore.isShowDownloadIcon()
    val showShareButton: Flow<Boolean> = dataStore.isShowShareIcon()

    /**
     * Expose la préférence du mode d'affichage par défaut.
     */
    val defaultLayoutGrid: Flow<Boolean> = dataStore.isDefaultLayoutGrid()

    val showFavoriteButton: Flow<Boolean> = dataStore.isShowFavorite()
    val autoNextOnFav: Flow<Boolean> = dataStore.isAutoNextOnFav()
    val includeArchived: Flow<Boolean> = dataStore.isIncludeArchived()
    val showSwipeButtons: Flow<Boolean> = dataStore.isShowSwipeButtons()
    val swapSummaryArchive: Flow<Boolean> = dataStore.isSwapSummaryArchive()
    val backupWarningShown: Flow<Boolean> = dataStore.isBackupWarningShown()
    val syncLocalDeletion: Flow<Boolean> = dataStore.isSyncLocalDeletion()
    val trashLocalDeletion: Flow<Boolean> = dataStore.isTrashLocalDeletion()
    
    val sortOrder: Flow<SortOrder> = dataStore.getSortOrder().map {
        it?.let { try { SortOrder.valueOf(it) } catch(e: Exception) { SortOrder.CHRONOLOGICAL_DESC } } ?: SortOrder.CHRONOLOGICAL_DESC
    }

    /**
     * Expose le mode d'affichage par défaut des cartes.
     */
    val defaultCardDisplayMode: Flow<CardDisplayMode> = dataStore.getDefaultCardDisplayMode().map {
        it?.let { try { CardDisplayMode.valueOf(it) } catch(e: Exception) { CardDisplayMode.FIT } } ?: CardDisplayMode.FIT
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
     * Sauvegarde si les couleurs dynamiques sont activées.
     */
    suspend fun saveDynamicColor(enabled: Boolean) {
        dataStore.saveDynamicColor(enabled)
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
     * Sauvegarde la position de l'icône mute.
     */
    suspend fun saveMuteButtonPosition(pos: IconPosition) {
        dataStore.saveMuteIconPosition(pos.name)
    }

    /**
     * Sauvegarde la position de l'icône download.
     */
    suspend fun saveDownloadButtonPosition(pos: IconPosition) {
        dataStore.saveDownloadIconPosition(pos.name)
    }

    /**
     * Sauvegarde la position de l'icône share.
     */
    suspend fun saveShareButtonPosition(pos: IconPosition) {
        dataStore.saveShareIconPosition(pos.name)
    }

    suspend fun saveShowFullscreenButton(show: Boolean) { dataStore.saveShowFullscreenIcon(show) }
    suspend fun saveShowImmichButton(show: Boolean) { dataStore.saveShowImmichIcon(show) }
    suspend fun saveShowCardDisplayButton(show: Boolean) { dataStore.saveShowCardDisplayIcon(show) }
    suspend fun saveShowMuteButton(show: Boolean) { dataStore.saveShowMuteIcon(show) }
    suspend fun saveShowDownloadButton(show: Boolean) { dataStore.saveShowDownloadIcon(show) }
    suspend fun saveShowShareButton(show: Boolean) { dataStore.saveShowShareIcon(show) }

    /**
     * Sauvegarde le mode d'affichage par défaut.
     */
    suspend fun saveDefaultLayoutGrid(isGrid: Boolean) {
        dataStore.saveDefaultLayoutGrid(isGrid)
    }

    suspend fun saveShowFavorite(show: Boolean) { dataStore.saveShowFavorite(show) }
    suspend fun saveAutoNextOnFav(autoNextOnFav: Boolean) { dataStore.saveAutoNextOnFav(autoNextOnFav) }
    suspend fun saveIncludeArchived(include: Boolean) { dataStore.saveIncludeArchived(include) }
    suspend fun saveShowSwipeButtons(show: Boolean) { dataStore.saveShowSwipeButtons(show) }
    suspend fun saveSwapSummaryArchive(swap: Boolean) { dataStore.saveSwapSummaryArchive(swap) }
    suspend fun saveBackupWarningShown(shown: Boolean) { dataStore.saveBackupWarningShown(shown) }
    suspend fun saveSyncLocalDeletion(sync: Boolean) { dataStore.saveSyncLocalDeletion(sync) }
    suspend fun saveTrashLocalDeletion(trash: Boolean) { dataStore.saveTrashLocalDeletion(trash) }
    suspend fun saveSortOrder(order: SortOrder) { dataStore.saveSortOrder(order.name) }

    suspend fun saveDefaultCardDisplayMode(mode: CardDisplayMode) {
        dataStore.saveDefaultCardDisplayMode(mode.name)
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
