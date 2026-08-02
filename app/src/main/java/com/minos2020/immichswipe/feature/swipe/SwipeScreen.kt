package com.minos2020.immichswipe.feature.swipe

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.minos2020.immichswipe.R
import com.minos2020.immichswipe.core.CardDisplayMode
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.SortOrder
import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.domain.model.Asset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MaterialGreen = Color(0xFF2E7D32) // Un vert plus profond (Green 800)
private val MaterialRed = Color(0xFFC62828)   // Un rouge plus marqué (Red 800)

/**
 * Helper pour trouver l'Activity à partir du Context.
 */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun SwipeScreen(
    album: Album,
    assetRepository: AssetRepository,
    swipeDecisionRepository: SwipeDecisionRepository,
    sessionRepository: SessionRepository,
    modifier: Modifier = Modifier
) {
    val viewModel: SwipeViewModel = viewModel(
        key = album.id,
        factory = SwipeViewModelFactory(assetRepository, sessionRepository, swipeDecisionRepository, album)
    )
    
    // Rafraîchir les données chaque fois que l'écran devient visible
    // Afin de refléter d'éventuels changements survenus sur l'album
    LaunchedEffect(album.id) {
        viewModel.retryLoading()
    }

    val uiState by viewModel.uiState.collectAsState()
    
    var showSortMenu by remember { mutableStateOf(false) }

    // On observe la santé globale de la connexion
    val connectionStatus by SessionManager.connectionStatus.collectAsState()

    // SOLUTION ROBUSTE : Relancer le chargement UNIQUEMENT si on passe de OFFLINE à ONLINE
    // et qu'on était en erreur. On ne surveille plus uiState.error ici pour éviter les boucles.
    LaunchedEffect(connectionStatus.level) {
        if (uiState.error != null && connectionStatus.level == com.minos2020.immichswipe.core.ConnectionLevel.ONLINE) {
            viewModel.retryLoading()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 0. En-tête avec nom de l'album et statistiques
        SwipeHeader(
            uiState = uiState,
            onSummaryClick = { viewModel.toggleSummary(true) }
        )

        // 1. Timeline (Barre du haut avec vignettes)
        AssetTimeline(
            assets = uiState.assets,
            decisions = uiState.decisions,
            currentIndex = uiState.currentIndex,
            isFavorite = { uiState.isFavorite(it) },
            isArchived = { uiState.isArchived(it) },
            isLocked = { uiState.isLocked(it) },
            onAssetClick = { viewModel.onMoveToAsset(it) }
        )

        // 2. Zone centrale : La pile de cartes
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.error != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.error!!, 
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(onClick = { viewModel.retryLoading() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.common_retry))
                    }
                }
            } else if (uiState.currentIndex < uiState.assets.size) {
                val currentIndex = uiState.currentIndex
                val assets = uiState.assets

                // On affiche les 2 assets (courant et prochain non traité)
                // On inverse l'ordre (reversed) pour que l'asset courant soit au dessus.
                val nextUnprocessedIndex = viewModel.getNextUnprocessedIndex()
                val visibleIndices = listOfNotNull(
                    currentIndex,
                    nextUnprocessedIndex.takeIf { it != -1 }
                ).distinct().reversed()

                visibleIndices.forEach { index ->
                    val asset = assets[index]
                    val isNextCard = index > currentIndex
                    key(asset.id) {
                        SwipeCard(
                            asset = asset,
                            onSwipe = { viewModel.onSwipe(it) },
                            isNext = isNextCard,
                            playbackBehavior = uiState.playbackBehavior,
                            isSwipeInverted = uiState.isSwipeInverted,
                            fullscreenButtonPosition = uiState.fullscreenButtonPosition,
                            immichButtonPosition = uiState.immichButtonPosition,
                            cardDisplayButtonPosition = uiState.cardDisplayButtonPosition,
                            cardDisplayMode = uiState.cardDisplayMode,
                            onToggleDisplayMode = { viewModel.toggleDisplayMode() }
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Celebration,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(text = stringResource(R.string.swipe_congratulations), fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Barre d'actions en bas (Version compacte sans boutons KEEP/DELETE)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (uiState.showSwipeButtons) 16.dp else 24.dp),
                horizontalArrangement = if (uiState.showSwipeButtons) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // BOUTON SUPPRIMER (DELETE)
                if (uiState.showSwipeButtons) {
                    FloatingActionButton(
                        onClick = { viewModel.onSwipe(SwipeDecision.DELETE) },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.swipe_delete))
                    }
                }

                // UNDO
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = uiState.currentIndex > 0 || uiState.history.isNotEmpty(),
                    modifier = Modifier.size(if (uiState.showSwipeButtons) 36.dp else 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.nav_back),
                        modifier = Modifier.size(if (uiState.showSwipeButtons) 22.dp else 26.dp)
                    )
                }

                // SUMMARY
                IconButton(
                    onClick = { viewModel.toggleSummary(true) },
                    modifier = Modifier.size(if (uiState.showSwipeButtons) 36.dp else 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = stringResource(R.string.swipe_summary_title),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(if (uiState.showSwipeButtons) 22.dp else 26.dp)
                    )
                }

                // FAVORITE (Optionnel - Coeur)
                if (uiState.showFavoriteButton) {
                    val isFav = uiState.currentAsset?.let { uiState.isFavorite(it.id) } ?: false
                    IconButton(
                        onClick = { viewModel.toggleFavorite() },
                        modifier = Modifier.size(if (uiState.showSwipeButtons) 36.dp else 44.dp)
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.swipe_favorite),
                            tint = if (isFav) Color.Red else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(if (uiState.showSwipeButtons) 22.dp else 26.dp)
                        )
                    }
                }

                // SORT MENU
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(if (uiState.showSwipeButtons) 36.dp else 44.dp)
                    ) {
                        val icon = when (uiState.sortOrder) {
                            SortOrder.SHUFFLED -> Icons.Default.Shuffle
                            SortOrder.CHRONOLOGICAL_ASC -> Icons.Default.ArrowUpward
                            SortOrder.CHRONOLOGICAL_DESC -> Icons.Default.ArrowDownward
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(R.string.settings_sort_order_label),
                            tint = if (uiState.sortOrder != SortOrder.CHRONOLOGICAL_DESC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(if (uiState.showSwipeButtons) 22.dp else 26.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_sort_newest)) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.CHRONOLOGICAL_DESC)
                                showSortMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                            trailingIcon = { if (uiState.sortOrder == SortOrder.CHRONOLOGICAL_DESC) Icon(Icons.Default.Check, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_sort_oldest)) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.CHRONOLOGICAL_ASC)
                                showSortMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                            trailingIcon = { if (uiState.sortOrder == SortOrder.CHRONOLOGICAL_ASC) Icon(Icons.Default.Check, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_sort_shuffled)) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.SHUFFLED)
                                showSortMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Shuffle, null) },
                            trailingIcon = { if (uiState.sortOrder == SortOrder.SHUFFLED) Icon(Icons.Default.Check, null) }
                        )
                    }
                }

                // SKIP
                IconButton(
                    onClick = { viewModel.onSwipe(SwipeDecision.SKIP) },
                    modifier = Modifier.size(if (uiState.showSwipeButtons) 36.dp else 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Forward,
                        contentDescription = stringResource(R.string.swipe_skip),
                        modifier = Modifier.size(if (uiState.showSwipeButtons) 22.dp else 26.dp)
                    )
                }

                // BOUTON GARDER (KEEP)
                if (uiState.showSwipeButtons) {
                    FloatingActionButton(
                        onClick = { viewModel.onSwipe(SwipeDecision.KEEP) },
                        containerColor = MaterialGreen,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.swipe_keep))
                    }
                }
            }
        }
    }

    // Affichage du résumé
    if (uiState.showSummary) {
        SummaryDialog(
            uiState = uiState,
            onDismiss = { viewModel.toggleSummary(false) },
            onApply = { viewModel.applyChanges() },
            onUndoDecision = { viewModel.undoSpecificDecision(it) }
        )
    }

    // Animation de succès
    if (uiState.showSuccessAnimation) {
        SuccessAnimationOverlay()
    }
}

@Composable
fun SuccessAnimationOverlay() {
    // États d'animation internes
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    val iconScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Lancement synchronisé des animations
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(1f, tween(400))
        }
        // Petit délai pour l'icône pour créer un effet de cascade
        delay(200)
        iconScale.animateTo(
            targetValue = 1.2f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy)
        )
        iconScale.animateTo(1f, spring())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * alpha.value))
            .zIndex(100f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .padding(24.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Cercle de fond pour l'icône
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                scaleX = iconScale.value * 1.1f
                                scaleY = iconScale.value * 1.1f
                            }
                            .background(MaterialGreen.copy(alpha = 0.15f), CircleShape)
                    )
                    
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialGreen,
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer {
                                scaleX = iconScale.value
                                scaleY = iconScale.value
                            }
                    )
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    text = stringResource(R.string.swipe_sync_success),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = stringResource(R.string.swipe_sync_success_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SwipeHeader(
    uiState: SwipeUiState,
    onSummaryClick: () -> Unit
) {
    // Animation fluide de la progression
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "ProgressBarAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Barre de progression avec nom de l'album
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onSummaryClick() },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = constraints.maxWidth.toFloat()
            val progressWidth = totalWidth * animatedProgress
            val density = LocalDensity.current
            val paddingPx = with(density) { 16.dp.toPx() }
            val spacingPx = paddingPx // Equidistant : 16dp

            // 1. Logique de positionnement du pourcentage/icône (Info)
            // Fixé à 72dp pour que l'icône ne bouge pas selon le texte
            val infoWidthPx = with(density) { 72.dp.toPx() }
            val infoIsInside = progressWidth > infoWidthPx + paddingPx
            val infoTranslationX by animateFloatAsState(
                targetValue = if (infoIsInside)
                    (progressWidth - infoWidthPx - paddingPx).coerceAtLeast(paddingPx)
                else
                    totalWidth - infoWidthPx - paddingPx,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "InfoTranslation"
            )

            // 2. Logique de positionnement du Titre
            // Il est poussé par la barre, puis revient à gauche dès qu'il y a la place
            // On augmente le seuil à 250dp pour éviter le chevauchement avec le pourcentage
            val titleThreshold = with(density) { 250.dp.toPx() }
            val titleIsPushed = progressWidth > paddingPx && progressWidth < titleThreshold
            val titleTranslationX by animateFloatAsState(
                targetValue = if (titleIsPushed) progressWidth + spacingPx else paddingPx,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "TitleTranslation"
            )

            // On dessine le contenu complet deux fois (une sombre, une claire clippée)
            // Cela gère automatiquement le changement de couleur au passage de la barre

            // --- CALQUE DU DESSOUS (Sombre sur beige) ---
            Box(modifier = Modifier.fillMaxSize()) {
                HeaderTitle(
                    text = uiState.albumName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { translationX = titleTranslationX }.align(Alignment.CenterStart)
                )
                HeaderInfo(
                    progressText = "${(uiState.progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { translationX = infoTranslationX }.align(Alignment.CenterStart)
                )
            }

            // --- CALQUE DU DESSUS (Clair sur dégradé, clippé par la progression) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clipToBounds()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .background(Color.Black.copy(alpha = 0.1f))
            ) {
                // On utilise une largeur fixe (totalWidth) pour que les textes blancs
                // soient parfaitement alignés avec les textes sombres en dessous
                // Correction : on ajoute fillMaxHeight pour que l'alignement vertical soit identique
                Box(modifier = Modifier.width(with(density) { totalWidth.toDp() }).fillMaxHeight()) {
                    HeaderTitle(
                        text = uiState.albumName,
                        color = Color.White,
                        modifier = Modifier.graphicsLayer { translationX = titleTranslationX }.align(Alignment.CenterStart)
                    )
                    HeaderInfo(
                        progressText = "${(uiState.progress * 100).toInt()}%",
                        color = Color.White,
                        modifier = Modifier.graphicsLayer { translationX = infoTranslationX }.align(Alignment.CenterStart)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Rangée de statistiques
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatBadge(label = stringResource(R.string.swipe_keep), count = uiState.allKeptCount, color = MaterialGreen)
            StatBadge(label = stringResource(R.string.swipe_delete), count = uiState.deletedCount, color = MaterialRed)
            StatBadge(label = stringResource(R.string.swipe_skip), count = uiState.skippedCount, color = Color.Gray)
            StatBadge(label = stringResource(R.string.swipe_remaining), count = uiState.remainingCount, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun HeaderTitle(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier.widthIn(max = 170.dp)
    )
}

@Composable
private fun HeaderInfo(progressText: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        horizontalArrangement = Arrangement.End,
        modifier = modifier.width(72.dp)
    ) {
        Text(
            text = progressText,
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.9f)
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.Assessment,
            contentDescription = stringResource(R.string.swipe_summary_title),
            tint = color,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun StatBadge(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun AssetTimeline(
    assets: List<Asset>,
    decisions: Map<String, SwipeDecision>,
    currentIndex: Int,
    isFavorite: (String) -> Boolean,
    isArchived: (String) -> Boolean,
    isLocked: (String) -> Boolean,
    onAssetClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (assets.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(assets) { index, asset ->
            val decision = decisions[asset.id]
            val isCurrent = index == currentIndex
            val hasHeart = isFavorite(asset.id)
            val hasArchive = isArchived(asset.id)
            val hasLock = isLocked(asset.id)

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (isCurrent) 2.dp else 0.dp,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onAssetClick(index) }
            ) {
                val baseUrl = SessionManager.getBaseUrl()?.removeSuffix("/")
                val apiKey = SessionManager.getApiKey() ?: ""
                val thumbnailRequest = remember(asset.id, baseUrl, apiKey) {
                    ImageRequest.Builder(context)
                        .data("$baseUrl/api/assets/${asset.id}/thumbnail?format=WEBP")
                        .addHeader("x-api-key", apiKey)
                        .crossfade(true)
                        .precision(coil.size.Precision.INEXACT)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = thumbnailRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(if (isCurrent) 1f else 0.6f)
                )

                // Icône "Play" pour les vidéos dans la timeline
                if (asset.type == "VIDEO") {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(2.dp)
                            .size(14.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    )
                }

                // Conteneur de badges en haut à droite
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (decision != null) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    when (decision) {
                                        SwipeDecision.KEEP -> MaterialGreen
                                        SwipeDecision.DELETE -> MaterialRed
                                        SwipeDecision.SKIP -> Color.Gray
                                        SwipeDecision.ARCHIVE -> MaterialTheme.colorScheme.primary
                                        SwipeDecision.LOCK -> MaterialTheme.colorScheme.outline
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (decision) {
                                    SwipeDecision.KEEP -> Icons.Default.Check
                                    SwipeDecision.DELETE -> Icons.Default.Delete
                                    SwipeDecision.SKIP -> Icons.AutoMirrored.Filled.Forward
                                    SwipeDecision.ARCHIVE -> Icons.Default.Archive
                                    SwipeDecision.LOCK -> Icons.Default.Lock
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    if (hasHeart) {
                        TimelineMiniBadge(Icons.Default.Favorite, Color.Red)
                    }
                    if (hasArchive && decision != SwipeDecision.ARCHIVE) {
                        TimelineMiniBadge(Icons.Default.Archive, Color.Black)
                    }
                    if (hasLock && decision != SwipeDecision.LOCK) {
                        TimelineMiniBadge(Icons.Default.Lock, Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineMiniBadge(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(10.dp)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SwipeCard(
    asset: Asset,
    onSwipe: (SwipeDecision) -> Unit,
    isNext: Boolean,
    playbackBehavior: PlaybackBehavior,
    isSwipeInverted: Boolean,
    fullscreenButtonPosition: IconPosition,
    immichButtonPosition: IconPosition,
    cardDisplayButtonPosition: IconPosition,
    cardDisplayMode: CardDisplayMode,
    onToggleDisplayMode: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val baseUrl = SessionManager.getBaseUrl()?.removeSuffix("/")
    val apiKey = SessionManager.getApiKey() ?: ""
    val lifecycleOwner = LocalLifecycleOwner.current

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var isFullscreenOpen by rememberSaveable { mutableStateOf(false) }
    var isVideoReady by remember(asset.id) { mutableStateOf(false) }
    var showLoadingIndicator by remember(asset.id) { mutableStateOf(false) }

    // On affiche l'indicateur de chargement seulement après un court délai (ex: 400ms)
    // pour éviter les clignotements si la vidéo est déjà prête ou charge instantanément.
    LaunchedEffect(asset.id, isVideoReady) {
        if (!isVideoReady) {
            delay(500)
            showLoadingIndicator = true
        } else {
            showLoadingIndicator = false
        }
    }

    // Hauteur du panneau de métadonnées
    val metadataHeight = 300.dp
    val metadataHeightPx = with(density) { metadataHeight.toPx() }

    // On crée un Player unique pour cette carte si c'est une vidéo
    // On ajoute isNext dans les clés du remember pour que le player soit créé
    // au moment où la carte passe du second plan au premier plan.
    val exoPlayer = remember(asset.id, isNext) {
        if (asset.type == "VIDEO" && !isNext) {
            ExoPlayer.Builder(context).build().apply {
                // Configuration précise de l'Audio Focus
                if (playbackBehavior != PlaybackBehavior.IGNORE) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build()

                    // On active la gestion automatique du focus (coupe les autres sons)
                    setAudioAttributes(audioAttributes, true)
                }

                repeatMode = Player.REPEAT_MODE_ONE
                val videoUrl = "$baseUrl/api/assets/${asset.id}/video/playback"
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(mapOf("x-api-key" to apiKey))
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUrl))
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
        } else null
    }

    // Gestion du cycle de vie pour mettre en pause la vidéo quand on quitte l'app
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isVideoReady = state == Player.STATE_READY
            }
        }

        // Initialiser l'état immédiatement si le player est déjà prêt
        if (exoPlayer?.playbackState == Player.STATE_READY) {
            isVideoReady = true
        }
        exoPlayer?.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer?.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> if (!isFullscreenOpen) exoPlayer?.playWhenReady = true
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer?.removeListener(listener)
            exoPlayer?.stop()
            exoPlayer?.release()
        }
    }

    // Animation de grossissement de la carte suivante (vitesse moyenne 500ms)
    val animatedScale by animateFloatAsState(
        targetValue = if (isNext) 0.85f else 1f,
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        label = "ScaleAnimation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isNext) 0.6f else 1f)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    if (!isNext) {
                        translationX = offsetX.value
                        // Photo reste fixe verticalement (offsetY utilisé pour les métadonnées)
                        rotationZ = offsetX.value / 40f
                    }
                }
                .pointerInput(isNext) {
                    if (isNext) return@pointerInput
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val currentX = offsetX.value
                                val currentY = offsetY.value
                                if (currentX > 250) {
                                    offsetX.animateTo(1500f, tween(150))
                                    onSwipe(if (isSwipeInverted) SwipeDecision.DELETE else SwipeDecision.KEEP)
                                } else if (currentX < -250) {
                                    offsetX.animateTo(-1500f, tween(150))
                                    onSwipe(if (isSwipeInverted) SwipeDecision.KEEP else SwipeDecision.DELETE)
                                } else {
                                    launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }

                                    // Aimantage métadonnées :
                                    // Si on est en train d'ouvrir (Y < 0) et qu'on dépasse 25% de la hauteur -> on finit l'ouverture
                                    // Gestion du "cran" pour les métadonnées (Y)
                                    val wasOpen = offsetY.targetValue < -metadataHeightPx / 2
                                    if (wasOpen) {
                                        // Si c'était ouvert, on ferme au moindre geste vers le bas (seuil 90% de hauteur)
                                        if (currentY < -metadataHeightPx * 0.95f) {
                                            offsetY.animateTo(-metadataHeightPx, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                        } else {
                                            offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                        }
                                    } else {
                                        // Si c'était fermé, on ouvre si on dépasse 10% de la hauteur vers le haut
                                        if (currentY < -metadataHeightPx * 0.05f) {
                                            offsetY.animateTo(-metadataHeightPx, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                        } else {
                                            offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                        }
                                    }
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                offsetY.snapTo((offsetY.value + dragAmount.y).coerceIn(-metadataHeightPx, 0f))
                            }
                        }
                    )
                },
            elevation = CardDefaults.cardElevation(defaultElevation = if (isNext) 0.dp else 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                if (asset.type == "VIDEO" && !isNext && exoPlayer != null) {
                    // On ne garde qu'un seul PlayerView actif à la fois pour le même player
                    // Si le plein écran est ouvert, on cache celui de la carte pour qu'il puisse
                    // être ré-attaché proprement au retour.
                    if (!isFullscreenOpen) {
                        ZoomableBox(
                            modifier = Modifier.fillMaxSize(),
                            resetOnRelease = true,
                            onDoubleTap = { isFullscreenOpen = true }
                        ) {
                            SharedVideoPlayer(
                                player = exoPlayer,
                                isFullscreen = false,
                                cardDisplayMode = cardDisplayMode,
                                onDoubleTap = null // Géré par ZoomableBox
                            )

                            // Indicateur de chargement si la vidéo n'est pas prête
                            if (showLoadingIndicator) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Image de remplacement pendant que le player est utilisé en plein écran
                        val placeholderRequest = remember(asset.id, baseUrl, apiKey) {
                            ImageRequest.Builder(context)
                                .data("$baseUrl/api/assets/${asset.id}/thumbnail?format=JPEG&size=preview")
                                .addHeader("x-api-key", apiKey)
                                .crossfade(true)
                                .precision(coil.size.Precision.INEXACT)
                                .build()
                        }
                        AsyncImage(
                            model = placeholderRequest,
                            contentDescription = null,
                            contentScale = if (cardDisplayMode == CardDisplayMode.FILL) ContentScale.Crop else ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    val photoRequest = remember(asset.id, baseUrl, apiKey) {
                        ImageRequest.Builder(context)
                            .data("$baseUrl/api/assets/${asset.id}/thumbnail?format=JPEG&size=preview")
                            .addHeader("x-api-key", apiKey)
                            .crossfade(true)
                            .precision(coil.size.Precision.INEXACT)
                            .build()
                    }
                    ZoomableBox(
                        modifier = Modifier.fillMaxSize(),
                        resetOnRelease = true,
                        enabled = !isNext,
                        aspectRatio = asset.exifInfo?.let { it.imageWidth?.toFloat()?.div(it.imageHeight?.toFloat() ?: 1f) },
                        isFillMode = cardDisplayMode == CardDisplayMode.FILL,
                        onDoubleTap = { isFullscreenOpen = true }
                    ) {
                        AsyncImage(
                            model = photoRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Fit, // Utilisation de Fit + ZoomableBox pour le mode FILL
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Panneau Métadonnées Interactif (Intégré dans la carte)
                if (!isNext) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(metadataHeight)
                            .graphicsLayer { translationY = metadataHeight.toPx() + offsetY.value }
                    ) {
                        MetadataPanel(asset = asset, onClose = { scope.launch { offsetY.animateTo(0f) } })
                    }
                }

                // Boutons d'action (Plein écran, Immich, Mode d'affichage)
                // Placés à la fin pour être dessinés AU-DESSUS du dégradé et du panneau de métadonnées
                if (!isNext) {
                    val density = LocalDensity.current
                    val panelPushDp = with(density) { (-offsetY.value).toDp() }

                    listOf(Alignment.Start, Alignment.End).forEach { side ->
                        Column(
                            modifier = Modifier
                                .align(if (side == Alignment.Start) Alignment.TopStart else Alignment.TopEnd)
                                .fillMaxHeight()
                                .padding(8.dp),
                            horizontalAlignment = side
                        ) {
                            // Groupe du HAUT
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (fullscreenButtonPosition.toHorizontalAlignment() == side && (fullscreenButtonPosition == IconPosition.TOP_LEFT || fullscreenButtonPosition == IconPosition.TOP_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.Default.Fullscreen,
                                        contentDescription = stringResource(R.string.settings_fullscreen_pos_label),
                                        onClick = { isFullscreenOpen = true }
                                    )
                                }
                                if (cardDisplayButtonPosition.toHorizontalAlignment() == side && (cardDisplayButtonPosition == IconPosition.TOP_LEFT || cardDisplayButtonPosition == IconPosition.TOP_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = if (cardDisplayMode == CardDisplayMode.FILL) 
                                            Icons.Default.FitScreen else Icons.Default.AspectRatio,
                                        contentDescription = stringResource(R.string.swipe_toggle_display),
                                        onClick = onToggleDisplayMode
                                    )
                                }
                                if (immichButtonPosition.toHorizontalAlignment() == side && (immichButtonPosition == IconPosition.TOP_LEFT || immichButtonPosition == IconPosition.TOP_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = stringResource(R.string.settings_immich_pos_label),
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, "$baseUrl/photos/${asset.id}".toUri())
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            // Groupe du BAS
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (fullscreenButtonPosition.toHorizontalAlignment() == side && (fullscreenButtonPosition == IconPosition.BOTTOM_LEFT || fullscreenButtonPosition == IconPosition.BOTTOM_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.Default.Fullscreen,
                                        contentDescription = stringResource(R.string.settings_fullscreen_pos_label),
                                        onClick = { isFullscreenOpen = true }
                                    )
                                }
                                if (cardDisplayButtonPosition.toHorizontalAlignment() == side && (cardDisplayButtonPosition == IconPosition.BOTTOM_LEFT || cardDisplayButtonPosition == IconPosition.BOTTOM_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = if (cardDisplayMode == CardDisplayMode.FILL) 
                                            Icons.Default.FitScreen else Icons.Default.AspectRatio,
                                        contentDescription = stringResource(R.string.swipe_toggle_display),
                                        onClick = onToggleDisplayMode
                                    )
                                }
                                if (immichButtonPosition.toHorizontalAlignment() == side && (immichButtonPosition == IconPosition.BOTTOM_LEFT || immichButtonPosition == IconPosition.BOTTOM_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = stringResource(R.string.settings_immich_pos_label),
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, "$baseUrl/photos/${asset.id}".toUri())
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }

                            Spacer(Modifier.height(panelPushDp))
                        }
                    }
                }

                if (!isNext) {
                    val keepAlpha = (offsetX.value / 200f).coerceIn(0f, 1f)
                    val deleteAlpha = (-offsetX.value / 200f).coerceIn(0f, 1f)
                    if (isSwipeInverted) {
                        // Inversion des badges
                        if (keepAlpha > 0f) {
                            IndicatorBadge(stringResource(R.string.swipe_delete_upper), MaterialRed, Alignment.TopStart, keepAlpha * 0.9f)
                        } else if (deleteAlpha > 0f) {
                            IndicatorBadge(stringResource(R.string.swipe_keep_upper), MaterialGreen, Alignment.TopEnd, deleteAlpha * 0.9f)
                        }
                    } else {
                        // Comportement normal
                        if (keepAlpha > 0f) {
                            IndicatorBadge(stringResource(R.string.swipe_keep_upper), MaterialGreen, Alignment.TopStart, keepAlpha * 0.9f)
                        } else if (deleteAlpha > 0f) {
                            IndicatorBadge(stringResource(R.string.swipe_delete_upper), MaterialRed, Alignment.TopEnd, deleteAlpha * 0.9f)
                        }
                    }
                }
            }
        }
    }

    if (isFullscreenOpen) {
        FullscreenViewer(
            asset = asset,
            player = exoPlayer,
            onClose = { isFullscreenOpen = false }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SharedVideoPlayer(
    player: Player,
    isFullscreen: Boolean,
    cardDisplayMode: com.minos2020.immichswipe.core.CardDisplayMode = CardDisplayMode.FILL,
    onDoubleTap: (() -> Unit)? = null
) {
    AndroidView(
        factory = { context ->
            // On utilise un layout XML pour pouvoir spécifier le surface_type="texture_view"
            // car le setter setSurfaceType n'est pas public dans Media3 PlayerView.
            val view = LayoutInflater.from(context).inflate(R.layout.view_player_texture, null) as PlayerView
            view
        },
        update = { view ->
            // Re-attache le player si nécessaire
            if (view.player != player) view.player = player
            view.useController = isFullscreen

            // TextureView est indispensable pour une intégration parfaite avec les couches Compose
            // Cela évite les écrans noirs au retour du plein écran.
            view.resizeMode = if (isFullscreen) {
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            } else {
                if (cardDisplayMode == CardDisplayMode.FILL) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }

            // S'assure que la lecture reprend bien
            if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                player.play()
            }
        },
        onRelease = { view ->
            // Détache proprement le player de la vue avant destruction
            view.player = null
        },
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (onDoubleTap != null) {
                    Modifier.pointerInput(onDoubleTap) {
                        detectTapGestures(onDoubleTap = { onDoubleTap() })
                    }
                } else Modifier
            )
    )
}

@Composable
fun FullscreenViewer(
    asset: Asset,
    player: Player?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val swipeY = remember { Animatable(0f) }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - (swipeY.value / 1000f)).coerceIn(0f, 1f)))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (swipeY.value > 120) onClose()
                            else scope.launch { swipeY.animateTo(0f) }
                        },
                        onDrag = { change, dragAmount ->
                            // Le swipe down n'est déclenché que si le ZoomableBox ne consomme pas l'événement
                            // (ce qui arrive quand on n'est pas en train de zoomer/panner)
                            change.consume()
                            scope.launch { swipeY.snapTo((swipeY.value + dragAmount.y).coerceAtLeast(0f)) }
                        }
                    )
                }
                .offset { IntOffset(0, swipeY.value.roundToInt()) },
            contentAlignment = Alignment.Center
        ) {
            ZoomableBox(
                modifier = Modifier.fillMaxSize(),
                resetOnRelease = false,
                aspectRatio = asset.exifInfo?.let { it.imageWidth?.toFloat()?.div(it.imageHeight?.toFloat() ?: 1f) }
            ) {
                if (asset.type == "VIDEO" && player != null) {
                    SharedVideoPlayer(player = player, isFullscreen = true)
                } else {
                    val baseUrlClean = SessionManager.getBaseUrl()?.removeSuffix("/")
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("$baseUrlClean/api/assets/${asset.id}/thumbnail?format=JPEG&size=preview")
                            .addHeader("x-api-key", SessionManager.getApiKey() ?: "")
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(vertical = 50.dp, horizontal = 20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = Color.White)
            }
        }
    }
}

@Composable
fun IndicatorBadge(text: String, color: Color, align: Alignment, alpha: Float) {
    Box(
        modifier = Modifier
            .padding(horizontal = 70.dp, vertical = 35.dp)
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = align
    ) {
        Surface(
            color = color.copy(alpha = 0.05f), // Fond très léger pour le contraste
            contentColor = color, 
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, color.copy(alpha = 0.9f))
        ) {
            Text(
                text = text, fontSize = 32.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    .graphicsLayer { rotationZ = if (align == Alignment.TopStart) -15f else 15f }
            )
        }
    }
}

@Composable
fun MetadataPanel(asset: Asset, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Box(modifier = Modifier.size(40.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.swipe_metadata_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.common_close), modifier = Modifier.size(20.dp)) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
            MetadataRow(Icons.Default.Description, stringResource(R.string.swipe_metadata_file), asset.originalFileName ?: stringResource(R.string.diag_unknown))
            MetadataRow(Icons.Default.CalendarToday, stringResource(R.string.swipe_metadata_date), asset.fileCreatedAt.substringBefore("T"))
            val formatLabel = if (asset.fileExtension != null) "${asset.type} (.${asset.fileExtension.lowercase()})" else asset.type
            MetadataRow(Icons.Default.Info, stringResource(R.string.swipe_metadata_format), formatLabel)
            asset.exifInfo?.let { exif ->
                val sizeLabel = exif.fileSizeInBytes?.let { formatSize(it) } ?: "N/A"
                MetadataRow(Icons.Default.SdStorage, stringResource(R.string.swipe_metadata_size), sizeLabel)
                MetadataRow(Icons.Default.AspectRatio, stringResource(R.string.swipe_metadata_resolution), "${exif.imageWidth ?: "?"} x ${exif.imageHeight ?: "?"}")
            } ?: run {
                MetadataRow(Icons.Default.SdStorage, stringResource(R.string.swipe_metadata_size), stringResource(R.string.swipe_loading))
                MetadataRow(Icons.Default.AspectRatio, stringResource(R.string.swipe_metadata_resolution), stringResource(R.string.swipe_loading))
            }
        }
    }
}

@Composable
fun MetadataRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(80.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Formate une taille en bytes vers une chaîne lisible (Go, Mo).
 */
@Composable
fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> stringResource(R.string.size_unit_gb, gb)
        mb >= 1.0 -> stringResource(R.string.size_unit_mb, mb)
        else -> stringResource(R.string.size_unit_kb, kb)
    }
}

@Composable
fun SummaryDialog(
    uiState: SwipeUiState,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onUndoDecision: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = if (uiState.isSyncing) ({}) else onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.swipe_summary_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                IconButton(onClick = onDismiss, enabled = !uiState.isSyncing) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = uiState.albumName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(20.dp))
                
                // Grille de statistiques (3x2) - Utilisation d'une grille fixe pour éviter l'étirement
                Box(modifier = Modifier.fillMaxWidth()) {
                    val stats = listOf(
                        Triple(stringResource(R.string.swipe_keep), Triple(uiState.keptCount, uiState.keptSize, MaterialGreen), Icons.Default.Check),
                        Triple(stringResource(R.string.swipe_delete), Triple(uiState.deletedCount, uiState.deletedSize, MaterialRed), Icons.Default.Delete),
                        Triple(stringResource(R.string.swipe_archive), Triple(uiState.archiveCount, uiState.archiveSize, MaterialTheme.colorScheme.primary), Icons.Default.Archive),
                        Triple(stringResource(R.string.swipe_locked), Triple(uiState.lockedCount, uiState.lockedSize, MaterialTheme.colorScheme.outline), Icons.Default.Lock),
                        Triple(stringResource(R.string.swipe_skip), Triple(uiState.skippedCount, uiState.skippedSize, Color.Gray), Icons.AutoMirrored.Filled.Forward),
                        Triple(stringResource(R.string.swipe_remaining), Triple(uiState.remainingCount, uiState.remainingSize, MaterialTheme.colorScheme.outlineVariant), Icons.Default.Pending)
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until 3) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val left = stats[i * 2]
                                val right = stats[i * 2 + 1]
                                StatSummaryBox(
                                    label = left.first,
                                    count = left.second.first,
                                    size = left.second.second,
                                    color = left.second.third,
                                    icon = left.third,
                                    isEstimated = left.first == stringResource(R.string.swipe_remaining) && uiState.isRemainingEstimated,
                                    modifier = Modifier.weight(1f)
                                )
                                StatSummaryBox(
                                    label = right.first,
                                    count = right.second.first,
                                    size = right.second.second,
                                    color = right.second.third,
                                    icon = right.third,
                                    isEstimated = right.first == stringResource(R.string.swipe_remaining) && uiState.isRemainingEstimated,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Liste des miniatures à supprimer
                Text(
                    text = stringResource(R.string.swipe_check_before_delete),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(12.dp))

                val deletedAssets = remember(uiState.decisions) {
                    uiState.assets.filter { uiState.decisions[it.id] == SwipeDecision.DELETE }
                }

                if (deletedAssets.isNotEmpty()) {
                    Box(modifier = Modifier.height(220.dp).fillMaxWidth()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(
                                items = deletedAssets,
                                key = { it.id }
                            ) { asset ->
                                DeletedAssetThumbnail(
                                    asset = asset,
                                    uiState = uiState,
                                    onUndo = { onUndoDecision(asset.id) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.swipe_no_deletions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                if (uiState.isSyncing) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        },
        confirmButton = {
            // On veut confirmer la synchro si on a des décisions OU des favoris togglés
            val hasChanges = uiState.processedCount > 0 || uiState.localFavorites.isNotEmpty()
            
            Button(
                onClick = onApply,
                enabled = !uiState.isSyncing && hasChanges,
                colors = ButtonDefaults.buttonColors(containerColor = if (uiState.deletedCount > 0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                val totalSize = formatSize(uiState.deletedSize)
                Text(
                    text = if (uiState.isSyncing) stringResource(R.string.swipe_syncing) else stringResource(R.string.swipe_liberate_button, totalSize, uiState.deletedCount),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        shape = RoundedCornerShape(32.dp)
    )
}

@Composable
fun StatSummaryBox(
    label: String,
    count: Int,
    size: Long,
    color: Color,
    icon: ImageVector,
    isEstimated: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Box(modifier = Modifier.padding(10.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            )
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 1f), // Pleine opacité pour le contraste
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isEstimated) "~ ${formatSize(size)}" else formatSize(size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun DeletedAssetThumbnail(
    asset: Asset,
    uiState: SwipeUiState,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baseUrl = SessionManager.getBaseUrl()?.removeSuffix("/")
    val apiKey = SessionManager.getApiKey() ?: ""
    
    val hasHeart = uiState.isFavorite(asset.id)
    val hasArchive = asset.isArchived
    val hasLock = asset.isLocked

    val imageRequest = remember(asset.id, baseUrl, apiKey) {
        ImageRequest.Builder(context)
            .data("$baseUrl/api/assets/${asset.id}/thumbnail?format=WEBP")
            .addHeader("x-api-key", apiKey)
            .crossfade(true)
            .precision(coil.size.Precision.INEXACT)
            .build()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onUndo() }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Conteneur de badges en haut à gauche (comme timeline)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (hasHeart) TimelineMiniBadge(Icons.Default.Favorite, Color.Red)
            if (hasArchive) TimelineMiniBadge(Icons.Default.Archive, Color.Black)
            if (hasLock) TimelineMiniBadge(Icons.Default.Lock, Color.Black)
        }
        
        // Petit badge "Undo" discret en haut à droite
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            color = Color.Black.copy(alpha = 0.6f),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = Color.White,
                modifier = Modifier.size(16.dp).padding(2.dp)
            )
        }
        
        // Icône "Play" pour les vidéos dans le résumé
        if (asset.type == "VIDEO") {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            )
        }

        // Affichage de la taille de l'asset en bas
        val assetSize = asset.exifInfo?.fileSizeInBytes ?: 0L
        if (assetSize > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
                    .padding(4.dp)
            ) {
                Text(
                    text = formatSize(assetSize),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

private fun IconPosition.toHorizontalAlignment(): Alignment.Horizontal = when (this) {
    IconPosition.TOP_LEFT, IconPosition.BOTTOM_LEFT -> Alignment.Start
    else -> Alignment.End
}

@Composable
private fun SwipeActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
    ) {
        Icon(icon, contentDescription, tint = Color.White)
    }
}

/**
 * Un conteneur qui permet le pinch-to-zoom et le panoramique avec inertie.
 * Supporte une réinitialisation automatique au relâchement (pour les cartes)
 * ou un zoom persistant avec double-tap pour réinitialiser (pour le plein écran).
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    resetOnRelease: Boolean = false,
    enabled: Boolean = true,
    aspectRatio: Float? = null,
    isFillMode: Boolean = false,
    onDoubleTap: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier, content = content)
        return
    }

    val scope = rememberCoroutineScope()

    // Calcul de la "base" pour le mode FILL si l'aspect ratio est connu
    var fillScale by remember(aspectRatio) { mutableFloatStateOf(1f) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Animatables pour les transitions fluides et l'inertie
    val animatedScale = remember { Animatable(1f) }
    val animatedOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Mise à jour de la configuration quand le mode ou l'aspect ratio change
    LaunchedEffect(isFillMode, fillScale) {
        val target = if (isFillMode) fillScale else 1f
        if (resetOnRelease) {
            launch { animatedScale.animateTo(target, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
            launch { animatedOffset.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
        } else {
            scale = target
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { intSize ->
                if (aspectRatio != null && intSize.width > 0 && intSize.height > 0) {
                    val boxRatio = intSize.width.toFloat() / intSize.height.toFloat()
                    fillScale = if (aspectRatio > boxRatio) {
                        aspectRatio / boxRatio
                    } else {
                        boxRatio / aspectRatio
                    }
                }
            }
            .pointerInput(resetOnRelease, isFillMode, fillScale) {
                val velocityTracker = VelocityTracker()
                awaitEachGesture {
                    velocityTracker.resetTracking()
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid()

                        // On arrête toute animation en cours dès qu'un doigt touche l'écran
                        if (event.changes.any { it.pressed }) {
                            scope.launch {
                                animatedScale.stop()
                                animatedOffset.stop()
                            }
                        }

                        if (event.changes.size >= 2) {
                            // Pinch to zoom
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val oldScale = if (resetOnRelease) animatedScale.value else scale
                                // On permet de dézoomer en dessous du min pendant le geste pour la fluidité (ressort)
                                val minAllowedScale = if (resetOnRelease) (if (isFillMode) fillScale else 1f) * 0.7f else 0.7f
                                val newScale = (oldScale * zoomChange).coerceIn(minAllowedScale, 5f)

                                // Calcul du centre relatif pour zoomer au bon endroit
                                val centroidInCenter = centroid - Offset(size.width / 2f, size.height / 2f)
                                val oldOffset = if (resetOnRelease) animatedOffset.value else offset

                                // Formule pour garder le point sous les doigts stable pendant le zoom
                                val newOffset = oldOffset + (centroidInCenter - oldOffset) * ((oldScale - newScale) / oldScale) + panChange

                                if (resetOnRelease) {
                                    scope.launch {
                                        animatedScale.snapTo(newScale)
                                        animatedOffset.snapTo(newOffset)
                                    }
                                } else {
                                    scale = newScale
                                    offset = newOffset
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } else if (event.changes.size == 1 && !resetOnRelease) {
                            // Panning (un doigt) - Autorisé UNIQUEMENT en plein écran
                            val currentScale = scale
                            val minScale = 1f
                            
                            if (currentScale > minScale * 1.01f) {
                                if (panChange != Offset.Zero) {
                                    offset += panChange
                                    event.changes.forEach { 
                                        velocityTracker.addPosition(it.uptimeMillis, it.position)
                                        it.consume() 
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // À la fin du geste
                    val finalScale = if (resetOnRelease) animatedScale.value else scale
                    val baseScale = if (isFillMode && resetOnRelease) fillScale else 1f

                    if (resetOnRelease) {
                        // Réinitialisation complète et immédiate pour les swipe cards (retour au centre)
                        scope.launch {
                            launch { animatedScale.animateTo(baseScale, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                            launch { animatedOffset.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                        }
                    } else {
                        // Pour le plein écran : on gère l'inertie
                        val targetScale = finalScale.coerceAtLeast(1f)
                        val velocity = velocityTracker.calculateVelocity()
                        
                        scope.launch {
                            // Animation du scale si besoin (retour au x1 si dézoomé)
                            if (targetScale != finalScale) {
                                launch {
                                    animate(finalScale, targetScale, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { value, _ ->
                                        scale = value
                                    }
                                }
                            }
                            
                            // Animation du panoramique avec inertie (Fling)
                            launch {
                                val decay = exponentialDecay<Offset>(frictionMultiplier = 1.5f)
                                animatedOffset.snapTo(offset) // On utilise Animatable pour le fling
                                animatedOffset.animateDecay(Offset(velocity.x, velocity.y), decay) {
                                    // Pendant le fling, on contraint l'offset aux limites
                                    val currentMaxX = (size.width * (scale - 1f) / 2f).coerceAtLeast(0f)
                                    val currentMaxY = (size.height * (scale - 1f) / 2f).coerceAtLeast(0f)
                                    
                                    val constrained = Offset(
                                        value.x.coerceIn(-currentMaxX, currentMaxX),
                                        value.y.coerceIn(-currentMaxY, currentMaxY)
                                    )
                                    
                                    offset = constrained
                                    // Si on touche un bord, on arrête le fling
                                    if (constrained != value) {
                                        scope.launch { animatedOffset.stop() }
                                    }
                                }
                                
                                // Si pas de fling ou après le fling, on s'assure d'être dans les clous
                                val finalMaxX = (size.width * (scale - 1f) / 2f).coerceAtLeast(0f)
                                val finalMaxY = (size.height * (scale - 1f) / 2f).coerceAtLeast(0f)
                                val targetOffset = Offset(
                                    offset.x.coerceIn(-finalMaxX, finalMaxX),
                                    offset.y.coerceIn(-finalMaxY, finalMaxY)
                                )
                                if (targetOffset != offset) {
                                    animate(
                                        typeConverter = Offset.VectorConverter,
                                        initialValue = offset,
                                        targetValue = targetOffset,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                    ) { value, _ ->
                                        offset = value
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .then(
                Modifier.pointerInput(resetOnRelease, scale) {
                    detectTapGestures(onDoubleTap = { tapOffset ->
                        if (!resetOnRelease) {
                            // Mode plein écran : Bascule x1 / x3
                            if (scale > 1.01f) {
                                scope.launch {
                                    launch {
                                        androidx.compose.animation.core.animate(scale, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { value, _ ->
                                            scale = value
                                        }
                                    }
                                    launch {
                                        androidx.compose.animation.core.animate(
                                            typeConverter = Offset.VectorConverter,
                                            initialValue = offset,
                                            targetValue = Offset.Zero,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                        ) { value, _ ->
                                            offset = value
                                        }
                                    }
                                }
                            } else {
                                // Zoom x3 à l'endroit du tap
                                val targetScale = 3f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val centroidInCenter = tapOffset - center
                                
                                // Calcul de l'offset cible pour centrer sur le tap
                                val targetOffsetRaw = centroidInCenter * (1f - targetScale)
                                
                                // On applique les limites immédiatement
                                val maxX = (size.width * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                val maxY = (size.height * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                val targetOffset = Offset(
                                    targetOffsetRaw.x.coerceIn(-maxX, maxX),
                                    targetOffsetRaw.y.coerceIn(-maxY, maxY)
                                )

                                scope.launch {
                                    launch {
                                        androidx.compose.animation.core.animate(scale, targetScale, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { value, _ ->
                                            scale = value
                                        }
                                    }
                                    launch {
                                        androidx.compose.animation.core.animate(
                                            typeConverter = Offset.VectorConverter,
                                            initialValue = offset,
                                            targetValue = targetOffset,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                        ) { value, _ ->
                                            offset = value
                                        }
                                    }
                                }
                            }
                        } else {
                            // Mode Swipe Card : Action externe (ex: ouvrir plein écran)
                            onDoubleTap?.invoke()
                        }
                    })
                }
            )
            .graphicsLayer {
                val s = if (resetOnRelease) animatedScale.value else scale
                val o = if (resetOnRelease) animatedOffset.value else offset
                scaleX = s
                scaleY = s
                translationX = o.x
                translationY = o.y
            },
        content = content
    )
}

