package com.minos2020.immichswipe.data.api

import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.domain.model.Asset
import com.minos2020.immichswipe.domain.model.User
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.PUT

interface ImmichApi {

    @GET("api/users/me")
    suspend fun getCurrentUser(): User

    @GET("api/albums")
    suspend fun getAlbums(): List<Album>

//    @GET("api/albums/{id}")
//    suspend fun getAlbumInfo(@Path("id") albumId: String): AlbumWithAssets

    // Nouveau Endpoint à utiliser à partir de la version v3 du serveur Immich
    @POST("api/search/metadata")
    suspend fun searchAssets(@Body request: SearchAssetsRequest): SearchResponse

    @POST("api/search/statistics")
    suspend fun getSearchStatistics(@Body request: SearchAssetsRequest): SearchStatisticsResponse

    @GET("api/assets/{id}")
    suspend fun getAssetDetail(@Path("id") assetId: String): Asset

    /**
     * Supprime une liste d'assets (les déplace vers la corbeille).
     * Retourne 204 No Content en cas de succès.
     */
    @HTTP(method = "DELETE", path = "api/assets", hasBody = true)
    suspend fun deleteAssets(@Body request: DeleteAssetsRequest)

    @PUT("api/assets")
    suspend fun updateAssets(@Body request: UpdateAssetsRequest)
}

/**
 * Corps de la requête pour mettre à jour des assets.
 */
data class UpdateAssetsRequest(
    val ids: List<String>,
    val isFavorite: Boolean? = null,
    val visibility: String? = null // archive, timeline, hidden, locked
)

/**
 * Corps de la requête pour supprimer des assets.
 */
data class DeleteAssetsRequest(
    val ids: List<String>,
    val force: Boolean = false
)

/**
 * Corps de la requête pour récupérer des assets.
 * Basé sur l'endpoint /api/search/metadata
 */
data class SearchAssetsRequest(
    val albumIds: List<String>? = null,
    val ids: List<String>? = null, // A vérifier si supporté ou si c'est 'id' unique
    val isNotInAlbum: Boolean? = null,
    val size: Int = 1000,
    val page: Int = 1,
    val visibility: String? = null, // archive, timeline, hidden, locked
    val type: String? = null, // IMAGE, VIDEO
    val withExif: Boolean = true,
    val isFavorite: Boolean? = null
)

data class SearchResponse(
    val assets: SearchAssetResult
)

data class SearchStatisticsResponse(
    val total: Int
)

/**
 * Détail des assets trouvés.
 */
data class SearchAssetResult(
    val items: List<Asset>,
    val total: Int,
    val nextPage: String? = null
)

/**
 * Objet intermédiaire car l'API Immich renvoie les détails de l'album
 * ET la liste des assets dans le même appel.
 */
data class AlbumWithAssets(
    val id: String,
    val albumName: String,
    val assets: List<Asset>
)

