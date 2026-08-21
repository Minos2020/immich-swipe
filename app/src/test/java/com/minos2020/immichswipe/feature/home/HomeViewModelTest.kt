package com.minos2020.immichswipe.feature.home

import com.minos2020.immichswipe.MainDispatcherRule
import com.minos2020.immichswipe.data.repository.AlbumRepository
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.data.repository.UserRepository
import com.minos2020.immichswipe.domain.model.User
import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.data.local.dao.AlbumDecisionCount
import com.minos2020.immichswipe.core.SessionConfig
import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.coVerify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionRepository: SessionRepository = mockk(relaxed = true)
    private val albumRepository: AlbumRepository = mockk(relaxed = true)
    private val swipeDecisionRepository: SwipeDecisionRepository = mockk(relaxed = true)
    private val assetRepository: AssetRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk()

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        // Configuration par défaut des mocks pour éviter les plantages lors de l'init du ViewModel
        every { sessionRepository.playbackBehavior } returns flowOf(mockk(relaxed = true))
        every { sessionRepository.themeMode } returns flowOf(mockk(relaxed = true))
        every { sessionRepository.includeArchived } returns flowOf(false)
        every { sessionRepository.defaultLayoutGrid } returns flowOf(true)
        every { sessionRepository.sessionConfig } returns flowOf(SessionConfig("http://base.url", "api-key", "user-id"))
        every { sessionRepository.skipLifespanDays } returns flowOf(30)
        
        // On mocke les flux SQL pour éviter des null pointer lors du collect initial
        every { swipeDecisionRepository.getAllAlbumDecisionCounts(any(), any()) } returns flowOf(emptyList())
        every { swipeDecisionRepository.getSyncedSkipCount(any()) } returns flowOf(0)
        every { swipeDecisionRepository.getSyncHistory(any()) } returns flowOf(emptyList())
        every { swipeDecisionRepository.getAllDecisionsForUser(any()) } returns flowOf(emptyList())

        viewModel = HomeViewModel(
            sessionRepository,
            albumRepository,
            swipeDecisionRepository,
            assetRepository,
            userRepository
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `loadUser should update uiState with user info when successful`() = runTest {
        // GIVEN
        val mockUser = User(id = "user-id", name = "Test User", email = "test@example.com", avatarColor = "blue")
        coEvery { userRepository.getCurrentUser() } returns mockUser
        coEvery { albumRepository.refreshAlbums(any()) } returns emptyList()
        coEvery { assetRepository.getTotalAssetCount(any()) } returns 0
        coEvery { assetRepository.getOrphansCount(any()) } returns 0
        coEvery { swipeDecisionRepository.migrateLegacyDecisions(any()) } returns Unit
        coEvery { swipeDecisionRepository.cleanExpiredSkips(any(), any()) } returns 0

        // WHEN
        viewModel.loadUser()

        // THEN
        val state = viewModel.uiState.value
        assertNotNull("L'utilisateur ne devrait pas être null", state.user)
        assertEquals("Test User", state.user?.name)
        assertFalse("L'état ne devrait plus être en chargement", state.isLoading)
    }

    @Test
    fun `treated count should never exceed total asset count for any album`() = runTest {
        // GIVEN
        val album = Album(id = "album1", albumName = "Album 1", assetCount = 10, albumThumbnailAssetId = null)
        val treatedCounts = flowOf(listOf(
            AlbumDecisionCount(albumId = "album1", totalCount = 5, unsyncedCount = 1)
        ))
        
        coEvery { albumRepository.refreshAlbums(any()) } returns listOf(album)
        every { swipeDecisionRepository.getAllAlbumDecisionCounts(any(), any()) } returns treatedCounts
        coEvery { userRepository.getCurrentUser() } returns mockk(relaxed = true)

        // WHEN
        viewModel.loadUser()

        // THEN
        val state = viewModel.uiState.value
        val treated = state.albumTreatedCounts["album1"] ?: 0
        assertTrue("Le nombre de traités ($treated) doit être <= au total (10)", treated <= 10)
    }

    @Test
    fun `virtual album All Assets should be present when assets exist`() = runTest {
        // GIVEN
        coEvery { assetRepository.getTotalAssetCount(any()) } returns 100
        coEvery { userRepository.getCurrentUser() } returns mockk(relaxed = true)
        
        // On simule aussi des noms localisés pour les albums virtuels
        viewModel.updateVirtualNames(Album.VIRTUAL_ALL_ID, "Toutes les photos")

        // WHEN
        viewModel.loadUser()

        // THEN
        val state = viewModel.uiState.value
        val allAssetsAlbum = state.filteredAlbums.find { it.id == Album.VIRTUAL_ALL_ID }
        assertNotNull("La collection 'Tous les médias' devrait être visible", allAssetsAlbum)
        assertEquals(100, allAssetsAlbum?.assetCount)
        assertEquals("Toutes les photos", allAssetsAlbum?.albumName)
    }

    @Test
    fun `changing includeArchived should trigger a refresh of albums`() = runTest {
        // GIVEN
        val includeArchivedFlow = MutableStateFlow(false)
        every { sessionRepository.includeArchived } returns includeArchivedFlow
        
        // On recrée le ViewModel pour qu'il utilise NOTRE MutableStateFlow
        val testViewModel = HomeViewModel(
            sessionRepository, albumRepository, swipeDecisionRepository, assetRepository, userRepository
        )
        
        // WHEN
        includeArchivedFlow.value = true

        // THEN
        // On vérifie qu'un appel avec 'true' a eu lieu (le refresh)
        coVerify(atLeast = 1) { albumRepository.refreshAlbums(true) }
        assertTrue("Le state du VM devrait être mis à jour", testViewModel.uiState.value.includeArchived)
    }
}
