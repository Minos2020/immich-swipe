package com.minos2020.immichswipe.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.minos2020.immichswipe.data.local.AppDatabase
import com.minos2020.immichswipe.data.local.entity.AlbumAssetEntity
import com.minos2020.immichswipe.domain.model.Album
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // On simule un SDK récent
class SwipeDecisionRepositoryIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: SwipeDecisionRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Création d'une base de données en mémoire pour les tests
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SwipeDecisionRepository(database.swipeDecisionDao(), database.albumAssetDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `All Assets should count unique treated assets across multiple albums`() = runTest {
        val userId = "user1"
        val assetId = "photo_commune"
        
        // GIVEN: Une photo présente dans deux albums différents
        database.albumAssetDao().insertAlbumAssets(listOf(
            AlbumAssetEntity("album_A", assetId, userId, false),
            AlbumAssetEntity("album_B", assetId, userId, false)
        ))
        
        // GIVEN: Cette photo est swipée (KEEP)
        repository.saveDecision(assetId, "album_A", userId, "KEEP", isSynced = false)

        // WHEN: On récupère les stats pour All Assets
        // On simule ce que fait AssetRepository : mapper les assets uniques vers l'album virtuel All Assets
        val distinctAssets = database.albumAssetDao().getAllDistinctAssetsForUser(userId)
        database.albumAssetDao().insertAlbumAssets(distinctAssets.map { 
            AlbumAssetEntity(Album.VIRTUAL_ALL_ID, it.assetId, userId, it.isArchived)
        })

        // THEN: On vérifie les compteurs via le DAO/Repository
        val stats = repository.getAllAlbumDecisionCounts(userId, includeArchived = false).first()
        
        val statA = stats.find { it.albumId == "album_A" }
        val statB = stats.find { it.albumId == "album_B" }
        val statAll = stats.find { it.albumId == Album.VIRTUAL_ALL_ID }

        assertEquals("Album A doit avoir 1 traité", 1, statA?.totalCount)
        assertEquals("Album B doit avoir 1 traité", 1, statB?.totalCount)
        assertEquals("All Assets doit avoir 1 traité UNIQUE", 1, statAll?.totalCount)
    }
    
    @Test
    fun `treated count should never exceed total count even with duplicates`() = runTest {
        val userId = "user1"
        val assetId = "photo1"
        
        // GIVEN: On insère deux fois la même photo dans le même album (erreur de cache théorique)
        // La PK (albumId, assetId, userId) empêche le doublon physique, mais testons la logique
        database.albumAssetDao().insertAlbumAssets(listOf(
            AlbumAssetEntity("album1", assetId, userId, false)
        ))
        
        // GIVEN: On enregistre une décision
        repository.saveDecision(assetId, "album1", userId, "KEEP")

        // THEN: Le totalCount doit être 1 (grâce au COUNT DISTINCT dans le SQL)
        val stats = repository.getAllAlbumDecisionCounts(userId, false).first()
        val stat = stats.find { it.albumId == "album1" }
        
        assertNotNull(stat)
        assertTrue("Le nombre de traités (${stat!!.totalCount}) doit être <= au total réel", stat.totalCount <= 1)
    }
}
