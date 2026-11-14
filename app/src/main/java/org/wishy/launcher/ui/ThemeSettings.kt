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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val storage = remember { ThemeStorage.get(context) }
    val scope = rememberCoroutineScope()
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
        Text(
            stringResource(R.string.item_themes),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        SectionLabel(stringResource(R.string.themes_installed))
        val f = initialFocus()
        entries.forEachIndexed { i, e ->
            SettingsItem(
                selected = false,
                onClick = { update { it.copy(themeId = e.summary.id) } },
                headlineContent = { Text(e.summary.name) },
                supportingContent = {
                    Text(
                        listOf(e.summary.author, e.summary.version, if (e.bundled) stringResource(R.string.themes_builtin) else "")
                            .filter { it.isNotEmpty() }.joinToString("  ·  ")
                    )
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (e.installed && !e.bundled) {
                            Button(onClick = {
                                scope.launch {
                                    if (config.themeId == e.summary.id) store.update { it.copy(themeId = ThemeStorage.DEFAULT_ID) }
                                    withContext(Dispatchers.IO) { storage.remove(e.summary.id) }
                                    listTick++
                                }
                            }) { Text(stringResource(R.string.themes_remove)) }
                        }
                        CheckMark(checked = config.themeId == e.summary.id)
                    }
                },
                modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
            )
        }
        SettingsItem(
            selected = false,
            onClick = { update { it.copy(themeId = ThemeStorage.CLASSIC_ID) } },
            headlineContent = { Text(stringResource(R.string.themes_classic)) },
            supportingContent = { Text(stringResource(R.string.themes_classic_sub)) },
            trailingContent = { CheckMark(checked = config.themeId == ThemeStorage.CLASSIC_ID) },
            modifier = if (entries.isEmpty()) Modifier.focusRequester(f) else Modifier,
        )

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
        SettingsItem(
            selected = false,
            onClick = { update { it.copy(showClock = !it.showClock) } },
            headlineContent = { Text(stringResource(R.string.themes_clock)) },
            trailingContent = { CheckMark(checked = config.showClock) },
        )
        SelectorRow(
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
            trailingContent = { CheckMark(checked = config.offlineMode) },
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
        Text(
            stringResource(R.string.themes_credits),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
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
