package com.minos2020.immichswipe.core.ads

import android.content.Context
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.minos2020.immichswipe.R
import com.minos2020.immichswipe.domain.model.Asset
import android.view.LayoutInflater
import android.view.View

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.abs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class PlayAdManager : AdManager {
    override fun init(context: Context) {
        MobileAds.initialize(context) {}
    }

    override fun shouldInsertAdAt(index: Int): Boolean {
        // Une pub tous les 15 assets, mais pas sur le premier
        return index > 0 && index % 15 == 0
    }

    @Composable
    override fun AdCard(isNext: Boolean, onAdSwiped: () -> Unit) {
        val context = LocalContext.current
        var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
        val scope = rememberCoroutineScope()
        val offsetX = remember { Animatable(0f) }
        
        // Compteur pour bloquer le swipe (ex: 5 secondes)
        var timeLeft by remember { mutableIntStateOf(5) }
        
        LaunchedEffect(isNext, nativeAd) {
            // On ne lance le décompte que si la pub est au premier plan et chargée
            if (!isNext && nativeAd != null && timeLeft > 0) {
                while (timeLeft > 0) {
                    delay(1000)
                    timeLeft--
                }
            }
        }

        // Animation de grossissement si c'est la carte suivante
        val animatedScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isNext) 0.85f else 1f,
            animationSpec = tween(durationMillis = 400, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
            label = "AdScaleAnimation"
        )
        
        // Charger la publicité
        DisposableEffect(Unit) {
            val adLoader = AdLoader.Builder(context, "ca-app-pub-3940256099942544/2247696110")
                .forNativeAd { ad -> 
                    nativeAd = ad 
                }
                .build()
            
            adLoader.loadAd(AdRequest.Builder().build())
            onDispose { nativeAd?.destroy() }
        }

        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    alpha = if (isNext) 0.6f else 1f
                    if (!isNext) {
                        translationX = offsetX.value
                        rotationZ = offsetX.value / 40f
                    }
                }
                .pointerInput(isNext, timeLeft) {
                    if (isNext) return@pointerInput
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                // On ne permet le swipe QUE si le temps est écoulé
                                if (timeLeft <= 0 && abs(offsetX.value) > 250) {
                                    val targetX = if (offsetX.value > 0) 1500f else -1500f
                                    offsetX.animateTo(targetX, tween(150))
                                    onAdSwiped()
                                } else {
                                    // Retour fluide au centre si swippé trop tôt ou pas assez loin
                                    offsetX.animateTo(0f, androidx.compose.animation.core.spring())
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                        }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isNext) 0.dp else 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (nativeAd != null) {
                    AndroidView(
                        factory = { ctx ->
                            val adView = LayoutInflater.from(ctx).inflate(R.layout.ad_unified, null) as NativeAdView
                            populateNativeAdView(nativeAd!!, adView)
                            adView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Petit badge de décompte en haut à droite
                    if (timeLeft > 0 && !isNext) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(32.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = timeLeft.toString(),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        // Liaison des éléments de la vue avec les données de la pub
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.mediaView = adView.findViewById(R.id.ad_media)
        adView.advertiserView = adView.findViewById(R.id.ad_advertiser)

        (adView.headlineView as TextView).text = nativeAd.headline
        
        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView).text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as Button).text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        if (nativeAd.advertiser == null) {
            adView.advertiserView?.visibility = View.GONE
        } else {
            (adView.advertiserView as TextView).text = nativeAd.advertiser
            adView.advertiserView?.visibility = View.VISIBLE
        }

        // Lier la publicité à la vue
        adView.setNativeAd(nativeAd)
    }

    override fun createAdPlaceholder(id: String): Asset {
        return Asset(
            id = "ad_$id",
            ownerId = "system",
            fileCreatedAt = "",
            type = "AD",
            isFavorite = false
        )
    }
}
