package com.minos2020.immichswipe.core.ads

import android.content.Context
import androidx.compose.runtime.Composable
import com.minos2020.immichswipe.domain.model.Asset

/**
 * Interface pour la gestion des publicités, permettant des implémentations différentes
 * selon les flavors (foss vs play).
 */
interface AdManager {
    /**
     * Initialise le SDK de publicité si nécessaire.
     */
    fun init(context: Context)

    /**
     * Détermine si une publicité doit être insérée à cet index.
     */
    fun shouldInsertAdAt(index: Int): Boolean

    /**
     * Rend le composant de carte publicitaire.
     */
    @Composable
    fun AdCard(
        isNext: Boolean,
        onAdSwiped: () -> Unit
    )
    
    /**
     * Retourne une instance de publicité "fictive" pour l'insérer dans la liste d'assets.
     */
    fun createAdPlaceholder(id: String): Asset
}
