package com.minos2020.immichswipe.feature.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.minos2020.immichswipe.data.repository.AlbumRepository
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.domain.model.Album

class SwipeViewModelFactory(
    private val assetRepository: AssetRepository,
    private val sessionRepository: SessionRepository,
    private val swipeDecisionRepository: SwipeDecisionRepository,
    private val albumRepository: AlbumRepository,
    private val album: Album
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SwipeViewModel::class.java)) {
            return SwipeViewModel(assetRepository, sessionRepository, swipeDecisionRepository, albumRepository, album) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
