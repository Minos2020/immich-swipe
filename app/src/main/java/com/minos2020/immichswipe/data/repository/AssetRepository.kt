package com.minos2020.immichswipe.data.repository

import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.data.api.DeleteAssetsRequest
import com.minos2020.immichswipe.data.api.ImmichApi
import com.minos2020.immichswipe.data.api.SearchAssetsRequest
import com.minos2020.immichswipe.data.api.UpdateAssetsRequest
import com.minos2020.immichswipe.data.local.dao.SwipeDecisionDao
import com.minos2020.immichswipe.data.local.dao.AlbumAssetDao
import com.minos2020.immichswipe.data.local.entity.AlbumAssetEntity
import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.domain.model.Asset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Repository gérant les photos et vidéos (Assets).
 */
class AssetRepository(
    private val swipeDecisionDao: SwipeDecisionDao? = null,
    private val albumAssetDao: AlbumAssetDao? = null
) {
    private val api: ImmichApi get() = SessionManager.api ?: throw IllegalStateException("API not initialized")

    /**
     * Récupère les photos d'un album de manière incrémentale (Flow).
     * Émet la liste cumulative à chaque page reçue du serveur.
     */
    fun getAssetsByAlbum(albumId: String, includeArchived: Boolean = false, userId: String? = null): Flow<List<Asset>> = flow {
        // Nettoyage systématique des anciens liens pour cet album avant de les recréer (pour cet utilisateur uniquement)
        if (userId != null) albumAssetDao?.clearAlbumRelations(albumId, userId)

        val allAssets = mutableListOf<Asset>()

        // Cas particulier : Album virtuel "SKIP" (données locales)
        if (albumId == Album.VIRTUAL_SKIPPED_ID && swipeDecisionDao != null && userId != null) {
            // Album virtuel : On récupère les IDs des assets skipped depuis la base locale pour cet utilisateur
            val skippedDecisions = swipeDecisionDao.getSyncedSkipDecisions(userId).first()
            val assetIds = skippedDecisions.map { it.assetId }
            
            if (assetIds.isNotEmpty()) {
                albumAssetDao?.insertAlbumAssets(assetIds.map { AlbumAssetEntity(albumId, it, userId) })
                
                // On charge par paquets de 50 pour simuler un chargement progressif
                assetIds.chunked(50).forEach { batch ->
                    val detailedBatch = coroutineScope {
                        batch.map { id ->
                            async {
                                try { getAssetDetail(id, userId) } catch (_: Exception) { null }
                            }
                        }.awaitAll().filterNotNull()
                            .filter { !it.isTrashed }
                    }
                    allAssets.addAll(detailedBatch)
                    emit(allAssets.sortedByDescending { it.fileCreatedAt })
                }
            }
            return@flow
        }

        // Cas général : Albums Immich ou Collections virtuelles (Search API)
        val baseRequest = when (albumId) {
            Album.VIRTUAL_ALL_ID -> SearchAssetsRequest()
            Album.VIRTUAL_ORPHANS_ID -> SearchAssetsRequest(isNotInAlbum = true)
            else -> SearchAssetsRequest(albumIds = listOf(albumId))
        }

        // On définit les types de visibilité à charger
        val visibilities = mutableListOf("timeline")
        if (includeArchived) visibilities.add("archive")

        for (visibility in visibilities) {
            var nextToFetch: String? = "1"
            while (nextToFetch != null) {
                val response = api.searchAssets(
                    baseRequest.copy(
                        visibility = visibility,
                        size = 1000,
                        page = nextToFetch.toIntOrNull() ?: 1
                    )
                )
                val newItems = response.assets.items
                if (newItems.isNotEmpty()) {
                    allAssets.addAll(newItems)
                    // Mise à jour de l'indexation locale pour les compteurs du Home
                    if (albumAssetDao != null && userId != null) {
                        albumAssetDao.insertAlbumAssets(newItems.map { AlbumAssetEntity(albumId, it.id, userId) })
                    }
                    // On émet la liste triée chronologiquement au fur et à mesure
                    emit(allAssets.sortedByDescending { it.fileCreatedAt })
                }
                nextToFetch = response.assets.nextPage
                if (allAssets.size > 500000) break
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
     * Récupère les détails complets (EXIF, taille, albums...) d'un asset spécifique.
     * Indexe automatiquement l'asset dans ses albums pour synchroniser les compteurs.
     */
    suspend fun getAssetDetail(assetId: String, userId: String? = null): Asset {
        val detail = api.getAssetDetail(assetId)
        
        // Auto-indexation : On récupère les albums de l'asset pour mettre à jour les liens
        if (albumAssetDao != null && userId != null) {
            try {
                val albums = api.getAlbumsForAsset(assetId)
                if (albums.isNotEmpty()) {
                    albumAssetDao.insertAlbumAssets(albums.map { AlbumAssetEntity(it.id, assetId, userId) })
                }
            } catch (_: Exception) {
                // Échec silencieux de l'indexation
            }
        }
        
        return detail
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
