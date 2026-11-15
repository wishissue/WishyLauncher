package org.wishy.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import org.wishy.launcher.art.StoreWallpaper
import org.wishy.launcher.art.WallpaperStore
import org.wishy.launcher.art.Wallpapers
import org.wishy.launcher.data.ConfigStore
import org.wishy.launcher.data.LauncherConfig
import org.wishy.launcher.net.Repos

/**
 * Browse and download more wallpapers. Tapping a card downloads it (checked against its SHA-256) and uses it.
 * The list and the small pictures come from the official repository; nothing happens while offline mode is on.
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
    var list by remember { mutableStateOf<List<StoreWallpaper>?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val have by produceState<Set<String>>(emptySet(), tick) {
        value = withContext(Dispatchers.IO) { WallpaperStore.installed(context).map { it.id }.toSet() }
    }

    fun load() {
        if (busy) return
        busy = true; status = context.getString(R.string.online_loading)
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { WallpaperStore.fetchIndex() } }
            busy = false
            r.onSuccess { list = it; status = "" }
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

    LaunchedEffect(config.offlineMode) { if (!config.offlineMode && list == null) load() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.wp_store_title), onBack = onBack)
        Text(stringResource(R.string.wp_store_sub), style = MaterialTheme.typography.bodyMedium, color = Wish.TextDim, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
        val first = initialFocus()
        OfflineNotice(config, store)
        if (status.isNotEmpty()) {
            SettingsItem(selected = false, onClick = { if (!busy) status = "" }, headlineContent = { Text(status) })
        }
        val l = list
        if (l == null) {
            SettingsItem(
                selected = false, enabled = !config.offlineMode && !busy,
                onClick = { load() },
                headlineContent = { Text(stringResource(R.string.online_try_again)) },
                supportingContent = { Text(Repos.OFFICIAL_WALLPAPERS_INDEX.substringAfter("//").substringBefore("/"), maxLines = 1) },
                modifier = Modifier.focusRequester(first),
            )
        } else {
            l.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { col, w ->
                        StoreCard(
                            w = w,
                            owned = w.id in have,
                            modifier = Modifier.weight(1f).let { if (row == 0 && col == 0) it.focusRequester(first) else it },
                        ) { use(w) }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
            val mine = l.filter { it.id in have }
            if (mine.isNotEmpty()) {
                SectionLabel(stringResource(R.string.wp_store_manage))
                mine.forEach { w ->
                    SettingsItem(
                        selected = false,
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { WallpaperStore.remove(context, w.id); Wallpapers.refreshDownloads(context) }
                                tick++
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.wp_store_remove, w.name)) },
                        supportingContent = { Text("${w.author} · ${w.license}", maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreCard(w: StoreWallpaper, owned: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, w.id) {
        value = withContext(Dispatchers.IO) { WallpaperStore.thumbnail(context, w)?.asImageBitmap() }
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
                Text(w.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    if (owned) stringResource(R.string.wp_store_have) else "%.1f MB".format(w.totalBytes / 1048576.0),
                    style = MaterialTheme.typography.bodySmall, color = Wish.TextDim, maxLines = 1,
                )
            }
        }
    }
}
