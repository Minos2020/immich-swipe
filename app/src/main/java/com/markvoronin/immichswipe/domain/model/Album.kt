package com.markvoronin.immichswipe.domain.model

/**
 * Représente un album Immich.
 */
data class Album(
    val id: String,
    val albumName: String,
    val description: String? = null,
    val assetCount: Int,
    val albumThumbnailAssetId: String?
) {
    companion object {
        const val VIRTUAL_ALL_ID = "virtual_all_assets"
        const val VIRTUAL_ORPHANS_ID = "virtual_orphans"
    }
}
