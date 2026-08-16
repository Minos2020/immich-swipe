package com.minos2020.immichswipe.data.repository

import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.data.api.AddAssetsToAlbumRequest
import com.minos2020.immichswipe.data.api.ImmichApi
import com.minos2020.immichswipe.data.api.SearchAssetsRequest
import com.minos2020.immichswipe.data.local.dao.AlbumAssetDao
import com.minos2020.immichswipe.domain.model.Album
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Repository gérant la récupération des albums depuis le serveur Immich.
 */
class AlbumRepository(
    private val albumAssetDao: AlbumAssetDao? = null
) {
    private val api: ImmichApi get() = SessionManager.api ?: throw IllegalStateException("API not initialized")

    suspend fun getMappingCount(albumId: String, userId: String): Int {
        return albumAssetDao?.getMappingCountForAlbum(albumId, userId) ?: 0
    }

    /**
     * Rafraîchit la liste des albums depuis le serveur.
     * @param includeArchived Si vrai, inclut les photos archivées dans le compte total.
     *                        Si faux, soustrait les archives du compte total via search/statistics.
     */
    suspend fun refreshAlbums(includeArchived: Boolean = false): List<Album> {
        val albums = api.getAlbums()
        if (includeArchived) return albums

        return coroutineScope {
            albums.map { album ->
                async {
                    val archiveCount = try {
                        api.getSearchStatistics(
                            SearchAssetsRequest(albumIds = listOf(album.id), visibility = "archive")
                        ).total
                    } catch (_: Exception) {
                        0
                    }
                    album.copy(assetCount = (album.assetCount - archiveCount).coerceAtLeast(0))
                }
            }.awaitAll()
        }
    }

    /**
     * Ajoute des assets à un album.
     */
    suspend fun addAssetsToAlbum(albumId: String, assetIds: List<String>, userId: String? = null) {
        api.addAssetsToAlbum(albumId, AddAssetsToAlbumRequest(assetIds))
        
        // Mise à jour locale pour que les compteurs du Home soient à jour immédiatement
        if (albumAssetDao != null && userId != null) {
            // Note: on ne connaît pas forcément le statut archivé ici, 
            // mais l'indexation se corrigera plus tard. On met false par défaut.
            albumAssetDao.insertAlbumAssets(assetIds.map { assetId ->
                com.minos2020.immichswipe.data.local.entity.AlbumAssetEntity(albumId, assetId, userId, false)
            })
        }
    }
}
