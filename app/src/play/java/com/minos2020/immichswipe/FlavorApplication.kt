package com.minos2020.immichswipe

import com.minos2020.immichswipe.core.ads.AdManager
import com.minos2020.immichswipe.core.ads.PlayAdManager

class FlavorApplication : ImmichSwipeApp() {
    override fun createAdManager(): AdManager {
        return PlayAdManager()
    }
}
