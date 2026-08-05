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
        private val KEY_FULLSCREEN_ICON_POS = stringPreferencesKey("fullscreen_icon_pos")
        private val KEY_IMMICH_ICON_POS = stringPreferencesKey("immich_icon_pos")
        private val KEY_CARD_DISPLAY_ICON_POS = stringPreferencesKey("card_display_icon_pos")
        private val KEY_DEFAULT_LAYOUT_GRID = androidx.datastore.preferences.core.booleanPreferencesKey("default_layout_grid")
        private val KEY_SHOW_FAVORITE = androidx.datastore.preferences.core.booleanPreferencesKey("show_favorite")
        private val KEY_AUTO_NEXT_ON_FAV = androidx.datastore.preferences.core.booleanPreferencesKey("auto_next_on_fav")
        private val KEY_INCLUDE_ARCHIVED = androidx.datastore.preferences.core.booleanPreferencesKey("include_archived")
        private val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        private val KEY_DEFAULT_CARD_DISPLAY_MODE = stringPreferencesKey("default_card_display_mode")
        private val KEY_SHOW_SWIPE_BUTTONS = androidx.datastore.preferences.core.booleanPreferencesKey("show_swipe_buttons")
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

    fun isDefaultLayoutGrid(): Flow<Boolean> = context.dataStore.data.map { it[KEY_DEFAULT_LAYOUT_GRID] ?: false }

    suspend fun saveDefaultLayoutGrid(isGrid: Boolean) {
        context.dataStore.edit { it[KEY_DEFAULT_LAYOUT_GRID] = isGrid }
    }

    fun isShowFavorite(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_FAVORITE] ?: true }
    suspend fun saveShowFavorite(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_FAVORITE] = show } }

    fun isAutoNextOnFav(): Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_NEXT_ON_FAV] ?: true }
    suspend fun saveAutoNextOnFav(autoNext: Boolean) { context.dataStore.edit { it[KEY_AUTO_NEXT_ON_FAV] = autoNext } }

    fun isIncludeArchived(): Flow<Boolean> = context.dataStore.data.map { it[KEY_INCLUDE_ARCHIVED] ?: true }
    suspend fun saveIncludeArchived(include: Boolean) { context.dataStore.edit { it[KEY_INCLUDE_ARCHIVED] = include } }

    fun getSortOrder(): Flow<String?> = context.dataStore.data.map { it[KEY_SORT_ORDER] }
    suspend fun saveSortOrder(order: String) { context.dataStore.edit { it[KEY_SORT_ORDER] = order } }

    fun getDefaultCardDisplayMode(): Flow<String?> = context.dataStore.data.map { it[KEY_DEFAULT_CARD_DISPLAY_MODE] }
    suspend fun saveDefaultCardDisplayMode(mode: String) { context.dataStore.edit { it[KEY_DEFAULT_CARD_DISPLAY_MODE] = mode } }

    fun isShowSwipeButtons(): Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_SWIPE_BUTTONS] ?: false }
    suspend fun saveShowSwipeButtons(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_SWIPE_BUTTONS] = show } }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}