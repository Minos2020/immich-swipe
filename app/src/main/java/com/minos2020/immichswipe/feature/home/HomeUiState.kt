package com.minos2020.immichswipe.feature.home

import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.domain.model.User
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.core.ConnectionStatus
import com.minos2020.immichswipe.core.SortOrder
import com.minos2020.immichswipe.data.local.entity.UserAccountEntity

/**
 * Les différents onglets disponibles dans l'application.
 */
enum class HomeTab {
    HOME, SWIPE, SETTINGS
}

/**
 * État global de l'écran principal (après connexion).
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val user: User? = null,
    val albums: List<Album> = emptyList(),
    val currentTab: HomeTab = HomeTab.HOME,
    val selectedAlbum: Album? = null, // L'album que l'utilisateur a choisi de trier
    val error: String? = null,
    val playbackBehavior: PlaybackBehavior = PlaybackBehavior.PAUSE_OTHERS,
    val showProfilePopup: Boolean = false, // État de visibilité de la fenêtre profil
    val themeMode: AppTheme = AppTheme.SYSTEM,
    val previousTab: HomeTab = HomeTab.HOME,
    // Map pour stocker le nombre de photos triées par albumId
    val albumTreatedCounts: Map<String, Int> = emptyMap(),
    // Map pour stocker le nombre de modifications non synchronisées par albumId
    val albumUnsyncedChanges: Map<String, Int> = emptyMap(),
    val isGridView: Boolean = false, // Toggle entre liste et grille
    val searchQuery: String = "", // Texte de recherche pour filtrer les albums
    val connectionStatus: ConnectionStatus = ConnectionStatus(),
    val allAssetsCount: Int = 0, // Nombre total de médias
    val orphansCount: Int = 0, // Nombre de médias orphelins (sans album)
    val includeArchived: Boolean = false, // Inclure ou non les archives externes
    val sortOrder: SortOrder = SortOrder.CHRONOLOGICAL_DESC, // Ordre de tri
    val virtualNames: Map<String, String> = emptyMap(), // Noms localisés des albums virtuels
    val virtualDescriptions: Map<String, String> = emptyMap(), // Descriptions localisées
    val showStatsPopup: Boolean = false, // Visibilité de la popup stats
    val stats: StatsUiData = StatsUiData(), // Données des stats
    val collapsedCategories: Set<AlbumStatus> = setOf(
        AlbumStatus.IN_PROGRESS,
        AlbumStatus.NOT_STARTED,
        AlbumStatus.COMPLETED
    ), // Catégories réduites par défaut
    val savedAccounts: List<UserAccountEntity> = emptyList(), // Comptes enregistrés
    val isLoggingInToAnotherAccount: Boolean = false, // Si on est en train d'ajouter un compte
    val showBackupWarning: Boolean = false
) {
    /**
     * Retourne la liste des albums filtrée par le texte de recherche.
     */
    val filteredAlbums: List<Album>
        get() {
            val virtuals = mutableListOf<Album>()
            
            // 1. All Assets
            if (allAssetsCount > 0) {
                virtuals.add(Album(
                    id = Album.VIRTUAL_ALL_ID,
                    albumName = virtualNames[Album.VIRTUAL_ALL_ID] ?: "All Assets",
                    description = virtualDescriptions[Album.VIRTUAL_ALL_ID],
                    assetCount = allAssetsCount,
                    albumThumbnailAssetId = null
                ))
            }

            // 3. Orphans
            if (orphansCount > 0) {
                virtuals.add(Album(
                    id = Album.VIRTUAL_ORPHANS_ID,
                    albumName = virtualNames[Album.VIRTUAL_ORPHANS_ID] ?: "Orphans",
                    description = virtualDescriptions[Album.VIRTUAL_ORPHANS_ID],
                    assetCount = orphansCount,
                    albumThumbnailAssetId = null
                ))
            }

            val baseList = virtuals + albums

            return if (searchQuery.isBlank()) {
                baseList
            } else {
                baseList.filter { album ->
                    val nameToMatch = virtualNames[album.id] ?: album.albumName
                    val descToMatch = virtualDescriptions[album.id] ?: album.description ?: ""
                    nameToMatch.contains(searchQuery, ignoreCase = true) || 
                            descToMatch.contains(searchQuery, ignoreCase = true)
                }
            }
        }

    /**
     * Groupe les albums filtrés par état d'avancement.
     */
    val groupedAlbums: Map<AlbumStatus, List<Album>>
        get() {
            val filtered = filteredAlbums
            return filtered.groupBy { album ->
                if (album.id == Album.VIRTUAL_ALL_ID || 
                    album.id == Album.VIRTUAL_ORPHANS_ID) {
                    return@groupBy AlbumStatus.VIRTUAL
                }
                
                val treated = albumTreatedCounts[album.id] ?: 0
                when {
                    treated == 0 -> AlbumStatus.NOT_STARTED
                    treated >= album.assetCount -> AlbumStatus.COMPLETED
                    else -> AlbumStatus.IN_PROGRESS
                }
            }
        }
}

/**
 * Données calculées pour l'affichage des statistiques.
 */
data class StatsUiData(
    val totalDeleted: Int = 0,
    val totalBytesSaved: Long = 0,
    val totalKept: Int = 0,
    val totalArchived: Int = 0,
    val totalLocked: Int = 0,
    val totalAlbums: Int = 0,
    val completedAlbums: Int = 0,
    val weeklyDeleted: Int = 0,
    val weeklyBytesSaved: Long = 0
) {
    val totalSwiped: Int get() = totalDeleted + totalKept + totalArchived + totalLocked
    
    val distribution: Map<String, Float> get() {
        val total = totalSwiped.toFloat()
        if (total == 0f) return emptyMap()
        return mapOf(
            "KEEP" to totalKept / total,
            "DELETE" to totalDeleted / total,
            "ARCHIVE" to totalArchived / total,
            "LOCK" to totalLocked / total
        )
    }
}

/**
 * Représente l'état d'avancement d'un album pour le tri.
 */
enum class AlbumStatus(val label: String) {
    IN_PROGRESS("En cours"),
    NOT_STARTED("Pas commencé"),
    COMPLETED("Terminés"),
    VIRTUAL("Collections")
}
