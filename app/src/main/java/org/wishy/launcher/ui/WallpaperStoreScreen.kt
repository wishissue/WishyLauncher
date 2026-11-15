package org.wishy.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import org.wishy.launcher.art.CommunityWallpaper
import org.wishy.launcher.art.CommunityWallpapers
import org.wishy.launcher.art.StoreWallpaper
import org.wishy.launcher.art.WallpaperStore
import org.wishy.launcher.art.Wallpapers
import org.wishy.launcher.data.ConfigStore
import org.wishy.launcher.data.LauncherConfig
import org.wishy.launcher.net.Repos

private const val SOURCE_WISHY = 0
private const val SOURCE_COZY = 1

/**
 * Browse and download more wallpapers, from two places: the official Wishy list and the community CozyPixels
 * repository (switch at the top). CozyPixels can be narrowed by palette and category. Tapping a card downloads it
 * (checked before it is kept) and uses it. Nothing happens while offline mode is on.
 *
 * The whole screen is one lazy grid, so only the cards on screen load their pictures however long the list is.
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
    var source by remember { mutableIntStateOf(SOURCE_WISHY) }
    var list by remember { mutableStateOf<List<StoreWallpaper>?>(null) }
    var cozy by remember { mutableStateOf<List<CommunityWallpaper>?>(null) }
    var palette by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loadingWishy by remember { mutableStateOf(false) }
    var loadingCozy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
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

    fun loadCozy() {
        if (loadingCozy) return
        loadingCozy = true; status = context.getString(R.string.online_loading)
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { CommunityWallpapers.fetchIndex(context) } }
            loadingCozy = false
            r.onSuccess { cozy = it; status = "" }
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

    fun useCozy(w: CommunityWallpaper) {
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

    LaunchedEffect(config.offlineMode, source) {
        if (config.offlineMode) return@LaunchedEffect
        if (source == SOURCE_WISHY && list == null) load()
        if (source == SOURCE_COZY && cozy == null) loadCozy()
    }

    val first = initialFocus()
    val fullWidth: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
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
                    stringResource(if (source == SOURCE_COZY) R.string.wp_cozy_sub else R.string.wp_store_sub),
                    style = MaterialTheme.typography.bodyMedium, color = Wish.TextDim,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (config.offlineMode) item(span = fullWidth) { OfflineNotice(config, store) }

        // Where the wallpapers come from.
        item(span = fullWidth) {
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(stringResource(R.string.wp_src_wishy), source == SOURCE_WISHY, Modifier.focusRequester(first)) { source = SOURCE_WISHY }
                FilterChip(stringResource(R.string.wp_src_cozy), source == SOURCE_COZY) { source = SOURCE_COZY }
            }
        }

        if (status.isNotEmpty()) {
            item(span = fullWidth) {
                SettingsItem(selected = false, onClick = { if (!busy) status = "" }, headlineContent = { Text(status) })
            }
        }

        if (source == SOURCE_WISHY) {
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
            val c = cozy
            if (c == null) {
                item(span = fullWidth) {
                    SettingsItem(
                        selected = false, enabled = !config.offlineMode && !loadingCozy,
                        onClick = { loadCozy() },
                        headlineContent = { Text(stringResource(R.string.online_try_again)) },
                        supportingContent = { Text("github.com/SleepyCatHey/CozyPixels", maxLines = 1) },
                    )
                }
            } else {
                val palettes = CommunityWallpapers.palettes(c)
                val categories = CommunityWallpapers.categories(c, palette)
                val shown = CommunityWallpapers.filter(c, palette, category)
                if (palettes.size > 1) {
                    item(span = fullWidth) {
                        FilterRow(stringResource(R.string.wp_filter_palette), palettes, palette) { palette = it; category = null }
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
                    ) { useCozy(w) }
                }
                val mine = c.filter { it.id in have }
                if (mine.isNotEmpty()) {
                    item(span = fullWidth) { SectionLabel(stringResource(R.string.wp_store_manage)) }
                    items(mine, key = { "cm_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { w ->
                        SettingsItem(
                            selected = false,
                            onClick = { removeInstalled(w.id) },
                            headlineContent = { Text(stringResource(R.string.wp_store_remove, w.name)) },
                            supportingContent = { Text("CozyPixels community · MIT", maxLines = 1) },
                        )
                    }
                }
            }
        }
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
