package com.minos2020.immichswipe.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.minos2020.immichswipe.R
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.core.IconPosition
import com.minos2020.immichswipe.core.PlaybackBehavior
import com.minos2020.immichswipe.core.SortOrder

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            // Si refusé, on désactive l'option
            viewModel.setSyncLocalDeletion(false)
            android.widget.Toast.makeText(context, "Permission denied. Local sync disabled.", android.widget.Toast.LENGTH_SHORT).show()
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
            // ... (rest of sections)
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

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Spacer(Modifier.height(16.dp))
                        SettingsToggleItemSmall(
                            title = stringResource(R.string.settings_dynamic_color_label),
                            checked = uiState.dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                            icon = Icons.Default.ColorLens
                        )
                        Text(
                            text = stringResource(R.string.settings_dynamic_color_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 40.dp, end = 16.dp)
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
                        text = stringResource(R.string.settings_sort_order_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_sort_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeButton(
                            text = stringResource(R.string.settings_sort_newest),
                            icon = Icons.Default.ArrowDownward,
                            selected = uiState.sortOrder == SortOrder.CHRONOLOGICAL_DESC,
                            onClick = { viewModel.setSortOrder(SortOrder.CHRONOLOGICAL_DESC) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeButton(
                            text = stringResource(R.string.settings_sort_oldest),
                            icon = Icons.Default.ArrowUpward,
                            selected = uiState.sortOrder == SortOrder.CHRONOLOGICAL_ASC,
                            onClick = { viewModel.setSortOrder(SortOrder.CHRONOLOGICAL_ASC) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeButton(
                            text = stringResource(R.string.settings_sort_shuffled),
                            icon = Icons.Default.Shuffle,
                            selected = uiState.sortOrder == SortOrder.SHUFFLED,
                            onClick = { viewModel.setSortOrder(SortOrder.SHUFFLED) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // SECTION INTERACTION
            SettingsSection(title = stringResource(R.string.settings_section_interaction), icon = Icons.Default.TouchApp) {
                Column {
                    // Actions de tri (Nouveau)
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_tri_actions_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        SettingsToggleItemSmall(
                            title = stringResource(R.string.settings_tri_favorite),
                            checked = uiState.showFavoriteButton,
                            onCheckedChange = { viewModel.setShowFavorite(it) },
                            icon = Icons.Default.Star
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
                                    icon = Icons.AutoMirrored.Filled.Forward
                                )
                                Text(
                                    text = stringResource(R.string.settings_auto_next_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 8.dp)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

                        SettingsToggleItemSmall(
                            title = stringResource(R.string.settings_show_swipe_buttons_label),
                            checked = uiState.showSwipeButtons,
                            onCheckedChange = { viewModel.setShowSwipeButtons(it) },
                            icon = Icons.Default.AdsClick
                        )
                        Text(
                            text = stringResource(R.string.settings_show_swipe_buttons_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 8.dp)
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

                        SettingsToggleItemSmall(
                            title = stringResource(R.string.settings_swap_summary_archive_label),
                            checked = uiState.swapSummaryArchive,
                            onCheckedChange = { viewModel.setSwapSummaryArchive(it) },
                            icon = Icons.Default.SwapHoriz
                        )
                        Text(
                            text = stringResource(R.string.settings_swap_summary_archive_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 8.dp)
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

                        SettingsToggleItemSmall(
                            title = stringResource(R.string.settings_sync_local_deletion_label),
                            checked = uiState.syncLocalDeletion,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    // Demander les permissions avant d'activer
                                    val perms = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        arrayOf(
                                            android.Manifest.permission.READ_MEDIA_IMAGES,
                                            android.Manifest.permission.READ_MEDIA_VIDEO
                                        )
                                    } else {
                                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    permissionLauncher.launch(perms)
                                }
                                viewModel.setSyncLocalDeletion(checked)
                            },
                            icon = Icons.Default.PhonelinkErase
                        )
                        Text(
                            text = stringResource(R.string.settings_sync_local_deletion_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 8.dp)
                        )

                        AnimatedVisibility(
                            visible = uiState.syncLocalDeletion,
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
                                    title = stringResource(R.string.settings_trash_local_deletion_label),
                                    checked = uiState.trashLocalDeletion,
                                    onCheckedChange = { viewModel.setTrashLocalDeletion(it) },
                                    icon = Icons.Default.DeleteSweep
                                )
                                Text(
                                    text = stringResource(R.string.settings_trash_local_deletion_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 8.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                    // Position icône plein écran
                    IconPositionPicker(
                        title = stringResource(R.string.settings_fullscreen_pos_label),
                        selectedPosition = uiState.fullscreenButtonPosition,
                        onPositionSelected = { viewModel.setFullscreenButtonPosition(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                    // Position icône Immich
                    IconPositionPicker(
                        title = stringResource(R.string.settings_immich_pos_label),
                        selectedPosition = uiState.immichButtonPosition,
                        onPositionSelected = { viewModel.setImmichButtonPosition(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                    // Position icône Mode d'affichage (Nouveau)
                    IconPositionPicker(
                        title = stringResource(R.string.settings_display_mode_pos_label),
                        selectedPosition = uiState.cardDisplayButtonPosition,
                        onPositionSelected = { viewModel.setCardDisplayButtonPosition(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                    // Position icône Mute (Nouveau)
                    IconPositionPicker(
                        title = stringResource(R.string.settings_mute_pos_label),
                        selectedPosition = uiState.muteButtonPosition,
                        onPositionSelected = { viewModel.setMuteButtonPosition(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                    // Comportement vidéo
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                icon = Icons.AutoMirrored.Filled.VolumeOff,
                                selected = uiState.playbackBehavior == PlaybackBehavior.PAUSE_OTHERS,
                                onClick = { viewModel.setPlaybackBehavior(PlaybackBehavior.PAUSE_OTHERS) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeButton(
                                text = stringResource(R.string.settings_video_ignore),
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                selected = uiState.playbackBehavior == PlaybackBehavior.IGNORE,
                                onClick = { viewModel.setPlaybackBehavior(PlaybackBehavior.IGNORE) },
                                modifier = Modifier.weight(1f)
                            )
                        }
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

            Spacer(Modifier.height(140.dp))
        }

    // Dialogue des LOGS
    if (uiState.showLogsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowLogs(false) },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_logs_dialog_title))
                    IconButton(onClick = { viewModel.setShowLogs(false) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                    }
                }
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            text = {
                val rawLogs = remember { viewModel.getLogs() }
                val errorColor = MaterialTheme.colorScheme.error
                val warningColor = Color(0xFFFFA500) // Orange

                val annotatedLogs = remember(rawLogs, errorColor) {
                    buildAnnotatedString {
                        if (rawLogs.isNotEmpty()) {
                            rawLogs.lineSequence().forEach { line ->
                                val color = when {
                                    line.contains(" E/") -> errorColor
                                    line.contains(" W/") -> warningColor
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
                    Text(
                        text = if (rawLogs.isEmpty()) androidx.compose.ui.text.AnnotatedString(stringResource(R.string.settings_logs_empty)) else annotatedLogs,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.verticalScroll(scroll)
                    )

                    // Barre de défilement (Scrollbar)
                    if (rawLogs.isNotEmpty() && scroll.maxValue > 0) {
                        val indicatorHeightFraction = 0.1f
                        val scrollFraction = scroll.value.toFloat() / scroll.maxValue
                        val availableHeight = maxHeight
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(4.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(indicatorHeightFraction)
                                    .fillMaxWidth()
                                    .offset(y = availableHeight * (scrollFraction * (1f - indicatorHeightFraction)))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            },
            confirmButton = {
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
            },
            dismissButton = {
                TextButton(onClick = { 
                    viewModel.clearLogs()
                    viewModel.setShowLogs(false)
                }) {
                    Text(stringResource(R.string.settings_logs_clear), color = MaterialTheme.colorScheme.error)
                }
            }
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
                                val fileName = "immich_swipe_backup_${if(scope == DatabaseScope.ALL) "total" else "user"}_${System.currentTimeMillis()}.json"
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
fun IconPositionPicker(
    title: String,
    selectedPosition: IconPosition,
    onPositionSelected: (IconPosition) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        modifier = modifier.height(48.dp),
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


