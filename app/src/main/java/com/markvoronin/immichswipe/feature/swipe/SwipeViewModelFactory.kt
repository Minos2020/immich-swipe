package com.markvoronin.immichswipe.feature.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.markvoronin.immichswipe.data.repository.AssetRepository
import com.markvoronin.immichswipe.data.repository.SessionRepository
import com.markvoronin.immichswipe.data.repository.SwipeDecisionRepository
import com.markvoronin.immichswipe.domain.model.Album

class SwipeViewModelFactory(
    private val assetRepository: AssetRepository,
    private val sessionRepository: SessionRepository,
    private val swipeDecisionRepository: SwipeDecisionRepository,
    private val album: Album,
    private val userQuotaBytes: Long? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SwipeViewModel::class.java)) {
            return SwipeViewModel(assetRepository, sessionRepository, swipeDecisionRepository, album, userQuotaBytes) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
