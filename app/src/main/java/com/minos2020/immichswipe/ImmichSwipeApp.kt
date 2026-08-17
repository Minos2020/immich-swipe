package com.minos2020.immichswipe

import android.app.Application
import com.minos2020.immichswipe.core.ads.AdManagerProvider

/**
 * Classe Application personnalisée pour l'initialisation globale.
 * La méthode "onCreate" initialise l'AdManager fourni par le flavor.
 */
abstract class ImmichSwipeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialisation de l'AdManager spécifique au flavor
        val adManager = createAdManager()
        AdManagerProvider.init(adManager)
        adManager.init(this)
    }

    /**
     * Doit retourner l'implémentation de l'AdManager pour le flavor actuel.
     */
    abstract fun createAdManager(): com.minos2020.immichswipe.core.ads.AdManager
}
