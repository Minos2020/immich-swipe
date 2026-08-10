package com.markvoronin.immichswipe.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.markvoronin.immichswipe.data.repository.AlbumRepository
import com.markvoronin.immichswipe.data.repository.SessionRepository
import com.markvoronin.immichswipe.data.repository.SwipeDecisionRepository
import com.markvoronin.immichswipe.data.repository.AssetRepository
import com.markvoronin.immichswipe.data.repository.AccountRepository

class HomeViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val albumRepository: AlbumRepository,
    private val swipeDecisionRepository: SwipeDecisionRepository,
    private val assetRepository: AssetRepository,
    private val accountRepository: AccountRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(sessionRepository, albumRepository, swipeDecisionRepository, assetRepository, accountRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
