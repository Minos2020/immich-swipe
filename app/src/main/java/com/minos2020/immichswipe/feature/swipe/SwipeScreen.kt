package com.minos2020.immichswipe.feature.swipe

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.minos2020.immichswipe.core.*
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.domain.model.Asset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private val MaterialGreen = Color(0xFF2E7D32)
private val MaterialRed = Color(0xFFC62828)

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
    sessionKey: String,
    resetSignal: kotlinx.coroutines.flow.SharedFlow<Unit>,
    modifier: Modifier = Modifier
) {
    val viewModel: SwipeViewModel = viewModel(
        key = "$sessionKey-${album.id}",
        factory = SwipeViewModelFactory(assetRepository, sessionRepository, swipeDecisionRepository, album)
    )
    
    LaunchedEffect(resetSignal) {
        resetSignal.collect {
            viewModel.toggleResetConfirmation(true)
        }
    }

    LaunchedEffect(album.id) {
        viewModel.retryLoading()
    }

    val uiState by viewModel.uiState.collectAsState()

    // Gestion partagée de l'ExoPlayer pour l'asset courant (Regular <-> Fullscreen)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playbackBehavior = uiState.playbackBehavior
    val currentAsset = uiState.currentAsset
    val sharedPlayer = remember(currentAsset?.id) {
        if (currentAsset?.type == "VIDEO") {
            val baseUrl = SessionManager.getBaseUrl()?.removeSuffix("/")
            val apiKey = SessionManager.getApiKey() ?: ""
            ExoPlayer.Builder(context).build().apply {
                if (playbackBehavior != PlaybackBehavior.IGNORE) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build()
                    setAudioAttributes(audioAttributes, true)
                }
                repeatMode = Player.REPEAT_MODE_ONE
                val videoUrl = "$baseUrl/api/assets/${currentAsset.id}/video/playback"
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

    // Mise à jour du volume quand isMuted change
    LaunchedEffect(sharedPlayer, uiState.isMuted) {
        sharedPlayer?.volume = if (uiState.isMuted) 0f else 1f
    }

    DisposableEffect(sharedPlayer) {
        onDispose { sharedPlayer?.release() }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { _ ->
        viewModel.onLocalDeleteIntentHandled()
    }

    LaunchedEffect(uiState.localDeletePendingIntent) {
        uiState.localDeletePendingIntent?.let { pendingIntent ->
            try {
                deleteLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } catch (_: Exception) {
                viewModel.onLocalDeleteIntentHandled()
            }
        }
    }
    
    var showSortMenu by remember { mutableStateOf(false) }
    val connectionStatus by SessionManager.connectionStatus.collectAsState()

    LaunchedEffect(connectionStatus.level) {
        if (uiState.error != null && connectionStatus.level == com.minos2020.immichswipe.core.ConnectionLevel.ONLINE) {
            viewModel.retryLoading()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.downloadRequestSignal.collect { asset ->
            val baseUrl = SessionManager.getBaseUrl()?.removeSuffix("/") ?: return@collect
            val apiKey = SessionManager.getApiKey() ?: return@collect
            
            // Using /original for the raw file, which is often more reliable than /download (which can return a zip)
            val downloadUrl = "$baseUrl/api/assets/${asset.id}/original"
            
            // Sanitize filename: remove path components and keep only the name
            val rawName = asset.originalFileName ?: "immich_${asset.id}.${asset.fileExtension ?: "jpg"}"
            val fileName = rawName.substringAfterLast('/').substringAfterLast('\\')
            
            try {
                val request = android.app.DownloadManager.Request(downloadUrl.toUri())
                    .setTitle(fileName)
                    .setDescription("Downloading from Immich")
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                    .addRequestHeader("x-api-key", apiKey)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                downloadManager.enqueue(request)
                
                android.widget.Toast.makeText(context, "Download started: $fileName", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                AppLogger.e("Download", "Error starting download", e)
                android.widget.Toast.makeText(context, "Error starting download: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareRequestSignal.collect { asset ->
            val baseUrl = SessionManager.getBaseUrl()?.removeSuffix("/") ?: return@collect
            val apiKey = SessionManager.getApiKey() ?: return@collect
            val shareUrl = "$baseUrl/api/assets/${asset.id}/original"
            
            android.widget.Toast.makeText(context, "Preparing share...", android.widget.Toast.LENGTH_SHORT).show()

            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val client = okhttp3.OkHttpClient()
                    val request = okhttp3.Request.Builder()
                        .url(shareUrl)
                        .addHeader("x-api-key", apiKey)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Server returned ${response.code}")
                        
                        val body = response.body ?: throw Exception("Empty response body")
                        val fileName = asset.originalFileName?.substringAfterLast('/')?.substringAfterLast('\\') 
                            ?: "immich_${asset.id}.${asset.fileExtension ?: "jpg"}"
                        
                        val cacheDir = java.io.File(context.cacheDir, "shared_assets").apply { mkdirs() }
                        val file = java.io.File(cacheDir, fileName)
                        
                        file.outputStream().use { output ->
                            body.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }

                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = if (asset.type == "VIDEO") "video/*" else "image/*"
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share media"))
                    }
                } catch (e: Exception) {
                    AppLogger.e("Share", "Error sharing asset", e)
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to share: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SwipeHeader(
            uiState = uiState,
            onSummaryClick = { viewModel.toggleSummary(true) }
        )

        AssetTimeline(
            assets = uiState.assets,
            decisions = uiState.decisions,
            currentIndex = uiState.currentIndex,
            isFavorite = { uiState.isFavorite(it) },
            isArchived = { uiState.isArchived(it) },
            isLocked = { uiState.isLocked(it) },
            onAssetClick = { viewModel.onMoveToAsset(it) }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
                            fullscreenButtonPosition = uiState.fullscreenButtonPosition,
                            immichButtonPosition = uiState.immichButtonPosition,
                            cardDisplayButtonPosition = uiState.cardDisplayButtonPosition,
                            muteButtonPosition = uiState.muteButtonPosition,
                            showFullscreenButton = uiState.showFullscreenButton,
                            showImmichButton = uiState.showImmichButton,
                            showCardDisplayButton = uiState.showCardDisplayButton,
                            showMuteButton = uiState.showMuteButton,
                            cardDisplayMode = uiState.cardDisplayMode,
                            onToggleDisplayMode = { viewModel.toggleDisplayMode() },
                            isFullscreenOpen = uiState.isFullscreenMode,
                            onDoubleTap = { viewModel.toggleFavorite() },
                            onOpenFullscreen = { viewModel.toggleFullscreen(true) },
                            providedPlayer = if (!isNextCard) sharedPlayer else null,
                            showSizeIndicator = uiState.sortOrder == SortOrder.SIZE_DESC || uiState.sortOrder == SortOrder.SIZE_ASC,
                            isMuted = uiState.isMuted,
                            onToggleMute = { viewModel.toggleMute() },
                            downloadButtonPosition = uiState.downloadButtonPosition,
                            showDownloadButton = uiState.showDownloadButton,
                            onDownload = { viewModel.downloadAsset(it) },
                            shareButtonPosition = uiState.shareButtonPosition,
                            showShareButton = uiState.showShareButton,
                            onShare = { viewModel.shareAsset(it) }
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Celebration,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.swipe_congratulations),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.toggleSummary(true) },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.swipe_sync_changes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (uiState.showSwipeButtons) 16.dp else 24.dp),
                horizontalArrangement = if (uiState.showSwipeButtons) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                IconButton(
                    onClick = {
                        if (!uiState.swapSummaryArchive) viewModel.toggleArchive()
                        else viewModel.toggleSummary(true)
                    },
                    modifier = Modifier.size(if (uiState.showSwipeButtons) 36.dp else 44.dp)
                ) {
                    Icon(
                        imageVector = if (!uiState.swapSummaryArchive) Icons.Default.Archive else Icons.Default.Assessment,
                        contentDescription = if (!uiState.swapSummaryArchive) stringResource(R.string.swipe_archive) else stringResource(R.string.swipe_summary_title),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(if (uiState.showSwipeButtons) 22.dp else 26.dp)
                    )
                }

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

                IconButton(
                    onClick = { viewModel.toggleLock() },
                    modifier = Modifier.size(if (uiState.showSwipeButtons) 36.dp else 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.swipe_locked),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(if (uiState.showSwipeButtons) 22.dp else 26.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(if (uiState.showSwipeButtons) 36.dp else 44.dp)
                    ) {
                        val icon = when (uiState.sortOrder) {
                            SortOrder.SHUFFLED -> Icons.Default.Shuffle
                            SortOrder.CHRONOLOGICAL_ASC -> Icons.Default.ArrowUpward
                            SortOrder.CHRONOLOGICAL_DESC -> Icons.Default.ArrowDownward
                            SortOrder.SIZE_DESC -> Icons.Default.ExpandMore
                            SortOrder.SIZE_ASC -> Icons.Default.ExpandLess
                            SortOrder.TYPE_VIDEO_FIRST -> Icons.Default.Videocam
                            SortOrder.TYPE_PHOTO_FIRST -> Icons.Default.Image
                            SortOrder.TYPE_VIDEO_FIRST_SHUFFLED -> Icons.Default.Videocam
                            SortOrder.TYPE_PHOTO_FIRST_SHUFFLED -> Icons.Default.Image
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(R.string.settings_sort_order_label),
                            tint = if (uiState.sortOrder != SortOrder.CHRONOLOGICAL_DESC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(if (uiState.showSwipeButtons) 22.dp else 26.dp)
                        )
                    }
                    
                    if (showSortMenu) {
                        Popup(
                            alignment = Alignment.BottomCenter,
                            offset = IntOffset(0, -110),
                            onDismissRequest = { showSortMenu = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Surface(
                                modifier = Modifier.width(300.dp),
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp,
                                shadowElevation = 12.dp,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CategoryButton(
                                            text = stringResource(R.string.sort_category_time),
                                            selected = uiState.sortCategory == SortCategory.TIME,
                                            onClick = { viewModel.setSortCategory(SortCategory.TIME) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CategoryButton(
                                            text = stringResource(R.string.sort_category_size),
                                            selected = uiState.sortCategory == SortCategory.SIZE,
                                            onClick = { viewModel.setSortCategory(SortCategory.SIZE) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CategoryButton(
                                            text = stringResource(R.string.sort_category_type),
                                            selected = uiState.sortCategory == SortCategory.TYPE,
                                            onClick = { viewModel.setSortCategory(SortCategory.TYPE) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp)
                                    Spacer(Modifier.height(8.dp))

                                    when (uiState.sortCategory) {
                                        SortCategory.TIME -> {
                                            SortPopupItem(R.string.settings_sort_newest, Icons.Default.ArrowDownward, uiState.sortOrder == SortOrder.CHRONOLOGICAL_DESC) {
                                                viewModel.setSortOrder(SortOrder.CHRONOLOGICAL_DESC)
                                                showSortMenu = false
                                            }
                                            SortPopupItem(R.string.settings_sort_oldest, Icons.Default.ArrowUpward, uiState.sortOrder == SortOrder.CHRONOLOGICAL_ASC) {
                                                viewModel.setSortOrder(SortOrder.CHRONOLOGICAL_ASC)
                                                showSortMenu = false
                                            }
                                            SortPopupItem(R.string.settings_sort_shuffled, Icons.Default.Shuffle, uiState.sortOrder == SortOrder.SHUFFLED) {
                                                viewModel.setSortOrder(SortOrder.SHUFFLED)
                                                showSortMenu = false
                                            }
                                        }
                                        SortCategory.SIZE -> {
                                            SortPopupItem(R.string.settings_sort_biggest, Icons.Default.ExpandMore, uiState.sortOrder == SortOrder.SIZE_DESC) {
                                                viewModel.setSortOrder(SortOrder.SIZE_DESC)
                                                showSortMenu = false
                                            }
                                            SortPopupItem(R.string.settings_sort_smallest, Icons.Default.ExpandLess, uiState.sortOrder == SortOrder.SIZE_ASC) {
                                                viewModel.setSortOrder(SortOrder.SIZE_ASC)
                                                showSortMenu = false
                                            }
                                        }
                                        SortCategory.TYPE -> {
                                            SortPopupItem(R.string.settings_sort_videos, Icons.Default.Videocam, uiState.sortOrder == SortOrder.TYPE_VIDEO_FIRST || uiState.sortOrder == SortOrder.TYPE_VIDEO_FIRST_SHUFFLED) {
                                                val isShuffledNow = uiState.sortOrder == SortOrder.TYPE_VIDEO_FIRST_SHUFFLED || uiState.sortOrder == SortOrder.TYPE_PHOTO_FIRST_SHUFFLED
                                                viewModel.setSortOrder(if (isShuffledNow) SortOrder.TYPE_VIDEO_FIRST_SHUFFLED else SortOrder.TYPE_VIDEO_FIRST)
                                                showSortMenu = false
                                            }
                                            SortPopupItem(R.string.settings_sort_photos, Icons.Default.Image, uiState.sortOrder == SortOrder.TYPE_PHOTO_FIRST || uiState.sortOrder == SortOrder.TYPE_PHOTO_FIRST_SHUFFLED) {
                                                val isShuffledNow = uiState.sortOrder == SortOrder.TYPE_VIDEO_FIRST_SHUFFLED || uiState.sortOrder == SortOrder.TYPE_PHOTO_FIRST_SHUFFLED
                                                viewModel.setSortOrder(if (isShuffledNow) SortOrder.TYPE_PHOTO_FIRST_SHUFFLED else SortOrder.TYPE_PHOTO_FIRST)
                                                showSortMenu = false
                                            }
                                            
                                            Spacer(Modifier.height(8.dp))
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), thickness = 0.5.dp)
                                            
                                            val isShuffled = uiState.sortOrder == SortOrder.TYPE_VIDEO_FIRST_SHUFFLED || uiState.sortOrder == SortOrder.TYPE_PHOTO_FIRST_SHUFFLED
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { 
                                                        val newOrder = when(uiState.sortOrder) {
                                                            SortOrder.TYPE_VIDEO_FIRST -> SortOrder.TYPE_VIDEO_FIRST_SHUFFLED
                                                            SortOrder.TYPE_VIDEO_FIRST_SHUFFLED -> SortOrder.TYPE_VIDEO_FIRST
                                                            SortOrder.TYPE_PHOTO_FIRST -> SortOrder.TYPE_PHOTO_FIRST_SHUFFLED
                                                            SortOrder.TYPE_PHOTO_FIRST_SHUFFLED -> SortOrder.TYPE_PHOTO_FIRST
                                                            else -> SortOrder.TYPE_VIDEO_FIRST_SHUFFLED
                                                        }
                                                        viewModel.setSortOrder(newOrder)
                                                    }
                                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Shuffle, null, tint = if (isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(16.dp))
                                                Text(
                                                    text = stringResource(R.string.settings_sort_shuffled),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (isShuffled) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Switch(
                                                    checked = isShuffled,
                                                    onCheckedChange = { checked ->
                                                        val newOrder = if (checked) {
                                                            if (uiState.sortOrder == SortOrder.TYPE_PHOTO_FIRST) SortOrder.TYPE_PHOTO_FIRST_SHUFFLED else SortOrder.TYPE_VIDEO_FIRST_SHUFFLED
                                                        } else {
                                                            if (uiState.sortOrder == SortOrder.TYPE_PHOTO_FIRST_SHUFFLED) SortOrder.TYPE_PHOTO_FIRST else SortOrder.TYPE_VIDEO_FIRST
                                                        }
                                                        viewModel.setSortOrder(newOrder)
                                                    },
                                                    modifier = Modifier.scale(0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

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

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(120.dp))
        }
    }

    if (uiState.showSummary) {
        SummaryDialog(
            uiState = uiState,
            onDismiss = { viewModel.toggleSummary(false) },
            onApply = { viewModel.applyChanges() },
            onUndoDecision = { viewModel.undoSpecificDecision(it) }
        )
    }

    if (uiState.isFullscreenMode && uiState.currentAsset != null) {
        val currentAsset = uiState.currentAsset!!
        FullscreenViewer(
            asset = currentAsset,
            isFavorite = uiState.isFavorite(currentAsset.id),
            playbackBehavior = uiState.playbackBehavior,
            muteButtonPosition = uiState.muteButtonPosition,
            onSwipe = {
                viewModel.onSwipe(it)
            },
            onUndo = { viewModel.undo() },
            onDoubleTap = { viewModel.toggleFavorite() },
            onClose = { viewModel.toggleFullscreen(false) },
            providedPlayer = sharedPlayer,
            showSizeIndicator = uiState.sortOrder == SortOrder.SIZE_DESC || uiState.sortOrder == SortOrder.SIZE_ASC,
            isMuted = uiState.isMuted,
            onToggleMute = { viewModel.toggleMute() },
            showMuteButton = true, // Mute button is always shown in fullscreen for videos
            downloadButtonPosition = uiState.downloadButtonPosition,
            showDownloadButton = false, // Download button hidden in fullscreen mode as requested
            onDownload = { viewModel.downloadAsset(it) },
            shareButtonPosition = uiState.shareButtonPosition,
            showShareButton = false, // Share button hidden in fullscreen mode for consistency
            onShare = { viewModel.shareAsset(it) }
        )
    }

    if (uiState.showSuccessAnimation) {
        SuccessAnimationOverlay()
    }

    if (uiState.showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleResetConfirmation(false) },
            title = { Text(stringResource(R.string.swipe_reset_confirm_title)) },
            text = { Text(stringResource(R.string.swipe_reset_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetAlbumDecisions() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.swipe_reset_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleResetConfirmation(false) }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
fun CategoryButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun SortPopupItem(textRes: Int, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SuccessAnimationOverlay() {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    val iconScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
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
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "ProgressBarAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onSummaryClick() },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = constraints.maxWidth.toFloat()
            val progressWidth = totalWidth * animatedProgress
            val density = LocalDensity.current
            val paddingPx = with(density) { 16.dp.toPx() }
            val spacingPx = paddingPx

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

            val titleThreshold = with(density) { 250.dp.toPx() }
            val titleIsPushed = progressWidth > paddingPx && progressWidth < titleThreshold
            val titleTranslationX by animateFloatAsState(
                targetValue = if (titleIsPushed) progressWidth + spacingPx else paddingPx,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "TitleTranslation"
            )

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatBadge(label = stringResource(R.string.swipe_keep), count = uiState.allKeptCount, color = MaterialGreen)
            StatBadge(label = stringResource(R.string.swipe_delete), count = uiState.deletedCount, color = MaterialRed)
            StatBadge(label = stringResource(R.string.swipe_remaining), count = uiState.remainingCount, color = MaterialTheme.colorScheme.outline)
        }
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
            .height(60.dp)
            .padding(vertical = 2.dp),
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
                    .size(48.dp)
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
    fullscreenButtonPosition: IconPosition,
    immichButtonPosition: IconPosition,
    cardDisplayButtonPosition: IconPosition,
    muteButtonPosition: IconPosition,
    showFullscreenButton: Boolean = true,
    showImmichButton: Boolean = true,
    showCardDisplayButton: Boolean = true,
    showMuteButton: Boolean = true,
    downloadButtonPosition: IconPosition = IconPosition.TOP_LEFT,
    showDownloadButton: Boolean = false,
    shareButtonPosition: IconPosition = IconPosition.TOP_RIGHT,
    showShareButton: Boolean = false,
    cardDisplayMode: CardDisplayMode,
    onToggleDisplayMode: () -> Unit,
    isFullscreenOpen: Boolean,
    onDoubleTap: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onDownload: (Asset) -> Unit = {},
    onShare: (Asset) -> Unit = {},
    providedPlayer: ExoPlayer? = null,
    showSizeIndicator: Boolean = false,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val baseUrl = SessionManager.getBaseUrl()?.removeSuffix("/")
    val apiKey = SessionManager.getApiKey() ?: ""
    val lifecycleOwner = LocalLifecycleOwner.current

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    var pausedByHoldState by remember { mutableStateOf(false) }
    var ignoreNextTap by remember { mutableStateOf(false) }

    var isVideoReady by remember(asset.id) { mutableStateOf(false) }
    var showLoadingIndicator by remember(asset.id) { mutableStateOf(false) }
    var showMuteIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(showMuteIndicator) {
        if (showMuteIndicator) {
            delay(1000)
            showMuteIndicator = false
        }
    }

    LaunchedEffect(asset.id, isVideoReady) {
        if (!isVideoReady) {
            delay(500)
            showLoadingIndicator = true
        } else {
            showLoadingIndicator = false
        }
    }

    val metadataHeight = 300.dp
    val metadataHeightPx = with(density) { metadataHeight.toPx() }

    val internalExoPlayer = remember(asset.id, isNext) {
        if (asset.type == "VIDEO" && !isNext && providedPlayer == null) {
            ExoPlayer.Builder(context).build().apply {
                if (playbackBehavior != PlaybackBehavior.IGNORE) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build()
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

    val exoPlayer = providedPlayer ?: internalExoPlayer

    // Mise à jour du volume quand isMuted change pour le player interne
    LaunchedEffect(internalExoPlayer, isMuted) {
        internalExoPlayer?.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isVideoReady = state == Player.STATE_READY
            }
        }

        if (exoPlayer?.playbackState == Player.STATE_READY) {
            isVideoReady = true
        }
        exoPlayer?.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Si on est en fullscreen, on laisse le fullscreenViewer gérer la pause
                    if (!isFullscreenOpen) exoPlayer?.playWhenReady = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (!isNext && !isFullscreenOpen) exoPlayer?.playWhenReady = true
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        exoPlayer?.volume = if (isMuted) 0f else 1f
        if (pausedByHoldState) exoPlayer?.pause() else if (!isNext && !isFullscreenOpen) exoPlayer?.play()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer?.removeListener(listener)
            internalExoPlayer?.stop()
            internalExoPlayer?.release()
        }
    }

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
                                    onSwipe(SwipeDecision.KEEP)
                                } else if (currentX < -250) {
                                    offsetX.animateTo(-1500f, tween(150))
                                    onSwipe(SwipeDecision.DELETE)
                                } else {
                                    launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }

                                    val wasOpen = offsetY.targetValue < -metadataHeightPx / 2
                                    if (wasOpen) {
                                        if (currentY < -metadataHeightPx * 0.95f) {
                                            offsetY.animateTo(-metadataHeightPx, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                        } else {
                                            offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                        }
                                    } else {
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
                    if (!isFullscreenOpen) {
                        ZoomableBox(
                            modifier = Modifier.fillMaxSize(),
                            resetOnRelease = true,
                            onTap = {
                                if (!ignoreNextTap) {
                                    onToggleMute()
                                    showMuteIndicator = true
                                }
                                ignoreNextTap = false
                            },
                            onDoubleTap = onDoubleTap,
                            onPress = {
                                ignoreNextTap = false
                                val wasReleased = withTimeoutOrNull(500) {
                                    awaitRelease()
                                    true
                                }
                                if (wasReleased == null) {
                                    ignoreNextTap = true
                                    pausedByHoldState = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        pausedByHoldState = false
                                    }
                                }
                            }
                        ) {
                            SharedVideoPlayer(
                                player = exoPlayer,
                                isFullscreen = false,
                                isMuted = isMuted,
                                isPaused = pausedByHoldState,
                                cardDisplayMode = cardDisplayMode,
                                fileSize = asset.exifInfo?.fileSizeInBytes,
                                showSize = showSizeIndicator
                            )

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
                        onDoubleTap = onDoubleTap
                    ) {
                        AsyncImage(
                            model = photoRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (showSizeIndicator && !isNext) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = formatSize(asset.exifInfo?.fileSizeInBytes ?: 0L),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

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

                if (!isNext) {
                    val densityLocal = LocalDensity.current
                    val panelPushDp = with(densityLocal) { (-offsetY.value).toDp() }

                    listOf(Alignment.Start, Alignment.End).forEach { side ->
                        Column(
                            modifier = Modifier
                                .align(if (side == Alignment.Start) Alignment.TopStart else Alignment.TopEnd)
                                .fillMaxHeight()
                                .padding(8.dp),
                            horizontalAlignment = side
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (showFullscreenButton && fullscreenButtonPosition.toHorizontalAlignment() == side && (fullscreenButtonPosition == IconPosition.TOP_LEFT || fullscreenButtonPosition == IconPosition.TOP_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.Default.Fullscreen,
                                        contentDescription = stringResource(R.string.settings_fullscreen_pos_label),
                                        onClick = onOpenFullscreen
                                    )
                                }
                                if (showCardDisplayButton && cardDisplayButtonPosition.toHorizontalAlignment() == side && (cardDisplayButtonPosition == IconPosition.TOP_LEFT || cardDisplayButtonPosition == IconPosition.TOP_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = if (cardDisplayMode == CardDisplayMode.FILL)
                                            Icons.Default.FitScreen else Icons.Default.AspectRatio,
                                        contentDescription = stringResource(R.string.swipe_toggle_display),
                                        onClick = onToggleDisplayMode
                                    )
                                }
                                if (showImmichButton && immichButtonPosition.toHorizontalAlignment() == side && (immichButtonPosition == IconPosition.TOP_LEFT || immichButtonPosition == IconPosition.TOP_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = stringResource(R.string.settings_immich_pos_label),
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, "$baseUrl/photos/${asset.id}".toUri())
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                                if (showMuteButton && muteButtonPosition.toHorizontalAlignment() == side && (muteButtonPosition == IconPosition.TOP_LEFT || muteButtonPosition == IconPosition.TOP_RIGHT) && asset.type == "VIDEO") {
                                    SwipeActionIconButton(
                                        icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Mute",
                                        onClick = {
                                            onToggleMute()
                                            showMuteIndicator = true
                                        }
                                    )
                                }
                                if (showDownloadButton && downloadButtonPosition.toHorizontalAlignment() == side && (downloadButtonPosition == IconPosition.TOP_LEFT || downloadButtonPosition == IconPosition.TOP_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.Default.FileDownload,
                                        contentDescription = "Download",
                                        onClick = { onDownload(asset) }
                                    )
                                }
                                if (showShareButton && shareButtonPosition.toHorizontalAlignment() == side && (shareButtonPosition == IconPosition.TOP_LEFT || shareButtonPosition == IconPosition.TOP_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.Default.Share,
                                        contentDescription = "Share",
                                        onClick = { onShare(asset) }
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (showFullscreenButton && fullscreenButtonPosition.toHorizontalAlignment() == side && (fullscreenButtonPosition == IconPosition.BOTTOM_LEFT || fullscreenButtonPosition == IconPosition.BOTTOM_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.Default.Fullscreen,
                                        contentDescription = stringResource(R.string.settings_fullscreen_pos_label),
                                        onClick = onOpenFullscreen
                                    )
                                }
                                if (showCardDisplayButton && cardDisplayButtonPosition.toHorizontalAlignment() == side && (cardDisplayButtonPosition == IconPosition.BOTTOM_LEFT || cardDisplayButtonPosition == IconPosition.BOTTOM_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = if (cardDisplayMode == CardDisplayMode.FILL)
                                            Icons.Default.FitScreen else Icons.Default.AspectRatio,
                                        contentDescription = stringResource(R.string.swipe_toggle_display),
                                        onClick = onToggleDisplayMode
                                    )
                                }
                                if (showImmichButton && immichButtonPosition.toHorizontalAlignment() == side && (immichButtonPosition == IconPosition.BOTTOM_LEFT || immichButtonPosition == IconPosition.BOTTOM_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = stringResource(R.string.settings_immich_pos_label),
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, "$baseUrl/photos/${asset.id}".toUri())
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                                if (showMuteButton && muteButtonPosition.toHorizontalAlignment() == side && (muteButtonPosition == IconPosition.BOTTOM_LEFT || muteButtonPosition == IconPosition.BOTTOM_RIGHT) && asset.type == "VIDEO") {
                                    SwipeActionIconButton(
                                        icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Mute",
                                        onClick = {
                                            onToggleMute()
                                            showMuteIndicator = true
                                        }
                                    )
                                }
                                if (showDownloadButton && downloadButtonPosition.toHorizontalAlignment() == side && (downloadButtonPosition == IconPosition.BOTTOM_LEFT || downloadButtonPosition == IconPosition.BOTTOM_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.Default.FileDownload,
                                        contentDescription = "Download",
                                        onClick = { onDownload(asset) }
                                    )
                                }
                                if (showShareButton && shareButtonPosition.toHorizontalAlignment() == side && (shareButtonPosition == IconPosition.BOTTOM_LEFT || shareButtonPosition == IconPosition.BOTTOM_RIGHT)) {
                                    SwipeActionIconButton(
                                        icon = Icons.Default.Share,
                                        contentDescription = "Share",
                                        onClick = { onShare(asset) }
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
                    if (keepAlpha > 0f) {
                        IndicatorBadge(stringResource(R.string.swipe_keep_upper), MaterialGreen, Alignment.TopStart, keepAlpha * 0.9f)
                    } else if (deleteAlpha > 0f) {
                        IndicatorBadge(stringResource(R.string.swipe_delete_upper), MaterialRed, Alignment.TopEnd, deleteAlpha * 0.9f)
                    }
                }

                // Mute/Unmute popup indicator
                androidx.compose.animation.AnimatedVisibility(
                    visible = showMuteIndicator,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 1.2f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SharedVideoPlayer(
    player: Player,
    isFullscreen: Boolean,
    isMuted: Boolean = false,
    isPaused: Boolean = false,
    cardDisplayMode: com.minos2020.immichswipe.core.CardDisplayMode = CardDisplayMode.FILL,
    onDoubleTap: (() -> Unit)? = null,
    fileSize: Long? = null,
    showSize: Boolean = false,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    controlsOffset: Dp = 0.dp
) {
    var currentTime by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        while (true) {
            currentTime = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(200)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                val view = LayoutInflater.from(context).inflate(R.layout.view_player_texture, null) as PlayerView
                view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    onControllerVisibilityChanged?.invoke(visibility == android.view.View.VISIBLE)
                })
                view
            },
            update = { view ->
                if (view.player != player) view.player = player
                view.useController = isFullscreen
                player.volume = if (isMuted) 0f else 1f
                view.resizeMode = if (isFullscreen) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    if (cardDisplayMode == CardDisplayMode.FILL) {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }

                if (player.playbackState == Player.STATE_READY && !player.isPlaying && !isPaused) {
                    player.play()
                } else if (isPaused) {
                    player.pause()
                }
            },
            onRelease = { view ->
                view.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Size tag at BottomCenter
        if (showSize && fileSize != null) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (if (isFullscreen) (if (isLandscape) 80.dp else 80.dp) else 12.dp) + controlsOffset),
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = formatSize(fileSize),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (duration > 0 && !isFullscreen) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "${formatMediaTime(currentTime)} / ${formatMediaTime(duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (isPaused) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Paused",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
fun FullscreenViewer(
    asset: Asset,
    isFavorite: Boolean,
    playbackBehavior: PlaybackBehavior,
    muteButtonPosition: IconPosition,
    onSwipe: (SwipeDecision) -> Unit,
    onUndo: () -> Unit,
    onDoubleTap: () -> Unit,
    onClose: () -> Unit,
    providedPlayer: ExoPlayer? = null,
    showSizeIndicator: Boolean = false,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    showMuteButton: Boolean = true,
    downloadButtonPosition: IconPosition = IconPosition.TOP_LEFT,
    showDownloadButton: Boolean = false,
    shareButtonPosition: IconPosition = IconPosition.TOP_RIGHT,
    showShareButton: Boolean = false,
    onDownload: (Asset) -> Unit = {},
    onShare: (Asset) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    var pausedByHoldState by remember { mutableStateOf(false) }
    var ignoreNextTap by remember { mutableStateOf(false) }

    val swipeY = remember { Animatable(0f) }
    val swipeX = remember { Animatable(0f) }

    val currentOnSwipe by rememberUpdatedState(onSwipe)

    var controlsVisible by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    val controlsOffset by animateDpAsState(
        targetValue = if (controlsVisible && isLandscape) 64.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ControlsShift"
    )

    LaunchedEffect(asset.id) {
        swipeX.snapTo(0f)
        swipeY.snapTo(0f)
    }

    val baseUrl = SessionManager.getBaseUrl()?.removeSuffix("/")
    val apiKey = SessionManager.getApiKey() ?: ""

    val internalExoPlayer = remember(asset.id) {
        if (asset.type == "VIDEO" && providedPlayer == null) {
            ExoPlayer.Builder(context).build().apply {
                if (playbackBehavior != PlaybackBehavior.IGNORE) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build()
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

    val exoPlayer = providedPlayer ?: internalExoPlayer

    LaunchedEffect(exoPlayer, isMuted) {
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(pausedByHoldState, exoPlayer) {
        if (pausedByHoldState) exoPlayer?.pause() else exoPlayer?.play()
    }

    DisposableEffect(asset.id) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            internalExoPlayer?.release()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogView = LocalView.current

        SideEffect {
            val window = (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, dialogView)
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - (swipeY.value / 1000f)).coerceIn(0f, 1f)))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val currentX = swipeX.value
                                val currentY = swipeY.value

                                if (currentY > 120 && abs(currentX) < 100) {
                                    onClose()
                                } else if (currentX > 250) {
                                    swipeX.animateTo(2000f, tween(200))
                                    currentOnSwipe(SwipeDecision.KEEP)
                                } else if (currentX < -250) {
                                    swipeX.animateTo(-2000f, tween(200))
                                    currentOnSwipe(SwipeDecision.DELETE)
                                } else {
                                    launch { swipeY.animateTo(0f) }
                                    launch { swipeX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                swipeY.snapTo((swipeY.value + dragAmount.y).coerceAtLeast(0f))
                                swipeX.snapTo(swipeX.value + dragAmount.x)
                            }
                        }
                    )
                }
                .offset { IntOffset(swipeX.value.roundToInt(), swipeY.value.roundToInt()) }
                .graphicsLayer {
                    rotationZ = swipeX.value / 60f
                },
            contentAlignment = Alignment.Center
        ) {
            ZoomableBox(
                modifier = Modifier.fillMaxSize(),
                resetOnRelease = false,
                onTap = {
                    if (!ignoreNextTap) onToggleMute()
                    ignoreNextTap = false
                },
                onDoubleTap = onDoubleTap,
                onPress = {
                    ignoreNextTap = false
                    val wasReleased = withTimeoutOrNull(500) {
                        awaitRelease()
                        true
                    }
                    if (wasReleased == null) {
                        ignoreNextTap = true
                        pausedByHoldState = true
                        try {
                            awaitRelease()
                        } finally {
                            pausedByHoldState = false
                        }
                    }
                },
                aspectRatio = asset.exifInfo?.let { it.imageWidth?.toFloat()?.div(it.imageHeight?.toFloat() ?: 1f) }
            ) {
                if (asset.type == "VIDEO" && exoPlayer != null) {
                    SharedVideoPlayer(
                        player = exoPlayer,
                        isFullscreen = true,
                        isMuted = isMuted,
                        isPaused = pausedByHoldState,
                        fileSize = asset.exifInfo?.fileSizeInBytes,
                        showSize = showSizeIndicator,
                        onControllerVisibilityChanged = { controlsVisible = it },
                        controlsOffset = controlsOffset
                    )
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

            if (showSizeIndicator && asset.type != "VIDEO") {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (if (isLandscape) 80.dp else 80.dp) + controlsOffset),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = formatSize(asset.exifInfo?.fileSizeInBytes ?: 0L),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            val keepAlpha = (swipeX.value / 200f).coerceIn(0f, 1f)
            val deleteAlpha = (-swipeX.value / 200f).coerceIn(0f, 1f)

            if (keepAlpha > 0f) IndicatorBadge(stringResource(R.string.swipe_keep_upper), MaterialGreen, Alignment.TopStart, keepAlpha * 0.9f)
            else if (deleteAlpha > 0f) IndicatorBadge(stringResource(R.string.swipe_delete_upper), MaterialRed, Alignment.TopEnd, deleteAlpha * 0.9f)

            // Indicateur de Favori (Coeur au centre en bas)
            val heartScale = animateFloatAsState(if (isFavorite) 1.2f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "HeartScale").value
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (if (isLandscape) 20.dp else 106.dp) + controlsOffset)
                    .graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                    }
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    .clip(CircleShape)
                    .clickable { onDoubleTap() }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) Color.Red else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
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

            IconButton(
                onClick = onUndo,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = (if (isLandscape) 20.dp else 100.dp) + controlsOffset)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.nav_back),
                    tint = Color.White
                )
            }

            if (showMuteButton && asset.type == "VIDEO") {
                val muteAlign = when (muteButtonPosition) {
                    IconPosition.TOP_LEFT -> Alignment.TopStart
                    IconPosition.TOP_RIGHT -> Alignment.TopEnd
                    IconPosition.BOTTOM_LEFT -> Alignment.BottomStart
                    IconPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                }

                val mutePadding = when (muteButtonPosition) {
                    IconPosition.TOP_LEFT -> Modifier.padding(top = 50.dp, start = 20.dp)
                    IconPosition.TOP_RIGHT -> Modifier.padding(top = 110.dp, end = 20.dp)
                    IconPosition.BOTTOM_LEFT -> Modifier.padding(bottom = (if (isLandscape) 80.dp else 160.dp) + controlsOffset, start = 24.dp)
                    IconPosition.BOTTOM_RIGHT -> Modifier.padding(bottom = (if (isLandscape) 20.dp else 100.dp) + controlsOffset, end = 24.dp)
                }

                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .align(muteAlign)
                        .then(mutePadding)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute",
                        tint = Color.White
                    )
                }
            }

            if (showDownloadButton) {
                val dlAlign = when (downloadButtonPosition) {
                    IconPosition.TOP_LEFT -> Alignment.TopStart
                    IconPosition.TOP_RIGHT -> Alignment.TopEnd
                    IconPosition.BOTTOM_LEFT -> Alignment.BottomStart
                    IconPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                }

                val dlPadding = when (downloadButtonPosition) {
                    IconPosition.TOP_LEFT -> Modifier.padding(top = 50.dp, start = 20.dp)
                    IconPosition.TOP_RIGHT -> Modifier.padding(top = 110.dp, end = 20.dp)
                    IconPosition.BOTTOM_LEFT -> Modifier.padding(bottom = (if (isLandscape) 80.dp else 160.dp) + controlsOffset, start = 24.dp)
                    IconPosition.BOTTOM_RIGHT -> Modifier.padding(bottom = (if (isLandscape) 20.dp else 100.dp) + controlsOffset, end = 24.dp)
                }

                IconButton(
                    onClick = { onDownload(asset) },
                    modifier = Modifier
                        .align(dlAlign)
                        .then(dlPadding)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Download",
                        tint = Color.White
                    )
                }
            }

            if (showShareButton) {
                val shareAlign = when (shareButtonPosition) {
                    IconPosition.TOP_LEFT -> Alignment.TopStart
                    IconPosition.TOP_RIGHT -> Alignment.TopEnd
                    IconPosition.BOTTOM_LEFT -> Alignment.BottomStart
                    IconPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                }

                val sharePadding = when (shareButtonPosition) {
                    IconPosition.TOP_LEFT -> Modifier.padding(top = 50.dp, start = 20.dp)
                    IconPosition.TOP_RIGHT -> Modifier.padding(top = 110.dp, end = 20.dp)
                    IconPosition.BOTTOM_LEFT -> Modifier.padding(bottom = (if (isLandscape) 80.dp else 160.dp) + controlsOffset, start = 24.dp)
                    IconPosition.BOTTOM_RIGHT -> Modifier.padding(bottom = (if (isLandscape) 20.dp else 100.dp) + controlsOffset, end = 24.dp)
                }

                IconButton(
                    onClick = { onShare(asset) },
                    modifier = Modifier
                        .align(shareAlign)
                        .then(sharePadding)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }
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
            color = color.copy(alpha = 0.05f),
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

                Box(modifier = Modifier.fillMaxWidth()) {
                    val stats = listOf(
                        Triple(stringResource(R.string.swipe_keep), Triple(uiState.keptCount, uiState.keptSize, MaterialGreen), Icons.Default.Check),
                        Triple(stringResource(R.string.swipe_delete), Triple(uiState.deletedCount, uiState.deletedSize, MaterialRed), Icons.Default.Delete),
                        Triple(stringResource(R.string.swipe_archive), Triple(uiState.archiveCount, uiState.archiveSize, MaterialTheme.colorScheme.primary), Icons.Default.Archive),
                        Triple(stringResource(R.string.swipe_locked), Triple(uiState.lockedCount, uiState.lockedSize, MaterialTheme.colorScheme.outline), Icons.Default.Lock),
                        Triple(stringResource(R.string.swipe_remaining), Triple(uiState.remainingCount, uiState.remainingSize, MaterialTheme.colorScheme.outlineVariant), Icons.Default.Pending)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until 2) {
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
                        val last = stats.last()
                        StatSummaryBox(
                            label = last.first,
                            count = last.second.first,
                            size = last.second.second,
                            color = last.second.third,
                            icon = last.third,
                            isEstimated = last.first == stringResource(R.string.swipe_remaining) && uiState.isRemainingEstimated,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

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
                    color = color.copy(alpha = 1f),
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
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onPress: (suspend PressGestureScope.(Offset) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier, content = content)
        return
    }

    val scope = rememberCoroutineScope()
    var fillScale by remember(aspectRatio) { mutableFloatStateOf(1f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val animatedScale = remember { Animatable(1f) }
    val animatedOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

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
                boxSize = intSize
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
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)

                        if (event.changes.size >= 2) {
                            // Zooming with 2 fingers
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val oldScale = if (resetOnRelease) animatedScale.value else scale
                                val newScale = (oldScale * zoomChange).coerceIn(0.7f, 5f)
                                
                                val oldOffset = if (resetOnRelease) animatedOffset.value else offset
                                // Correct formula for zooming around centroid with default Center origin
                                val newOffset = (centroid - size.toSize().center) * (1f - zoomChange) + (oldOffset * zoomChange) + panChange

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
                        }
else if (event.changes.size == 1 && (if(resetOnRelease) animatedScale.value else scale) > 1.05f) {
                            // Panning with 1 finger ONLY if zoomed in
                            if (panChange != Offset.Zero) {
                                if (resetOnRelease) {
                                    scope.launch {
                                        animatedOffset.snapTo(animatedOffset.value + panChange)
                                    }
                                } else {
                                    offset += panChange
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (resetOnRelease) {
                        scope.launch {
                            launch { animatedScale.animateTo(if (isFillMode) fillScale else 1f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                            launch { animatedOffset.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                        }
                    } else if (scale < 1.01f) {
                        // Snap back to default if zoomed out in fullscreen
                        scope.launch {
                            launch { animate(scale, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { v, _ -> scale = v } }
                            launch { animate(typeConverter = Offset.VectorConverter, initialValue = offset, targetValue = Offset.Zero, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { v, _ -> offset = v } }
                        }
                    }
                }
            }
            .then(
                Modifier.pointerInput(resetOnRelease, scale, onDoubleTap, onTap, onPress) {
                    detectTapGestures(
                        onTap = { onTap?.invoke() },
                        onDoubleTap = { tapOffset ->
                            if (onDoubleTap != null) {
                                onDoubleTap()
                            } else if (!resetOnRelease) {
                                if (scale > 1.01f) {
                                    scope.launch {
                                        launch { animate(scale, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { v, _ -> scale = v } }
                                        launch { animate(typeConverter = Offset.VectorConverter, initialValue = offset, targetValue = Offset.Zero, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { v, _ -> offset = v } }
                                    }
                                } else {
                                    val targetScale = 3f
                                    val zoomChange = targetScale / scale
                                    val targetOffset = (tapOffset - boxSize.toSize().center) * (1f - zoomChange) + offset * zoomChange
                                    scope.launch {
                                        launch { animate(scale, targetScale, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { v, _ -> scale = v } }
                                        launch { animate(typeConverter = Offset.VectorConverter, initialValue = offset, targetValue = targetOffset, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { v, _ -> offset = v } }
                                    }
                                }
                            }
                        },
                        onPress = { offset ->
                            onPress?.invoke(this, offset)
                        }
                    )
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

@Composable
fun HeaderTitle(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun HeaderInfo(progressText: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = progressText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = color
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.List,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color
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
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
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

private fun formatMediaTime(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
