package org.wishy.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.tv.material3.Button
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wishy.launcher.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import org.wishy.launcher.data.ConfigStore
import org.wishy.launcher.data.LauncherConfig
import org.wishy.launcher.theme.ThemeEntry
import org.wishy.launcher.theme.ThemeStorage
import org.wishy.theme.core.ColorSchemeName
import org.wishy.theme.core.LoadContext

private val SCALE_STEPS = listOf(70, 85, 100, 115, 130)

/** Theme picker and theme-related options. Every row works with the D-pad alone. */
@Composable
internal fun ThemesScreen(
    config: LauncherConfig,
    store: ConfigStore,
    onCredits: () -> Unit,
    onGetMore: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val storage = remember { ThemeStorage.get(context) }
    val scope = rememberCoroutineScope()
    val caps = rememberThemeCaps(config.themeId)
    fun update(transform: (LauncherConfig) -> LauncherConfig) { scope.launch { store.update(transform) } }

    var listTick by remember { mutableIntStateOf(0) }
    val entries by produceState<List<ThemeEntry>>(emptyList(), config.themeId, listTick) {
        value = withContext(Dispatchers.IO) { runCatching { storage.list() }.getOrDefault(emptyList()) }
    }
    var errorTick by remember { mutableIntStateOf(0) }
    val lastError = remember(errorTick, config.themeId) { storage.lastError }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.item_themes), onBack = onBack)
        val f = initialFocus()
        SettingsItem(
            selected = false, onClick = onGetMore,
            headlineContent = { Text(stringResource(R.string.themes_get_more)) },
            supportingContent = { Text(stringResource(R.string.themes_get_more_sub)) },
        )
        SectionLabel(stringResource(R.string.themes_installed))
        val dark = when (config.themeMode) {
            1 -> false
            2 -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        } || config.themeId == ThemeStorage.CLASSIC_ID
        val peeks by produceState<Map<String, ThemePeek?>>(emptyMap(), entries, dark) {
            value = withContext(Dispatchers.IO) { entries.associate { it.summary.id to peekTheme(storage, it.summary.id, dark) } }
        }
        // One card per look, two to a row: a flat preview in the theme's own colours, its name, and whether your wallpaper shows.
        val cards = entries.map { CardItem(it.summary.id, it.summary.name, peeks[it.summary.id]) } +
            CardItem(ThemeStorage.CLASSIC_ID, stringResource(R.string.themes_classic), null)
        cards.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { col, c ->
                    ThemeCard(
                        item = c,
                        selected = config.themeId == c.id,
                        modifier = Modifier.weight(1f).let { if (row == 0 && col == 0) it.focusRequester(f) else it },
                    ) { update { it.copy(themeId = c.id) } }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
            Box(Modifier.height(10.dp))
        }
        val removable = entries.filter { it.installed && !it.bundled }
        if (removable.isNotEmpty()) {
            SectionLabel(stringResource(R.string.themes_manage))
            removable.forEach { e ->
                SettingsItem(
                    selected = false,
                    onClick = {
                        scope.launch {
                            if (config.themeId == e.summary.id) store.update { it.copy(themeId = ThemeStorage.DEFAULT_ID) }
                            withContext(Dispatchers.IO) { storage.remove(e.summary.id) }
                            listTick++
                        }
                    },
                    headlineContent = { Text(e.summary.name) },
                    supportingContent = { Text(stringResource(R.string.themes_remove)) },
                )
            }
        }

        SectionLabel(stringResource(R.string.themes_options))
        SelectorRow(
            label = stringResource(R.string.themes_mode),
            value = listOf(stringResource(R.string.themes_mode_theme), stringResource(R.string.themes_mode_light), stringResource(R.string.themes_mode_dark))[config.themeMode.coerceIn(0, 2)],
            steps = 3,
            current = config.themeMode.coerceIn(0, 2),
            onSelect = { v -> update { it.copy(themeMode = v) } },
        )
        SelectorRow(
            label = stringResource(R.string.themes_scale),
            value = "${SCALE_STEPS[SCALE_STEPS.indexOfFirst { it >= config.themeScalePct }.let { if (it < 0) SCALE_STEPS.lastIndex else it }]}%",
            steps = SCALE_STEPS.size,
            current = SCALE_STEPS.indexOfFirst { it >= config.themeScalePct }.let { if (it < 0) SCALE_STEPS.lastIndex else it },
            onSelect = { v -> update { it.copy(themeScalePct = SCALE_STEPS[v]) } },
        )
        SelectorRow(
            label = stringResource(R.string.themes_animations),
            value = listOf(stringResource(R.string.anim_full), stringResource(R.string.anim_reduced), stringResource(R.string.anim_off))[config.animations.coerceIn(0, 2)],
            description = stringResource(R.string.themes_animations_sub),
            steps = 3,
            current = config.animations.coerceIn(0, 2),
            onSelect = { v -> update { it.copy(animations = v) } },
        )
        if (caps.clock) SettingsItem(
            selected = false,
            onClick = { update { it.copy(showClock = !it.showClock) } },
            headlineContent = { Text(stringResource(R.string.themes_clock)) },
            trailingContent = { SwitchPill(config.showClock) },
        )
        if (caps.sectionsOptional) SettingsItem(
            selected = false,
            onClick = { update { it.copy(themeSections = !it.themeSections) } },
            headlineContent = { Text(stringResource(R.string.themes_sections)) },
            supportingContent = { Text(stringResource(R.string.themes_sections_sub)) },
            trailingContent = { SwitchPill(config.themeSections) },
        )
        if (caps.date) SelectorRow(
            label = stringResource(R.string.themes_date),
            value = org.wishy.launcher.data.DATE_FORMATS.getOrElse(config.dateFormat) { "" }.ifEmpty { stringResource(R.string.themes_date_off) },
            steps = org.wishy.launcher.data.DATE_FORMATS.size,
            current = config.dateFormat.coerceIn(0, org.wishy.launcher.data.DATE_FORMATS.lastIndex),
            onSelect = { v -> update { it.copy(dateFormat = v) } },
        )

        SectionLabel(stringResource(R.string.themes_network))
        SettingsItem(
            selected = false,
            onClick = { update { it.copy(offlineMode = !it.offlineMode) } },
            headlineContent = { Text(stringResource(R.string.themes_offline)) },
            supportingContent = { Text(stringResource(R.string.themes_offline_sub)) },
            trailingContent = { SwitchPill(config.offlineMode) },
        )

        SectionLabel(stringResource(R.string.themes_info))
        SettingsItem(
            selected = false,
            onClick = onCredits,
            headlineContent = { Text(stringResource(R.string.themes_credits)) },
            supportingContent = { Text(stringResource(R.string.themes_credits_sub)) },
        )

        SectionLabel(stringResource(R.string.themes_recovery))
        if (lastError.isNotEmpty()) {
            SettingsItem(
                selected = false,
                onClick = { storage.clearError(); errorTick++ },
                headlineContent = { Text(stringResource(R.string.themes_last_problem)) },
                supportingContent = { Text(lastError) },
                trailingContent = { Text(stringResource(R.string.themes_dismiss)) },
            )
        }
        SettingsItem(
            selected = false,
            onClick = {
                storage.clearError(); errorTick++
                update { it.copy(themeId = ThemeStorage.DEFAULT_ID, themeMode = 0, themeScalePct = 100, animations = 0, showClock = true) }
            },
            headlineContent = { Text(stringResource(R.string.themes_reset)) },
            supportingContent = { Text(stringResource(R.string.themes_reset_sub)) },
        )
    }
}

/** Lists the credits and licences the current theme declares for its files. */
@Composable
internal fun ThemeCreditsScreen(config: LauncherConfig, onBack: () -> Unit) {
    val context = LocalContext.current
    val storage = remember { ThemeStorage.get(context) }
    val loaded by produceState<org.wishy.theme.core.LoadedTheme?>(null, config.themeId) {
        val dm = context.resources.displayMetrics
        val ctx = LoadContext(dm.widthPixels.toFloat() / dm.heightPixels, dm.heightPixels, ColorSchemeName.DARK, false)
        value = withContext(Dispatchers.IO) { storage.resolve(config.themeId, ctx).theme }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(stringResource(R.string.themes_credits), onBack = onBack)
        val f = initialFocus()
        val doc = loaded?.doc
        if (doc == null) {
            SettingsItem(
                selected = false, onClick = onBack,
                headlineContent = { Text(stringResource(R.string.themes_credits_none)) },
                modifier = Modifier.focusRequester(f),
            )
        } else {
            SettingsItem(
                selected = false, onClick = {},
                headlineContent = { Text(doc.name) },
                supportingContent = { Text("${doc.author}  ·  ${doc.version}  ·  ${doc.license}") },
                modifier = Modifier.focusRequester(f),
            )
            doc.credits.forEach { c ->
                SettingsItem(
                    selected = false, onClick = {},
                    headlineContent = { Text(c.title ?: c.file) },
                    supportingContent = {
                        Text(listOfNotNull(c.author, c.license, c.source, c.notes).joinToString("\n"))
                    },
                )
            }
            if (doc.credits.isEmpty()) {
                SettingsItem(
                    selected = false, onClick = {},
                    headlineContent = { Text(stringResource(R.string.themes_credits_empty)) },
                )
            }
        }
    }
}

/* ------------------------------ theme cards ------------------------------ */

private class CardItem(val id: String, val name: String, val peek: ThemePeek?)

/** The few colours of a theme that make a recognisable preview, and its wallpaper rule ("allowed", "ignored" or "required"). */
internal class ThemePeek(val bg: Color, val surface: Color, val primary: Color, val text: Color, val wallpaper: String, val image: java.io.File?)

internal fun peekTheme(storage: ThemeStorage, id: String, dark: Boolean): ThemePeek? = runCatching {
    val dir = storage.dirFor(id) ?: return@runCatching null
    val root = org.json.JSONObject(java.io.File(dir, "theme.json").readText())
    val cs = root.getJSONObject("colorSchemes").getJSONObject(if (dark) "dark" else "light")
    fun c(k: String) = Color(android.graphics.Color.parseColor(cs.getString(k)))
    val bgObj = root.optJSONObject("background")
    // The theme's own background picture, in the tone being shown (its file names carry "dark" or "light").
    val rel = bgObj?.optString("image", "").orEmpty()
    val image = if (rel.isEmpty() || rel.contains("..")) null else {
        val tone = if (dark) "dark" else "light"
        listOf(rel.replace(if (dark) "light" else "dark", tone), rel).map { java.io.File(dir, it) }.firstOrNull { it.isFile }
    }
    ThemePeek(
        bg = c("background"), surface = c("surface"), primary = c("primary"), text = c("onSurface"),
        wallpaper = bgObj?.optString("userWallpaper", "allowed") ?: "allowed",
        image = image,
    )
}.getOrNull()

@Composable
private fun ThemeCard(item: CardItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = item.peek
    val pic by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, p?.image) {
        value = withContext(Dispatchers.IO) {
            val f = p?.image ?: return@withContext null
            runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(f.path, o)
                var s = 1
                while (o.outWidth / (s * 2) >= 480) s *= 2
                android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = s })
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    val bg = p?.bg ?: Color(0xFF111827)
    val tile = p?.surface ?: Color(0xFF374151)
    val bar = p?.primary ?: Color(0xFF8AB4F8)
    val line = (p?.text ?: Color.White).copy(alpha = 0.7f)
    val wpText = when (p?.wallpaper) {
        "ignored" -> stringResource(R.string.themes_wp_fixed)
        "required" -> stringResource(R.string.themes_wp_required)
        else -> stringResource(R.string.themes_wp_yours)
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Wish.Surface,
            focusedContainerColor = Wish.SurfaceHigh,
            contentColor = Wish.Text,
            focusedContentColor = Wish.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, Wish.Primary)) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Wish.Primary)),
        ),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(14.dp)).background(bg)) {
                pic?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxSize().padding(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(44.dp).height(5.dp).background(bar, RoundedCornerShape(3.dp)))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { Box(Modifier.width(38.dp).height(26.dp).background(tile, RoundedCornerShape(6.dp))) }
                    }
                    Box(Modifier.width(70.dp).height(4.dp).background(line, RoundedCornerShape(2.dp)))
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(Modifier.size(24.dp).background(Wish.Primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Sym.Check, null, tint = Wish.OnPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                }
            }
            Column(Modifier.padding(horizontal = 6.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Sym.Wallpaper, null, tint = Wish.TextDim, modifier = Modifier.size(14.dp))
                    Text(wpText, style = MaterialTheme.typography.bodySmall, color = Wish.TextDim, maxLines = 1)
                }
            }
        }
    }
}


/**
 * What the active theme can actually show. Settings that would do nothing under it are hidden, so no switch on
 * screen is ever a dead button. Read from the theme's own file; the classic layouts support everything.
 */
internal class ThemeCaps(
    val clock: Boolean,
    val date: Boolean,
    val sections: Boolean,
    /** Sections exist and the user may switch them on or off (false when the theme keeps them always on). */
    val sectionsOptional: Boolean,
    val statusIcons: Boolean,
    /** The theme hides the launcher's status bar (and may draw its own icons). */
    val ownsStatusBar: Boolean,
    /** Some shape has a corner the roundness setting can change. */
    val corners: Boolean,
    val labels: Boolean,
    /** "allowed", "ignored" or "required": whether the user's wallpaper choice shows. */
    val wallpaper: String,
) {
    companion object {
        val Classic = ThemeCaps(true, true, true, true, false, false, true, true, "allowed")
    }
}

private fun roundedCorner(shape: org.json.JSONObject?): Boolean {
    val kind = shape?.optString("kind", "rounded") ?: "rounded"
    val radius = shape?.optDouble("radius", 12.0) ?: 12.0
    return kind != "rect" && radius > 0.0
}

internal fun scanCaps(storage: ThemeStorage, id: String): ThemeCaps? = runCatching {
    val dir = storage.dirFor(id) ?: return@runCatching null
    val root = org.json.JSONObject(java.io.File(dir, "theme.json").readText())
    val types = HashSet<String>()
    var corners = false
    var sectionsAlways = false
    fun walk(n: org.json.JSONObject) {
        val type = n.optString("type")
        types += type
        if (type == "sections" && n.optBoolean("always", false)) sectionsAlways = true
        if (type == "shape" && roundedCorner(n.optJSONObject("shape"))) corners = true
        val kids = n.optJSONArray("children") ?: return
        for (i in 0 until kids.length()) walk(kids.getJSONObject(i))
    }
    walk(root.getJSONObject("screens").getJSONObject("home").getJSONObject("root"))
    var labels = false
    root.optJSONObject("cardStyles")?.let { cards ->
        for (k in cards.keys()) {
            val card = cards.getJSONObject(k)
            if (roundedCorner(card.optJSONObject("shape"))) corners = true
            if ((card.optJSONObject("label")?.optString("position", "below") ?: "below") != "none") labels = true
        }
    }
    ThemeCaps(
        clock = "clock" in types,
        date = "date" in types,
        sections = "sections" in types,
        sectionsOptional = "sections" in types && !sectionsAlways,
        statusIcons = "statusIcons" in types,
        ownsStatusBar = root.optJSONObject("chrome")?.optString("statusBar", "launcher") == "theme",
        corners = corners,
        labels = labels,
        wallpaper = root.optJSONObject("background")?.optString("userWallpaper", "allowed") ?: "allowed",
    )
}.getOrNull()

@Composable
internal fun rememberThemeCaps(themeId: String): ThemeCaps {
    val context = LocalContext.current
    val storage = remember { ThemeStorage.get(context) }
    val caps by produceState(ThemeCaps.Classic, themeId) {
        value = if (themeId == ThemeStorage.CLASSIC_ID) ThemeCaps.Classic
        else withContext(Dispatchers.IO) { scanCaps(storage, themeId) } ?: ThemeCaps.Classic
    }
    return caps
}
