package com.minos2020.immichswipe.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.minos2020.immichswipe.R
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.PlaybackBehavior

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            val scope = uiState.pendingDatabaseScope ?: DatabaseScope.USER
            context.contentResolver.openOutputStream(it)?.let { outputStream ->
                viewModel.exportDatabase(scope, outputStream)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.let { inputStream ->
                viewModel.importDatabase(inputStream)
            }
        }
    }

    // Toast pour le statut des actions de base de données
    LaunchedEffect(uiState.databaseActionStatus) {
        uiState.databaseActionStatus?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearDatabaseActionStatus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
            // SECTION APPARENCE
            SettingsSection(title = stringResource(R.string.settings_section_appearance), icon = Icons.Default.Palette) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_theme_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeButton(
                            text = stringResource(R.string.settings_theme_system),
                            icon = Icons.Default.SettingsSuggest,
                            selected = uiState.themeMode == AppTheme.SYSTEM,
                            onClick = { viewModel.setThemeMode(AppTheme.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeButton(
                            text = stringResource(R.string.settings_theme_light),
                            icon = Icons.Default.LightMode,
                            selected = uiState.themeMode == AppTheme.LIGHT,
                            onClick = { viewModel.setThemeMode(AppTheme.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeButton(
                            text = stringResource(R.string.settings_theme_dark),
                            icon = Icons.Default.DarkMode,
                            selected = uiState.themeMode == AppTheme.DARK,
                            onClick = { viewModel.setThemeMode(AppTheme.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.settings_layout_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeButton(
                            text = stringResource(R.string.settings_layout_list),
                            icon = Icons.AutoMirrored.Filled.ViewList,
                            selected = !uiState.isDefaultLayoutGrid,
                            onClick = { viewModel.setDefaultLayoutGrid(false) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeButton(
                            text = stringResource(R.string.settings_layout_grid),
                            icon = Icons.Default.GridView,
                            selected = uiState.isDefaultLayoutGrid,
                            onClick = { viewModel.setDefaultLayoutGrid(true) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Mode d'affichage par défaut des swipe cards
                    Text(
                        text = stringResource(R.string.settings_display_mode_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeButton(
                            text = stringResource(R.string.settings_display_mode_fill),
                            icon = Icons.Default.AspectRatio,
                            selected = uiState.defaultCardDisplayMode == com.minos2020.immichswipe.core.CardDisplayMode.FILL,
                            onClick = { viewModel.setDefaultCardDisplayMode(com.minos2020.immichswipe.core.CardDisplayMode.FILL) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeButton(
                            text = stringResource(R.string.settings_display_mode_fit),
                            icon = Icons.Default.FitScreen,
                            selected = uiState.defaultCardDisplayMode == com.minos2020.immichswipe.core.CardDisplayMode.FIT,
                            onClick = { viewModel.setDefaultCardDisplayMode(com.minos2020.immichswipe.core.CardDisplayMode.FIT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // SECTION TRI
            SettingsSection(title = stringResource(R.string.settings_section_tri), icon = Icons.AutoMirrored.Filled.Sort) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Option inclure archives (Nouveau)
                    SettingsToggleItemSmall(
                        title = stringResource(R.string.settings_include_archived_label),
                        checked = uiState.includeArchived,
                        onCheckedChange = { viewModel.setIncludeArchived(it) },
                        icon = Icons.Default.Inventory2 // Icône évocatrice pour archives
                    )
                    Text(
                        text = stringResource(R.string.settings_include_archived_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 16.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp), thickness = 0.5.dp)

                    Text(
                        text = stringResource(R.string.settings_skip_lifespan_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_skip_lifespan_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    // État local pour le slider pour une réponse fluide
                    var localSliderValue by remember(uiState.skipLifespanDays) { 
                        mutableStateOf(mapDaysToSlider(uiState.skipLifespanDays)) 
                    }
                    // Si une alerte est annulée, on s'assure que le slider revient à la valeur réelle
                    LaunchedEffect(uiState.showSkipLifespanWarning) {
                        if (uiState.showSkipLifespanWarning == null) {
                            localSliderValue = mapDaysToSlider(uiState.skipLifespanDays)
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val currentDays = mapSliderToDays(localSliderValue)
                        Text(
                            text = formatDays(currentDays),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Slider(
                            value = localSliderValue,
                            onValueChange = { localSliderValue = it },
                            onValueChangeFinished = {
                                viewModel.requestSkipLifespanChange(mapSliderToDays(localSliderValue))
                            },
                            valueRange = 0f..500f, // Échelle personnalisée de 0 à 500
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        Text(
                            text = if (currentDays == 0L) stringResource(R.string.settings_skip_never) else stringResource(R.string.settings_skip_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // SECTION ACTIONS DE TRI
            SettingsSection(title = stringResource(R.string.settings_interaction_sorting_title), icon = Icons.Default.TouchApp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsToggleItemSmall(
                        title = stringResource(R.string.settings_tri_favorite),
                        checked = uiState.showFavoriteButton,
                        onCheckedChange = { viewModel.setShowFavorite(it) },
                        icon = Icons.Default.Favorite
                    )

                    AnimatedVisibility(
                        visible = uiState.showFavoriteButton,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(start = 32.dp, end = 8.dp, bottom = 8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            SettingsToggleItemSmall(
                                title = stringResource(R.string.settings_auto_next_label),
                                checked = uiState.autoNextOnFav,
                                onCheckedChange = { viewModel.setAutoNextOnFav(it) },
                                icon = Icons.Default.Check
                            )
                            Text(
                                text = stringResource(R.string.settings_auto_next_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 8.dp)
                            )
                        }
                    }

                    SettingsToggleItemSmall(
                        title = stringResource(R.string.settings_tri_archive),
                        checked = uiState.showArchiveButton,
                        onCheckedChange = { viewModel.setShowArchive(it) },
                        icon = Icons.Default.Archive
                    )
                    SettingsToggleItemSmall(
                        title = stringResource(R.string.settings_tri_lock),
                        checked = uiState.showLockButton,
                        onCheckedChange = { viewModel.setShowLock(it) },
                        icon = Icons.Default.Lock
                    )
                    SettingsToggleItemSmall(
                        title = stringResource(R.string.settings_tri_add_to_album),
                        checked = uiState.showAddToAlbumButton,
                        onCheckedChange = { viewModel.setShowAddToAlbum(it) },
                        icon = Icons.Default.LibraryAdd
                    )
                    SettingsToggleItemSmall(
                        title = stringResource(R.string.settings_tri_keep_delete),
                        checked = uiState.showKeepDeleteButtons,
                        onCheckedChange = { viewModel.setShowKeepDelete(it) },
                        icon = Icons.Default.Swipe // Icône évoquant le mouvement et les boutons
                    )
                    // Inversion du swipe
                    SettingsToggleItemSmall(
                        title = stringResource(R.string.settings_swipe_invert_label),
                        checked = uiState.isSwipeInverted,
                        onCheckedChange = { viewModel.setSwipeInverted(it) },
                        icon = Icons.Default.SwapHoriz
                    )
                    Text(
                        text = stringResource(R.string.settings_swipe_invert_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 16.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp), thickness = 0.5.dp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // SECTION ACTIONS SUR CARTES
            SettingsSection(title = stringResource(R.string.settings_interaction_card_title), icon = Icons.Default.Style) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CardActionWithPosition(
                        title = stringResource(R.string.settings_card_fullscreen),
                        icon = Icons.Default.Fullscreen,
                        checked = uiState.showFullscreenButton,
                        onCheckedChange = { viewModel.setShowFullscreen(it) },
                        position = uiState.fullscreenButtonPosition,
                        onPositionSelected = { viewModel.setFullscreenButtonPosition(it) }
                    )

                    CardActionWithPosition(
                        title = stringResource(R.string.settings_card_immich),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        checked = uiState.showImmichButton,
                        onCheckedChange = { viewModel.setShowImmich(it) },
                        position = uiState.immichButtonPosition,
                        onPositionSelected = { viewModel.setImmichButtonPosition(it) }
                    )

                    CardActionWithPosition(
                        title = stringResource(R.string.settings_card_display_mode),
                        icon = Icons.Default.AspectRatio,
                        checked = uiState.showCardDisplayButton,
                        onCheckedChange = { viewModel.setShowCardDisplay(it) },
                        position = uiState.cardDisplayButtonPosition,
                        onPositionSelected = { viewModel.setCardDisplayButtonPosition(it) }
                    )

                    CardActionWithPosition(
                        title = stringResource(R.string.settings_card_mute),
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        checked = uiState.showMuteButton,
                        onCheckedChange = { viewModel.setShowMute(it) },
                        position = uiState.muteButtonPosition,
                        onPositionSelected = { viewModel.setMuteButtonPosition(it) }
                    )

                    CardActionWithPosition(
                        title = stringResource(R.string.settings_tri_share),
                        icon = Icons.Default.Share,
                        checked = uiState.showShareButton,
                        onCheckedChange = { viewModel.setShowShare(it) },
                        position = uiState.shareButtonPosition,
                        onPositionSelected = { viewModel.setShareButtonPosition(it) }
                    )

                    CardActionWithPosition(
                        title = stringResource(R.string.settings_card_rotate),
                        icon = Icons.Default.RotateRight,
                        checked = uiState.showRotateButton,
                        onCheckedChange = { viewModel.setShowRotate(it) },
                        position = uiState.rotateButtonPosition,
                        onPositionSelected = { viewModel.setRotateButtonPosition(it) }
                    )

                    AnimatedVisibility(
                        visible = uiState.showRotateButton,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(start = 32.dp, end = 8.dp, bottom = 8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            SettingsToggleItemSmall(
                                title = stringResource(R.string.settings_sync_rotate_label),
                                checked = uiState.syncRotate,
                                onCheckedChange = { viewModel.setSyncRotate(it) },
                                icon = Icons.Default.CloudUpload
                            )
                            Text(
                                text = stringResource(R.string.settings_sync_rotate_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // SECTION AVANCÉ (Interaction globale)
            SettingsSection(title = stringResource(R.string.settings_section_interaction), icon = Icons.Default.Settings) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Comportement vidéo
                    Text(
                        text = stringResource(R.string.settings_video_behavior_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_video_behavior_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeButton(
                            text = stringResource(R.string.settings_video_pause),
                            icon = Icons.Default.PauseCircleFilled,
                            selected = uiState.playbackBehavior == PlaybackBehavior.PAUSE_OTHERS,
                            onClick = { viewModel.setPlaybackBehavior(PlaybackBehavior.PAUSE_OTHERS) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeButton(
                            text = stringResource(R.string.settings_video_ignore),
                            icon = Icons.Default.ControlPointDuplicate,
                            selected = uiState.playbackBehavior == PlaybackBehavior.IGNORE,
                            onClick = { viewModel.setPlaybackBehavior(PlaybackBehavior.IGNORE) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // SECTION BASE DE DONNÉES
            SettingsSection(title = stringResource(R.string.settings_section_database), icon = Icons.Default.Storage) {
                Column {
                    SettingsClickableItem(
                        title = stringResource(R.string.settings_db_delete_label),
                        subtitle = stringResource(R.string.settings_db_delete_desc),
                        icon = Icons.Default.DeleteForever,
                        onClick = { viewModel.requestDatabaseAction(DatabaseAction.DELETE, DatabaseScope.USER) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    SettingsClickableItem(
                        title = stringResource(R.string.settings_db_export_label),
                        subtitle = stringResource(R.string.settings_db_export_desc),
                        icon = Icons.Default.FileUpload,
                        onClick = { viewModel.requestDatabaseAction(DatabaseAction.EXPORT, DatabaseScope.USER) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    SettingsClickableItem(
                        title = stringResource(R.string.settings_db_import_label),
                        subtitle = stringResource(R.string.settings_db_import_desc),
                        icon = Icons.Default.FileDownload,
                        onClick = { viewModel.requestDatabaseAction(DatabaseAction.IMPORT, DatabaseScope.ALL) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // SECTION DEBUG
            SettingsSection(title = stringResource(R.string.settings_section_debug), icon = Icons.Default.BugReport) {
                Column {
                    SettingsClickableItem(
                        title = stringResource(R.string.settings_view_logs_label),
                        subtitle = stringResource(R.string.settings_view_logs_desc),
                        icon = Icons.Default.History,
                        onClick = { viewModel.setShowLogs(true) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // SECTION COMPTE
            SettingsSection(title = stringResource(R.string.settings_section_account), icon = Icons.Default.Person) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(text = uiState.userName, style = MaterialTheme.typography.titleMedium)
                            Text(text = stringResource(R.string.profile_connected_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.profile_logout_button))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

    // Dialogue d'avertissement SKIP
    if (uiState.showSkipLifespanWarning != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSkipLifespanWarning() },
            title = { Text(stringResource(R.string.settings_skip_warning_title)) },
            text = { 
                Text(stringResource(R.string.settings_skip_warning_msg)) 
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSkipLifespanChange() }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSkipLifespanWarning() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Dialogue des LOGS
    if (uiState.showLogsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowLogs(false) },
            title = { Text(stringResource(R.string.settings_logs_dialog_title)) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            text = {
                val rawLogs = remember { viewModel.getLogs() }
                val errorColor = MaterialTheme.colorScheme.error
                val warningColor = Color(0xFFFFA500) // Orange
                val infoColor = Color(0xFF00B0FF)    // Bleu clair
                val debugColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)

                val annotatedLogs = remember(rawLogs, errorColor, warningColor, infoColor, debugColor) {
                    buildAnnotatedString {
                        if (rawLogs.isNotEmpty()) {
                            rawLogs.lineSequence().forEach { line ->
                                val color = when {
                                    line.contains(" E/") -> errorColor
                                    line.contains(" W/") -> warningColor
                                    line.contains(" I/") -> infoColor
                                    line.contains(" D/") -> debugColor
                                    else -> Color.Unspecified
                                }
                                withStyle(style = SpanStyle(color = color)) {
                                    append(line + "\n")
                                }
                            }
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val scroll = rememberScrollState()
                    SelectionContainer {
                        Text(
                            text = if (rawLogs.isEmpty()) androidx.compose.ui.text.AnnotatedString(stringResource(R.string.settings_logs_empty)) else annotatedLogs,
                            style = MaterialTheme.typography.labelSmall.copy(
                                lineHeight = 16.sp,
                                letterSpacing = 0.5.sp
                            ),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier
                                .verticalScroll(scroll)
                                .padding(end = 16.dp) // Évite que le texte ne morde sur la barre de défilement (14dp)
                        )
                    }

                    // Barre de défilement interactive (Scrollbar)
                    if (rawLogs.isNotEmpty() && scroll.maxValue > 0) {
                        val indicatorHeightFraction = 0.15f
                        val trackHeightPx = with(LocalDensity.current) { this@BoxWithConstraints.maxHeight.toPx() }
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(14.dp) // Plus large pour le confort tactile
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .pointerInput(scroll.maxValue) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val scrollRange = scroll.maxValue
                                        val dragFactor = scrollRange / (trackHeightPx * (1f - indicatorHeightFraction))
                                        scope.launch {
                                            scroll.scrollTo((scroll.value + dragAmount.y * dragFactor).toInt().coerceIn(0, scroll.maxValue))
                                        }
                                    }
                                }
                        ) {
                            val scrollFraction = scroll.value.toFloat() / scroll.maxValue
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(indicatorHeightFraction)
                                    .fillMaxWidth()
                                    .offset(y = this@BoxWithConstraints.maxHeight * (scrollFraction * (1f - indicatorHeightFraction)))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        viewModel.setShowClearLogsConfirmation(true)
                    }) {
                        Text(stringResource(R.string.settings_logs_clear), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        val logs = viewModel.getLogs()
                        scope.launch {
                            val clipData = android.content.ClipData.newPlainText("Immich Swipe Logs", logs)
                            clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(clipData))
                        }
                        android.widget.Toast.makeText(context, context.getString(R.string.settings_logs_copied_toast), android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.settings_logs_copy))
                    }
                }
            },
            dismissButton = null
        )
    }

    // Dialogue de confirmation pour les actions sur la Base de Données
    uiState.pendingDatabaseAction?.let { action ->
        val scope = uiState.pendingDatabaseScope ?: DatabaseScope.USER
        
        AlertDialog(
            onDismissRequest = { viewModel.dismissDatabaseConfirmation() },
            title = {
                val titleRes = when(action) {
                    DatabaseAction.DELETE -> R.string.settings_db_confirm_delete_title
                    DatabaseAction.EXPORT -> R.string.settings_db_confirm_export_title
                    DatabaseAction.IMPORT -> R.string.settings_db_confirm_import_title
                }
                Text(stringResource(titleRes))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val msgRes = when(action) {
                        DatabaseAction.DELETE -> R.string.settings_db_confirm_delete_msg
                        DatabaseAction.IMPORT -> R.string.settings_db_confirm_import_msg
                        DatabaseAction.EXPORT -> null
                    }
                    msgRes?.let { Text(stringResource(it)) }
                    
                    if (action != DatabaseAction.IMPORT) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Portée de l'opération :",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.selectableGroup()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = scope == DatabaseScope.USER,
                                        onClick = { viewModel.requestDatabaseAction(action, DatabaseScope.USER) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = scope == DatabaseScope.USER, onClick = null)
                                Text(stringResource(R.string.settings_db_scope_user, uiState.userName), modifier = Modifier.padding(start = 12.dp))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = scope == DatabaseScope.ALL,
                                        onClick = { viewModel.requestDatabaseAction(action, DatabaseScope.ALL) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = scope == DatabaseScope.ALL, onClick = null)
                                Text(stringResource(R.string.settings_db_scope_all), modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when(action) {
                            DatabaseAction.DELETE -> viewModel.executeDelete(scope)
                            DatabaseAction.EXPORT -> {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.getDefault())
                                val dateStr = sdf.format(java.util.Date())
                                val scopeName = if (scope == DatabaseScope.ALL) "ALL_USERS" else {
                                    uiState.userName.ifBlank { "user" }
                                        .replace("\\s+".toRegex(), "-") // Remplace espaces par tirets
                                        .replace("[^a-zA-Z0-9-]".toRegex(), "") // Garde que l'alpha-numérique
                                }
                                val fileName = "immich_swipe_backup_${scopeName}_$dateStr.json"
                                exportLauncher.launch(fileName)
                            }
                            DatabaseAction.IMPORT -> importLauncher.launch("application/json")
                        }
                    },
                    colors = if (action == DatabaseAction.DELETE) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDatabaseConfirmation() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Dialogue de confirmation pour effacer les logs
    if (uiState.showClearLogsConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowClearLogsConfirmation(false) },
            title = { Text(stringResource(R.string.settings_logs_confirm_clear_title)) },
            text = { Text(stringResource(R.string.settings_logs_confirm_clear_msg)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearLogs() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowClearLogsConfirmation(false) }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
fun formatDays(days: Long): String {
    return when {
        days == 0L -> stringResource(R.string.duration_never)
        days < 7 -> stringResource(R.string.duration_day, days)
        days < 30 -> stringResource(R.string.duration_week, days / 7)
        days < 360 -> stringResource(R.string.duration_month, days / 30)
        else -> {
            val years = (days + 5) / 365 
            if (years <= 1L) stringResource(R.string.duration_year_one) 
            else stringResource(R.string.duration_years, years)
        }
    }
}

/**
 * Mappe une valeur de slider (0-500) vers un nombre de jours (0-1825).
 */
private fun mapSliderToDays(v: Float): Long {
    if (v <= 0f) return 0L
    return when {
        v <= 100f -> lerpLong(1, 14, v / 100f)
        v <= 200f -> lerpLong(15, 60, (v - 100f) / 100f)
        v <= 300f -> lerpLong(61, 180, (v - 200f) / 100f)
        v <= 400f -> lerpLong(181, 365, (v - 300f) / 100f)
        else -> lerpLong(366, 1825, (v - 400f) / 100f)
    }
}

private fun mapDaysToSlider(d: Long): Float {
    if (d <= 0L) return 0f
    return when {
        d <= 14 -> (d - 1f) / 13f * 100f + 1f
        d <= 60 -> (d - 15f) / 45f * 100f + 100f
        d <= 180 -> (d - 61f) / 119f * 100f + 200f
        d <= 365 -> (d - 181f) / 184f * 100f + 300f
        else -> (d - 366f) / 1459f * 100f + 400f
    }.coerceIn(0f, 500f)
}

private fun lerpLong(start: Long, end: Long, fraction: Float): Long {
    return start + ((end - start) * fraction).toLong()
}

@Composable
fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsClickableItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun SettingsToggleItemSmall(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun IconPositionPicker(
    title: String,
    selectedPosition: IconPosition,
    onPositionSelected: (IconPosition) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CornerButton(
                    text = stringResource(R.string.settings_pos_top_left),
                    selected = selectedPosition == IconPosition.TOP_LEFT,
                    onClick = { onPositionSelected(IconPosition.TOP_LEFT) },
                    modifier = Modifier.weight(1f)
                )
                CornerButton(
                    text = stringResource(R.string.settings_pos_top_right),
                    selected = selectedPosition == IconPosition.TOP_RIGHT,
                    onClick = { onPositionSelected(IconPosition.TOP_RIGHT) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CornerButton(
                    text = stringResource(R.string.settings_pos_bottom_left),
                    selected = selectedPosition == IconPosition.BOTTOM_LEFT,
                    onClick = { onPositionSelected(IconPosition.BOTTOM_LEFT) },
                    modifier = Modifier.weight(1f)
                )
                CornerButton(
                    text = stringResource(R.string.settings_pos_bottom_right),
                    selected = selectedPosition == IconPosition.BOTTOM_RIGHT,
                    onClick = { onPositionSelected(IconPosition.BOTTOM_RIGHT) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CornerButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ThemeButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun CardActionWithPosition(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: IconPosition,
    onPositionSelected: (IconPosition) -> Unit
) {
    Column {
        SettingsToggleItemSmall(
            title = title,
            checked = checked,
            onCheckedChange = onCheckedChange,
            icon = icon
        )
        AnimatedVisibility(
            visible = checked,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 12.dp)) {
                IconPositionPicker(
                    title = "Position",
                    selectedPosition = position,
                    onPositionSelected = onPositionSelected
                )
            }
        }
    }
}

@Composable
fun PlaybackOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // null car le clic est géré par la Row
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
