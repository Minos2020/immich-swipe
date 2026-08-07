package com.minos2020.immichswipe.data.repository

import com.minos2020.immichswipe.data.api.DeleteAssetsRequest
import com.minos2020.immichswipe.data.api.ImmichApi
import com.minos2020.immichswipe.data.api.SearchAssetsRequest
import com.minos2020.immichswipe.data.api.UpdateAssetsRequest
import com.minos2020.immichswipe.core.AppLogger
import com.minos2020.immichswipe.data.local.dao.AlbumAssetDao
import com.minos2020.immichswipe.data.local.entity.AlbumAssetEntity
import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.domain.model.Asset
import com.minos2020.immichswipe.core.SortOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Repository gérant les photos et vidéos (Assets).
 */
class AssetRepository(
    private val context: android.content.Context,
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
     * Tente de trouver les URIs locaux correspondant à une liste d'assets serveurs.
     * Se base sur le nom de fichier original et la taille.
     */
    fun findLocalUris(assets: List<Asset>): List<android.net.Uri> {
        val uris = mutableListOf<android.net.Uri>()
        val resolver = context.contentResolver
        
        assets.forEach { asset ->
            val fileName = asset.originalFileName ?: return@forEach
            val fileSize = asset.exifInfo?.fileSizeInBytes ?: return@forEach
            
            // On cherche par taille d'abord (très fiable pour identifier un doublon exact)
            // combiné avec le nom de fichier (même si l'extension change JPG/JPEG)
            val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            val selection = "${android.provider.MediaStore.MediaColumns.SIZE} = ?"
            val selectionArgs = arrayOf(fileSize.toString())
            
            fun searchIn(contentUri: android.net.Uri) {
                resolver.query(contentUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val localName = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME))
                        
                        // Vérification de nom souple (sans extension ou insensible à la casse)
                        val nameMatch = localName.equals(fileName, ignoreCase = true) || 
                                       localName.substringBeforeLast('.').equals(fileName.substringBeforeLast('.'), ignoreCase = true)
                        
                        if (nameMatch) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                            uris.add(android.content.ContentUris.withAppendedId(contentUri, id))
                            return // Trouvé ! On passe à l'asset suivant
                        }
                    }
                }
            }

            searchIn(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            searchIn(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
        return uris
    }

    /**
     * Crée une demande de suppression locale via MediaStore (Android 10+).
     */
    fun createLocalDeleteRequest(uris: List<android.net.Uri>): android.app.PendingIntent? {
        if (uris.isEmpty() || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
        return android.provider.MediaStore.createDeleteRequest(context.contentResolver, uris)
    }

    /**
     * Crée une demande de mise à la corbeille locale via MediaStore (Android 11+).
     */
    fun createLocalTrashRequest(uris: List<android.net.Uri>, trash: Boolean): android.app.PendingIntent? {
        if (uris.isEmpty() || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
        return android.provider.MediaStore.createTrashRequest(context.contentResolver, uris, trash)
    }

    /**
     * Récupère TOUS les assets correspondant à une requête en gérant la pagination de manière parallélisée.
     */
    private suspend fun fetchAllAssets(baseRequest: SearchAssetsRequest, albumIdForMapping: String? = null, userId: String? = null): List<Asset> {
        // 1. On récupère d'abord le TOTAL réel via l'endpoint dédié (plus fiable que le champ total de search)
        val statsRequest = baseRequest.copy(withExif = false)
        val total = try {
            api.getSearchStatistics(statsRequest).total
        } catch (e: Exception) {
            AppLogger.e("AssetRepo", "Erreur stats: ${e.message}")
            0
        }

        if (total <= 0) return emptyList()

        // 2. On prépare le chargement parallèle par pages de 1000
        val size = 1000
        val totalPages = (total + (size - 1)) / size
        val allItems = java.util.Collections.synchronizedList(mutableListOf<Asset>())
        
        // On limite le parallélisme à 5 requêtes simultanées
        val semaphore = Semaphore(5)
        val fetchRequest = baseRequest.copy(size = size, withExif = false)

        coroutineScope {
            val deferredPages = (1..totalPages).map { page ->
                async {
                    semaphore.withPermit {
                        try {
                            val resp = api.searchAssets(fetchRequest.copy(page = page))
                            val items = resp.assets.items
                            if (albumIdForMapping != null && userId != null && items.isNotEmpty()) {
                                albumAssetDao?.insertAlbumAssets(items.map { AlbumAssetEntity(albumIdForMapping, it.id, userId) })
                            }
                            items
                        } catch (e: Exception) {
                            AppLogger.e("AssetRepo", "Erreur page $page: ${e.message}")
                            emptyList<Asset>()
                        }
                    }
                }
            }
            allItems.addAll(deferredPages.awaitAll().flatten())
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
