package com.minos2020.immichswipe.feature.home

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.minos2020.immichswipe.R
import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.domain.model.Album
import com.minos2020.immichswipe.feature.settings.SettingsScreen
import com.minos2020.immichswipe.feature.settings.SettingsViewModel
import com.minos2020.immichswipe.feature.settings.SettingsViewModelFactory
import com.minos2020.immichswipe.feature.swipe.SwipeDecision
import com.minos2020.immichswipe.feature.swipe.SwipeScreen
import com.minos2020.immichswipe.feature.swipe.SwipeViewModel
import com.minos2020.immichswipe.feature.swipe.SwipeViewModelFactory
import com.minos2020.immichswipe.ui.theme.VirtualGold
import com.minos2020.immichswipe.core.SwipeSortOrder
import com.minos2020.immichswipe.core.SwipeSortPriority
import com.minos2020.immichswipe.data.repository.AlbumRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    assetRepository: AssetRepository,
    swipeDecisionRepository: SwipeDecisionRepository,
    albumRepository: AlbumRepository,
    sessionKey: String,
    modifier: Modifier = Modifier,
) {
    val uiState: HomeUiState by viewModel.uiState.collectAsState()
    val isSettings = uiState.currentTab == HomeTab.SETTINGS
    val isHome = uiState.currentTab == HomeTab.HOME
    val isSwipe = uiState.currentTab == HomeTab.SWIPE

    // On instancie le SwipeViewModel ici pour pouvoir l'utiliser dans la TopAppBar
    // SOLUTION : On ajoute le sessionKey à la clé du ViewModel pour éviter les fuites de données entre utilisateurs
    // On ajoute également l'assetCount à la clé pour forcer le rafraîchissement si le contenu de l'album change
    val swipeViewModel: SwipeViewModel? = if (uiState.selectedAlbum != null) {
        val album = uiState.selectedAlbum!!
        viewModel(
            key = "${album.id}-${album.assetCount}-$sessionKey",
            factory = SwipeViewModelFactory(
                assetRepository,
                viewModel.getSessionRepository(),
                swipeDecisionRepository,
                albumRepository,
                album
            )
        )
    } else null

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // Animation d'orbite fluide pour la découverte/indexation
    val badgeAngle = remember { Animatable(0f) }

    LaunchedEffect(uiState.isDiscovering) {
        if (uiState.isDiscovering) {
            // Rotation continue avec effet d'accélération/ralentissement
            while (true) {
                badgeAngle.animateTo(
                    targetValue = badgeAngle.value + 800,
                    animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing)
                )
            }
        } else {
            // Retour fluide à la position de repos (multiple de 360°)
            val target = kotlin.math.ceil(badgeAngle.value / 360f) * 360f
            badgeAngle.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Charger l'utilisateur et les albums au premier affichage
    LaunchedEffect(Unit) {
        viewModel.loadUser()
    }

    // Mise à jour des noms localisés pour les albums virtuels
    val virtualSkippedName = stringResource(R.string.home_virtual_skipped_synced)
    val virtualSkippedDesc = stringResource(R.string.home_virtual_skipped_synced_desc)
    val virtualAllName = stringResource(R.string.home_virtual_all_assets)
    val virtualAllDesc = stringResource(R.string.home_virtual_all_assets_desc)
    val virtualOrphansName = stringResource(R.string.home_virtual_orphans)
    val virtualOrphansDesc = stringResource(R.string.home_virtual_orphans_desc)

    LaunchedEffect(virtualSkippedName, virtualSkippedDesc, virtualAllName, virtualAllDesc, virtualOrphansName, virtualOrphansDesc) {
        viewModel.updateVirtualNames(Album.VIRTUAL_SKIPPED_ID, virtualSkippedName, virtualSkippedDesc)
        viewModel.updateVirtualNames(Album.VIRTUAL_ALL_ID, virtualAllName, virtualAllDesc)
        viewModel.updateVirtualNames(Album.VIRTUAL_ORPHANS_ID, virtualOrphansName, virtualOrphansDesc)
    }

    // Gestion du retour physique/gestuel du téléphone
    BackHandler(enabled = uiState.currentTab != HomeTab.HOME) {
        viewModel.goBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (isSettings) {
                // Barre de titre pour les paramètres
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                // Barre principale avec logo et profil
                Column {
                    TopAppBar(
                        title = {
                            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                            val logoRes = if (isDark) R.drawable.immichswipe_logo_colors_dark else R.drawable.immichswipe_logo_colors_light
                            
                            Image(
                                painter = painterResource(id = logoRes),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier
                                    .height(35.dp)
                                    .padding(vertical = 4.dp),
                                contentScale = ContentScale.Fit
                            )
                        },
                        actions = {
                            if (isHome) {
                                // Bouton Stats (Nouveau)
                                IconButton(onClick = { viewModel.toggleStatsPopup(true) }) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = "Statistiques"
                                    )
                                }

                                // Bouton pour basculer le layout
                                IconButton(onClick = { viewModel.toggleLayoutMode() }) {
                                    Icon(
                                        imageVector = if (uiState.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                        contentDescription = stringResource(R.string.settings_layout_label)
                                    )
                                }
                            }

                            if (isSwipe && swipeViewModel != null) {
                                val swipeUiState by swipeViewModel.uiState.collectAsState()
                                var showSortMenu by remember { mutableStateOf(false) }

                                // Bouton Reset Session avec animation de rotation/échelle
                                AnimatedVisibility(
                                    visible = swipeUiState.decisions.isNotEmpty(),
                                    enter = fadeIn() + scaleIn(initialScale = 0.5f) + expandHorizontally(),
                                    exit = fadeOut() + scaleOut(targetScale = 0.5f) + shrinkHorizontally()
                                ) {
                                    val rotation by transition.animateFloat(label = "resetRotate") { state ->
                                        if (state == EnterExitState.Visible) 0f else -180f
                                    }
                                    IconButton(
                                        onClick = { swipeViewModel.setShowResetConfirmation(true) },
                                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = stringResource(R.string.swipe_reset_button),
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Sort,
                                            contentDescription = stringResource(R.string.swipe_sort_button),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        modifier = Modifier
                                            .width(220.dp)
                                            .background(MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.swipe_sort_order_title).uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                        )

                                        SortMenuItem(
                                            label = stringResource(R.string.swipe_sort_date_desc),
                                            icon = Icons.Default.ArrowDownward,
                                            selected = swipeUiState.sortOrder == SwipeSortOrder.DATE_DESC,
                                            caption = stringResource(R.string.swipe_sort_faster_loading),
                                            onClick = { swipeViewModel.setSortOrder(SwipeSortOrder.DATE_DESC); showSortMenu = false }
                                        )
                                        SortMenuItem(
                                            label = stringResource(R.string.swipe_sort_date_asc),
                                            icon = Icons.Default.ArrowUpward,
                                            selected = swipeUiState.sortOrder == SwipeSortOrder.DATE_ASC,
                                            caption = stringResource(R.string.swipe_sort_faster_loading),
                                            onClick = { swipeViewModel.setSortOrder(SwipeSortOrder.DATE_ASC); showSortMenu = false }
                                        )
                                        SortMenuItem(
                                            label = stringResource(R.string.swipe_sort_size_desc),
                                            icon = Icons.Default.ExpandMore,
                                            selected = swipeUiState.sortOrder == SwipeSortOrder.SIZE_DESC,
                                            onClick = { swipeViewModel.setSortOrder(SwipeSortOrder.SIZE_DESC); showSortMenu = false }
                                        )
                                        SortMenuItem(
                                            label = stringResource(R.string.swipe_sort_size_asc),
                                            icon = Icons.Default.ExpandLess,
                                            selected = swipeUiState.sortOrder == SwipeSortOrder.SIZE_ASC,
                                            onClick = { swipeViewModel.setSortOrder(SwipeSortOrder.SIZE_ASC); showSortMenu = false }
                                        )
                                        SortMenuItem(
                                            label = stringResource(R.string.swipe_sort_random),
                                            icon = Icons.Default.Shuffle,
                                            selected = swipeUiState.sortOrder == SwipeSortOrder.RANDOM,
                                            onClick = { swipeViewModel.setSortOrder(SwipeSortOrder.RANDOM); showSortMenu = false }
                                        )

                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )

                                        Text(
                                            text = stringResource(R.string.swipe_sort_priority_title).uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                        )

                                        SortMenuItem(
                                            label = stringResource(R.string.swipe_sort_priority_none),
                                            icon = Icons.Default.Sort,
                                            selected = swipeUiState.sortPriority == SwipeSortPriority.NONE,
                                            onClick = { swipeViewModel.setSortPriority(SwipeSortPriority.NONE); showSortMenu = false }
                                        )
                                        SortMenuItem(
                                            label = stringResource(R.string.swipe_sort_priority_videos),
                                            icon = Icons.Default.Videocam,
                                            selected = swipeUiState.sortPriority == SwipeSortPriority.VIDEOS_FIRST,
                                            onClick = { swipeViewModel.setSortPriority(SwipeSortPriority.VIDEOS_FIRST); showSortMenu = false }
                                        )
                                        SortMenuItem(
                                            label = stringResource(R.string.swipe_sort_priority_photos),
                                            icon = Icons.Default.Image,
                                            selected = swipeUiState.sortPriority == SwipeSortPriority.PHOTOS_FIRST,
                                            onClick = { swipeViewModel.setSortPriority(SwipeSortPriority.PHOTOS_FIRST); showSortMenu = false }
                                        )
                                        
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }

                            val baseUrl = SessionManager.getBaseUrl()
                            val userId = uiState.user?.id
                            val avatarColor = getAvatarColor(uiState.user?.avatarColor)
                            
                            Box(
                                contentAlignment = Alignment.BottomEnd,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(36.dp)
                                    .clickable { viewModel.toggleProfilePopup(true) }
                            ) {
                                // Avatar
                                val profileImageModifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, avatarColor, CircleShape)
                                    .padding(2.dp)
                                    .clip(CircleShape)

                                if ((userId != null) && (baseUrl != null)) {
                                    val cleanBaseUrl = baseUrl.removeSuffix("/")
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data("$cleanBaseUrl/api/users/$userId/profile-image")
                                            .addHeader("x-api-key", SessionManager.getApiKey() ?: "")
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = stringResource(R.string.settings_section_account),
                                        placeholder = rememberVectorPainter(Icons.Default.AccountCircle),
                                        error = rememberVectorPainter(Icons.Default.AccountCircle),
                                        modifier = profileImageModifier,
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = stringResource(R.string.settings_section_account),
                                        modifier = profileImageModifier,
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }

                                // Indicateur de connexion (Badge tricolore) avec animation d'orbite fluide
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            rotationZ = badgeAngle.value
                                        }
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 4.dp, y = 4.dp)
                                            .padding(end = 5.dp, bottom = 4.dp)
                                            .size(10.dp)
                                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                        color = uiState.connectionStatus.level.color,
                                        shape = CircleShape
                                    ) {}
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Barre de recherche (uniquement sur Home)
                    if (isHome) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text(stringResource(R.string.home_search_placeholder), fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_cancel), modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        },
        bottomBar = {
            // On n'affiche la barre du bas QUE si on n'est pas dans les paramètres
            if (!isSettings) {
                NavigationBar {
                    NavigationBarItem(
                        selected = uiState.currentTab == HomeTab.HOME,
                        onClick = { viewModel.onTabSelected(HomeTab.HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home)) }
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == HomeTab.SWIPE,
                        onClick = { viewModel.onTabSelected(HomeTab.SWIPE) },
                        icon = { Icon(Icons.Default.Swipe, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_swipe)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = uiState.currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "TabTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetTab ->
                when (targetTab) {
                    HomeTab.HOME -> {
                        if (uiState.isLoading && uiState.albums.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (uiState.error != null) {
                            ErrorView(error = uiState.error!!, onRetry = { viewModel.loadUser() })
                        } else if (uiState.filteredAlbums.isEmpty() && uiState.searchQuery.isNotEmpty()) {
                            // Aucun résultat de recherche
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.home_no_results), color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            Crossfade(
                                targetState = uiState.isGridView,
                                animationSpec = tween(durationMillis = 500),
                                label = "LayoutSwitch"
                            ) { isGrid ->
                                if (isGrid) {
                                    AlbumGrid(
                                        groupedAlbums = uiState.groupedAlbums,
                                        treatedCounts = uiState.albumTreatedCounts,
                                        unsyncedChanges = uiState.albumUnsyncedChanges,
                                        collapsedCategories = uiState.collapsedCategories,
                                        isRefreshing = uiState.isRefreshing,
                                        state = gridState,
                                        onRefresh = { viewModel.refreshAlbums() },
                                        onAlbumClick = { viewModel.onAlbumSelected(it) },
                                        onToggleCategory = { viewModel.toggleCategory(it) }
                                    )
                                } else {
                                    AlbumList(
                                        groupedAlbums = uiState.groupedAlbums,
                                        treatedCounts = uiState.albumTreatedCounts,
                                        unsyncedChanges = uiState.albumUnsyncedChanges,
                                        collapsedCategories = uiState.collapsedCategories,
                                        isRefreshing = uiState.isRefreshing,
                                        state = listState,
                                        onRefresh = { viewModel.refreshAlbums() },
                                        onAlbumClick = { viewModel.onAlbumSelected(it) },
                                        onResetClick = { viewModel.requestAlbumAction(it, AlbumAction.RESET) },
                                        onKeepAllClick = { viewModel.requestAlbumAction(it, AlbumAction.KEEP_ALL) },
                                        onToggleCategory = { viewModel.toggleCategory(it) }
                                    )
                                }
                            }
                        }
                    }
                    HomeTab.SWIPE -> {
                        if (uiState.selectedAlbum != null && swipeViewModel != null) {
                            SwipeScreen(
                                viewModel = swipeViewModel,
                                availableAlbums = uiState.albums
                            )
                        } else {
                            SwipePlaceholder(selectedAlbum = null)
                        }
                    }
                    HomeTab.SETTINGS -> {
                        val settingsViewModel: SettingsViewModel = viewModel(
                            key = "settings-$sessionKey",
                            factory = SettingsViewModelFactory(
                                viewModel.getSessionRepository(),
                                swipeDecisionRepository
                            )
                        )
                        SettingsScreen(
                            viewModel = settingsViewModel
                        )
                    }
                }
            }
        }
    }

    // Affichage de la fenêtre popup de profil
    if (uiState.showProfilePopup) {
        ProfilePopup(
            user = uiState.user,
            connectionStatus = uiState.connectionStatus,
            onClose = { viewModel.toggleProfilePopup(false) },
            onSettingsClick = { viewModel.onTabSelected(HomeTab.SETTINGS) },
            onLogout = { viewModel.logout() }
        )
    }

    // Affichage de la fenêtre popup de statistiques
    if (uiState.showStatsPopup) {
        StatsPopup(
            stats = uiState.stats,
            onClose = { viewModel.toggleStatsPopup(false) }
        )
    }

    // Affichage des dialogues de confirmation pour les actions d'album
    uiState.pendingAlbumAction?.let { action ->
        val album = uiState.pendingAlbum ?: return@let
        AlertDialog(
            onDismissRequest = { viewModel.dismissAlbumAction() },
            title = {
                Text(
                    text = when (action) {
                        AlbumAction.RESET -> stringResource(R.string.home_album_reset_title)
                        AlbumAction.KEEP_ALL -> stringResource(R.string.home_album_keep_all_title)
                    }
                )
            },
            text = {
                val albumName = album.albumName
                val fullText = when (action) {
                    AlbumAction.RESET -> stringResource(R.string.home_album_reset_msg, albumName)
                    AlbumAction.KEEP_ALL -> stringResource(R.string.home_album_keep_all_msg, album.assetCount, albumName)
                }

                val startIndex = fullText.indexOf(albumName)
                val annotatedText = buildAnnotatedString {
                    if (startIndex >= 0) {
                        append(fullText.substring(0, startIndex))
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            append(albumName)
                        }
                        append(fullText.substring(startIndex + albumName.length))
                    } else {
                        append(fullText)
                    }
                }
                Text(text = annotatedText)
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            AlbumAction.RESET -> viewModel.resetAlbumDecisions(album.id)
                            AlbumAction.KEEP_ALL -> viewModel.markAlbumAsKeep(album)
                        }
                    },
                    colors = if (action == AlbumAction.RESET) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAlbumAction() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun StatsPopup(
    stats: StatsUiData,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                    }
                    Text(
                        text = stringResource(R.string.stats_popup_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(48.dp))
                }

                Spacer(Modifier.height(24.dp))

                // Section "Depuis le début"
                Text(
                    text = stringResource(R.string.stats_section_global),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = stringResource(R.string.stats_deleted_count),
                        value = stats.totalDeleted.toString(),
                        icon = Icons.Default.Delete,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    StatCard(
                        label = stringResource(R.string.stats_bytes_saved),
                        value = formatSize(stats.totalBytesSaved),
                        icon = Icons.Default.CloudDone,
                        color = Color(0xFF388E3C),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = stringResource(R.string.stats_swiped_count),
                        value = stats.totalSwiped.toString(),
                        icon = Icons.Default.Swipe,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    StatCard(
                        label = stringResource(R.string.stats_albums_completed),
                        value = "${stats.completedAlbums} / ${stats.totalAlbums}",
                        icon = Icons.Default.LibraryAddCheck,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Section "Cette semaine"
                Text(
                    text = stringResource(R.string.stats_section_weekly),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stats.weeklyDeleted.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(text = stringResource(R.string.stats_deleted_count), style = MaterialTheme.typography.labelSmall)
                        }
                        VerticalDivider(modifier = Modifier.height(40.dp), thickness = 1.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = formatSize(stats.weeklyBytesSaved), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(text = stringResource(R.string.stats_bytes_saved), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Répartition des décisions
                Text(
                    text = stringResource(R.string.stats_distribution_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(16.dp))

                val distribution = stats.distribution
                if (distribution.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stats_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DistributionBar(label = stringResource(R.string.swipe_keep), percentValue = distribution["KEEP"] ?: 0f, color = Color(0xFF4CAF50))
                        DistributionBar(label = stringResource(R.string.swipe_delete), percentValue = distribution["DELETE"] ?: 0f, color = Color(0xFFF44336))
                        DistributionBar(label = stringResource(R.string.swipe_archive), percentValue = distribution["ARCHIVE"] ?: 0f, color = Color(0xFFFF9800))
                        DistributionBar(label = stringResource(R.string.swipe_locked), percentValue = distribution["LOCK"] ?: 0f, color = Color(0xFF9C27B0))
                        DistributionBar(label = stringResource(R.string.swipe_skip), percentValue = distribution["SKIP"] ?: 0f, color = Color(0xFF9E9E9E))
                    }
                }
                
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun DistributionBar(label: String, percentValue: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = "${(percentValue * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percentValue },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
    }
}

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
fun ProfilePopup(
    user: com.minos2020.immichswipe.domain.model.User?,
    connectionStatus: com.minos2020.immichswipe.core.ConnectionStatus,
    onClose: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val baseUrl = SessionManager.getBaseUrl()?.removeSuffix("/")
    val apiKey = SessionManager.getApiKey() ?: ""

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header avec logo et bouton fermer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                    }
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val logoRes = if (isDark) R.drawable.immichswipe_logo_colors_dark else R.drawable.immichswipe_logo_colors_light
                    
                    Image(
                        painter = painterResource(id = logoRes),
                        contentDescription = null,
                        modifier = Modifier.height(24.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(48.dp))
                }

                Spacer(Modifier.height(24.dp))

                // Photo de profil grande
                val avatarColor = getAvatarColor(user?.avatarColor)
                val profileModifier = Modifier
                    .size(100.dp)
                    .border(3.dp, avatarColor, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)

                if (user != null && baseUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("$baseUrl/api/users/${user.id}/profile-image")
                            .addHeader("x-api-key", apiKey)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.settings_section_account),
                        placeholder = rememberVectorPainter(Icons.Default.AccountCircle),
                        error = rememberVectorPainter(Icons.Default.AccountCircle),
                        modifier = profileModifier,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = stringResource(R.string.settings_section_account),
                        modifier = profileModifier,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = user?.name ?: stringResource(R.string.home_user_fallback),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user?.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(Modifier.height(24.dp))

                // Diagnostic de connexion
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = connectionStatus.level.color.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, connectionStatus.level.color.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(connectionStatus.level.color, CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when(connectionStatus.level) {
                                    com.minos2020.immichswipe.core.ConnectionLevel.ONLINE -> stringResource(R.string.diag_online)
                                    com.minos2020.immichswipe.core.ConnectionLevel.ISSUES -> stringResource(R.string.diag_issues)
                                    com.minos2020.immichswipe.core.ConnectionLevel.OFFLINE -> stringResource(R.string.diag_offline)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = connectionStatus.level.color
                            )
                            val message = when(connectionStatus.type) {
                                com.minos2020.immichswipe.core.DiagStatus.CONNECTED -> stringResource(R.string.diag_connected_msg)
                                com.minos2020.immichswipe.core.DiagStatus.AUTH_ERROR -> stringResource(R.string.diag_error_auth)
                                com.minos2020.immichswipe.core.DiagStatus.UNAVAILABLE -> stringResource(R.string.diag_error_unavailable, connectionStatus.statusCode ?: 0)
                                com.minos2020.immichswipe.core.DiagStatus.UNEXPECTED -> stringResource(R.string.diag_error_unexpected, connectionStatus.statusCode ?: 0)
                                com.minos2020.immichswipe.core.DiagStatus.DNS_ERROR -> stringResource(R.string.diag_error_dns)
                                com.minos2020.immichswipe.core.DiagStatus.TIMEOUT -> stringResource(R.string.diag_error_timeout)
                                com.minos2020.immichswipe.core.DiagStatus.NO_INTERNET -> stringResource(R.string.diag_error_no_internet)
                                com.minos2020.immichswipe.core.DiagStatus.CONNECTION_ERROR -> stringResource(R.string.diag_error_connection)
                                com.minos2020.immichswipe.core.DiagStatus.LOGGED_OUT -> stringResource(R.string.diag_logged_out)
                                else -> connectionStatus.rawMessage ?: stringResource(R.string.diag_unknown)
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val hint = when(connectionStatus.type) {
                                com.minos2020.immichswipe.core.DiagStatus.AUTH_ERROR -> stringResource(R.string.diag_error_auth_hint)
                                com.minos2020.immichswipe.core.DiagStatus.UNAVAILABLE -> stringResource(R.string.diag_error_unavailable_hint)
                                com.minos2020.immichswipe.core.DiagStatus.DNS_ERROR -> stringResource(R.string.diag_error_dns_hint)
                                com.minos2020.immichswipe.core.DiagStatus.TIMEOUT -> stringResource(R.string.diag_error_timeout_hint)
                                com.minos2020.immichswipe.core.DiagStatus.NO_INTERNET -> stringResource(R.string.diag_error_no_internet_hint)
                                com.minos2020.immichswipe.core.DiagStatus.CONNECTION_ERROR -> connectionStatus.rawMessage
                                else -> null
                            }
                            if (hint != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Actions
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column {
                        PopupActionItem(
                            icon = Icons.Default.Settings,
                            text = stringResource(R.string.settings_title),
                            onClick = onSettingsClick
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        PopupActionItem(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            text = stringResource(R.string.profile_logout_button),
                            onClick = onLogout,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Lien Code Source et Version
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW,
                                    "https://github.com/Minos2020/immich-swipe".toUri())
                                context.startActivity(intent)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = rememberVectorPainter(Icons.Default.Code),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.profile_source_code),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val packageInfo = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val versionName = packageInfo?.versionName ?: ""

                    if (versionName.isNotEmpty()) {
                        Text(
                            text = "v$versionName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 100.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PopupActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
fun SwipeableAlbumRow(
    album: Album,
    treatedCount: Int,
    unsyncedCount: Int,
    swipedAlbumId: String?,
    onSwiped: (String?) -> Unit,
    onAlbumClick: () -> Unit,
    onResetClick: () -> Unit,
    onKeepAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    
    // SÉCURITÉ EXCLUSIVE : Si un autre album est swipé, on se referme
    LaunchedEffect(swipedAlbumId) {
        if (swipedAlbumId != album.id && offsetX.value != 0f) {
            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
        }
    }

    // Configuration dynamique des actions
    val slotWidth = 72.dp
    val actionCount = 2 
    val totalActionWidth = slotWidth * actionCount
    val totalActionWidthPx = with(LocalDensity.current) { totalActionWidth.toPx() }
    
    // On ajoute un "débordement" vers la gauche pour couvrir les arrondis de l'album
    val backgroundWidth = totalActionWidth + 40.dp 

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) 
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        // --- COUCHE FOND (Actions rapides stylisées) ---
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(backgroundWidth) // Plus large que la zone de snap
                .fillMaxHeight() 
        ) {
            // Action 2 : VALIDATE ALL (Le slot qui s'étend sous l'album)
            Box(
                modifier = Modifier
                    .weight(1f) // Prend tout le surplus vers la gauche
                    .fillMaxHeight()
                    .background(Color(0xFF2E7D32).copy(alpha = 0.15f))
                    .clickable { 
                        scope.launch { offsetX.animateTo(0f) }
                        onKeepAllClick()
                    },
                contentAlignment = Alignment.CenterEnd // Aligne le slot utile vers la droite (colle au bouton Reset)
            ) {
                // On centre l'icône uniquement dans sa zone utile de 72dp
                Box(
                    modifier = Modifier.width(slotWidth).fillMaxHeight(), 
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Action 1 : RESET (Slot standard à droite)
            Box(
                modifier = Modifier
                    .width(slotWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                    .clickable { 
                        scope.launch { offsetX.animateTo(0f) }
                        onResetClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // --- COUCHE DESSUS (Contenu de l'album) ---
        // On applique l'offset et les gestes directement sur l'AlbumItem
        AlbumItem(
            album = album,
            treatedCount = treatedCount,
            unsyncedCount = unsyncedCount,
            onClick = {
                if (offsetX.value != 0f) {
                    scope.launch { offsetX.animateTo(0f) }
                } else {
                    onAlbumClick()
                }
            },
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                // Aimantage intelligent basé sur la largeur totale des actions
                                if (offsetX.value < -totalActionWidthPx * 0.4f) {
                                    offsetX.animateTo(-totalActionWidthPx, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                    onSwiped(album.id)
                                } else {
                                    offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                    if (swipedAlbumId == album.id) onSwiped(null)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            // Dès qu'on commence à tirer significativement, on prévient les autres
                            if (offsetX.value == 0f && dragAmount < 0) onSwiped(album.id)

                            // On limite la glisse pour ne pas sortir de l'écran (max 1.2x la zone d'action)
                            val newOffset = (offsetX.value + dragAmount).coerceIn(-totalActionWidthPx * 1.2f, 0f)
                            scope.launch { offsetX.snapTo(newOffset) }
                        }
                    )
                }
        )
    }
}

@Composable
fun AlbumList(
    groupedAlbums: Map<AlbumStatus, List<Album>>,
    treatedCounts: Map<String, Int>,
    unsyncedChanges: Map<String, Int>,
    collapsedCategories: Set<AlbumStatus>,
    isRefreshing: Boolean,
    state: LazyListState,
    onRefresh: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onResetClick: (Album) -> Unit,
    onKeepAllClick: (Album) -> Unit,
    onToggleCategory: (AlbumStatus) -> Unit
) {
    var swipedAlbumId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // On définit l'ordre d'affichage des catégories
                val statusOrder = listOf(AlbumStatus.IN_PROGRESS, AlbumStatus.NOT_STARTED, AlbumStatus.COMPLETED, AlbumStatus.VIRTUAL)

                statusOrder.forEach { status ->
                    val albumsInStatus = groupedAlbums[status]
                    if (!albumsInStatus.isNullOrEmpty()) {
                        val isCollapsed = collapsedCategories.contains(status)
                        item(key = "header_${status.name}") {
                            val statusLabel = when(status) {
                                AlbumStatus.IN_PROGRESS -> stringResource(R.string.home_status_in_progress)
                                AlbumStatus.NOT_STARTED -> stringResource(R.string.home_status_not_started)
                                AlbumStatus.COMPLETED -> stringResource(R.string.home_status_completed)
                                AlbumStatus.VIRTUAL -> stringResource(R.string.home_status_virtual)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleCategory(status) }
                                    .padding(top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = statusLabel,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (status == AlbumStatus.VIRTUAL)
                                        VirtualGold // doré
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                                
                                val rotation by animateFloatAsState(
                                    targetValue = if (isCollapsed) -90f else 0f,
                                    label = "chevronRotation"
                                )
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                                )
                            }
                        }

                        if (!isCollapsed) {
                            items(albumsInStatus, key = { it.id }) { album ->
                                SwipeableAlbumRow(
                                    album = album,
                                    treatedCount = treatedCounts[album.id] ?: 0,
                                    unsyncedCount = unsyncedChanges[album.id] ?: 0,
                                    swipedAlbumId = swipedAlbumId,
                                    onSwiped = { swipedAlbumId = it },
                                    onAlbumClick = { onAlbumClick(album) },
                                    onResetClick = { onResetClick(album) },
                                    onKeepAllClick = { onKeepAllClick(album) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Barre de défilement fluide
        val layoutInfo = state.layoutInfo
        if (layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.isNotEmpty()) {
            val firstItem = layoutInfo.visibleItemsInfo.first()
            val totalItems = layoutInfo.totalItemsCount
            
            // Position absolue du haut de la vue en "unités d'items"
            val currentPos = firstItem.index + (-firstItem.offset.toFloat() / firstItem.size.coerceAtLeast(1).toFloat())
            val scrollFraction = currentPos / totalItems.toFloat()
            
            // Calcul de la taille de la barre basé sur la proportion réelle du viewport
            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
            val averageItemSize = layoutInfo.visibleItemsInfo.sumOf { it.size }.toFloat() / layoutInfo.visibleItemsInfo.size
            val visibleFraction = (viewportHeight / averageItemSize) / totalItems
            
            val animatedOffset by animateFloatAsState(targetValue = scrollFraction, label = "scrollbarOffset")
            val animatedHeight by animateFloatAsState(targetValue = visibleFraction.coerceIn(0.05f, 1.0f), label = "scrollbarHeight")

            if (visibleFraction < 1.0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, top = 16.dp, bottom = 16.dp)
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val height = maxHeight * animatedHeight.coerceAtLeast(0.1f)
                        // Le décalage est simplement proportionnel à la hauteur totale
                        val offset = maxHeight * animatedOffset
                        Box(
                            modifier = Modifier
                                .offset(y = offset.coerceAtMost(maxHeight - height))
                                .fillMaxWidth()
                                .height(height)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumGrid(
    groupedAlbums: Map<AlbumStatus, List<Album>>,
    treatedCounts: Map<String, Int>,
    unsyncedChanges: Map<String, Int>,
    collapsedCategories: Set<AlbumStatus>,
    isRefreshing: Boolean,
    state: LazyGridState,
    onRefresh: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onToggleCategory: (AlbumStatus) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalGrid(
                state = state,
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val statusOrder = listOf(AlbumStatus.IN_PROGRESS, AlbumStatus.NOT_STARTED, AlbumStatus.COMPLETED, AlbumStatus.VIRTUAL)

                statusOrder.forEach { status ->
                    val albumsInStatus = groupedAlbums[status]
                    if (!albumsInStatus.isNullOrEmpty()) {
                        val isCollapsed = collapsedCategories.contains(status)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val statusLabel = when(status) {
                                AlbumStatus.IN_PROGRESS -> stringResource(R.string.home_status_in_progress)
                                AlbumStatus.NOT_STARTED -> stringResource(R.string.home_status_not_started)
                                AlbumStatus.COMPLETED -> stringResource(R.string.home_status_completed)
                                AlbumStatus.VIRTUAL -> stringResource(R.string.home_status_virtual)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleCategory(status) }
                                    .padding(top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = statusLabel,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (status == AlbumStatus.VIRTUAL)
                                        VirtualGold // doré
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                                
                                val rotation by animateFloatAsState(
                                    targetValue = if (isCollapsed) -90f else 0f,
                                    label = "chevronRotation"
                                )
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                                )
                            }
                        }

                        if (!isCollapsed) {
                            gridItems(albumsInStatus, key = { it.id }) { album ->
                                AlbumGridItem(
                                    album = album,
                                    treatedCount = treatedCounts[album.id] ?: 0,
                                    unsyncedCount = unsyncedChanges[album.id] ?: 0,
                                    onClick = { onAlbumClick(album) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Barre de défilement fluide
        val layoutInfo = state.layoutInfo
        if (layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.isNotEmpty()) {
            val firstItem = layoutInfo.visibleItemsInfo.first()
            val totalItems = layoutInfo.totalItemsCount

            // Calcul de la position précise (en se basant sur les lignes de 3)
            val currentPos = (firstItem.index / 3f) + (-firstItem.offset.y.toFloat() / firstItem.size.height.coerceAtLeast(
                1
            ).toFloat())
            val totalRows = totalItems / 3f
            val scrollFraction = currentPos / totalRows
            
            // Calcul de la taille de la barre basé sur la proportion réelle du viewport
            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
            val averageItemHeight = layoutInfo.visibleItemsInfo.sumOf { it.size.height }.toFloat() / layoutInfo.visibleItemsInfo.size
            val visibleFraction = (viewportHeight / averageItemHeight) / totalRows

            val animatedOffset by animateFloatAsState(targetValue = scrollFraction.coerceIn(0f, 1f), label = "scrollbarOffset")
            val animatedHeight by animateFloatAsState(targetValue = visibleFraction.coerceIn(0.05f, 1.0f), label = "scrollbarHeight")

            if (visibleFraction < 1.0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, top = 16.dp, bottom = 16.dp)
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val height = maxHeight * animatedHeight
                        val offset = maxHeight * animatedOffset
                        Box(
                            modifier = Modifier
                                .offset(y = offset.coerceAtMost(maxHeight - height))
                                .fillMaxWidth()
                                .height(height)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumGridItem(
    album: Album,
    treatedCount: Int,
    unsyncedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baseUrl = remember { SessionManager.getBaseUrl()?.removeSuffix("/") }
    val apiKey = remember { SessionManager.getApiKey() ?: "" }
    val progress = if (album.assetCount > 0) treatedCount.toFloat() / album.assetCount else 0f
    val isCompleted = album.id != Album.VIRTUAL_SKIPPED_ID && album.assetCount in 1..treatedCount
    val hasUnsyncedChanges = unsyncedCount > 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Image de fond
            if (album.albumThumbnailAssetId != null && baseUrl != null) {
                // On pré-construit la requête Coil de manière stable pour optimiser la fluidité du scroll
                val imageRequest = remember(album.albumThumbnailAssetId, baseUrl, apiKey) {
                    ImageRequest.Builder(context)
                        .data("$baseUrl/api/assets/${album.albumThumbnailAssetId}/thumbnail?format=WEBP")
                        .addHeader("x-api-key", apiKey)
                        .crossfade(true)
                        .precision(coil.size.Precision.INEXACT)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isCompleted) Modifier.alpha(0.8f) else Modifier)
                )
            } else {
                val (icon, brush, tint) = getVirtualCollectionStyle(album.id)
                Box(
                    modifier = Modifier.fillMaxSize().background(brush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Overlay dégradé pour le texte
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            // Badges d'état
            if (isCompleted) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    color = Color(0xFF388E3C),
                    shape = CircleShape,
                    shadowElevation = 4.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(4.dp).size(16.dp)
                    )
                }
            }

            // Contenu texte
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                if (hasUnsyncedChanges) {
                    Text(
                        text = stringResource(R.string.home_unsynced_badge),
                        color = Color(0xFFD32F2F),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = album.albumName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = if (album.id == Album.VIRTUAL_SKIPPED_ID) {
                        stringResource(R.string.home_skip_count, album.assetCount)
                    } else {
                        stringResource(R.string.home_sorted_count, treatedCount, album.assetCount)
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }

            // Barre de progression en haut de l'album (discrète)
            if (progress > 0 && !isCompleted && album.id != Album.VIRTUAL_SKIPPED_ID) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
fun AlbumItem(
    album: Album,
    treatedCount: Int,
    unsyncedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baseUrl = remember { SessionManager.getBaseUrl()?.removeSuffix("/") }
    val apiKey = remember { SessionManager.getApiKey() ?: "" }
    val progress = if (album.assetCount > 0) treatedCount.toFloat() / album.assetCount else 0f
    val isCompleted = album.id != Album.VIRTUAL_SKIPPED_ID && album.assetCount in 1..treatedCount
    val isNotStarted = treatedCount == 0
    val hasUnsyncedChanges = unsyncedCount > 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(60.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        if (album.albumThumbnailAssetId != null && baseUrl != null) {
                            val imageRequest = remember(album.albumThumbnailAssetId, baseUrl, apiKey) {
                                ImageRequest.Builder(context)
                                    .data("$baseUrl/api/assets/${album.albumThumbnailAssetId}/thumbnail?format=WEBP")
                                    .addHeader("x-api-key", apiKey)
                                    .crossfade(true)
                                    .precision(coil.size.Precision.INEXACT)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }

                            AsyncImage(
                                model = imageRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                placeholder = rememberVectorPainter(Icons.Default.PhotoLibrary),
                                error = rememberVectorPainter(Icons.Default.PhotoLibrary)
                            )
                        } else {
                            val (icon, brush, tint) = getVirtualCollectionStyle(album.id)
                            Box(
                                modifier = Modifier.fillMaxSize().background(brush),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = tint
                                )
                            }
                        }
                    }
                    
                    // Petit badge sur la miniature
                    if (isCompleted) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
                            color = Color(0xFF388E3C),
                            shape = CircleShape,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp).padding(2.dp))
                        }
                    } else if (isNotStarted) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(14.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = album.albumName, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (isCompleted) {
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_album_completed), fontSize = 10.sp, color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                        }
                    }
                    if (hasUnsyncedChanges) {
                        Text(
                            text = stringResource(R.string.home_unsynced_changes, unsyncedCount),
                            fontSize = 11.sp,
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!album.description.isNullOrBlank()) {
                        Text(text = album.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline, maxLines = 2)
                    }
                    Text(
                        text = if (album.id == Album.VIRTUAL_SKIPPED_ID) {
                            stringResource(R.string.home_skip_count, album.assetCount)
                        } else {
                            stringResource(R.string.home_sorted_count, treatedCount, album.assetCount)
                        },
                        fontSize = 12.sp,
                        color = if (isCompleted) Color(0xFF388E3C) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant)
            }

            // Barre de progression
            if (progress > 0 && !isCompleted && album.id != Album.VIRTUAL_SKIPPED_ID) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
fun SwipePlaceholder(selectedAlbum: Album?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Swipe, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            if (selectedAlbum != null) {
                Text(stringResource(R.string.home_session_title, selectedAlbum.albumName), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.home_photos_to_discover, selectedAlbum.assetCount), fontSize = 14.sp)
            } else {
                Text(stringResource(R.string.home_select_album))
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    caption: String? = null,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (caption != null) {
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
fun ErrorView(error: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.home_error_title), color = MaterialTheme.colorScheme.error)
            Text(error, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.home_retry_button)) }
        }
    }
}

/**
 * Retourne le style visuel pour une collection virtuelle.
 */
@Composable
private fun getVirtualCollectionStyle(albumId: String): Triple<androidx.compose.ui.graphics.vector.ImageVector, Brush, Color> {
    return when (albumId) {
        Album.VIRTUAL_SKIPPED_ID -> Triple(
            Icons.Default.FastForward,
            Brush.linearGradient(listOf(Color(0xFF667eea), Color(0xFF764ba2))),
            Color.White
        )
        Album.VIRTUAL_ALL_ID -> Triple(
            Icons.Default.AutoAwesomeMotion,
            Brush.linearGradient(listOf(Color(0xFFf6d365), Color(0xFFfda085))),
            Color.White
        )
        Album.VIRTUAL_ORPHANS_ID -> Triple(
            Icons.Default.Extension,
            Brush.linearGradient(listOf(Color(0xFF84fab0), Color(0xFF8fd3f4))),
            Color.White
        )
        else -> Triple(
            Icons.Default.PhotoLibrary, 
            Brush.linearGradient(listOf(Color.Gray, Color.DarkGray)), 
            Color.White
        )
    }
}

/**
 * Retourne la couleur Compose correspondant au nom de couleur Immich.
 */
private fun getAvatarColor(colorName: String?): Color {
    return when (colorName?.lowercase()) {
        "primary" -> Color(0xFFadcbfa)
        "pink" -> Color(0xFFE91E63)
        "red" -> Color(0xFFF44336)
        "yellow" -> Color(0xFFFFEB3B)
        "blue" -> Color(0xFF2196F3)
        "green" -> Color(0xFF4CAF50)
        "purple" -> Color(0xFF9C27B0)
        "orange" -> Color(0xFFFF9800)
        "gray", "grey" -> Color(0xFF9E9E9E)
        "amber" -> Color(0xFFFFC107)
        "cyan" -> Color(0xFF00BCD4)
        "indigo" -> Color(0xFF3F51B5)
        "lime" -> Color(0xFFCDDC39)
        "teal" -> Color(0xFF009688)
        else -> Color(0xFF9C27B0) // Valeur par défaut (violet)
    }
}
