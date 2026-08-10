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
    /**
     * Rafraîchit la liste des albums depuis le serveur.
     * @param includeArchived Si vrai, inclut les photos archivées dans le compte total.
     *                        Si faux, soustrait les archives du compte total via search/statistics.
     */
    suspend fun refreshAlbums(includeArchived: Boolean = false): List<Album> {
        val albums = api.getAlbums()
        if (includeArchived) return albums

        val semaphore = Semaphore(10) // Autorise 10 requêtes simultanées pour les stats

        return coroutineScope {
            albums.map { album ->
                async {
                    semaphore.withPermit {
                        val archiveCount = try {
                            api.getSearchStatistics(
                                SearchAssetsRequest(albumIds = listOf(album.id), visibility = "archive")
                            ).total
                        } catch (_: Exception) {
                            0
                        }
                        album.copy(assetCount = (album.assetCount - archiveCount).coerceAtLeast(0))
                    }
                }
            }.awaitAll()
        }
    }
}
