package com.markvoronin.immichswipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.markvoronin.immichswipe.data.repository.SessionRepository

class AppViewModelFactory(
    private val sessionRepository: SessionRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(sessionRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
