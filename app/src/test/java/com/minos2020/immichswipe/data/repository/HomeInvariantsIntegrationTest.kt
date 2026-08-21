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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeInvariantsIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var swipeRepo: SwipeDecisionRepository
    private lateinit var assetRepo: AssetRepository
    private val userId = "test_user"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        swipeRepo = SwipeDecisionRepository(database.swipeDecisionDao(), database.albumAssetDao())
        assetRepo = AssetRepository(database.swipeDecisionDao(), database.albumAssetDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `INVARIANT 1 - Unsynced changes of All Assets must equal unique unsynced changes of all albums except SKIP`() = runTest {
        // GIVEN: Photo 1 dans Album A et B, Photo 2 dans Album C
        database.albumAssetDao().insertAlbumAssets(listOf(
            AlbumAssetEntity("album_A", "photo_1", userId, false),
            AlbumAssetEntity("album_B", "photo_1", userId, false),
            AlbumAssetEntity("album_C", "photo_2", userId, false),
            AlbumAssetEntity(Album.VIRTUAL_SKIPPED_ID, "photo_skip", userId, false)
        ))
        
        // GIVEN: Photo 1 et Photo 2 ont des décisions non synchronisées
        swipeRepo.saveDecision("photo_1", "album_A", userId, "KEEP", isSynced = false)
        swipeRepo.saveDecision("photo_2", "album_C", userId, "DELETE", isSynced = false)
        // Photo_skip a une décision non synchronisée mais elle est dans l'album virtuel SKIP
        swipeRepo.saveDecision("photo_skip", Album.VIRTUAL_SKIPPED_ID, userId, "KEEP", isSynced = false)

        // WHEN: On met à jour la collection All Assets
        assetRepo.updateAllAssetsMapping(userId)

        // THEN: On récupère les stats
        val stats = swipeRepo.getAllAlbumDecisionCounts(userId, false).first()
        val allAssetsStat = stats.find { it.albumId == Album.VIRTUAL_ALL_ID }
        
        // Photo 1 (unique) + Photo 2 (unique) = 2. Photo_skip ne doit pas compter.
        assertEquals("All Assets doit compter 2 modifications non synchronisées uniques", 2, allAssetsStat?.unsyncedCount)
    }

    @Test
    fun `INVARIANT 2 - Treated assets of All Assets must equal unique treated assets of all albums except SKIP`() = runTest {
        // GIVEN: Photo 1 dans A et B (traité), Photo 2 dans C (traité), Photo 3 dans A (non traité)
        database.albumAssetDao().insertAlbumAssets(listOf(
            AlbumAssetEntity("album_A", "photo_1", userId, false),
            AlbumAssetEntity("album_B", "photo_1", userId, false),
            AlbumAssetEntity("album_C", "photo_2", userId, false),
            AlbumAssetEntity("album_A", "photo_3", userId, false),
            AlbumAssetEntity(Album.VIRTUAL_SKIPPED_ID, "photo_skip", userId, false)
        ))
        
        // Photo 1 et 2 sont traitées (synced ou pas, peu importe pour le 'treated')
        swipeRepo.saveDecision("photo_1", "album_A", userId, "KEEP", isSynced = true)
        swipeRepo.saveDecision("photo_2", "album_C", userId, "KEEP", isSynced = false)
        swipeRepo.saveDecision("photo_skip", Album.VIRTUAL_SKIPPED_ID, userId, "KEEP", isSynced = true)

        // WHEN
        assetRepo.updateAllAssetsMapping(userId)

        // THEN
        val stats = swipeRepo.getAllAlbumDecisionCounts(userId, false).first()
        val allAssetsStat = stats.find { it.albumId == Album.VIRTUAL_ALL_ID }
        
        // Photo 1 + Photo 2 = 2 traités uniques.
        assertEquals("All Assets doit compter 2 assets traités uniques", 2, allAssetsStat?.totalCount)
    }

    @Test
    fun `INVARIANT 3 - Total assets in All Assets must equal unique assets of all albums except SKIP`() = runTest {
        // GIVEN: Photo 1 (A, B), Photo 2 (C), Photo 3 (A), Photo 4 (SKIP)
        database.albumAssetDao().insertAlbumAssets(listOf(
            AlbumAssetEntity("album_A", "photo_1", userId, false),
            AlbumAssetEntity("album_B", "photo_1", userId, false),
            AlbumAssetEntity("album_C", "photo_2", userId, false),
            AlbumAssetEntity("album_A", "photo_3", userId, false),
            AlbumAssetEntity(Album.VIRTUAL_SKIPPED_ID, "photo_4", userId, false)
        ))

        // WHEN: Simulation de la tâche de découverte qui reconstruit All Assets
        assetRepo.updateAllAssetsMapping(userId)

        // THEN: On vérifie le nombre de lignes dans la table de mapping pour All Assets
        val count = database.albumAssetDao().getMappingCountForAlbum(Album.VIRTUAL_ALL_ID, userId)
        
        // photo_1, photo_2, photo_3 = 3 assets uniques. photo_4 est dans SKIP, exclu.
        assertEquals("Le mapping All Assets doit contenir 3 assets uniques", 3, count)
    }

    @Test
    fun `INVARIANT 4 - Treated count must be less than or equal to total count for all albums`() = runTest {
        // GIVEN: Album A avec 2 photos, 1 traitée. Album B avec 1 photo, 2 décisions (théorique/erreur)
        database.albumAssetDao().insertAlbumAssets(listOf(
            AlbumAssetEntity("album_A", "photo_1", userId, false),
            AlbumAssetEntity("album_A", "photo_2", userId, false),
            AlbumAssetEntity("album_B", "photo_3", userId, false)
        ))
        
        swipeRepo.saveDecision("photo_1", "album_A", userId, "KEEP")
        swipeRepo.saveDecision("photo_3", "album_B", userId, "KEEP")
        // On simule une décision fantôme pour la photo 3 dans un autre album pour tester le JOIN
        swipeRepo.saveDecision("photo_3", "album_X", userId, "KEEP") 

        // WHEN
        val stats = swipeRepo.getAllAlbumDecisionCounts(userId, false).first()

        // THEN
        stats.forEach { stat ->
            val mappingCount = database.albumAssetDao().getMappingCountForAlbum(stat.albumId, userId)
            assertTrue(
                "L'album ${stat.albumId} a plus de traités (${stat.totalCount}) que d'assets réels ($mappingCount)",
                stat.totalCount <= mappingCount
            )
        }
    }

    @Test
    fun `includeArchived parameter must correctly toggle visibility of archived assets in counts`() = runTest {
        // GIVEN: 2 photos normales, 1 photo archivée dans Album A
        database.albumAssetDao().insertAlbumAssets(listOf(
            AlbumAssetEntity("album_A", "normal_1", userId, isArchived = false),
            AlbumAssetEntity("album_A", "normal_2", userId, isArchived = false),
            AlbumAssetEntity("album_A", "archived_1", userId, isArchived = true)
        ))
        
        // GIVEN: On swipe les 3 photos
        swipeRepo.saveDecision("normal_1", "album_A", userId, "KEEP")
        swipeRepo.saveDecision("normal_2", "album_A", userId, "KEEP")
        swipeRepo.saveDecision("archived_1", "album_A", userId, "KEEP")

        // 1. VERIFICATION SI FALSE
        val statsOFF = swipeRepo.getAllAlbumDecisionCounts(userId, includeArchived = false).first()
        val countOFF = statsOFF.find { it.albumId == "album_A" }?.totalCount ?: 0
        assertEquals("Avec includeArchived=OFF, on ne doit voir que 2 traités", 2, countOFF)

        // 2. VERIFICATION SI TRUE
        val statsON = swipeRepo.getAllAlbumDecisionCounts(userId, includeArchived = true).first()
        val countON = statsON.find { it.albumId == "album_A" }?.totalCount ?: 0
        assertEquals("Avec includeArchived=ON, on doit voir les 3 traités", 3, countON)
    }
}
