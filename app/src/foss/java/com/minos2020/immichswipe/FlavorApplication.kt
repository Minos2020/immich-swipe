package com.minos2020.immichswipe

import com.minos2020.immichswipe.core.ads.AdManager
import com.minos2020.immichswipe.core.ads.FossAdManager

class FlavorApplication : ImmichSwipeApp() {
    override fun createAdManager(): AdManager {
        return FossAdManager()
    }
}
