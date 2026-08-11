package com.markvoronin.immichswipe.data.repository

import com.markvoronin.immichswipe.data.api.DeleteAssetsRequest
import com.markvoronin.immichswipe.data.api.ImmichApi
import com.markvoronin.immichswipe.data.api.SearchAssetsRequest
import com.markvoronin.immichswipe.data.api.UpdateAssetsRequest
import com.markvoronin.immichswipe.core.AppLogger
import com.markvoronin.immichswipe.data.local.dao.AlbumAssetDao
import com.markvoronin.immichswipe.data.local.entity.AlbumAssetEntity
import com.markvoronin.immichswipe.domain.model.Album
import com.markvoronin.immichswipe.domain.model.Asset
import com.markvoronin.immichswipe.core.SortOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Un lot d'assets récupérés avec le total disponible.
 */
data class AssetBatch(
    val assets: List<Asset>,
    val total: Int
)

/**
 * Repository gérant les photos et vidéos (Assets).
 */
class AssetRepository(
    private val context: android.content.Context,
    private val api: ImmichApi,
    private val albumAssetDao: AlbumAssetDao? = null
) {
    /**
     * Récupère toutes les photos d'un album sous forme de Flow pour un chargement progressif.
     */
    fun getAssetsByAlbum(
        albumId: String,
        includeArchived: Boolean = false,
        userId: String? = null,
        sortOrder: SortOrder = SortOrder.CHRONOLOGICAL_DESC,
        shuffleSeed: Long? = null
    ): Flow<AssetBatch> = flow {
        // Nettoyage systématique des anciens liens pour cet album et cet utilisateur avant de les recréer
        if (userId != null) {
            albumAssetDao?.clearAlbumRelations(albumId, userId)
        }

        // On a besoin de l'EXIF pour le tri par taille et pour les estimations de poids dans le résumé
        // On l'active désormais systématiquement (sauf si on a des milliers de pages) pour garantir l'exactitude
        // La performance est très similaire sur l'API Search d'Immich.
        val needsExif = true

        // Mapping de l'ordre de tri pour l'API Immich (v3)
        // Note: On n'utilise pas "random" sur le serveur car avec la pagination (pages de 1000),
        // cela provoquerait des doublons et des manques entre les pages.
        // On récupère donc une liste stable (desc) et on mélange localement par chunk.
        val apiOrder = when (sortOrder) {
            SortOrder.CHRONOLOGICAL_DESC -> "desc"
            SortOrder.CHRONOLOGICAL_ASC -> "asc"
            else -> "desc"
        }

        // Si on inclut les archives, on ne spécifie pas de visibilité (null) pour récupérer 
        // à la fois Timeline et Archive de manière entrelacée par le serveur.
        val visibility = if (includeArchived) null else "timeline"

        val baseRequest = when (albumId) {
            Album.VIRTUAL_ALL_ID -> SearchAssetsRequest(
                visibility = visibility,
                withExif = needsExif,
                order = apiOrder
            )
            Album.VIRTUAL_ORPHANS_ID -> SearchAssetsRequest(
                isNotInAlbum = true,
                visibility = visibility,
                withExif = needsExif,
                order = apiOrder
            )
            else -> SearchAssetsRequest(
                albumIds = listOf(albumId),
                visibility = visibility,
                withExif = needsExif,
                order = apiOrder
            )
        }

        // Pour les tris non-chronologiques (Aléatoire, Taille, Type), 
        // on doit charger l'intégralité des assets pour avoir un tri global correct.
        val isGlobalSortNeeded = sortOrder != SortOrder.CHRONOLOGICAL_DESC && sortOrder != SortOrder.CHRONOLOGICAL_ASC

        if (isGlobalSortNeeded) {
            val allAssets = fetchAllAssets(baseRequest, albumId, userId, needsExif)
            
            // Tri Global
            val globallySorted = when (sortOrder) {
                SortOrder.SHUFFLED -> {
                    val random = if (shuffleSeed != null) java.util.Random(shuffleSeed) else java.util.Random()
                    allAssets.shuffled(random)
                }
                SortOrder.SIZE_DESC -> allAssets.sortedByDescending { it.exifInfo?.fileSizeInBytes ?: 0L }
                SortOrder.SIZE_ASC -> allAssets.sortedBy { it.exifInfo?.fileSizeInBytes ?: 0L }
                SortOrder.TYPE_VIDEO_FIRST -> allAssets.sortedWith(compareByDescending<Asset> { it.type == "VIDEO" }.thenByDescending { it.fileCreatedAt })
                SortOrder.TYPE_PHOTO_FIRST -> allAssets.sortedWith(compareByDescending<Asset> { it.type == "IMAGE" }.thenByDescending { it.fileCreatedAt })
                SortOrder.TYPE_VIDEO_FIRST_SHUFFLED -> {
                    val random = if (shuffleSeed != null) java.util.Random(shuffleSeed) else java.util.Random()
                    val videos = allAssets.filter { it.type == "VIDEO" }.shuffled(random)
                    val photos = allAssets.filter { it.type != "VIDEO" }.shuffled(random)
                    videos + photos
                }
                SortOrder.TYPE_PHOTO_FIRST_SHUFFLED -> {
                    val random = if (shuffleSeed != null) java.util.Random(shuffleSeed) else java.util.Random()
                    val photos = allAssets.filter { it.type == "IMAGE" }.shuffled(random)
                    val others = allAssets.filter { it.type != "IMAGE" }.shuffled(random)
                    photos + others
                }
                else -> allAssets // Ne devrait pas arriver
            }

            // Émission par paquets pour la réactivité de l'UI
            globallySorted.chunked(100).forEach { chunk ->
                emit(AssetBatch(chunk, globallySorted.size))
            }
        } else {
            // Pour le chronologique, on garde le flux progressif (déjà trié par le serveur)
            fetchAssetsFlow(baseRequest, albumId, userId, needsExif).collect { emit(it) }
        }
    }.map { batch ->
        // Note: Le tri local est conservé ici comme filet de sécurité pour le mode progressif (chronologique)
        // Pour les autres modes, ils arrivent déjà triés globalement dans le batch.
        if (sortOrder == SortOrder.CHRONOLOGICAL_DESC || sortOrder == SortOrder.CHRONOLOGICAL_ASC) {
            batch
        } else {
            // On laisse passer tel quel car le tri a déjà été fait globalement ci-dessus
            batch
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
     * Récupère l'intégralité des assets d'un coup (nécessaire pour le tri global).
     */
    private suspend fun fetchAllAssets(
        baseRequest: SearchAssetsRequest,
        albumIdForMapping: String? = null,
        userId: String? = null,
        withExif: Boolean = false
    ): List<Asset> {
        val allAssets = mutableListOf<Asset>()
        
        // 1. Récupération du total
        val statsRequest = baseRequest.copy(withExif = withExif)
        val total = try {
            api.getSearchStatistics(statsRequest).total
        } catch (e: Exception) {
            AppLogger.e("AssetRepo", "Erreur stats: ${e.message}")
            0
        }

        if (total <= 0) return emptyList()

        // 2. Chargement de toutes les pages
        val size = 1000
        val totalPages = (total + (size - 1)) / size
        val fetchRequest = baseRequest.copy(size = size, withExif = withExif)

        for (page in 1..totalPages) {
            try {
                val resp = api.searchAssets(fetchRequest.copy(page = page))
                val items = resp.assets.items
                if (albumIdForMapping != null && userId != null && items.isNotEmpty()) {
                    albumAssetDao?.insertAlbumAssets(items.map { AlbumAssetEntity(albumIdForMapping, it.id, userId) })
                }
                allAssets.addAll(items)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e("AssetRepo", "Erreur page $page: ${e.message}")
            }
        }
        return allAssets
    }

    /**
     * Récupère TOUS les assets correspondant à une requête en gérant la pagination de manière progressive via un Flow.
     * Chaque émission contient un lot d'assets et le total de la requête.
     */
    private fun fetchAssetsFlow(
        baseRequest: SearchAssetsRequest,
        albumIdForMapping: String? = null,
        userId: String? = null,
        withExif: Boolean = false
    ): Flow<AssetBatch> = flow {
        // 1. On récupère d'abord le TOTAL réel via l'endpoint dédié
        val statsRequest = baseRequest.copy(withExif = withExif)
        val total = try {
            api.getSearchStatistics(statsRequest).total
        } catch (e: Exception) {
            AppLogger.e("AssetRepo", "Erreur stats: ${e.message}")
            0
        }

        if (total <= 0) return@flow

        // 2. Chargement séquentiel des pages pour streaming immédiat
        val size = 1000
        val totalPages = (total + (size - 1)) / size
        val fetchRequest = baseRequest.copy(size = size, withExif = withExif)

        for (page in 1..totalPages) {
            try {
                val resp = api.searchAssets(fetchRequest.copy(page = page))
                val items = resp.assets.items
                if (albumIdForMapping != null && userId != null && items.isNotEmpty()) {
                    albumAssetDao?.insertAlbumAssets(items.map { AlbumAssetEntity(albumIdForMapping, it.id, userId) })
                }
                emit(AssetBatch(items, total))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e("AssetRepo", "Erreur page $page: ${e.message}")
            }
        }
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
