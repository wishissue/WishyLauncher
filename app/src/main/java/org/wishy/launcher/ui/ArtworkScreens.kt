package org.wishy.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wishy.launcher.R
import org.wishy.launcher.art.AppArt
import org.wishy.launcher.art.ArtDiscovery
import org.wishy.launcher.art.ArtLibrary
import org.wishy.launcher.art.BannerManager
import org.wishy.launcher.art.BannerRenderer
import org.wishy.launcher.data.AppEntry
import org.wishy.launcher.data.ConfigStore
import org.wishy.launcher.data.LauncherConfig
import org.wishy.theme.core.ArtEntry
import org.wishy.theme.core.ArtMatcher
import org.wishy.theme.core.ArtOrigin
import org.wishy.theme.core.AutoDesign
import org.wishy.theme.core.BannerSpec
import org.wishy.theme.core.GenericIcons
import org.wishy.theme.core.LogoMatch
import org.wishy.theme.core.MatchApp
import java.io.File

internal enum class ArtScreen { Hub, Find, FindApp, Apps, AppDetail, Options, Versions }

/** Navigation inside Settings, Artwork. Back always goes one step back; at the hub it leaves Artwork. */
internal class ArtNav {
    var screen by mutableStateOf(ArtScreen.Hub)
        private set
    var pkg by mutableStateOf<String?>(null)
        private set
    /** On the app list: true opens the editor, false opens the app's artwork page. */
    var createMode by mutableStateOf(false)
        private set
    private val stack = ArrayList<ArtScreen>()

    fun go(s: ArtScreen, pkg: String? = this.pkg, create: Boolean = createMode) {
        stack.add(screen); this.pkg = pkg; createMode = create; screen = s
    }

    /** Returns false when already at the hub, so the caller leaves Artwork. */
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        screen = stack.removeAt(stack.size - 1)
        return true
    }
}

/** What "Find artwork" learned, kept on the device only. */
internal class FindResult(
    val matches: Map<String, List<LogoMatch>>,
    val official: Map<String, ArtEntry>,
    val catalogs: ArtDiscovery.Catalogs,
)

/** One picture the person can choose for an app, already drawn small for the list. */
internal class Cand(
    val origin: ArtOrigin,
    val title: String,
    val credit: String,
    val preview: Bitmap,
    val logo: EditorLogo?,
    val spec: BannerSpec,
    val officialFile: File?,
    val officialEntry: ArtEntry?,
)

internal class EditorRequest(val app: AppEntry, val cand: Cand?)

private fun runFind(ctx: Context, apps: List<AppEntry>, netOk: Boolean): FindResult {
    val d = ArtDiscovery.get(ctx)
    val cats = d.loadCatalogs(netOk)
    val matches = ArtMatcher.match(apps.map { MatchApp(it.pkg, it.label, it.autoCategory) }, cats.logos)
    val official = apps.mapNotNull { a -> d.officialEntry(cats.official, a.pkg)?.let { a.pkg to it } }.toMap()
    return FindResult(matches, official, cats)
}

private fun originLabelRes(o: ArtOrigin): Int = when (o) {
    ArtOrigin.OFFICIAL -> R.string.art_origin_official
    ArtOrigin.COMMUNITY -> R.string.art_origin_community
    ArtOrigin.GENERATED -> R.string.art_origin_generated
    else -> R.string.art_origin_yours
}

private fun credit(author: String, license: String) =
    listOf(author, license).filter { it.isNotBlank() }.joinToString(" · ")

/** The five or so pictures offered for one app. Needs the network only for pictures not already cached. */
private fun buildCands(ctx: Context, app: AppEntry, find: FindResult, netOk: Boolean): List<Cand> {
    val d = ArtDiscovery.get(ctx)
    val pkg = app.pkg
    val out = ArrayList<Cand>()
    find.official[pkg]?.let { e ->
        val f = d.officialFile(pkg, e, netOk)
        val bmp = f?.let { d.previewOf(it) }
        if (f != null && bmp != null) {
            out += Cand(ArtOrigin.OFFICIAL, ctx.getString(R.string.art_cand_official_title), credit(e.author, e.license), bmp, null, BannerSpec(), f, e)
        }
    }
    for (m in find.matches[pkg].orEmpty()) {
        val fetched = d.fetchLogo(m.logo, netOk) ?: continue
        val l = runCatching { editorLogoFrom(fetched) }.getOrNull() ?: continue
        val spec = AutoDesign.forLogo(pkg, l.image.mono, l.brandHex, l.dominant)
        out += Cand(
            ArtOrigin.COMMUNITY, ctx.getString(R.string.art_cand_logo_title, m.logo.title),
            ctx.getString(R.string.art_logo_by, l.author, l.license.ifBlank { ctx.getString(R.string.art_license_unknown) }),
            BannerRenderer.render(spec, l.image, null, 640, 360), l, spec, null, null,
        )
    }
    editorLogoFromIcon(ctx, pkg, ctx.getString(R.string.ed_app_icon))?.let { l ->
        val spec = AutoDesign.forLogo(pkg, false, null, l.dominant)
        out += Cand(ArtOrigin.GENERATED, ctx.getString(R.string.art_cand_icon_title), ctx.getString(R.string.art_made_by_wishy),
            BannerRenderer.render(spec, l.image, null, 640, 360), l, spec, null, null)
    }
    val g = GenericIcons.forCategory(app.autoCategory)
    runCatching { editorLogoFromGeneric(g) }.getOrNull()?.let { l ->
        val spec = AutoDesign.forLogo(pkg, true, null, null)
        out += Cand(ArtOrigin.GENERATED, ctx.getString(R.string.art_cand_generic_title, g.name), ctx.getString(R.string.art_made_by_wishy),
            BannerRenderer.render(spec, l.image, null, 640, 360), l, spec, null, null)
    }
    return out
}

/** Saves a chosen picture as the person's banner. Blocking. */
private fun saveCand(ctx: Context, pkg: String, c: Cand) {
    if (c.officialFile != null && c.officialEntry != null) ArtLibrary.get(ctx).saveOfficial(pkg, c.officialFile, c.officialEntry)
    else saveBannerBlocking(ctx, pkg, c.logo, c.spec, null, null)
}

/** Which of the five statuses applies to an app. Reads files and asks the system, so keep off the main thread. */
private fun statusRes(ctx: Context, m: BannerManager, pkg: String): Int = when {
    m.hasUserArt(pkg) -> R.string.art_status_yours
    m.prefersOriginal(pkg) -> R.string.art_status_original
    m.repoFile(pkg) != null -> R.string.art_status_official
    AppArt.hasOwnBanner(ctx, pkg) -> R.string.art_status_app
    else -> R.string.art_status_icon
}

/** Entry point from Settings. [nav] is owned by the Settings sheet so that Back can reach it. */
@Composable
internal fun ArtworkFlow(
    config: LauncherConfig,
    store: ConfigStore,
    apps: List<AppEntry>,
    nav: ArtNav,
    onArtChanged: () -> Unit,
    onExit: () -> Unit,
) {
    val netOk = config.artworkOnline && !config.offlineMode
    var tick by remember { mutableIntStateOf(0) }
    var find by remember { mutableStateOf<FindResult?>(null) }
    var editing by remember { mutableStateOf<EditorRequest?>(null) }
    val changed = { tick++; onArtChanged() }

    when (nav.screen) {
        ArtScreen.Hub -> ArtHub(
            onFind = { find = null; nav.go(ArtScreen.Find) },
            onCreate = { nav.go(ArtScreen.Apps, create = true) },
            onMine = { nav.go(ArtScreen.Apps, create = false) },
            onOptions = { nav.go(ArtScreen.Options) },
            onBack = onExit,
        )
        ArtScreen.Find -> FindScreen(config, store, apps, netOk, find, tick, { find = it }, { nav.go(ArtScreen.FindApp, it) }, changed) { nav.back() }
        ArtScreen.FindApp -> {
            val app = apps.firstOrNull { it.pkg == nav.pkg }
            if (app != null) FindAppScreen(app, netOk, find, tick, { editing = it }, changed) { nav.back() } else LaunchedEffect(Unit) { nav.back() }
        }
        ArtScreen.Apps -> AppsScreen(apps, nav.createMode, tick, changed,
            onPick = { app -> if (nav.createMode) editing = EditorRequest(app, null) else nav.go(ArtScreen.AppDetail, app.pkg) },
        ) { nav.back() }
        ArtScreen.AppDetail -> {
            val app = apps.firstOrNull { it.pkg == nav.pkg }
            if (app != null) AppDetailScreen(app, tick, changed, { editing = EditorRequest(app, null) }, { nav.go(ArtScreen.FindApp, app.pkg) }) { nav.back() }
            else LaunchedEffect(Unit) { nav.back() }
        }
        ArtScreen.Options -> OptionsScreen(changed, { nav.go(ArtScreen.Versions) }) { nav.back() }
        // The existing screen: online switch, refresh, clear downloads, banner versions per app, extra sources.
        ArtScreen.Versions -> BannersScreen(config = config, store = store, apps = apps, onArtChanged = changed, onBack = { nav.back() })
    }

    editing?.let { req ->
        BannerEditorDialog(app = req.app, initial = req.cand, netOk = netOk, onDismiss = { editing = null }, onSaved = changed)
    }
}

@Composable
private fun ArtHub(onFind: () -> Unit, onCreate: () -> Unit, onMine: () -> Unit, onOptions: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ScreenHeader(stringResource(R.string.item_banners), onBack = onBack)
        val f = initialFocus()
        SettingsItem(
            onClick = onFind,
            headlineContent = { Text(stringResource(R.string.art_find)) },
            supportingContent = { Text(stringResource(R.string.art_find_sub)) },
            leadingContent = { androidx.tv.material3.Icon(AppIcons.Image, contentDescription = null) },
            modifier = Modifier.focusRequester(f),
        )
        SettingsItem(
            onClick = onCreate,
            headlineContent = { Text(stringResource(R.string.art_create)) },
            supportingContent = { Text(stringResource(R.string.art_create_sub)) },
            leadingContent = { androidx.tv.material3.Icon(AppIcons.Apps, contentDescription = null) },
        )
        SettingsItem(
            onClick = onMine,
            headlineContent = { Text(stringResource(R.string.art_mine)) },
            supportingContent = { Text(stringResource(R.string.art_mine_sub)) },
            leadingContent = { androidx.tv.material3.Icon(AppIcons.Folder, contentDescription = null) },
        )
        SectionLabel(stringResource(R.string.art_more))
        SettingsItem(
            onClick = onOptions,
            headlineContent = { Text(stringResource(R.string.art_more)) },
            supportingContent = { Text(stringResource(R.string.art_more_sub), maxLines = 1) },
        )
    }
}

@Composable
private fun FindScreen(
    config: LauncherConfig,
    store: ConfigStore,
    apps: List<AppEntry>,
    netOk: Boolean,
    find: FindResult?,
    tick: Int,
    onResult: (FindResult?) -> Unit,
    onOpenApp: (String) -> Unit,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { BannerManager.get(ctx) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(find == null, netOk) {
        if (find == null) onResult(withContext(Dispatchers.IO) { runFind(ctx, apps, netOk) })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ScreenHeader(stringResource(R.string.art_find), onBack = onBack)
        val f = initialFocus()
        if (find == null) {
            SettingsItem(onClick = {}, headlineContent = { Text(stringResource(R.string.art_searching)) }, modifier = Modifier.focusRequester(f))
            return@Column
        }
        val withArt = apps.filter { it.pkg in find.matches || it.pkg in find.official }
        SettingsItem(
            onClick = {},
            headlineContent = { Text(stringResource(R.string.art_found_summary, withArt.size, apps.size)) },
            supportingContent = if (status.isNotEmpty()) ({ Text(status) }) else null,
            modifier = Modifier.focusRequester(f),
        )
        if (!config.artworkOnline) {
            SettingsItem(
                onClick = { scope.launch { store.update { it.copy(artworkOnline = true, offlineMode = false) }; onResult(null) } },
                headlineContent = { Text(stringResource(R.string.art_turn_on)) },
                supportingContent = { Text(stringResource(R.string.art_online_off)) },
            )
        } else if (config.offlineMode) {
            OfflineNotice(config, store)
        } else if (!netOk) {
            SettingsItem(onClick = {}, headlineContent = { Text(stringResource(R.string.art_offline_note)) })
        }
        if (find.catalogs.unavailable.isNotEmpty()) {
            SettingsItem(onClick = {}, headlineContent = { Text(stringResource(R.string.art_provider_down, find.catalogs.unavailable.joinToString(", "))) })
        }
        if (withArt.isEmpty()) {
            SettingsItem(onClick = {}, headlineContent = { Text(stringResource(R.string.art_none_found)) })
        }
        val statuses by produceState(emptyMap<String, Int>(), apps, tick) {
            value = withContext(Dispatchers.IO) { apps.associate { it.pkg to statusRes(ctx, manager, it.pkg) } }
        }
        val ordered = withArt + apps.filter { it !in withArt }
        for (app in ordered) {
            val official = app.pkg in find.official
            val n = find.matches[app.pkg]?.size ?: 0
            val found = when {
                official && n > 0 -> stringResource(R.string.art_app_official) + " · " + stringResource(R.string.art_app_logos, n)
                official -> stringResource(R.string.art_app_official)
                n > 0 -> stringResource(R.string.art_app_logos, n)
                else -> stringResource(R.string.art_app_none)
            }
            val cur = statuses[app.pkg]?.let { stringResource(R.string.art_current, stringResource(it)) } ?: ""
            SettingsItem(
                onClick = { onOpenApp(app.pkg) },
                headlineContent = { Text(app.label, maxLines = 1) },
                supportingContent = { Text(found + if (cur.isEmpty()) "" else "\n" + cur, maxLines = 2) },
                leadingContent = { AppThumb(app) },
            )
        }
        SettingsItem(
            selected = false, enabled = !busy,
            onClick = {
                scope.launch {
                    val needing = withContext(Dispatchers.IO) {
                        apps.filter { !manager.hasUserArt(it.pkg) && !manager.prefersOriginal(it.pkg) && manager.repoFile(it.pkg) == null && !AppArt.hasOwnBanner(ctx, it.pkg) }
                    }
                    if (needing.isEmpty()) status = ctx.getString(R.string.art_generate_none) else confirmCount = needing.size
                }
            },
            headlineContent = { Text(stringResource(R.string.art_generate_missing)) },
        )
    }
    confirmCount?.let { n ->
        ConfirmDialog(
            title = stringResource(R.string.art_generate_confirm_title),
            body = stringResource(R.string.art_generate_confirm_body, n),
            confirm = stringResource(R.string.ed_done),
            onConfirm = {
                confirmCount = null
                val fr = find ?: return@ConfirmDialog
                busy = true
                scope.launch {
                    val made = withContext(Dispatchers.IO) { generateMissing(ctx, apps, fr, netOk) { name -> status = ctx.getString(R.string.art_working_on, name) } }
                    busy = false
                    status = ctx.getString(R.string.art_generate_done, made)
                    onChanged()
                }
            },
            onDismiss = { confirmCount = null },
        )
    }
}

/**
 * Makes a banner for every app that only has an icon: from the best logo found for it (already cached or fetched
 * for this app alone), else the app icon, else a generic icon. Never touches an app that already has a banner of
 * the person's, an official one, its own, or one the person restored on purpose. Blocking.
 */
private fun generateMissing(ctx: Context, apps: List<AppEntry>, find: FindResult, netOk: Boolean, progress: (String) -> Unit): Int {
    val manager = BannerManager.get(ctx)
    val d = ArtDiscovery.get(ctx)
    var made = 0
    for (app in apps) {
        if (manager.hasUserArt(app.pkg) || manager.prefersOriginal(app.pkg) || manager.repoFile(app.pkg) != null || AppArt.hasOwnBanner(ctx, app.pkg)) continue
        progress(app.label)
        val ok = runCatching {
            val fromCatalog = find.matches[app.pkg].orEmpty().firstNotNullOfOrNull { m ->
                d.fetchLogo(m.logo, netOk)?.let { runCatching { editorLogoFrom(it) }.getOrNull() }
            }
            val logo = fromCatalog
                ?: editorLogoFromIcon(ctx, app.pkg, ctx.getString(R.string.ed_app_icon))
                ?: editorLogoFromGeneric(GenericIcons.forCategory(app.autoCategory))
            val spec = AutoDesign.forLogo(app.pkg, logo.image.mono, logo.brandHex, logo.dominant)
            saveBannerBlocking(ctx, app.pkg, logo, spec, null, null)
        }.isSuccess
        if (ok) made++
    }
    return made
}

@Composable
private fun FindAppScreen(
    app: AppEntry,
    netOk: Boolean,
    find: FindResult?,
    tick: Int,
    onCustomize: (EditorRequest) -> Unit,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { BannerManager.get(ctx) }
    var cands by remember { mutableStateOf<List<Cand>?>(null) }
    var chosen by remember { mutableStateOf<Cand?>(null) }
    var status by remember { mutableStateOf("") }
    var hasMine by remember { mutableStateOf(false) }

    LaunchedEffect(app.pkg, tick) {
        cands = null
        val r = withContext(Dispatchers.IO) {
            hasMine = manager.hasUserArt(app.pkg)
            val fr = find ?: runFind(ctx, listOf(app), netOk)
            buildCands(ctx, app, fr, netOk)
        }
        cands = r
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenHeader(app.label, onBack = onBack)
        val list = cands
        if (list == null) {
            val f = initialFocus()
            SettingsItem(onClick = {}, headlineContent = { Text(stringResource(R.string.art_loading_options)) }, modifier = Modifier.focusRequester(f))
        } else {
            if (status.isNotEmpty()) SettingsItem(onClick = { status = "" }, headlineContent = { Text(status) })
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list) { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val img = remember(c) { c.preview.asImageBitmap() }
                        Tile(Modifier.fillMaxWidth().aspectRatio(16f / 9f), false, { chosen = c }) {
                            Image(bitmap = img, contentDescription = c.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Text(stringResource(originLabelRes(c.origin)), color = Wish.Primary, fontSize = 12.sp)
                        Text(c.title, color = Wish.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.credit.isNotBlank()) Text(c.credit, color = Wish.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    chosen?.let { c ->
        Dialog(onDismissRequest = { chosen = null }) {
            Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.width(460.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        bitmap = remember(c) { c.preview.asImageBitmap() }, contentDescription = c.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Text(stringResource(originLabelRes(c.origin)) + " · " + c.title, style = MaterialTheme.typography.titleSmall)
                    if (c.credit.isNotBlank()) Text(c.credit, style = MaterialTheme.typography.bodySmall)
                    if (hasMine) Text(stringResource(R.string.art_replace_warn), style = MaterialTheme.typography.bodySmall)
                    ButtonRow {
                        val f = initialFocus()
                        // With a banner of the person's own already in place, the safe choice (Cancel) holds the focus.
                        if (hasMine) WishButton(stringResource(R.string.cancel), compact = true, filled = false, modifier = Modifier.focusRequester(f)) { chosen = null }
                        WishButton(
                            stringResource(R.string.art_use_this), compact = true,
                            modifier = if (hasMine) Modifier else Modifier.focusRequester(f),
                        ) {
                            chosen = null
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { runCatching { saveCand(ctx, app.pkg, c) } }
                                status = r.fold({ ctx.getString(R.string.art_saved) }, { ctx.getString(R.string.art_save_failed, it.message ?: "") })
                                if (r.isSuccess) { hasMine = true; onChanged() }
                            }
                        }
                        if (c.logo != null) WishButton(stringResource(R.string.art_customize), compact = true, filled = false) {
                            chosen = null; onCustomize(EditorRequest(app, c))
                        }
                        if (!hasMine) WishButton(stringResource(R.string.cancel), compact = true, filled = false) { chosen = null }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsScreen(
    apps: List<AppEntry>,
    createMode: Boolean,
    tick: Int,
    onChanged: () -> Unit,
    onPick: (AppEntry) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { BannerManager.get(ctx) }
    val library = remember { ArtLibrary.get(ctx) }
    var status by remember { mutableStateOf("") }
    val statuses by produceState(emptyMap<String, Int>(), apps, tick) {
        value = withContext(Dispatchers.IO) { apps.associate { it.pkg to statusRes(ctx, manager, it.pkg) } }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val out = ctx.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Could not write the file.")
                    out.use { library.exportPack(it) }
                }
            }
            status = r.fold({ ctx.getString(R.string.art_exported, it) }, { ctx.getString(R.string.online_failed, it.message ?: "") })
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { library.importPack(uri) } }
            status = r.fold({ ctx.getString(R.string.art_imported, it.added, it.kept, it.skipped) }, { ctx.getString(R.string.online_failed, it.message ?: "") })
            if (r.isSuccess) onChanged()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ScreenHeader(stringResource(if (createMode) R.string.art_create else R.string.art_mine), onBack = onBack)
        val f = initialFocus()
        var first = true
        fun focusIfFirst(): Modifier = if (first) { first = false; Modifier.focusRequester(f) } else Modifier
        if (status.isNotEmpty()) SettingsItem(onClick = { status = "" }, headlineContent = { Text(status) }, modifier = focusIfFirst())
        if (!createMode) {
            SettingsItem(
                onClick = {
                    scope.launch {
                        val any = withContext(Dispatchers.IO) { manager.userPackages().isNotEmpty() }
                        if (any) exporter.launch("wishy-artwork.zip") else status = ctx.getString(R.string.art_nothing_to_export)
                    }
                },
                headlineContent = { Text(stringResource(R.string.art_export)) },
                supportingContent = { Text(stringResource(R.string.art_export_sub)) },
                modifier = focusIfFirst(),
            )
            SettingsItem(
                onClick = { importer.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                headlineContent = { Text(stringResource(R.string.art_import)) },
                supportingContent = { Text(stringResource(R.string.art_import_sub)) },
                modifier = focusIfFirst(),
            )
        }
        SectionLabel(stringResource(R.string.art_pick_app))
        val sorted = if (createMode) apps.sortedBy { it.label.lowercase() }
        else apps.sortedWith(compareBy({ statuses[it.pkg] != R.string.art_status_yours }, { it.label.lowercase() }))
        for (app in sorted) {
            SettingsItem(
                onClick = { onPick(app) },
                headlineContent = { Text(app.label, maxLines = 1) },
                supportingContent = { Text(statuses[app.pkg]?.let { stringResource(it) } ?: "", maxLines = 1) },
                leadingContent = { AppThumb(app) },
                modifier = focusIfFirst(),
            )
        }
    }
}

@Composable
private fun AppDetailScreen(
    app: AppEntry,
    tick: Int,
    onChanged: () -> Unit,
    onEdit: () -> Unit,
    onFind: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { BannerManager.get(ctx) }
    val library = remember { ArtLibrary.get(ctx) }
    var status by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    val state by produceState(Pair(R.string.art_status_icon, false), app.pkg, tick) {
        value = withContext(Dispatchers.IO) { statusRes(ctx, manager, app.pkg) to (manager.hasUserArt(app.pkg) || manager.prefersOriginal(app.pkg)) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { library.setPicture(app.pkg, uri) } }
            status = r.fold({ ctx.getString(R.string.art_saved) }, { ctx.getString(R.string.online_failed, it.message ?: "") })
            if (r.isSuccess) onChanged()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ScreenHeader(app.label, onBack = onBack)
        val shown = app.banner
        if (shown != null) {
            Image(
                bitmap = shown, contentDescription = app.label,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(horizontal = 6.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        if (status.isNotEmpty()) SettingsItem(onClick = { status = "" }, headlineContent = { Text(status) })
        val f = initialFocus()
        SettingsItem(
            onClick = onEdit,
            headlineContent = { Text(stringResource(if (state.second) R.string.art_edit else R.string.art_edit_new)) },
            supportingContent = { Text(stringResource(state.first)) },
            modifier = Modifier.focusRequester(f),
        )
        SettingsItem(onClick = onFind, headlineContent = { Text(stringResource(R.string.art_find_for_app)) })
        SettingsItem(onClick = { picker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }, headlineContent = { Text(stringResource(R.string.art_change_picture)) })
        if (state.second) SettingsItem(
            onClick = { confirmRestore = true },
            headlineContent = { Text(stringResource(R.string.art_restore)) },
            supportingContent = { Text(stringResource(R.string.art_restore_sub)) },
        )
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = stringResource(R.string.art_restore_confirm_title), body = stringResource(R.string.art_restore_confirm_body),
            confirm = stringResource(R.string.art_restore_confirm),
            onConfirm = {
                confirmRestore = false
                scope.launch { withContext(Dispatchers.IO) { library.restoreOriginal(app.pkg) }; onChanged() }
            },
            onDismiss = { confirmRestore = false },
        )
    }
}

@Composable
private fun OptionsScreen(onChanged: () -> Unit, onVersions: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ScreenHeader(stringResource(R.string.art_more), onBack = onBack)
        val f = initialFocus()
        if (status.isNotEmpty()) SettingsItem(onClick = { status = "" }, headlineContent = { Text(status) })
        SettingsItem(
            onClick = onVersions,
            headlineContent = { Text(stringResource(R.string.art_versions)) },
            supportingContent = { Text(stringResource(R.string.art_versions_sub)) },
            modifier = Modifier.focusRequester(f),
        )
        SettingsItem(
            onClick = { scope.launch { withContext(Dispatchers.IO) { ArtDiscovery.get(ctx).clearCache() }; status = ctx.getString(R.string.art_saved); onChanged() } },
            headlineContent = { Text(stringResource(R.string.art_clear_search_cache)) },
            supportingContent = { Text(stringResource(R.string.art_clear_search_cache_sub)) },
        )
    }
}
