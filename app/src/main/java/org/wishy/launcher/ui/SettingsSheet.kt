package org.wishy.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import org.wishy.launcher.Actions
import org.wishy.launcher.R
import org.wishy.launcher.data.AppEntry
import org.wishy.launcher.data.AppRepository
import org.wishy.launcher.data.CategoryCfg
import org.wishy.launcher.data.ConfigStore
import org.wishy.launcher.data.DATE_FORMATS
import org.wishy.launcher.data.LAYOUT_DOCK
import org.wishy.launcher.data.LAYOUT_GRID
import org.wishy.launcher.data.LauncherConfig
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.json.Json

private val format = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

private enum class SettingsScreen { Main, Themes, ThemeCredits, OnlineThemes, Banners, Wallpaper, WallpaperStore, Display, StatusBar, Apps, Categories, Launcher, About }

/**
 * Settings panel built entirely from focusable rows and buttons — every
 * element is reachable with the D-pad alone. Text entry only ever happens
 * inside a dedicated dialog (opened with the pencil button).
 */
@Composable
fun SettingsSheet(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onDismiss: () -> Unit,
    onWallpaperChanged: () -> Unit,
    onArtChanged: () -> Unit = {},
    onRerunWizard: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(SettingsScreen.Main) }

    // Same document-picker flow as videos: opens the system file manager,
    // works with USB drives and network storage providers.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        File(context.filesDir, "wallpaper.jpg").outputStream().use { out ->
                            input.copyTo(out)
                        }
                    }
                }
                store.update {
                    it.copy(
                        useCustomWallpaper = true,
                    )
                }
                onWallpaperChanged()
            }
        }
    }

    // Bundled wallpapers have a dark and a light tone; the classic layouts are always dark.
    val settingsDark = config.themeId == org.wishy.launcher.theme.ThemeStorage.CLASSIC_ID ||
        when (config.themeMode) {
            1 -> false
            2 -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }

    Dialog(
        onDismissRequest = {
            if (screen == SettingsScreen.ThemeCredits || screen == SettingsScreen.OnlineThemes) screen = SettingsScreen.Themes else if (screen == SettingsScreen.WallpaperStore) screen = SettingsScreen.Wallpaper else if (screen != SettingsScreen.Main) screen = SettingsScreen.Main else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Force the dialog window to fill the screen — Compose dialogs otherwise
        // keep a small platform margin, so the panel wouldn't sit flush against
        // the right edge.
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            ((dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window)
                ?.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                )
        }
        // A Dialog spawns its own window that re-provides the platform density,
        // so the launcher's ScaledUi doesn't reach here — re-apply it. The
        // dialog's LocalConfiguration is the real (unscaled) device width.
        ScaledUi(uiScaleFactor(config.uiScale, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            // The wallpaper store is the one screen that takes the whole display: a long list of pictures needs the room.
            val fullScreen = screen == SettingsScreen.WallpaperStore
            Surface(
                shape = if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                colors = SurfaceDefaults.colors(containerColor = Wish.Bg),
                modifier = (if (fullScreen) Modifier.fillMaxWidth() else Modifier.width(440.dp))
                    .fillMaxHeight(),
            ) {
                // Slide left when entering a sub-screen, right when going back
                // to Main (the root); cross-fade so it never looks abrupt.
                androidx.compose.animation.AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState != SettingsScreen.Main
                        val dir = if (forward) 1 else -1
                        (androidx.compose.animation.slideInHorizontally(tween(110)) { w -> dir * w / 3 } +
                            androidx.compose.animation.fadeIn(tween(110))) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(tween(90)) { w -> -dir * w / 3 } +
                                androidx.compose.animation.fadeOut(tween(90)))
                    },
                    label = "settingsScreen",
                ) { target ->
                when (target) {
                    SettingsScreen.Main -> SettingsHome(
                        config = config,
                        dark = settingsDark,
                        onThemes = { screen = SettingsScreen.Themes },
                        onBanners = { screen = SettingsScreen.Banners },
                        onWallpaper = { screen = SettingsScreen.Wallpaper },
                        onDisplay = { screen = SettingsScreen.Display },
                        onStatusBar = { screen = SettingsScreen.StatusBar },
                        onApps = { screen = SettingsScreen.Apps },
                        onCategories = { screen = SettingsScreen.Categories },
                        onLauncher = { screen = SettingsScreen.Launcher },
                        onAndroidSettings = { Actions.openSystemSettings(context) },
                        onAbout = { screen = SettingsScreen.About },
                    )
                    SettingsScreen.Themes -> ThemesScreen(
                        config = config,
                        store = store,
                        onCredits = { screen = SettingsScreen.ThemeCredits },
                        onGetMore = { screen = SettingsScreen.OnlineThemes },
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.OnlineThemes -> OnlineThemesScreen(
                        config = config, store = store,
                        onBack = { screen = SettingsScreen.Themes },
                    )
                    SettingsScreen.Banners -> BannersScreen(
                        config = config, store = store, apps = apps,
                        onArtChanged = onArtChanged,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.ThemeCredits -> ThemeCreditsScreen(
                        config = config,
                        onBack = { screen = SettingsScreen.Themes },
                    )
                    SettingsScreen.Wallpaper -> WallpaperScreen(
                        config = config,
                        // Bundled wallpapers have a dark and a light tone; the classic layouts are always dark.
                        dark = config.themeId == org.wishy.launcher.theme.ThemeStorage.CLASSIC_ID ||
                            when (config.themeMode) {
                                1 -> false
                                2 -> true
                                else -> androidx.compose.foundation.isSystemInDarkTheme()
                            },
                        onSetScrim = { v ->
                            scope.launch { store.update { it.copy(scrimMode = v) } }
                        },
                        onSetDim = { v ->
                            scope.launch { store.update { it.copy(wallpaperDim = v) } }
                        },
                        onPreset = { i ->
                            scope.launch {
                                store.update {
                                    it.copy(
                                        wallpaper = i,
                                        wallpaperId = "",
                                        useCustomWallpaper = false,
                                    )
                                }
                            }
                        },
                        onBundled = { id ->
                            scope.launch {
                                store.update { it.copy(wallpaperId = id, useCustomWallpaper = false) }
                            }
                        },
                        onNoWallpaperChoice = {
                            scope.launch {
                                store.update { it.copy(wallpaperId = "", useCustomWallpaper = false) }
                            }
                        },
                        onStore = { screen = SettingsScreen.WallpaperStore },
                        onPickPhoto = {
                            runCatching { photoPicker.launch(arrayOf("image/*")) }
                                .onFailure { Actions.toast(context, context.getString(R.string.toast_no_picker)) }
                        },
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.WallpaperStore -> WallpaperStoreScreen(
                        config = config,
                        store = store,
                        onApply = { id ->
                            scope.launch { store.update { it.copy(wallpaperId = id, useCustomWallpaper = false) } }
                            screen = SettingsScreen.Wallpaper
                        },
                        onBack = { screen = SettingsScreen.Wallpaper },
                    )
                    SettingsScreen.Display -> DisplayScreen(
                        config = config,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.StatusBar -> StatusBarScreen(
                        config = config,
                        apps = apps,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.Apps -> AppsScreen(
                        apps = apps,
                        config = config,
                        onBack = { screen = SettingsScreen.Main },
                        onLaunch = { pkg -> Actions.launchApp(context, pkg) },
                        onAppInfo = { pkg -> Actions.openAppInfo(context, pkg) },
                        onUninstall = { pkg -> Actions.uninstall(context, pkg) },
                        onToggleHide = { pkg ->
                            scope.launch {
                                store.update { c ->
                                    c.copy(hidden = if (pkg in c.hidden) c.hidden - pkg else c.hidden + pkg)
                                }
                            }
                        },
                    )
                    SettingsScreen.Categories -> CategoriesScreen(
                        config = config,
                        apps = apps,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.About -> AboutScreen(
                        config = config,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.Launcher -> LauncherSettingsSubscreen(
                        config = config,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                        onRerunWizard = onRerunWizard,
                    )
                }
                }
            }
        }
        }
    }
}

/* ------------------------------ about ------------------------------ */

@Composable
private fun AboutScreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"
    }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<org.wishy.launcher.net.UpdateResult?>(null) }
    var linkHint by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.item_about_updates), onBack = onBack)
        val updateFocus = initialFocus()
        OfflineNotice(config, store)
        val statusText = when (val r = result) {
            null -> if (checking) stringResource(R.string.update_checking) else stringResource(R.string.update_check_sub, installed)
            is org.wishy.launcher.net.UpdateResult.Available -> stringResource(R.string.update_available, r.info.version)
            org.wishy.launcher.net.UpdateResult.UpToDate -> stringResource(R.string.update_uptodate)
            is org.wishy.launcher.net.UpdateResult.Failed -> stringResource(R.string.update_failed, r.reason)
        }
        SettingsItem(
            selected = false,
            enabled = !config.offlineMode && !checking,
            onClick = {
                checking = true; result = null; linkHint = ""
                scope.launch {
                    result = withContext(Dispatchers.IO) { org.wishy.launcher.net.UpdateChecker.check(installed) }
                    checking = false
                }
            },
            headlineContent = { Text(stringResource(R.string.update_check)) },
            supportingContent = { Text(statusText) },
            modifier = Modifier.focusRequester(updateFocus),
        )
        (result as? org.wishy.launcher.net.UpdateResult.Available)?.let { found ->
            SettingsItem(
                selected = false,
                onClick = {
                    if (!org.wishy.launcher.net.UpdateChecker.openPage(context, found.info.pageUrl)) {
                        linkHint = context.getString(R.string.update_open_failed, found.info.pageUrl)
                    }
                },
                headlineContent = { Text(stringResource(R.string.update_open)) },
                supportingContent = { Text(if (linkHint.isNotEmpty()) linkHint else found.info.name) },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_wishy_mark),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Column {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "v" + (runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("?")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(R.string.about_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp),
        )

        SectionLabel(stringResource(R.string.about_foss))
        Text(
            stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
        // Source code
        Text(
            stringResource(R.string.about_source) + "  ·  " + stringResource(R.string.github_url),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )

        Text(
            stringResource(R.string.about_thirdparty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 8.dp),
        )
    }
}

/* ------------------------------ display ------------------------------ */

@Composable
private fun DisplayScreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Under a theme the theme decides the layout, so the classic layout switches are not shown. Every other
    // option below is applied by every theme that has something for it to change.
    val themed = config.themeId != org.wishy.launcher.theme.ThemeStorage.CLASSIC_ID
    val caps = rememberThemeCaps(config.themeId)
    fun update(transform: (LauncherConfig) -> LauncherConfig) {
        scope.launch { store.update(transform) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.item_display), onBack = onBack)
        if (!themed) LayoutPreview(config)
        val f = initialFocus()
        if (!themed) SectionLabel(stringResource(R.string.section_layout))
        if (!themed) SelectorRow(
            modifier = Modifier.focusRequester(f),
            label = stringResource(R.string.view_mode),
            value = listOf(stringResource(R.string.mode_carousel), stringResource(R.string.mode_grid), stringResource(R.string.mode_dock))[config.layout.coerceIn(0, 2)],
            description = when (config.layout) {
                LAYOUT_GRID -> stringResource(R.string.mode_grid_desc)
                LAYOUT_DOCK -> stringResource(R.string.mode_dock_desc)
                else -> stringResource(R.string.mode_carousel_desc)
            },
            steps = 3,
            current = config.layout.coerceIn(0, 2),
            onSelect = { v -> update { it.copy(layout = v) } },
        )
        // Section names are only drawn in carousel/grid — hide the toggle in dock.
        if (!themed && config.layout != LAYOUT_DOCK) {
            SectionLabel(stringResource(R.string.section_elements))
            SettingsItem(
                selected = false,
                onClick = { update { it.copy(showCategoryNames = !it.showCategoryNames) } },
                headlineContent = { Text(stringResource(R.string.section_names_label)) },
                trailingContent = { SwitchPill(config.showCategoryNames) },
            )
        }
        SectionLabel(stringResource(R.string.section_size))
        run {
            val labels = listOf(stringResource(R.string.scale_auto), "75%", "90%", "100%", "115%", "130%")
            val idx = config.uiScale.coerceIn(0, labels.size - 1)
            SelectorRow(
                modifier = if (themed) Modifier.focusRequester(f) else Modifier,
                label = stringResource(R.string.ui_scale),
                value = labels[idx],
                description = if (idx == 0) stringResource(R.string.ui_scale_auto_sub) else null,
                steps = labels.size,
                current = idx,
                onSelect = { v -> update { it.copy(uiScale = v) } },
            )
        }
        if (caps.corners) SelectorRow(
            label = stringResource(R.string.roundness),
            value = listOf(stringResource(R.string.size_xs), stringResource(R.string.size_s), stringResource(R.string.size_n), stringResource(R.string.size_l), stringResource(R.string.size_xl))[config.cornerRadius.coerceIn(0, 4)],
            steps = 5,
            current = config.cornerRadius.coerceIn(0, 4),
            onSelect = { v -> update { it.copy(cornerRadius = v) } },
        )
        SelectorRow(
            label = stringResource(R.string.icon_size),
            value = listOf(stringResource(R.string.size_xs), stringResource(R.string.size_s), stringResource(R.string.size_n), stringResource(R.string.size_l), stringResource(R.string.size_xl))[config.iconScale.coerceIn(0, 4)],
            steps = 5,
            current = config.iconScale.coerceIn(0, 4),
            onSelect = { v -> update { it.copy(iconScale = v) } },
        )
        SelectorRow(
            label = stringResource(R.string.spacing),
            value = listOf(stringResource(R.string.size_xs), stringResource(R.string.size_s), stringResource(R.string.size_n), stringResource(R.string.size_l), stringResource(R.string.size_xl))[config.spacing.coerceIn(0, 4)],
            steps = 5,
            current = config.spacing.coerceIn(0, 4),
            onSelect = { v -> update { it.copy(spacing = v) } },
        )
        if (caps.labels) SettingsItem(
            selected = false,
            onClick = { update { it.copy(showAppLabels = !it.showAppLabels) } },
            headlineContent = { Text(stringResource(R.string.app_labels_label)) },
            trailingContent = { SwitchPill(config.showAppLabels) },
        )
        run {
            val names = listOf(
                stringResource(R.string.accent_default), stringResource(R.string.accent_green), stringResource(R.string.accent_yellow),
                stringResource(R.string.accent_red), stringResource(R.string.accent_purple), stringResource(R.string.accent_cyan),
            )
            val idx = config.accent.coerceIn(0, names.size - 1)
            SelectorRow(
                label = stringResource(R.string.accent_label),
                value = names[idx],
                steps = names.size,
                current = idx,
                onSelect = { v -> update { it.copy(accent = v) } },
            )
        }
    }
}

/**
 * D-pad selector row: Left/Right changes the value directly, OK cycles.
 * The dots show the position within the available steps.
 */
@Composable
internal fun SelectorRow(
    label: String,
    value: String,
    steps: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    SettingsItem(
        selected = false,
        onClick = { onSelect((current + 1) % steps) },
        headlineContent = { Text(label) },
        supportingContent = { if (description != null) Text(description) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Sym.ChevronLeft, null, modifier = Modifier.size(20.dp))
                Text(value)
                Icon(Sym.ChevronRight, null, modifier = Modifier.size(20.dp))
            }
        },
        modifier = modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> { onSelect((current - 1 + steps) % steps); true }
                Key.DirectionRight -> { onSelect((current + 1) % steps); true }
                else -> false
            }
        },
    )
}

/* --------------- app list: icon + name launches, ⓘ = app info --------------- */

@Composable
private fun AppsScreen(
    apps: List<AppEntry>,
    config: LauncherConfig,
    onBack: () -> Unit,
    onLaunch: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onToggleHide: (String) -> Unit,
) {
    val catNames = remember(config.categories) { config.categories.associate { it.id to it.name } }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        ScreenHeader(stringResource(R.string.item_app), onBack = onBack)
        Text(
            stringResource(R.string.app_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        val f = initialFocus()
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(apps.size, key = { apps[it].pkg }) { i ->
                val app = apps[i]
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        SettingsItem(
                            selected = false,
                            onClick = { onLaunch(app.pkg) },
                            modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
                            headlineContent = { Text(app.label) },
                            supportingContent = {
                                val names = AppRepository.sectionsOf(app, config)
                                    .mapNotNull { catNames[it] }
                                val suffix = if (app.pkg in config.hidden) " · " + stringResource(R.string.hidden_tag) else ""
                                Text(names.joinToString(" · ").ifEmpty { stringResource(R.string.no_section) } + suffix)
                            },
                            leadingContent = { AppThumb(app) },
                        )
                    }
                    val isHidden = app.pkg in config.hidden
                    SmallIconButton(
                        if (isHidden) AppIcons.Hide else AppIcons.Show,
                        stringResource(if (isHidden) R.string.menu_unhide else R.string.menu_hide),
                    ) { onToggleHide(app.pkg) }
                    SmallIconButton(AppIcons.Info, stringResource(R.string.menu_app_info)) { onAppInfo(app.pkg) }
                    SmallIconButton(AppIcons.Delete, stringResource(R.string.menu_uninstall)) { onUninstall(app.pkg) }
                }
            }
        }
    }
}

/** Small leading artwork for list rows: banner if the app ships one, else icon. */
@Composable
internal fun AppThumb(app: AppEntry) {
    when {
        app.banner != null -> Image(
            bitmap = app.banner,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .clip(RoundedCornerShape(5.dp)),
        )
        app.icon != null -> Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
        else -> Box(
            Modifier
                .size(width = 52.dp, height = 30.dp)
                .background(Wish.Text.copy(alpha = 0.1f), RoundedCornerShape(5.dp))
        )
    }
}

/* ---------- section apps picker: vertical grid, checked = included ---------- */

@Composable
private fun SectionAppsDialog(
    cat: CategoryCfg,
    apps: List<AppEntry>,
    config: LauncherConfig,
    onToggle: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.width(720.dp).height(520.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text(cat.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.section_apps_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                val f = initialFocus()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(apps.size, key = { apps[it].pkg }) { i ->
                        val app = apps[i]
                        val inSection = cat.id in AppRepository.sectionsOf(app, config)
                        Surface(
                            onClick = { onToggle(app) },
                            modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Wish.Text.copy(alpha = 0.06f),
                                focusedContainerColor = Wish.Text.copy(alpha = 0.18f),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                        ) {
                            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (app.banner != null) {
                                        Image(
                                            bitmap = app.banner,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(6.dp)),
                                        )
                                    } else {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .background(Wish.Text.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (app.icon != null) {
                                                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                                            }
                                        }
                                    }
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                                    CheckMark(checked = inSection)
                                }
                            }
                        }
                    }
                }
                WishButton(stringResource(R.string.done), compact = true, modifier = Modifier.padding(top = 12.dp), onClick = onDismiss)
            }
        }
    }
}


/* ------------------------------ launcher settings ------------------------------ */

@Composable
private fun LauncherSettingsSubscreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
    onRerunWizard: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var langScreen by remember { mutableStateOf(false) }

    if (langScreen) {
        LanguageScreen(config = config, store = store, onBack = { langScreen = false })
        return
    }

    fun saveConfig() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    dir.mkdirs()
                    File(dir, "WishyBackup.json").writeText(format.encodeToString(LauncherConfig.serializer(), config))
                }
            }
            Actions.toast(context, "Saved to Downloads/WishyBackup.json")
        }
    }

    val loadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            format.decodeFromString(LauncherConfig.serializer(), String(input.readBytes()))
                        }
                    }.getOrNull()
                }
                if (loaded != null) {
                    store.update { loaded.copy(knownApps = config.knownApps, setupDone = true) }
                    Actions.toast(context, context.getString(R.string.toast_config_loaded))
                } else {
                    Actions.toast(context, context.getString(R.string.toast_config_bad))
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.item_launcher_settings), onBack = onBack)
        SettingsItem(
            selected = false,
            onClick = { langScreen = true },
            headlineContent = { Text(stringResource(R.string.item_language)) },
            supportingContent = { Text(stringResource(R.string.item_language_sub)) },
            leadingContent = { Icon(AppIcons.Language, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = { saveConfig() },
            headlineContent = { Text(stringResource(R.string.item_save_config)) },
            supportingContent = { Text(stringResource(R.string.item_save_config_sub)) },
            leadingContent = { Icon(AppIcons.Save, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = { runCatching { loadPicker.launch(arrayOf("application/json", "*/*")) }.onFailure { Actions.toast(context, context.getString(R.string.toast_no_picker)) } },
            headlineContent = { Text(stringResource(R.string.item_load_config)) },
            supportingContent = { Text(stringResource(R.string.item_load_config_sub)) },
            leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = onRerunWizard,
            headlineContent = { Text(stringResource(R.string.rerun_wizard)) },
            supportingContent = { Text(stringResource(R.string.rerun_wizard_sub)) },
            leadingContent = { Icon(AppIcons.Play, contentDescription = null) },
        )
    }
}

/* ------------------------------ language ------------------------------ */

@Composable
private fun LanguageScreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.item_language), onBack = onBack)
        val f = initialFocus()
        org.wishy.launcher.data.LANGUAGES.forEachIndexed { i, code ->
            // Endonym: each language named in its own tongue (no per-language strings)
            val label = if (code.isEmpty()) stringResource(R.string.lang_system)
            else java.util.Locale(code).let { it.getDisplayName(it) }
                .replaceFirstChar { it.uppercase() }
            SettingsItem(
                selected = false,
                onClick = {
                    // Just persist it; LauncherApp mirrors it and recreates.
                    if (code != config.language) scope.launch {
                        store.update { it.copy(language = code) }
                    }
                },
                headlineContent = { Text(label) },
                trailingContent = { CheckMark(checked = config.language == code) },
                modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
            )
        }
    }
}

/* ------------------------------ status bar ------------------------------ */

@Composable
private fun StatusBarScreen(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Show only what the active theme can use: the launcher's bar (and its glass) when it is drawn, the clock and
    // date when the theme has them.
    val caps = rememberThemeCaps(config.themeId)
    val barUsed = !caps.ownsStatusBar || caps.statusIcons
    var pickVpn by remember { mutableStateOf(false) }
    val vpnLabel = apps.firstOrNull { it.pkg == config.vpnApp }?.label ?: stringResource(R.string.vpn_system)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.item_statusbar), onBack = onBack)
        if (barUsed) StatusBarPreview(config)
        if (barUsed) SectionLabel(stringResource(R.string.sb_group_bar))
        val f = initialFocus()
        if (barUsed) SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(showStatusBar = !it.showStatusBar) } } },
            modifier = Modifier.focusRequester(f),
            headlineContent = { Text(stringResource(R.string.show_statusbar)) },
            supportingContent = { Text(stringResource(R.string.show_statusbar_sub)) },
            trailingContent = { SwitchPill(config.showStatusBar) },
        )
        if (!caps.ownsStatusBar) SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(statusBarGlass = !it.statusBarGlass) } } },
            headlineContent = { Text(stringResource(R.string.statusbar_glass)) },
            supportingContent = { Text(stringResource(R.string.statusbar_glass_sub)) },
            trailingContent = { SwitchPill(config.statusBarGlass) },
        )
        if (caps.clock || caps.date) SectionLabel(stringResource(R.string.sb_group_clock))
        if (caps.clock) SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(h24 = !it.h24) } } },
            headlineContent = { Text(stringResource(R.string.clock_24h)) },
            trailingContent = { SwitchPill(config.h24) },
        )
        if (caps.date) run {
            val idx = config.dateFormat.coerceIn(0, DATE_FORMATS.size - 1)
            SelectorRow(
                label = stringResource(R.string.date_label),
                // Live preview of today's date in the selected format
                value = if (idx == 0) stringResource(R.string.off)
                else java.text.SimpleDateFormat(DATE_FORMATS[idx], java.util.Locale.getDefault())
                    .format(java.util.Date()),
                steps = DATE_FORMATS.size,
                current = idx,
                onSelect = { v -> scope.launch { store.update { it.copy(dateFormat = v) } } },
            )
        }
        if (barUsed) SectionLabel(stringResource(R.string.sb_group_vpn))
        if (barUsed) SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(showVpnButton = !it.showVpnButton) } } },
            headlineContent = { Text(stringResource(R.string.show_vpn_button)) },
            leadingContent = { Icon(AppIcons.Vpn, contentDescription = null) },
            trailingContent = { SwitchPill(config.showVpnButton) },
        )
        // Grayed out (and not focusable) while the VPN button is hidden
        if (barUsed) SettingsItem(
            selected = false,
            enabled = config.showVpnButton,
            onClick = { pickVpn = true },
            headlineContent = { Text(stringResource(R.string.vpn_opens)) },
            supportingContent = { Text(vpnLabel) },
            leadingContent = { Icon(AppIcons.Vpn, contentDescription = null) },
        )
    }

    if (pickVpn) {
        Dialog(onDismissRequest = { pickVpn = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.width(380.dp).height(480.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        stringResource(R.string.vpn_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                    )
                    val fv = initialFocus()
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            SettingsItem(
                                selected = false,
                                onClick = {
                                    scope.launch { store.update { it.copy(vpnApp = "") } }
                                    pickVpn = false
                                },
                                headlineContent = { Text(stringResource(R.string.vpn_system)) },
                                trailingContent = { CheckMark(checked = config.vpnApp.isEmpty()) },
                                modifier = Modifier.focusRequester(fv),
                            )
                        }
                        items(apps.size, key = { apps[it].pkg }) { i ->
                            val app = apps[i]
                            SettingsItem(
                                selected = false,
                                onClick = {
                                    scope.launch { store.update { it.copy(vpnApp = app.pkg) } }
                                    pickVpn = false
                                },
                                headlineContent = { Text(app.label) },
                                leadingContent = { AppThumb(app) },
                                trailingContent = { CheckMark(checked = config.vpnApp == app.pkg) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------ sections ------------------------------ */

@Composable
private fun CategoriesScreen(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var renameFor by remember { mutableStateOf<CategoryCfg?>(null) }
    var appsFor by remember { mutableStateOf<CategoryCfg?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ScreenHeader(stringResource(R.string.item_sections), onBack = onBack)
        val f = initialFocus()
        // Auto-filled "All apps" section: a single toggle, no per-app assignment.
        run {
            val hasAll = config.categories.any { it.id == AppRepository.ALL_APPS_ID }
            val allName = stringResource(R.string.cat_all_apps)
            SettingsItem(
                selected = false,
                onClick = {
                    scope.launch {
                        store.update { cfg ->
                            val rest = cfg.categories.filter { it.id != AppRepository.ALL_APPS_ID }
                            cfg.copy(
                                categories = if (hasAll) rest
                                else rest + CategoryCfg(AppRepository.ALL_APPS_ID, allName),
                            )
                        }
                    }
                },
                modifier = Modifier.focusRequester(f),
                headlineContent = { Text(allName) },
                supportingContent = { Text(stringResource(R.string.all_apps_sub)) },
                leadingContent = { Icon(AppIcons.Apps, contentDescription = null) },
                trailingContent = { CheckMark(checked = hasAll) },
            )
        }
        config.categories.forEachIndexed { i, cat ->
            if (cat.id == AppRepository.ALL_APPS_ID) return@forEachIndexed
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    SettingsItem(
                        selected = false,
                        onClick = { appsFor = cat },
                        headlineContent = { Text(cat.name) },
                        supportingContent = { Text(stringResource(R.string.section_row_sub)) },
                        leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
                    )
                }
                SmallIconButton(AppIcons.Pencil, stringResource(R.string.rename_section)) { renameFor = cat }
                SmallIconButton(AppIcons.Up, stringResource(R.string.cd_move_up)) {
                    if (i > 0) scope.launch {
                        store.update { cfg ->
                            val l = cfg.categories.toMutableList()
                            val tmp = l[i - 1]; l[i - 1] = l[i]; l[i] = tmp
                            cfg.copy(categories = l)
                        }
                    }
                }
                SmallIconButton(AppIcons.Down, stringResource(R.string.cd_move_down)) {
                    if (i < config.categories.size - 1) scope.launch {
                        store.update { cfg ->
                            val l = cfg.categories.toMutableList()
                            val tmp = l[i + 1]; l[i + 1] = l[i]; l[i] = tmp
                            cfg.copy(categories = l)
                        }
                    }
                }
                if (config.categories.size > 1) {
                    SmallIconButton(AppIcons.Delete, stringResource(R.string.cd_delete_section)) {
                        scope.launch {
                            store.update { cfg ->
                                cfg.copy(categories = cfg.categories.filter { it.id != cat.id })
                            }
                        }
                    }
                }
            }
        }
        val newSectionName = stringResource(R.string.new_section)
        SettingsItem(
            selected = false,
            onClick = {
                scope.launch {
                    store.update {
                        it.copy(
                            categories = it.categories +
                                CategoryCfg("c${System.currentTimeMillis()}", newSectionName)
                        )
                    }
                }
            },
            headlineContent = { Text(stringResource(R.string.add_section)) },
            leadingContent = { Icon(AppIcons.Add, contentDescription = null) },
        )
    }

    appsFor?.let { cat ->
        // Keep the dialog's category object in sync with the live config
        val liveCat = config.categories.firstOrNull { it.id == cat.id } ?: cat
        SectionAppsDialog(
            cat = liveCat,
            apps = apps,
            config = config,
            onToggle = { app ->
                scope.launch {
                    store.update { cfg ->
                        val current = AppRepository.sectionsOf(app, cfg)
                        val next = if (liveCat.id in current) current - liveCat.id
                        else current + liveCat.id
                        cfg.copy(sections = cfg.sections + (app.pkg to next))
                    }
                }
            },
            onDismiss = { appsFor = null },
        )
    }

    renameFor?.let { cat ->
        RenameDialog(
            initial = cat.name,
            onSave = { newName ->
                scope.launch {
                    store.update { cfg ->
                        cfg.copy(categories = cfg.categories.map {
                            if (it.id == cat.id) it.copy(name = newName) else it
                        })
                    }
                }
                renameFor = null
            },
            onDismiss = { renameFor = null },
        )
    }
}

/** Pencil dialog: the ONLY place with a text field, so D-pad browsing never lands in an editor. */
@Composable
private fun RenameDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.width(340.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.rename_section), style = MaterialTheme.typography.titleMedium)
                val f = initialFocus()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Wish.Text.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Wish.Text, fontSize = 15.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { if (name.isNotBlank()) onSave(name.trim()) }
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WishButton(stringResource(R.string.save), compact = true) { if (name.isNotBlank()) onSave(name.trim()) }
                    WishButton(stringResource(R.string.cancel), compact = true, filled = false, onClick = onDismiss)
                }
            }
        }
    }
}

/* ------------------------------ wallpaper ------------------------------ */

/** One tile of the wallpaper grid: a thumbnail (or an icon), a label, and a check when it is the active choice. */
private class WallpaperTileSpec(
    val label: String,
    val preview: ImageBitmap?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun WallpaperTile(spec: WallpaperTileSpec, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val preview = spec.preview
    val icon = spec.icon
    // No focus scale (it would blur the label, like SettingsItem); a white border marks the focused tile.
    Surface(
        onClick = spec.onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Wish.Text.copy(alpha = 0.08f),
            focusedContainerColor = Wish.Text.copy(alpha = 0.08f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier),
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (icon != null) {
                Icon(
                    icon, contentDescription = null, tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(30.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(spec.label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
            }
            if (spec.selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { CheckMark(checked = true) }
            }
        }
    }
}

/**
 * Wallpaper picker: dimming, then a grid of thumbnails — the wallpapers that ship with the launcher and your own
 * photo. Everything works with the D-pad alone. Under a theme the first tile is "Theme default" (the theme's own
 * wallpaper); in the classic layouts it is the colour gradient, which keeps its own preset selector.
 */
@Composable
private fun WallpaperScreen(
    config: LauncherConfig,
    dark: Boolean,
    onSetScrim: (Int) -> Unit,
    onSetDim: (Int) -> Unit,
    onPreset: (Int) -> Unit,
    onBundled: (String) -> Unit,
    onNoWallpaperChoice: () -> Unit,
    onStore: () -> Unit,
    onPickPhoto: () -> Unit,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val themed = config.themeId != org.wishy.launcher.theme.ThemeStorage.CLASSIC_ID

    // Small decodes of the bundled pictures, off the main thread; redone when the colour tone changes.
    val thumbs by produceState<Map<String, ImageBitmap>>(emptyMap(), dark) {
        value = withContext(Dispatchers.IO) {
            org.wishy.launcher.art.Wallpapers.all.mapNotNull { wp ->
                val bmp = runCatching {
                    org.wishy.launcher.art.Wallpapers.decode(context, wp, dark, maxWidth = 400)?.asImageBitmap()
                }.getOrNull()
                if (bmp == null) null else wp.id to bmp
            }.toMap()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.item_wallpaper), onBack = onBack)
        val f = initialFocus()
        // Say plainly when the active theme will not show the picture chosen here.
        val storage = remember { org.wishy.launcher.theme.ThemeStorage.get(context) }
        val policy by produceState("allowed", config.themeId, dark) {
            value = withContext(Dispatchers.IO) { peekTheme(storage, config.themeId, dark)?.wallpaper ?: "allowed" }
        }
        if (themed && policy != "allowed") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)
                    .background(Wish.PrimaryContainer, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(AppIcons.Info, null, tint = Wish.OnPrimaryContainer, modifier = Modifier.size(24.dp))
                Text(
                    stringResource(if (policy == "ignored") R.string.wp_note_ignored else R.string.wp_note_required),
                    color = Wish.OnPrimaryContainer,
                )
            }
        }
        // An id this version does not know (say, saved by a newer one) counts as "none chosen".
        val chosen = if (config.useCustomWallpaper) null else org.wishy.launcher.art.Wallpapers.byId(config.wallpaperId)?.id
        val usingNone = !config.useCustomWallpaper && chosen == null

        // Dimming — how much the wallpaper is darkened for readability. The classic layouts use the gradient
        // scrims ("Top & bottom" keeps the centre clear); under a theme it is a plain even darkening.
        if (themed) {
            val steps = 6
            val idx = config.wallpaperDim.coerceIn(0, steps - 1)
            SelectorRow(
                modifier = Modifier.focusRequester(f),
                label = stringResource(R.string.wp_dim_theme),
                value = if (idx == 0) stringResource(R.string.wp_dim_none) else "${idx * 10}%",
                steps = steps,
                current = idx,
                onSelect = { v -> onSetDim(v) },
            )
        } else {
            val names = listOf(
                stringResource(R.string.dim_top_bottom),
                stringResource(R.string.dim_top),
                stringResource(R.string.dim_bottom),
                stringResource(R.string.dim_full),
                stringResource(R.string.dim_off),
            )
            val idx = config.scrimMode.coerceIn(0, names.size - 1)
            SelectorRow(
                modifier = Modifier.focusRequester(f),
                label = stringResource(R.string.dim_label),
                value = names[idx],
                steps = names.size,
                current = idx,
                onSelect = { v -> onSetScrim(v) },
            )
        }

        SettingsItem(
            onClick = onStore,
            headlineContent = { Text(stringResource(R.string.wp_store_title)) },
            supportingContent = { Text(stringResource(R.string.wp_store_sub), maxLines = 1) },
            leadingContent = { Icon(Sym.Download, contentDescription = null) },
        )

        SectionLabel(stringResource(R.string.wp_section_wallpapers))

        val noneLabel = stringResource(if (themed) R.string.wp_theme_default else R.string.color_gradient)
        val photoLabel = stringResource(R.string.pick_photo)
        val tiles = mutableListOf<WallpaperTileSpec>()
        tiles += WallpaperTileSpec(noneLabel, null, AppIcons.Palette, usingNone, onNoWallpaperChoice)
        org.wishy.launcher.art.Wallpapers.all.forEach { wp ->
            tiles += WallpaperTileSpec(wp.displayName(), thumbs[wp.id], null, chosen == wp.id) { onBundled(wp.id) }
        }
        tiles += WallpaperTileSpec(photoLabel, null, AppIcons.Image, config.useCustomWallpaper, onPickPhoto)

        tiles.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { t -> WallpaperTile(t, Modifier.weight(1f)) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }

        // Classic layouts only: the colour-gradient presets, while the gradient is the active background.
        if (!themed && usingNone) {
            val presetIdx = config.wallpaper.coerceIn(0, WALLPAPERS.size - 1)
            val presetNames = listOf(
                stringResource(R.string.wp_midnight),
                stringResource(R.string.wp_aurora),
                stringResource(R.string.wp_sunset),
                stringResource(R.string.wp_deep),
                stringResource(R.string.wp_charcoal),
            )
            SelectorRow(
                label = stringResource(R.string.preset_label),
                value = presetNames[presetIdx],
                steps = WALLPAPERS.size,
                current = presetIdx,
                onSelect = { v -> onPreset(v) },
            )
        }
    }
}

/* ------------------------------ widgets ------------------------------ */

/**
 * Every menu focuses its first item on entry (Back on the remote steps out,
 * so there are no on-screen Back buttons).
 */
@Composable
internal fun initialFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Claim focus the moment the first item's node is attached (retry per
        // frame, stop on the first success) instead of after a fixed 300ms delay.
        // A late request would yank focus back to item 1 if the user had already
        // navigated down — the "quick navigation resets to the top" bug.
        repeat(30) {
            if (runCatching { fr.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos {}
        }
    }
    return fr
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = Wish.Primary,
        modifier = Modifier.padding(top = 16.dp, start = 12.dp, bottom = 6.dp),
    )
}

@Composable
internal fun CheckMark(checked: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .background(
                if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(5.dp),
            )
            .border(
                width = 2.dp,
                color = if (checked) MaterialTheme.colorScheme.primary
                else Wish.Text.copy(alpha = 0.4f),
                shape = RoundedCornerShape(5.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                tint = Wish.OnPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * A settings row with NO focus scale. tv-material's default focusedScale
 * scales the whole row — including its already-rasterized text — which looks
 * blurry on TV; the focused container colour alone signals selection.
 */
@Composable
internal fun SettingsItem(
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(16.dp)
    // Android-style row: a flat rounded card, its icon in a rounded tile, solid primary when focused.
    ListItem(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .background(if (focused) Wish.Primary else Wish.Surface, rowShape),
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent?.let { lead ->
            {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(if (focused) Wish.OnPrimary.copy(alpha = 0.16f) else Wish.PrimaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.tv.material3.LocalContentColor provides
                            (if (focused) Wish.OnPrimary else Wish.OnPrimaryContainer),
                    ) { Box(contentAlignment = Alignment.Center) { lead() } }
                }
            }
        },
        trailingContent = trailingContent,
        colors = androidx.tv.material3.ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Wish.Text,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = Wish.OnPrimary,
        ),
    )
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Wish.SurfaceHigh,
            contentColor = Wish.Text,
            focusedContainerColor = Wish.Primary,
            focusedContentColor = Wish.OnPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            // label is the accessibility name announced by TalkBack.
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}
