package com.minos2020.immichswipe.domain.model

/**
 * Représente une photo ou vidéo (Asset) sur Immich.
 */
import com.google.gson.annotations.SerializedName

data class Asset(
    val id: String,
    val ownerId: String,
    val fileCreatedAt: String,
    val type: String, // IMAGE ou VIDEO
    val duration: String? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isLocked: Boolean = false,
    val isTrashed: Boolean = false,
    val albumIds: List<String>? = emptyList(),
    val originalFileName: String? = null,
    val originalMimeType: String? = null,
    @SerializedName("extension")
    val fileExtension: String? = null,
    val exifInfo: ExifInfo? = null,
    val isEdited: Boolean = false
)

data class ExifInfo(
    @SerializedName("fileSizeInByte")
    val fileSizeInBytes: Long? = null,
    val description: String? = null,
    val projectionType: String? = null,
    val orientation: String? = null,
    val dateTimeOriginal: String? = null,
    @SerializedName("exifImageWidth")
    val imageWidth: Int? = null,
    @SerializedName("exifImageHeight")
    val imageHeight: Int? = null
)
