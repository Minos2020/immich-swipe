package com.minos2020.immichswipe.data.repository

import android.content.Context
import com.minos2020.immichswipe.core.AppLogger
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.SessionConfig
import com.minos2020.immichswipe.core.CardDisplayMode
import com.minos2020.immichswipe.core.SortOrder
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
     * Expose la préférence du mode d'affichage par défaut.
     */
    val defaultLayoutGrid: Flow<Boolean> = dataStore.isDefaultLayoutGrid()

    /**
     * Expose la durée de vie des SKIP (en jours). 0 = Jamais.
     */
    val skipLifespanDays: Flow<Long> = dataStore.getSkipLifespan()

    val showFavoriteButton: Flow<Boolean> = dataStore.isShowFavorite()
    val autoNextOnFav: Flow<Boolean> = dataStore.isAutoNextOnFav()
    val includeArchived: Flow<Boolean> = dataStore.isIncludeArchived()
    val showSwipeButtons: Flow<Boolean> = dataStore.isShowSwipeButtons()
    
    val sortOrder: Flow<SortOrder> = dataStore.getSortOrder().map {
        it?.let { try { SortOrder.valueOf(it) } catch(e: Exception) { SortOrder.CHRONOLOGICAL_DESC } } ?: SortOrder.CHRONOLOGICAL_DESC
    }

    /**
     * Expose le mode d'affichage par défaut des cartes.
     */
    val defaultCardDisplayMode: Flow<CardDisplayMode> = dataStore.getDefaultCardDisplayMode().map {
        it?.let { try { CardDisplayMode.valueOf(it) } catch(e: Exception) { CardDisplayMode.FILL } } ?: CardDisplayMode.FILL
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
    suspend fun saveAutoNextOnFav(autoNextOnFav: Boolean) { dataStore.saveAutoNextOnFav(autoNextOnFav) }
    suspend fun saveIncludeArchived(include: Boolean) { dataStore.saveIncludeArchived(include) }
    suspend fun saveShowSwipeButtons(show: Boolean) { dataStore.saveShowSwipeButtons(show) }
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
