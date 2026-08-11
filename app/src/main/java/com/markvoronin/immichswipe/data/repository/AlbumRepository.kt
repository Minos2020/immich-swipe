package com.markvoronin.immichswipe.data.repository

import com.markvoronin.immichswipe.data.api.ImmichApi
import com.markvoronin.immichswipe.data.api.SearchAssetsRequest
import com.markvoronin.immichswipe.domain.model.Album
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Repository gérant la récupération des albums depuis le serveur Immich.
 */
class AlbumRepository(
    private val api: ImmichApi
) {
    suspend fun getAlbumsRaw(): List<Album> {
        return api.getAlbums()
    }

    /**
     * Calcule le nombre d'assets archivés pour un album donné.
     */
    suspend fun getAlbumArchiveCount(albumId: String): Int {
        return try {
            api.getSearchStatistics(
                SearchAssetsRequest(albumIds = listOf(albumId), visibility = "archive")
            ).total
        } catch (_: Exception) {
            0
        }
    }

    suspend fun refineAlbumCounts(albums: List<Album>): List<Album> {
        val semaphore = Semaphore(15) // Augmenté un peu pour la performance
        return coroutineScope {
            albums.map { album ->
                async {
                    semaphore.withPermit {
                        val archiveCount = getAlbumArchiveCount(album.id)
                        album.copy(assetCount = (album.assetCount - archiveCount).coerceAtLeast(0))
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Rafraîchit la liste des albums depuis le serveur.
     * @param includeArchived Si vrai, inclut les photos archivées dans le compte total.
     *                        Si faux, soustrait les archives du compte total via search/statistics.
     */
    suspend fun refreshAlbums(includeArchived: Boolean = false): List<Album> {
        val albums = getAlbumsRaw()
        if (includeArchived) return albums
        return refineAlbumCounts(albums)
    }
}
