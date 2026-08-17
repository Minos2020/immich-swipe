package com.minos2020.immichswipe.core.ads

import android.content.Context
import androidx.compose.runtime.Composable
import com.minos2020.immichswipe.domain.model.Asset

/**
 * FOSS IMPLEMENTATION - PRIVACY CONCERNS
 * 
 * This file is part of the FOSS (Free and Open Source Software) flavor of Immich Swipe.
 *
 * 1. ZERO ADS: This implementation contains no advertising logic.
 * 2. ZERO TRACKING: No Google Play Services or AdMob SDKs are linked or used here.
 * 3. TRANSPARENCY: This class is a no-op stub to satisfy the interface without including
 *    any proprietary binary blobs.
 */
class FossAdManager : AdManager {
    override fun init(context: Context) {
        // No-op for FOSS
    }

    override fun requestConsent(activity: android.app.Activity, onConsentComplete: () -> Unit) {
        onConsentComplete()
    }

    override fun showPrivacyOptionsForm(activity: android.app.Activity, onComplete: () -> Unit) {
        onComplete()
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
