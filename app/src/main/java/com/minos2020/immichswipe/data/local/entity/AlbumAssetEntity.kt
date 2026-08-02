package com.minos2020.immichswipe.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Table de correspondance entre les albums et les assets.
 * Permet de savoir quel asset appartient à quel album pour synchroniser les compteurs
 * même si la décision a été prise dans un autre album ou collection.
 */
@Entity(
    tableName = "album_assets",
    primaryKeys = ["albumId", "assetId", "userId"],
    indices = [Index(value = ["assetId"]), Index(value = ["userId"])]
)
data class AlbumAssetEntity(
    val albumId: String,
    val assetId: String,
    val userId: String
)
