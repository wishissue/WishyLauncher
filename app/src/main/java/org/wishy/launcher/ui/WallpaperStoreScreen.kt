package org.wishy.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wishy.launcher.R
import org.wishy.launcher.art.CommunityRepo
import org.wishy.launcher.art.CommunityWallpaper
import org.wishy.launcher.art.CommunityWallpapers
import org.wishy.launcher.art.StoreWallpaper
import org.wishy.launcher.art.WallpaperStore
import org.wishy.launcher.art.Wallpapers
import org.wishy.launcher.data.ConfigStore
import org.wishy.launcher.data.LauncherConfig
import org.wishy.launcher.net.Repos

private const val MAX_CUSTOM_REPOS = 8

/**
 * Browse and download more wallpapers. One chip per source along the top: the official Wishy list, CozyPixels, and any
 * GitHub repository the person added (plain folders of pictures work). Community sources can be narrowed by their top
 * folder and sub-folder. Tapping a card downloads it (checked before it is kept) and uses it. Nothing happens while
 * offline mode is on.
 *
 * The whole screen is one lazy grid that adapts to the width it is given, so it fills a full-screen panel with as many
 * columns as fit, and only the cards on screen load their pictures however long the list is.
 */
@Composable
internal fun WallpaperStoreScreen(
    config: LauncherConfig,
    store: ConfigStore,
    onApply: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Source 0 is the official list; 1.. are community repositories (CozyPixels first, then the person's own).
    val repos = remember(config.wallpaperRepos) {
        (listOf(CommunityWallpapers.COZY) + config.wallpaperRepos.mapNotNull { CommunityWallpapers.parseRepo(it) }).distinct()
    }
    var source by remember { mutableIntStateOf(0) }
    val active: CommunityRepo? = repos.getOrNull(source - 1)

    var list by remember { mutableStateOf<List<StoreWallpaper>?>(null) }
    var lists by remember { mutableStateOf<Map<String, List<CommunityWallpaper>>>(emptyMap()) }
    var loadingRepos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var palette by remember(source) { mutableStateOf<String?>(null) }
    var category by remember(source) { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loadingWishy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var addDialog by remember { mutableStateOf(false) }
    var confirmRepo by remember { mutableStateOf<CommunityRepo?>(null) }
    val have by produceState<Set<String>>(emptySet(), tick) {
        value = withContext(Dispatchers.IO) { WallpaperStore.installed(context).map { it.id }.toSet() }
    }

    fun load() {
        if (loadingWishy) return
        loadingWishy = true; status = context.getString(R.string.online_loading)
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { WallpaperStore.fetchIndex() } }
            loadingWishy = false
            r.onSuccess { list = it; status = "" }
                .onFailure { status = context.getString(R.string.online_failed, it.message ?: "") }
        }
    }

    fun loadRepo(repo: CommunityRepo) {
        val k = repo.slug.lowercase()
        if (k in loadingRepos) return
        loadingRepos = loadingRepos + k; status = context.getString(R.string.online_loading)
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { CommunityWallpapers.fetchIndex(context, repo) } }
            loadingRepos = loadingRepos - k
            r.onSuccess { lists = lists + (k to it); status = "" }
                .onFailure { status = context.getString(R.string.online_failed, it.message ?: "") }
        }
    }

    fun use(w: StoreWallpaper) {
        if (busy) return
        if (w.id in have) { onApply(w.id); return }
        busy = true; status = context.getString(R.string.wp_store_downloading, w.name)
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { WallpaperStore.download(context, w); Wallpapers.refreshDownloads(context) }
            }
            busy = false; tick++
            r.onSuccess { status = ""; onApply(w.id) }
                .onFailure { status = context.getString(R.string.online_failed, it.message ?: "") }
        }
    }

    fun useCommunity(w: CommunityWallpaper) {
        if (busy) return
        if (w.id in have) { onApply(w.id); return }
        busy = true; status = context.getString(R.string.wp_store_downloading, w.name)
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { CommunityWallpapers.download(context, w); Wallpapers.refreshDownloads(context) }
            }
            busy = false; tick++
            r.onSuccess { status = ""; onApply(w.id) }
                .onFailure { status = context.getString(R.string.online_failed, it.message ?: "") }
        }
    }

    fun removeInstalled(id: String) {
        scope.launch {
            withContext(Dispatchers.IO) { WallpaperStore.remove(context, id); Wallpapers.refreshDownloads(context) }
            tick++
        }
    }

    LaunchedEffect(config.offlineMode, source, repos.size) {
        if (config.offlineMode) return@LaunchedEffect
        if (source == 0 && list == null) load()
        if (active != null && active.slug.lowercase() !in lists) loadRepo(active)
    }

    val first = initialFocus()
    val fullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 200.dp),
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Back || e.key == Key.Escape) {
                    if (e.type == KeyEventType.KeyUp) onBack()
                    true
                } else false
            },
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = fullWidth) {
            Column {
                ScreenHeader(stringResource(R.string.wp_store_title), onBack = onBack)
                Text(
                    when {
                        active == null -> stringResource(R.string.wp_store_sub)
                        active == CommunityWallpapers.COZY -> stringResource(R.string.wp_cozy_sub)
                        else -> stringResource(R.string.wp_repo_sub, active.slug)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Wish.TextDim,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (config.offlineMode) item(span = fullWidth) { OfflineNotice(config, store) }

        // Where the wallpapers come from: one chip per source, then "add".
        item(span = fullWidth) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                item(key = "src_wishy") {
                    FilterChip(stringResource(R.string.wp_src_wishy), source == 0, Modifier.focusRequester(first)) { source = 0 }
                }
                items(repos, key = { "src_" + it.slug.lowercase() }) { r ->
                    FilterChip(r.repo, active == r) { source = repos.indexOf(r) + 1 }
                }
                if (repos.size - 1 < MAX_CUSTOM_REPOS) {
                    item(key = "src_add") { FilterChip("+  " + stringResource(R.string.wp_repo_add), false) { addDialog = true } }
                }
            }
        }

        if (status.isNotEmpty()) {
            item(span = fullWidth) {
                SettingsItem(selected = false, onClick = { if (!busy) status = "" }, headlineContent = { Text(status) })
            }
        }

        if (active == null) {
            val l = list
            if (l == null) {
                item(span = fullWidth) {
                    SettingsItem(
                        selected = false, enabled = !config.offlineMode && !loadingWishy,
                        onClick = { load() },
                        headlineContent = { Text(stringResource(R.string.online_try_again)) },
                        supportingContent = { Text(Repos.OFFICIAL_WALLPAPERS_INDEX.substringAfter("//").substringBefore("/"), maxLines = 1) },
                    )
                }
            } else {
                items(l, key = { "w_" + it.id }) { w ->
                    StoreCard(
                        thumbKey = w.id, title = w.name,
                        subtitle = if (w.id in have) stringResource(R.string.wp_store_have) else "%.1f MB".format(w.totalBytes / 1048576.0),
                        owned = w.id in have,
                        loadThumb = { WallpaperStore.thumbnail(context, w) },
                    ) { use(w) }
                }
                val mine = l.filter { it.id in have }
                if (mine.isNotEmpty()) {
                    item(span = fullWidth) { SectionLabel(stringResource(R.string.wp_store_manage)) }
                    items(mine, key = { "wm_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { w ->
                        SettingsItem(
                            selected = false,
                            onClick = { removeInstalled(w.id) },
                            headlineContent = { Text(stringResource(R.string.wp_store_remove, w.name)) },
                            supportingContent = { Text("${w.author} · ${w.license}", maxLines = 1) },
                        )
                    }
                }
            }
        } else {
            val c = lists[active.slug.lowercase()]
            if (c == null) {
                item(span = fullWidth) {
                    SettingsItem(
                        selected = false, enabled = !config.offlineMode && active.slug.lowercase() !in loadingRepos,
                        onClick = { loadRepo(active) },
                        headlineContent = { Text(stringResource(R.string.online_try_again)) },
                        supportingContent = { Text("github.com/${active.slug}", maxLines = 1) },
                    )
                }
            } else {
                val palettes = CommunityWallpapers.palettes(c)
                val categories = CommunityWallpapers.categories(c, palette)
                val shown = CommunityWallpapers.filter(c, palette, category)
                if (palettes.size > 1) {
                    item(span = fullWidth) {
                        FilterRow(
                            stringResource(if (active == CommunityWallpapers.COZY) R.string.wp_filter_palette else R.string.wp_filter_collection),
                            palettes, palette,
                        ) { palette = it; category = null }
                    }
                }
                if (categories.size > 1) {
                    item(span = fullWidth) {
                        FilterRow(stringResource(R.string.wp_filter_category), categories, category) { category = it }
                    }
                }
                item(span = fullWidth) {
                    Text(
                        if (shown.isEmpty()) stringResource(R.string.wp_store_empty) else stringResource(R.string.wp_store_count, shown.size),
                        style = MaterialTheme.typography.bodySmall, color = Wish.TextDim,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                items(shown, key = { it.id }) { w ->
                    StoreCard(
                        thumbKey = w.id, title = w.name,
                        subtitle = if (w.id in have) stringResource(R.string.wp_store_have) else "${w.palette} · ${w.category}",
                        owned = w.id in have,
                        loadThumb = { CommunityWallpapers.thumbnail(context, w) },
                    ) { useCommunity(w) }
                }
                val mine = c.filter { it.id in have }
                if (mine.isNotEmpty()) {
                    item(span = fullWidth) { SectionLabel(stringResource(R.string.wp_store_manage)) }
                    items(mine, key = { "cm_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { w ->
                        SettingsItem(
                            selected = false,
                            onClick = { removeInstalled(w.id) },
                            headlineContent = { Text(stringResource(R.string.wp_store_remove, w.name)) },
                            supportingContent = { Text("${w.repo.slug} · ${w.repo.licenseLabel}", maxLines = 1) },
                        )
                    }
                }
            }
            // Repositories the person added can be taken away again. CozyPixels is built in and stays.
            if (active != CommunityWallpapers.COZY) {
                item(span = fullWidth) {
                    SettingsItem(
                        selected = false,
                        onClick = {
                            val slug = active.slug
                            source = 0
                            scope.launch { store.update { cfg -> cfg.copy(wallpaperRepos = cfg.wallpaperRepos.filterNot { it.equals(slug, ignoreCase = true) }) } }
                        },
                        headlineContent = { Text(stringResource(R.string.wp_repo_remove, active.slug)) },
                    )
                }
            }
        }
    }

    if (addDialog) {
        TextEntryDialog(
            title = stringResource(R.string.wp_repo_add_title),
            hint = stringResource(R.string.wp_repo_hint),
            initial = "",
            onSave = { text ->
                addDialog = false
                val r = CommunityWallpapers.parseRepo(text)
                when {
                    r == null -> status = context.getString(R.string.wp_repo_bad)
                    r in repos -> status = context.getString(R.string.wp_repo_have)
                    else -> confirmRepo = r
                }
            },
            onDismiss = { addDialog = false },
        )
    }
    confirmRepo?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.wp_repo_confirm_title, r.slug),
            body = stringResource(R.string.wp_repo_confirm_body),
            confirm = stringResource(R.string.online_confirm_add),
            onConfirm = {
                confirmRepo = null
                scope.launch { store.update { cfg -> if (cfg.wallpaperRepos.any { it.equals(r.slug, true) }) cfg else cfg.copy(wallpaperRepos = cfg.wallpaperRepos + r.slug) } }
                source = repos.size + 1 // the new repository will be the last chip
            },
            onDismiss = { confirmRepo = null },
        )
    }
}

/** A labelled row of filter chips with an "All" chip first. Left and right move along the row. */
@Composable
private fun FilterRow(label: String, options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Wish.TextDim, modifier = Modifier.padding(start = 8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        ) {
            item(key = "chip_all") { FilterChip(stringResource(R.string.wp_filter_all), selected == null) { onSelect(null) } }
            items(options, key = { "chip_o_$it" }) { o -> FilterChip(o, selected == o) { onSelect(o) } }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Wish.PrimaryContainer else Wish.Surface,
            focusedContainerColor = Wish.Primary,
            contentColor = Wish.Text, focusedContentColor = Wish.Bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, Wish.Primary)) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Wish.Primary)),
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge, maxLines = 1,
        )
    }
}

@Composable
private fun StoreCard(
    thumbKey: String,
    title: String,
    subtitle: String,
    owned: Boolean,
    loadThumb: suspend () -> Bitmap?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val thumb by produceState<ImageBitmap?>(null, thumbKey) {
        value = withContext(Dispatchers.IO) { loadThumb()?.asImageBitmap() }
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Wish.Surface, focusedContainerColor = Wish.SurfaceHigh,
            contentColor = Wish.Text, focusedContentColor = Wish.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Wish.Primary))),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Wish.PrimaryContainer)) {
                val t = thumb
                if (t != null) Image(t, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp)
                        .background(if (owned) Wish.Success else Wish.Primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (owned) Sym.Check else Sym.Download, null, tint = Wish.Bg, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Wish.TextDim, maxLines = 1)
            }
        }
    }
}
