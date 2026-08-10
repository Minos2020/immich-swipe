package com.markvoronin.immichswipe.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.markvoronin.immichswipe.data.repository.AccountRepository
import com.markvoronin.immichswipe.data.repository.AuthRepository
import com.markvoronin.immichswipe.data.repository.SessionRepository

class AuthViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Vérifie si la classe demandée est bien AuthViewModel
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            // Crée l'instance avec les repositories requis
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(sessionRepository, authRepository, accountRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}
