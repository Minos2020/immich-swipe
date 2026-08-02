package com.minos2020.immichswipe.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minos2020.immichswipe.data.local.entity.AlbumAssetEntity

@Dao
interface AlbumAssetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbumAssets(relations: List<AlbumAssetEntity>)

    @Query("DELETE FROM album_assets WHERE albumId = :albumId AND userId = :userId")
    suspend fun clearAlbumRelations(albumId: String, userId: String)

    @Query("DELETE FROM album_assets WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)

    @Query("DELETE FROM album_assets")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM album_assets WHERE albumId = :albumId AND userId = :userId")
    suspend fun getMappingCountForAlbum(albumId: String, userId: String): Int
}
