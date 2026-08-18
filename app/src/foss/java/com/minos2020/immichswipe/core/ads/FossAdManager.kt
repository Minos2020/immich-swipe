package com.minos2020.immichswipe.core.ads

import android.content.Context
import androidx.compose.runtime.Composable
import com.minos2020.immichswipe.domain.model.Asset

class FossAdManager : AdManager {
    override fun init(context: Context) {
        // No-op for FOSS
    }

    override fun shouldInsertAdAt(index: Int): Boolean = false

    @Composable
    override fun AdCard(isNext: Boolean, onAdSwiped: () -> Unit) {
        // No-op for FOSS
    }

    override fun createAdPlaceholder(id: String): Asset {
        throw IllegalStateException("Ads are not supported in FOSS flavor")
    }
}
