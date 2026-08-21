package com.minos2020.immichswipe.feature.swipe

import com.minos2020.immichswipe.MainDispatcherRule
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.data.repository.AlbumRepository
import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.domain.model.Asset
import com.minos2020.immichswipe.data.local.entity.SwipeDecisionEntity
import com.minos2020.immichswipe.core.SessionConfig
import com.minos2020.immichswipe.core.SwipeSortOrder
import com.minos2020.immichswipe.core.SwipeSortPriority
import com.minos2020.immichswipe.core.CardDisplayMode
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.PlaybackBehavior
import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val assetRepository: AssetRepository = mockk(relaxed = true)
    private val sessionRepository: SessionRepository = mockk(relaxed = true)
    private val swipeDecisionRepository: SwipeDecisionRepository = mockk(relaxed = true)
    private val albumRepository: AlbumRepository = mockk(relaxed = true)
    
    private val testAlbum = Album(id = "album1", albumName = "Test Album", assetCount = 10, albumThumbnailAssetId = null)
    private val testUserId = "user1"

    // Sources de données stables pour les Mocks
    private val sessionConfigFlow = MutableStateFlow<SessionConfig?>(SessionConfig("http://url", "key", testUserId))
    private val includeArchivedFlow = MutableStateFlow(false)
    private val swipeSortOrderFlow = MutableStateFlow(SwipeSortOrder.DATE_DESC)
    private val swipeSortPriorityFlow = MutableStateFlow(SwipeSortPriority.NONE)
    private val decisionsFlow = MutableStateFlow<List<SwipeDecisionEntity>>(emptyList())
    private val assetsFlow = MutableStateFlow<List<Asset>>(emptyList())

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } answers { println("DEBUG: ${it.invocation.args[0]}: ${it.invocation.args[1]}"); 0 }
        every { Log.i(any(), any()) } answers { println("INFO: ${it.invocation.args[0]}: ${it.invocation.args[1]}"); 0 }
        every { Log.w(any(), any(), any()) } answers { println("WARN: ${it.invocation.args[0]}: ${it.invocation.args[1]}"); 0 }
        every { Log.e(any(), any(), any()) } answers { 
            val t = it.invocation.args[2] as? Throwable
            println("ERROR: ${it.invocation.args[0]}: ${it.invocation.args[1]} - ${t?.message}")
            t?.printStackTrace()
            0 
        }
        every { Log.e(any(), any()) } answers { println("ERROR: ${it.invocation.args[0]}: ${it.invocation.args[1]}"); 0 }

        every { sessionRepository.sessionConfig } returns sessionConfigFlow
        every { sessionRepository.includeArchived } returns includeArchivedFlow
        every { sessionRepository.swipeSortOrder } returns swipeSortOrderFlow
        every { sessionRepository.swipeSortPriority } returns swipeSortPriorityFlow
        every { sessionRepository.defaultCardDisplayMode } returns MutableStateFlow(CardDisplayMode.FILL)
        every { sessionRepository.swipeInverted } returns MutableStateFlow(false)
        every { sessionRepository.skipLifespanDays } returns MutableStateFlow(30L)
        
        every { sessionRepository.showFavoriteButton } returns MutableStateFlow(true)
        every { sessionRepository.showArchiveButton } returns MutableStateFlow(true)
        every { sessionRepository.showLockButton } returns MutableStateFlow(true)
        every { sessionRepository.showKeepDeleteButtons } returns MutableStateFlow(true)
        every { sessionRepository.showAddToAlbumButton } returns MutableStateFlow(true)
        every { sessionRepository.showShareButton } returns MutableStateFlow(true)
        every { sessionRepository.showFullscreenButton } returns MutableStateFlow(true)
        every { sessionRepository.showImmichButton } returns MutableStateFlow(true)
        every { sessionRepository.showCardDisplayButton } returns MutableStateFlow(true)
        every { sessionRepository.showMuteButton } returns MutableStateFlow(true)
        every { sessionRepository.showRotateButton } returns MutableStateFlow(true)
        every { sessionRepository.syncRotate } returns MutableStateFlow(true)
        every { sessionRepository.autoNextOnFav } returns MutableStateFlow(true)
        
        every { sessionRepository.fullscreenButtonPosition } returns MutableStateFlow(IconPosition.TOP_RIGHT)
        every { sessionRepository.immichButtonPosition } returns MutableStateFlow(IconPosition.TOP_LEFT)
        every { sessionRepository.cardDisplayButtonPosition } returns MutableStateFlow(IconPosition.TOP_RIGHT)
        every { sessionRepository.muteButtonPosition } returns MutableStateFlow(IconPosition.BOTTOM_RIGHT)
        every { sessionRepository.shareButtonPosition } returns MutableStateFlow(IconPosition.TOP_LEFT)
        every { sessionRepository.rotateButtonPosition } returns MutableStateFlow(IconPosition.BOTTOM_RIGHT)
        every { sessionRepository.playbackBehavior } returns MutableStateFlow(PlaybackBehavior.PAUSE_OTHERS)

        every { swipeDecisionRepository.getDecisionsForAlbum(testAlbum.id, testUserId) } returns decisionsFlow
        every { assetRepository.getAssetsByAlbum(any(), any(), any(), any(), any()) } returns assetsFlow
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun createAsset(id: String) = Asset(
        id = id,
        ownerId = "owner1",
        fileCreatedAt = "2024-01-01",
        type = "IMAGE",
        isFavorite = false,
        isArchived = false,
        isLocked = false,
        isTrashed = false,
        originalFileName = "file_$id",
        exifInfo = null,
        isEdited = false
    )

    @Test
    fun `Timeline size must equal Total assets minus Synced assets`() = runTest {
        // GIVEN: 10 photos au total, 5 traitées dont 3 synchronisées
        val allAssets = (1..10).map { createAsset("photo_$it") }
        assetsFlow.value = allAssets
        
        decisionsFlow.value = listOf(
            SwipeDecisionEntity("photo_1", testAlbum.id, testUserId, "KEEP", isSynced = true),
            SwipeDecisionEntity("photo_2", testAlbum.id, testUserId, "KEEP", isSynced = true),
            SwipeDecisionEntity("photo_3", testAlbum.id, testUserId, "KEEP", isSynced = true),
            SwipeDecisionEntity("photo_6", testAlbum.id, testUserId, "KEEP", isSynced = false),
            SwipeDecisionEntity("photo_8", testAlbum.id, testUserId, "KEEP", isSynced = false)
        )

        // WHEN
        val viewModel = SwipeViewModel(assetRepository, sessionRepository, swipeDecisionRepository, albumRepository, testAlbum)
        testScheduler.advanceUntilIdle()

        // THEN
        assertEquals("La timeline doit contenir 7 photos (10 total - 3 synchro)", 7, viewModel.uiState.value.totalCount)
    }

    @Test
    fun `Remaining count must equal Total assets minus Treated assets (synced or not)`() = runTest {
        // GIVEN: 5 photos au total, 1 déjà synchronisée
        assetsFlow.value = (1..5).map { createAsset("photo_$it") }
        decisionsFlow.value = listOf(
            SwipeDecisionEntity("photo_1", testAlbum.id, testUserId, "KEEP", isSynced = true)
        )
        
        val viewModel = SwipeViewModel(assetRepository, sessionRepository, swipeDecisionRepository, albumRepository, testAlbum)
        testScheduler.advanceUntilIdle()

        // WHEN: On swipe une nouvelle photo (photo_2) localement (unsynced)
        viewModel.onSwipe(SwipeDecision.KEEP)
        testScheduler.advanceUntilIdle()

        // THEN
        // Total (5) - Traités (1 synced + 1 unsynced) = 3 restant
        assertEquals("Il doit rester 3 photos à traiter (5 total - 2 traités)", 3, viewModel.uiState.value.remainingCount)
    }

    @Test
    fun `Progress must be 100 percent when all timeline assets have a decision`() = runTest {
        // GIVEN: 2 photos, 1 déjà synchronisée
        assetsFlow.value = listOf(createAsset("photo_1"), createAsset("photo_2"))
        decisionsFlow.value = listOf(
            SwipeDecisionEntity("photo_1", testAlbum.id, testUserId, "KEEP", isSynced = true)
        )
        
        val viewModel = SwipeViewModel(assetRepository, sessionRepository, swipeDecisionRepository, albumRepository, testAlbum)
        testScheduler.advanceUntilIdle()

        // WHEN: On swipe la seule photo restante de la timeline (photo_2)
        viewModel.onSwipe(SwipeDecision.KEEP)
        testScheduler.advanceUntilIdle()

        // THEN
        assertEquals(1.0f, viewModel.uiState.value.progress)
        assertTrue("L'index doit être à la fin de la liste", 
            viewModel.uiState.value.currentIndex >= viewModel.uiState.value.assets.size)
    }

    @Test
    fun `Progress must be 0 percent when no timeline assets have a decision`() = runTest {
        // GIVEN: 5 photos, aucune synchronisée
        assetsFlow.value = (1..5).map { createAsset("photo_$it") }
        decisionsFlow.value = emptyList()

        // WHEN
        val viewModel = SwipeViewModel(assetRepository, sessionRepository, swipeDecisionRepository, albumRepository, testAlbum)
        testScheduler.advanceUntilIdle()

        // THEN
        assertEquals(0.0f, viewModel.uiState.value.progress)
    }
}
