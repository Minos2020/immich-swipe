package com.minos2020.immichswipe.data.repository

import com.minos2020.immichswipe.data.api.DeleteAssetsRequest
import com.minos2020.immichswipe.data.api.ImmichApi
import com.minos2020.immichswipe.data.api.SearchAssetsRequest
import com.minos2020.immichswipe.data.api.UpdateAssetsRequest
import com.minos2020.immichswipe.data.local.dao.AlbumAssetDao
import com.minos2020.immichswipe.data.local.entity.AlbumAssetEntity
import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.domain.model.Asset
import com.minos2020.immichswipe.core.SortOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Repository gérant les photos et vidéos (Assets).
 */
class AssetRepository(
    private val api: ImmichApi,
    private val albumAssetDao: AlbumAssetDao? = null
) {
    /**
     * Récupère toutes les photos d'un album.
     */
    suspend fun getAssetsByAlbum(
        albumId: String,
        includeArchived: Boolean = false,
        userId: String? = null,
        sortOrder: SortOrder = SortOrder.CHRONOLOGICAL_DESC,
        shuffleSeed: Long? = null
    ): List<Asset> {
        // Nettoyage systématique des anciens liens pour cet album et cet utilisateur avant de les recréer
        if (userId != null) {
            albumAssetDao?.clearAlbumRelations(albumId, userId)
        }

        val assets = if (albumId == Album.VIRTUAL_ALL_ID) {
            if (includeArchived) {
                // Return everything (by combining timeline + archive)
                coroutineScope {
                    val timeline = async { fetchAllAssets(SearchAssetsRequest(visibility = "timeline"), albumId, userId) }
                    val archive = async { fetchAllAssets(SearchAssetsRequest(visibility = "archive"), albumId, userId) }
                    (timeline.await() + archive.await())
                }
            } else {
                fetchAllAssets(SearchAssetsRequest(visibility = "timeline"), albumId, userId)
            }
        } else if (albumId == Album.VIRTUAL_ORPHANS_ID) {
            if (includeArchived) {
                coroutineScope {
                    val timeline = async { fetchAllAssets(SearchAssetsRequest(isNotInAlbum = true, visibility = "timeline"), albumId, userId) }
                    val archive = async { fetchAllAssets(SearchAssetsRequest(isNotInAlbum = true, visibility = "archive"), albumId, userId) }
                    (timeline.await() + archive.await())
                }
            } else {
                fetchAllAssets(SearchAssetsRequest(isNotInAlbum = true, visibility = "timeline"), albumId, userId)
            }
        } else {
            if (includeArchived) {
                coroutineScope {
                    val timelineDeferred = async { fetchAllAssets(SearchAssetsRequest(albumIds = listOf(albumId), visibility = "timeline"), albumId, userId) }
                    val archiveDeferred = async { fetchAllAssets(SearchAssetsRequest(albumIds = listOf(albumId), visibility = "archive"), albumId, userId) }
                    
                    val timeline = try { timelineDeferred.await() } catch (_: Exception) { emptyList() }
                    val archive = try { archiveDeferred.await() } catch (_: Exception) { emptyList() }
                    
                    (timeline + archive)
                }
            } else {
                try {
                    fetchAllAssets(SearchAssetsRequest(albumIds = listOf(albumId), visibility = "timeline"), albumId, userId)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

        return when (sortOrder) {
            SortOrder.CHRONOLOGICAL_DESC -> assets.sortedByDescending { it.fileCreatedAt }
            SortOrder.CHRONOLOGICAL_ASC -> assets.sortedBy { it.fileCreatedAt }
            SortOrder.SHUFFLED -> {
                if (shuffleSeed != null) {
                    assets.shuffled(java.util.Random(shuffleSeed))
                } else {
                    assets.shuffled()
                }
            }
        }
    }

    suspend fun getTotalAssetCount(includeArchived: Boolean = false): Int {
        return try {
            if (includeArchived) {
                coroutineScope {
                    val timeline = async { api.getSearchStatistics(SearchAssetsRequest(visibility = "timeline")).total }
                    val archive = async { api.getSearchStatistics(SearchAssetsRequest(visibility = "archive")).total }
                    timeline.await() + archive.await()
                }
            } else {
                api.getSearchStatistics(SearchAssetsRequest(visibility = "timeline")).total
            }
        } catch (_: Exception) {
            0
        }
    }

    suspend fun getAssetCountInAlbums(albumIds: List<String>, includeArchived: Boolean = false): Int {
        if (albumIds.isEmpty()) return 0
        return try {
            if (includeArchived) {
                coroutineScope {
                    val timeline = async { api.getSearchStatistics(SearchAssetsRequest(albumIds = albumIds, visibility = "timeline")).total }
                    val archive = async { api.getSearchStatistics(SearchAssetsRequest(albumIds = albumIds, visibility = "archive")).total }
                    timeline.await() + archive.await()
                }
            } else {
                api.getSearchStatistics(SearchAssetsRequest(albumIds = albumIds, visibility = "timeline")).total
            }
        } catch (_: Exception) {
            0
        }
    }

    suspend fun getOrphansCount(includeArchived: Boolean = false): Int {
        return try {
            if (includeArchived) {
                coroutineScope {
                    val timeline = async { api.getSearchStatistics(SearchAssetsRequest(isNotInAlbum = true, visibility = "timeline")).total }
                    val archive = async { api.getSearchStatistics(SearchAssetsRequest(isNotInAlbum = true, visibility = "archive")).total }
                    timeline.await() + archive.await()
                }
            } else {
                api.getSearchStatistics(SearchAssetsRequest(isNotInAlbum = true, visibility = "timeline")).total
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Récupère TOUS les assets correspondant à une requête en gérant la pagination.
     */
    private suspend fun fetchAllAssets(baseRequest: SearchAssetsRequest, albumIdForMapping: String? = null, userId: String? = null): List<Asset> {
        val allItems = mutableListOf<Asset>()
        var nextToFetch: String? = "1" // On commence à la page 1

        while (nextToFetch != null) {
            val response = api.searchAssets(
                baseRequest.copy(
                    size = 1000,
                    page = nextToFetch.toIntOrNull() ?: 1
                )
            )
            val newAssets = response.assets.items
            allItems.addAll(newAssets)
            
            // Mise à jour de la table de correspondance en arrière-plan
            if (albumIdForMapping != null && userId != null && albumAssetDao != null && newAssets.isNotEmpty()) {
                albumAssetDao.insertAlbumAssets(newAssets.map { AlbumAssetEntity(albumIdForMapping, it.id, userId) })
            }

            // Le serveur nous dit quelle est la prochaine page à charger
            nextToFetch = response.assets.nextPage

            // Sécurité : évite les boucles infinies
            if (allItems.size > 500000) break
        }
        return allItems
    }

    /**
     * Récupère les détails complets (EXIF, taille...) d'un asset spécifique.
     */
    suspend fun getAssetDetail(assetId: String): Asset {
        return api.getAssetDetail(assetId)
    }

    /**
     * Supprime plusieurs assets du serveur Immich.
     */
    suspend fun deleteAssets(assetIds: List<String>) {
        if (assetIds.isNotEmpty()) {
            api.deleteAssets(DeleteAssetsRequest(ids = assetIds, force = false))
        }
    }

    /**
     * Met à jour plusieurs assets sur le serveur Immich.
     */
    suspend fun updateAssets(
        assetIds: List<String>,
        isFavorite: Boolean? = null,
        visibility: String? = null
    ) {
        if (assetIds.isNotEmpty()) {
            api.updateAssets(
                UpdateAssetsRequest(
                    ids = assetIds,
                    isFavorite = isFavorite,
                    visibility = visibility
                )
            )
        }
    }
}
