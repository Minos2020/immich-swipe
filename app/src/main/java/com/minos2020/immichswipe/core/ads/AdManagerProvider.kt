package com.minos2020.immichswipe.core.ads

/**
 * Fournit l'instance de l'AdManager appropriée.
 * L'implémentation concrète de "createAdManager" se trouve dans les source sets foss et play.
 */
object AdManagerProvider {
    lateinit var instance: AdManager
        private set

    fun init(adManager: AdManager) {
        instance = adManager
    }
}
