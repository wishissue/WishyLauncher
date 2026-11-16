package org.wishy.launcher.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.pm.PackageInfoCompat
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wishy.launcher.R
import org.wishy.launcher.art.BannerManager
import org.wishy.launcher.data.AppEntry
import org.wishy.launcher.data.ConfigStore
import org.wishy.launcher.data.LauncherConfig
import org.wishy.launcher.net.Repos
import org.wishy.launcher.theme.RepoListing
import org.wishy.launcher.theme.ThemeRepoClient
import org.wishy.launcher.theme.ThemeStorage
import org.wishy.theme.core.RepoChecker
import org.wishy.theme.core.RepoTheme

private fun formatBytes(b: Long): String = when {
    b >= 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    b >= 1024 -> "${b / 1024} KB"
    else -> "$b B"
}

private fun hostOf(url: String): String = runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)

/** Why a network action is blocked, or null if allowed. */
@Composable
internal fun OfflineNotice(config: LauncherConfig, store: ConfigStore) {
    val scope = rememberCoroutineScope()
    if (config.offlineMode) {
        SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(offlineMode = false) } } },
            headlineContent = { Text(stringResource(R.string.online_blocked)) },
            supportingContent = { Text(stringResource(R.string.online_blocked_sub)) },
        )
    }
}

/** Browse and install themes from the official repository or repositories the user added. */
@Composable
internal fun OnlineThemesScreen(config: LauncherConfig, store: ConfigStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { ThemeStorage.get(context) }
    val client = remember { ThemeRepoClient(context, storage) }
    val versionCode = remember {
        runCatching { PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt() }.getOrDefault(1)
    }
    var listing by remember { mutableStateOf<RepoListing?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var confirmUrl by remember { mutableStateOf<String?>(null) }
    var installedTick by remember { mutableIntStateOf(0) }
    val installed by produceState<Map<String, String>>(emptyMap(), installedTick) {
        value = withContext(Dispatchers.IO) { storage.list().associate { it.summary.id to it.summary.version } }
    }

    fun open(url: String) {
        if (busy) return
        busy = true; status = context.getString(R.string.online_loading)
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { client.list(url) } }
            busy = false
            r.onSuccess { listing = it; status = "" }
                .onFailure { listing = null; status = context.getString(R.string.online_failed, it.message ?: "") }
        }
    }

    fun install(l: RepoListing, t: RepoTheme) {
        if (busy) return
        busy = true; status = context.getString(R.string.online_installing, t.name)
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { client.install(l, t, versionCode) } }
            busy = false
            installedTick++
            status = r.fold(
                onSuccess = { context.getString(R.string.online_installed, t.name) },
                onFailure = { context.getString(R.string.online_failed, it.message ?: "") },
            )
        }
    }

    var showAdvanced by remember { mutableStateOf(false) }
    // Open the official list straight away; nobody should have to find and press a repository button.
    LaunchedEffect(config.offlineMode) {
        if (!config.offlineMode && listing == null) open(Repos.OFFICIAL_THEMES_INDEX)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.themes_get_more), onBack = onBack)
        val f = initialFocus()
        OfflineNotice(config, store)
        if (status.isNotEmpty()) {
            SettingsItem(selected = false, onClick = { status = "" }, headlineContent = { Text(status) })
        }
        val l = listing
        if (l == null) {
            SettingsItem(
                selected = false, enabled = !config.offlineMode && !busy,
                onClick = { open(Repos.OFFICIAL_THEMES_INDEX) },
                headlineContent = { Text(stringResource(R.string.online_try_again)) },
                supportingContent = { Text(stringResource(R.string.online_official)) },
                modifier = Modifier.focusRequester(f),
            )
            SettingsItem(
                selected = false, onClick = { showAdvanced = !showAdvanced },
                headlineContent = { Text(stringResource(R.string.online_advanced)) },
                supportingContent = { Text(stringResource(R.string.online_advanced_sub)) },
                trailingContent = { CheckMark(checked = showAdvanced) },
            )
            if (showAdvanced) {
                config.extraRepos.forEach { url ->
                    SettingsItem(
                        selected = false, enabled = !config.offlineMode && !busy,
                        onClick = { open(url) },
                        headlineContent = { Text(hostOf(url)) },
                        supportingContent = { Text(url + "\n" + stringResource(R.string.online_third_party_hint)) },
                        trailingContent = {
                            WishButton(stringResource(R.string.online_remove_repo), compact = true, filled = false) { scope.launch { store.update { c -> c.copy(extraRepos = c.extraRepos - url) } } }
                        },
                    )
                }
                SettingsItem(
                    selected = false, onClick = { addDialog = true },
                    headlineContent = { Text(stringResource(R.string.online_add_repo)) },
                    supportingContent = { Text(stringResource(R.string.online_add_repo_sub)) },
                )
            }
        } else {
            SectionLabel(l.index.name + "  ·  " + hostOf(l.indexUrl))
            if (!Repos.isOfficial(l.indexUrl)) {
                SettingsItem(selected = false, onClick = {}, headlineContent = { Text(stringResource(R.string.online_unreviewed)) })
            }
            l.index.themes.forEach { t ->
                val have = installed[t.id]
                val action = when {
                    have == null -> stringResource(R.string.online_install)
                    RepoChecker.isNewer(t.version, have) -> stringResource(R.string.online_update)
                    else -> stringResource(R.string.online_up_to_date)
                }
                SettingsItem(
                    selected = false, enabled = !busy,
                    onClick = { if (have == null || RepoChecker.isNewer(t.version, have)) install(l, t) },
                    headlineContent = { Text("${t.name}  ${t.version}") },
                    supportingContent = { Text(listOf(t.author, t.license, formatBytes(t.sizeBytes), t.description).filter { it.isNotBlank() }.joinToString("  ·  ")) },
                    trailingContent = { Text(action) },
                    modifier = if (t === l.index.themes.first()) Modifier.focusRequester(f) else Modifier,
                )
            }
            if (!Repos.isOfficial(l.indexUrl)) {
                SettingsItem(selected = false, onClick = { listing = null; status = "" }, headlineContent = { Text(stringResource(R.string.online_back_to_repos)) })
            } else {
                // Reach "add a repository" without having to fail to load the official list first.
                SettingsItem(
                    selected = false,
                    onClick = { listing = null; status = ""; showAdvanced = true },
                    headlineContent = { Text(stringResource(R.string.online_advanced)) },
                    supportingContent = { Text(stringResource(R.string.online_advanced_sub)) },
                )
            }
        }
    }

    if (addDialog) {
        TextEntryDialog(
            title = stringResource(R.string.online_add_repo),
            hint = stringResource(R.string.online_add_repo_hint),
            initial = "https://",
            onSave = { text ->
                addDialog = false
                val u = text.trim()
                if (u.startsWith("https://") && u.endsWith("index.json") && u.length < 300 && runCatching { java.net.URI(u).host }.getOrNull() != null) confirmUrl = u
                else status = context.getString(R.string.online_bad_url)
            },
            onDismiss = { addDialog = false },
        )
    }
    confirmUrl?.let { url ->
        ConfirmDialog(
            title = stringResource(R.string.online_confirm_title, hostOf(url)),
            body = stringResource(R.string.online_confirm_body),
            confirm = stringResource(R.string.online_confirm_add),
            onConfirm = { confirmUrl = null; scope.launch { store.update { c -> if (url in c.extraRepos) c else c.copy(extraRepos = c.extraRepos + url) } } },
            onDismiss = { confirmUrl = null },
        )
    }
}

/** Artwork: online lookup, per-app pictures, cache. */
@Composable
internal fun BannersScreen(
    config: LauncherConfig,
    store: ConfigStore,
    apps: List<AppEntry>,
    onArtChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { BannerManager.get(context) }
    var tick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var pickFor by remember { mutableStateOf<String?>(null) }
    var showOwn by remember { mutableStateOf(false) }
    var versionsFor by remember { mutableStateOf<AppEntry?>(null) }
    var addSource by remember { mutableStateOf(false) }
    var confirmSource by remember { mutableStateOf<String?>(null) }
    val sizes by produceState(BannerManager.Sizes(0, 0, 0, 0), tick) { value = withContext(Dispatchers.IO) { manager.sizes() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val pkg = pickFor; pickFor = null
        if (uri == null || pkg == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { manager.setUserArt(pkg, uri) } }
            status = r.fold({ context.getString(R.string.banners_saved) }, { context.getString(R.string.online_failed, it.message ?: "") })
            tick++; onArtChanged()
        }
    }

    // Picking a banner for one app is its own screen; Back from there returns to this list.
    val picking = versionsFor
    if (picking != null) {
        BannerVersionsScreen(
            app = picking, config = config, store = store, manager = manager, refresh = tick,
            onPickOwn = { pickFor = picking.pkg; picker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) },
            onChanged = { tick++; onArtChanged() },
            onBack = { versionsFor = null },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.item_banners), onBack = onBack)
        val f = initialFocus()
        if (status.isNotEmpty()) SettingsItem(selected = false, onClick = { status = "" }, headlineContent = { Text(status) })

        // One switch turns the whole thing on (including the internet permission), one button refreshes.
        SettingsItem(
            selected = false,
            onClick = {
                scope.launch {
                    store.update {
                        val on = !it.artworkOnline
                        it.copy(artworkOnline = on, offlineMode = if (on) false else it.offlineMode)
                    }
                }
            },
            headlineContent = { Text(stringResource(R.string.banners_auto)) },
            supportingContent = { Text(stringResource(R.string.banners_auto_sub)) },
            trailingContent = { SwitchPill(config.artworkOnline && !config.offlineMode) },
            modifier = Modifier.focusRequester(f),
        )
        if (config.artworkOnline && !config.offlineMode) {
            SettingsItem(
                selected = false, enabled = status != context.getString(R.string.online_loading),
                onClick = {
                    status = context.getString(R.string.online_loading)
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { manager.syncOfficial(apps.map { it.pkg }.toSet()) } }
                        status = r.fold({ context.getString(R.string.banners_refreshed, it) }, { context.getString(R.string.online_failed, it.message ?: "") })
                        tick++; onArtChanged()
                    }
                },
                headlineContent = { Text(stringResource(R.string.banners_update_now)) },
                supportingContent = { Text(stringResource(R.string.banners_downloaded, sizes.repoCount) + "  ·  " + formatBytes(sizes.repoBytes)) },
            )
            if (sizes.repoCount > 0) {
                SettingsItem(
                    selected = false,
                    onClick = { scope.launch { withContext(Dispatchers.IO) { manager.clearDownloaded() }; tick++; onArtChanged() } },
                    headlineContent = { Text(stringResource(R.string.banners_clear)) },
                    supportingContent = { Text(stringResource(R.string.banners_clear_sub)) },
                )
            }
        } else if (config.offlineMode && config.artworkOnline) {
            OfflineNotice(config, store)
        }

        SettingsItem(
            selected = false, onClick = { showOwn = !showOwn },
            headlineContent = { Text(stringResource(R.string.banners_choose_toggle)) },
            supportingContent = { Text(stringResource(R.string.banners_yours, sizes.userCount)) },
            trailingContent = { CheckMark(checked = showOwn) },
        )
        if (showOwn) apps.sortedBy { it.label.lowercase() }.forEach { app ->
            val has = remember(tick, app.pkg) { manager.hasUserArt(app.pkg) }
            val pickNow = remember(tick, app.pkg) { manager.pick(app.pkg) }
            SettingsItem(
                selected = false,
                onClick = { versionsFor = app },
                headlineContent = { Text(app.label) },
                supportingContent = {
                    Text(
                        stringResource(
                            when {
                                has -> R.string.banners_choice_yours
                                pickNow == BannerManager.PICK_APP -> R.string.banners_choice_app
                                pickNow == BannerManager.PICK_DEFAULT -> R.string.banners_choice_auto
                                else -> R.string.banners_choice_version
                            }
                        ),
                        maxLines = 1,
                    )
                },
                leadingContent = { AppThumb(app) },
                trailingContent = {
                    if (has) WishButton(stringResource(R.string.banners_reset), compact = true, filled = false) { manager.clearUserArt(app.pkg); tick++; onArtChanged() }
                },
            )
        }

        // More places to get banners from. Anyone can host a variants.json; the official one is always included.
        SectionLabel(stringResource(R.string.banner_sources))
        SettingsItem(selected = false, onClick = {}, headlineContent = { Text(stringResource(R.string.banner_sources_official)) })
        config.bannerRepos.forEach { url ->
            SettingsItem(
                selected = false, onClick = {},
                headlineContent = { Text(hostOf(url)) },
                supportingContent = { Text(url, maxLines = 2) },
                trailingContent = {
                    WishButton(stringResource(R.string.online_remove_repo), compact = true, filled = false) {
                        scope.launch { store.update { c -> c.copy(bannerRepos = c.bannerRepos - url) } }
                    }
                },
            )
        }
        SettingsItem(
            selected = false, onClick = { addSource = true },
            headlineContent = { Text(stringResource(R.string.banner_sources_add)) },
            supportingContent = { Text(stringResource(R.string.banner_sources_add_sub)) },
        )
    }

    if (addSource) {
        TextEntryDialog(
            title = stringResource(R.string.banner_sources_add),
            hint = stringResource(R.string.banner_sources_hint),
            initial = "https://",
            onSave = { text ->
                addSource = false
                val u = text.trim()
                if (u.startsWith("https://") && u.endsWith("variants.json") && u.length < 300 && runCatching { java.net.URI(u).host }.getOrNull() != null) confirmSource = u
                else status = context.getString(R.string.online_bad_url)
            },
            onDismiss = { addSource = false },
        )
    }
    confirmSource?.let { url ->
        ConfirmDialog(
            title = stringResource(R.string.online_confirm_title, hostOf(url)),
            body = stringResource(R.string.banner_sources_confirm_body),
            confirm = stringResource(R.string.online_confirm_add),
            onConfirm = { confirmSource = null; scope.launch { store.update { c -> if (url in c.bannerRepos) c else c.copy(bannerRepos = c.bannerRepos + url) } } },
            onDismiss = { confirmSource = null },
        )
    }
}

@Composable
internal fun TextEntryDialog(title: String, hint: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(hint, style = MaterialTheme.typography.bodySmall)
                val f = initialFocus()
                Box(Modifier.fillMaxWidth().background(Wish.Text.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(12.dp)) {
                    BasicTextField(
                        value = text, onValueChange = { text = it }, singleLine = true,
                        textStyle = TextStyle(color = Wish.Text, fontSize = 15.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(f),
                    )
                }
                ButtonRow {
                    WishButton(stringResource(R.string.save), compact = true) { onSave(text) }
                    WishButton(stringResource(R.string.cancel), compact = true, filled = false, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
internal fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
                ButtonRow {
                    // Cancel is first so it holds the initial focus: the safe choice is the default.
                    val f = initialFocus()
                    WishButton(stringResource(R.string.cancel), compact = true, filled = false, modifier = Modifier.focusRequester(f), onClick = onDismiss)
                    WishButton(confirm, compact = true, onClick = onConfirm)
                }
            }
        }
    }
}
