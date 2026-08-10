package com.markvoronin.immichswipe

import com.markvoronin.immichswipe.core.AppTheme

/**
 * État global de l'application (pour la gestion du thème et de la connexion au démarrage).
 */
data class AppUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val activeUserId: String? = null,
    val themeMode: AppTheme = AppTheme.SYSTEM,
    val dynamicColor: Boolean = true
)
