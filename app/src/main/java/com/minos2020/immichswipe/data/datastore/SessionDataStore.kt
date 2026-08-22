package com.minos2020.immichswipe.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// extension DataStore attachée au Context
val Context.dataStore by preferencesDataStore(name = "session")

class SessionDataStore(private val context: Context) {

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_AUDIO_FOCUS = stringPreferencesKey("audio_focus")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_SWIPE_INVERTED = androidx.datastore.preferences.core.booleanPreferencesKey("swipe_inverted")
        private val KEY_FULLSCREEN_ICON_POS = stringPreferencesKey("fullscreen_icon_pos")
        private val KEY_IMMICH_ICON_POS = stringPreferencesKey("immich_icon_pos")
        private val KEY_CARD_DISPLAY_ICON_POS = stringPreferencesKey("card_display_icon_pos")
        private val KEY_MUTE_ICON_POS = stringPreferencesKey("mute_icon_pos")
        private val KEY_SHARE_ICON_POS = stringPreferencesKey("share_icon_pos")
        private val KEY_ROTATE_ICON_POS = stringPreferencesKey("rotate_icon_pos")
        private val KEY_DEFAULT_LAYOUT_GRID = androidx.datastore.preferences.core.booleanPreferencesKey("default_layout_grid")
        private val KEY_SKIP_LIFESPAN = androidx.datastore.preferences.core.longPreferencesKey("skip_lifespan")
        private val KEY_SHOW_FAVORITE = androidx.datastore.preferences.core.booleanPreferencesKey("show_favorite")
        private val KEY_SHOW_ARCHIVE = androidx.datastore.preferences.core.booleanPreferencesKey("show_archive")
        private val KEY_SHOW_LOCK = androidx.datastore.preferences.core.booleanPreferencesKey("show_lock")
        private val KEY_SHOW_KEEP_DELETE = androidx.datastore.preferences.core.booleanPreferencesKey("show_keep_delete")
        private val KEY_SHOW_ADD_TO_ALBUM = androidx.datastore.preferences.core.booleanPreferencesKey("show_add_to_album")
        private val KEY_SHOW_SHARE = androidx.datastore.preferences.core.booleanPreferencesKey("show_share")
        private val KEY_SHOW_FULLSCREEN = androidx.datastore.preferences.core.booleanPreferencesKey("show_fullscreen")
        private val KEY_SHOW_IMMICH = androidx.datastore.preferences.core.booleanPreferencesKey("show_immich")
        private val KEY_SHOW_CARD_DISPLAY = androidx.datastore.preferences.core.booleanPreferencesKey("show_card_display")
        private val KEY_SHOW_MUTE = androidx.datastore.preferences.core.booleanPreferencesKey("show_mute")
        private val KEY_SHOW_ROTATE = androidx.datastore.preferences.core.booleanPreferencesKey("show_rotate")
        private val KEY_SYNC_ROTATE = androidx.datastore.preferences.core.booleanPreferencesKey("sync_rotate")
        private val KEY_AUTO_NEXT_ON_FAV = androidx.datastore.preferences.core.booleanPreferencesKey("auto_next_on_fav")
        private val KEY_INCLUDE_ARCHIVED = androidx.datastore.preferences.core.booleanPreferencesKey("include_archived")
        private val KEY_DEFAULT_CARD_DISPLAY_MODE = stringPreferencesKey("default_card_display_mode")
        private val KEY_SWIPE_SORT_ORDER = stringPreferencesKey("swipe_sort_order")
        private val KEY_SWIPE_SORT_PRIORITY = stringPreferencesKey("swipe_sort_priority")
        private val KEY_IMMICH_OPEN_MODE = stringPreferencesKey("immich_open_mode")
        private val KEY_IMMICH_LONG_PRESS_WEB = androidx.datastore.preferences.core.booleanPreferencesKey("immich_long_press_web")
    }

    suspend fun saveSession(baseUrl: String, apiKey: String, userId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl
            prefs[KEY_API_KEY] = apiKey
            prefs[KEY_USER_ID] = userId
        }
    }

    fun getBaseUrl(): Flow<String?> {
        return context.dataStore.data.map { it[KEY_BASE_URL] }
    }

    fun getApiKey(): Flow<String?> {
        return context.dataStore.data.map { it[KEY_API_KEY] }
    }

    fun getUserId(): Flow<String?> {
        return context.dataStore.data.map { it[KEY_USER_ID] }
    }

    fun getAudioFocusMode(): Flow<String?> {
        return context.dataStore.data.map { it[KEY_AUDIO_FOCUS] }
    }

    suspend fun saveAudioFocusMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUDIO_FOCUS] = mode
        }
    }

    fun getThemeMode(): Flow<String?> = context.dataStore.data.map { it[KEY_THEME_MODE] }
    
    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    fun isSwipeInverted(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SWIPE_INVERTED] ?: false }
    
    suspend fun saveSwipeInverted(inverted: Boolean) {
        context.dataStore.edit { it[KEY_SWIPE_INVERTED] = inverted }
    }

    fun getFullscreenIconPosition(): Flow<String?> = context.dataStore.data.map { it[KEY_FULLSCREEN_ICON_POS] }
    
    suspend fun saveFullscreenIconPosition(pos: String) {
        context.dataStore.edit { it[KEY_FULLSCREEN_ICON_POS] = pos }
    }

    fun getImmichIconPosition(): Flow<String?> = context.dataStore.data.map { it[KEY_IMMICH_ICON_POS] }

    suspend fun saveImmichIconPosition(pos: String) {
        context.dataStore.edit { it[KEY_IMMICH_ICON_POS] = pos }
    }

    fun getCardDisplayIconPosition(): Flow<String?> = context.dataStore.data.map { it[KEY_CARD_DISPLAY_ICON_POS] }

    suspend fun saveCardDisplayIconPosition(pos: String) {
        context.dataStore.edit { it[KEY_CARD_DISPLAY_ICON_POS] = pos }
    }

    fun getMuteIconPosition(): Flow<String?> = context.dataStore.data.map { it[KEY_MUTE_ICON_POS] }

    suspend fun saveMuteIconPosition(pos: String) {
        context.dataStore.edit { it[KEY_MUTE_ICON_POS] = pos }
    }

    fun getShareIconPosition(): Flow<String?> = context.dataStore.data.map { it[KEY_SHARE_ICON_POS] }

    suspend fun saveShareIconPosition(pos: String) {
        context.dataStore.edit { it[KEY_SHARE_ICON_POS] = pos }
    }

    fun getRotateIconPosition(): Flow<String?> = context.dataStore.data.map { it[KEY_ROTATE_ICON_POS] }

    suspend fun saveRotateIconPosition(pos: String) {
        context.dataStore.edit { it[KEY_ROTATE_ICON_POS] = pos }
    }

    fun isDefaultLayoutGrid(): Flow<Boolean> = context.dataStore.data.map { it[KEY_DEFAULT_LAYOUT_GRID] ?: false }

    suspend fun saveDefaultLayoutGrid(isGrid: Boolean) {
        context.dataStore.edit { it[KEY_DEFAULT_LAYOUT_GRID] = isGrid }
    }

    fun getSkipLifespan(): Flow<Long> = context.dataStore.data.map { it[KEY_SKIP_LIFESPAN] ?: 0L }

    suspend fun saveSkipLifespan(days: Long) {
        context.dataStore.edit { it[KEY_SKIP_LIFESPAN] = days }
    }

    fun isShowFavorite(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_FAVORITE] ?: true }
    suspend fun saveShowFavorite(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_FAVORITE] = show } }

    fun isShowArchive(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_ARCHIVE] ?: true }
    suspend fun saveShowArchive(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_ARCHIVE] = show } }

    fun isShowLock(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_LOCK] ?: true }
    suspend fun saveShowLock(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_LOCK] = show } }

    fun isShowKeepDelete(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_KEEP_DELETE] ?: true }
    suspend fun saveShowKeepDelete(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_KEEP_DELETE] = show } }

    fun isShowAddToAlbum(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_ADD_TO_ALBUM] ?: true }
    suspend fun saveShowAddToAlbum(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_ADD_TO_ALBUM] = show } }

    fun isShowShare(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_SHARE] ?: true }
    suspend fun saveShowShare(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_SHARE] = show } }

    fun isShowFullscreen(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_FULLSCREEN] ?: true }
    suspend fun saveShowFullscreen(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_FULLSCREEN] = show } }

    fun isShowImmich(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_IMMICH] ?: true }
    suspend fun saveShowImmich(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_IMMICH] = show } }

    fun isShowCardDisplay(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_CARD_DISPLAY] ?: true }
    suspend fun saveShowCardDisplay(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_CARD_DISPLAY] = show } }

    fun isShowMute(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_MUTE] ?: true }
    suspend fun saveShowMute(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_MUTE] = show } }

    fun isShowRotate(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_ROTATE] ?: true }
    suspend fun saveShowRotate(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_ROTATE] = show } }

    fun isSyncRotate(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SYNC_ROTATE] ?: true }
    suspend fun saveSyncRotate(sync: Boolean) { context.dataStore.edit { it[KEY_SYNC_ROTATE] = sync } }

    fun isAutoNextOnFav(): Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_NEXT_ON_FAV] ?: false }
    suspend fun saveAutoNextOnFav(autoNext: Boolean) { context.dataStore.edit { it[KEY_AUTO_NEXT_ON_FAV] = autoNext } }

    fun isIncludeArchived(): Flow<Boolean> = context.dataStore.data.map { it[KEY_INCLUDE_ARCHIVED] ?: true }
    suspend fun saveIncludeArchived(include: Boolean) { context.dataStore.edit { it[KEY_INCLUDE_ARCHIVED] = include } }

    fun getDefaultCardDisplayMode(): Flow<String?> = context.dataStore.data.map { it[KEY_DEFAULT_CARD_DISPLAY_MODE] }
    suspend fun saveDefaultCardDisplayMode(mode: String) { context.dataStore.edit { it[KEY_DEFAULT_CARD_DISPLAY_MODE] = mode } }

    fun getSwipeSortOrder(): Flow<String?> = context.dataStore.data.map { it[KEY_SWIPE_SORT_ORDER] }
    suspend fun saveSwipeSortOrder(order: String) { context.dataStore.edit { it[KEY_SWIPE_SORT_ORDER] = order } }

    fun getSwipeSortPriority(): Flow<String?> = context.dataStore.data.map { it[KEY_SWIPE_SORT_PRIORITY] }
    suspend fun saveSwipeSortPriority(priority: String) { context.dataStore.edit { it[KEY_SWIPE_SORT_PRIORITY] = priority } }

    fun getImmichOpenMode(): Flow<String?> = context.dataStore.data.map { it[KEY_IMMICH_OPEN_MODE] }
    suspend fun saveImmichOpenMode(mode: String) { context.dataStore.edit { it[KEY_IMMICH_OPEN_MODE] = mode } }

    fun isImmichLongPressWeb(): Flow<Boolean> = context.dataStore.data.map { it[KEY_IMMICH_LONG_PRESS_WEB] ?: true }
    suspend fun saveImmichLongPressWeb(enabled: Boolean) { context.dataStore.edit { it[KEY_IMMICH_LONG_PRESS_WEB] = enabled } }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}