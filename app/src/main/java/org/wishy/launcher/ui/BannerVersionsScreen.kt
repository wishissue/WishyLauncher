package org.wishy.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import org.wishy.launcher.art.BannerManager
import org.wishy.launcher.data.AppEntry
import org.wishy.launcher.data.ConfigStore
import org.wishy.launcher.data.LauncherConfig

private const val CHOICE_YOURS = "yours"

/**
 * Pick the banner for one app. Every option is a card with a preview: automatic, the app's own banner, each version
 * offered by the official repository or a source the person added, and a picture from this device. The versions are
 * downloaded (and checked) as soon as the list arrives, so the previews fill in one by one.
 */
@Composable
internal fun BannerVersionsScreen(
    app: AppEntry,
    config: LauncherConfig,
    store: ConfigStore,
    manager: BannerManager,
    /** Changes when the parent saved a picture of the person's own, so the highlighted card updates. */
    refresh: Int,
    onPickOwn: () -> Unit,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<BannerManager.Version>?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    val choice by produceState(BannerManager.PICK_DEFAULT, tick, refresh) {
        value = withContext(Dispatchers.IO) { if (manager.hasUserArt(app.pkg)) CHOICE_YOURS else manager.pick(app.pkg) }
    }

    LaunchedEffect(app.pkg, config.offlineMode, config.bannerRepos) {
        if (config.offlineMode) return@LaunchedEffect
        status = context.getString(R.string.banner_pick_loading)
        val r = withContext(Dispatchers.IO) { runCatching { manager.fetchVersions(app.pkg, config.bannerRepos) } }
        if (r.isSuccess) {
            val list = r.getOrThrow()
            versions = list
            status = ""
            // One at a time keeps a small TV's network and memory calm; each preview appears as soon as its file is checked.
            for (v in list) {
                withContext(Dispatchers.IO) { runCatching { manager.downloadVersion(app.pkg, v) } }
                tick++
            }
        } else {
            status = context.getString(R.string.online_failed, r.exceptionOrNull()?.message ?: "")
        }
    }

    fun choose(block: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(block) }
            busy = false; tick++
            if (r.isSuccess) { status = ""; onChanged() }
            else status = context.getString(R.string.online_failed, r.exceptionOrNull()?.message ?: "")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Back || e.key == Key.Escape) {
                    if (e.type == KeyEventType.KeyUp) onBack()
                    true
                } else false
            }
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader(app.label, onBack = onBack)
        val first = initialFocus()
        OfflineNotice(config, store)
        if (status.isNotEmpty()) {
            SettingsItem(selected = false, onClick = { if (!busy) status = "" }, headlineContent = { Text(status) })
        }

        val defaultPreview by produceState<ImageBitmap?>(null, app.pkg, tick) { value = withContext(Dispatchers.IO) { manager.defaultPreview(app.pkg) } }
        ChoiceCard(
            title = stringResource(R.string.banner_pick_default),
            subtitle = stringResource(R.string.banner_pick_default_sub),
            selected = choice == BannerManager.PICK_DEFAULT,
            preview = defaultPreview ?: app.banner,
            app = app,
            modifier = Modifier.focusRequester(first),
        ) { choose { manager.pickDefault(app.pkg) } }

        val ownPreview by produceState<ImageBitmap?>(null, app.pkg) { value = withContext(Dispatchers.IO) { manager.appOwnPreview(app.pkg) } }
        ChoiceCard(
            title = stringResource(R.string.banner_pick_app),
            subtitle = stringResource(R.string.banner_pick_app_sub),
            selected = choice == BannerManager.PICK_APP,
            preview = ownPreview,
            app = app,
        ) { choose { manager.pickAppOwn(app.pkg) } }

        versions?.forEach { v ->
            val key = v.key
            val preview by produceState<ImageBitmap?>(null, key, tick) { value = withContext(Dispatchers.IO) { manager.versionPreview(app.pkg, key) } }
            ChoiceCard(
                title = v.info.name,
                subtitle = listOf(v.info.author, v.info.license).joinToString(" · "),
                selected = choice == key,
                preview = preview,
                app = app,
            ) { choose { manager.downloadVersion(app.pkg, v); manager.pickVersion(app.pkg, key) } }
        }
        if (versions?.isEmpty() == true) {
            Text(
                stringResource(R.string.banner_pick_none),
                style = MaterialTheme.typography.bodySmall, color = Wish.TextDim,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        val userPreview by produceState<ImageBitmap?>(null, app.pkg, tick, refresh) { value = withContext(Dispatchers.IO) { manager.userPreview(app.pkg) } }
        ChoiceCard(
            title = stringResource(R.string.banner_pick_yours),
            subtitle = stringResource(R.string.banner_pick_yours_sub),
            selected = choice == CHOICE_YOURS,
            preview = userPreview,
            app = app,
        ) { onPickOwn() }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    preview: ImageBitmap?,
    app: AppEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Wish.Surface, focusedContainerColor = Wish.SurfaceHigh,
            contentColor = Wish.Text, focusedContentColor = Wish.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, Wish.Success)) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Wish.Primary)),
        ),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(app.tile.copy(alpha = 0.35f))) {
                if (preview != null) {
                    Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else if (app.icon != null) {
                    // Nothing to preview (yet): the app's icon keeps the card recognisable.
                    Image(app.icon, null, Modifier.align(Alignment.Center).size(56.dp))
                }
                if (selected) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).background(Wish.Success, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Sym.Check, null, tint = Wish.Bg, modifier = Modifier.size(18.dp)) }
                }
            }
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Wish.TextDim, maxLines = 2)
            }
        }
    }
}
