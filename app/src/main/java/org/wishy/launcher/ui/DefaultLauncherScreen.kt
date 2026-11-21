package org.wishy.launcher.ui

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import org.wishy.launcher.Actions
import org.wishy.launcher.HomeLauncher
import org.wishy.launcher.R

/**
 * Settings > Default launcher. Shows what Android really says about the HOME app (never a guess), and gives the
 * supported ways to change it. The status is read again every time the screen comes back to the foreground, so
 * returning from Android's own Home app page shows the new state straight away.
 */
@Composable
internal fun DefaultLauncherScreen(onGuide: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var diag by remember { mutableStateOf(HomeLauncher.diagnose(context)) }
    fun refresh() { diag = HomeLauncher.diagnose(context) }

    // Refresh whenever we return from Android's settings or from the role dialog.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    fun reportOpened(o: HomeLauncher.Opened) {
        val msg = when (o) {
            HomeLauncher.Opened.HOME_SETTINGS -> R.string.toast_dl_home_settings
            HomeLauncher.Opened.DEFAULT_APPS -> R.string.toast_dl_default_apps
            HomeLauncher.Opened.GENERAL_SETTINGS -> R.string.toast_dl_general
            HomeLauncher.Opened.NONE -> R.string.toast_dl_failed
        }
        Actions.toast(context, context.getString(msg))
    }

    var overrideOn by remember { mutableStateOf(HomeLauncher.overrideEnabled(context)) }
    var allApps by remember { mutableStateOf(HomeLauncher.catchAllAppsKey(context)) }
    val state = diag.state
    val other = diag.preferredOther

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ScreenHeader(stringResource(R.string.dl_title), onBack = onBack)
        val f = initialFocus()

        // ---- status ----
        val (head, sub) = when (state) {
            HomeLauncher.State.DEFAULT -> stringResource(R.string.dl_status_default) to stringResource(R.string.dl_status_default_sub)
            HomeLauncher.State.OTHER -> stringResource(R.string.dl_status_other) to
                stringResource(R.string.dl_status_other_sub, diag.otherLabel ?: diag.resolvedPackage ?: "?")
            HomeLauncher.State.CHOOSER -> stringResource(R.string.dl_status_chooser) to stringResource(R.string.dl_status_chooser_sub)
        }
        SettingsItem(
            onClick = { refresh() },
            modifier = Modifier.focusRequester(f),
            headlineContent = { Text(head) },
            supportingContent = { Text(sub, maxLines = 3) },
            leadingContent = { Icon(if (state == HomeLauncher.State.DEFAULT) Sym.Check else Sym.Home, contentDescription = null) },
        )
        if (!diag.selfListed) Text(stringResource(R.string.dl_self_missing), color = Wish.Text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
        if (diag.googleTv && state != HomeLauncher.State.DEFAULT) {
            Text(stringResource(R.string.dl_gtv_note), color = Wish.TextDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }

        // ---- actions ----
        if (state != HomeLauncher.State.DEFAULT) {
            SettingsItem(
                onClick = {
                    val req = HomeLauncher.roleRequestIntent(context)
                    if (req != null) runCatching { roleLauncher.launch(req) }.onFailure { reportOpened(HomeLauncher.openHomeSettings(context)) }
                    else reportOpened(HomeLauncher.openHomeSettings(context))
                },
                headlineContent = { Text(stringResource(R.string.dl_set)) },
                supportingContent = { Text(stringResource(R.string.dl_set_sub), maxLines = 2) },
                leadingContent = { Icon(Sym.Home, contentDescription = null) },
            )
        }
        SettingsItem(
            onClick = { reportOpened(HomeLauncher.openHomeSettings(context)) },
            headlineContent = { Text(stringResource(R.string.dl_open_settings)) },
            supportingContent = { Text(stringResource(R.string.dl_open_settings_sub), maxLines = 2) },
            leadingContent = { Icon(AppIcons.Gear, contentDescription = null) },
        )
        // Remove default / change launcher: only meaningful while Wishy is the default or a choice is stored.
        SettingsItem(
            onClick = { reportOpened(HomeLauncher.changeDefault(context)) },
            headlineContent = { Text(stringResource(R.string.dl_change)) },
            supportingContent = { Text(stringResource(R.string.dl_change_sub), maxLines = 2) },
            leadingContent = { Icon(AppIcons.Delete, contentDescription = null) },
        )
        if (other != null) {
            SettingsItem(
                onClick = { if (!HomeLauncher.openOtherLauncher(context, other)) Actions.toast(context, context.getString(R.string.toast_dl_switch_failed)) },
                headlineContent = { Text(stringResource(R.string.dl_switch_now, other.label)) },
                supportingContent = { Text(stringResource(R.string.dl_switch_now_sub), maxLines = 2) },
                leadingContent = { Icon(Sym.ArrowForward, contentDescription = null) },
            )
        } else {
            SettingsItem(
                onClick = {},
                headlineContent = { Text(stringResource(R.string.dl_no_other)) },
                supportingContent = { Text(stringResource(R.string.dl_no_other_sub), maxLines = 3) },
                leadingContent = { Icon(AppIcons.Info, contentDescription = null) },
            )
        }

        // ---- Home button override (accessibility service) ----
        SectionLabel(stringResource(R.string.hk_section))
        val svcOn = diag.serviceOn
        SettingsItem(
            onClick = { if (!HomeLauncher.openAccessibilitySettings(context)) Actions.toast(context, context.getString(R.string.toast_hk_no_access)) },
            headlineContent = { Text(stringResource(if (svcOn && overrideOn) R.string.hk_status_on else R.string.hk_status_off)) },
            supportingContent = { Text(stringResource(if (svcOn && overrideOn) R.string.hk_status_on_sub else R.string.hk_status_off_sub), maxLines = 3) },
            leadingContent = { Icon(if (svcOn && overrideOn) Sym.Check else Sym.Home, contentDescription = null) },
        )
        SettingsItem(
            onClick = { if (!HomeLauncher.openAccessibilitySettings(context)) Actions.toast(context, context.getString(R.string.toast_hk_no_access)) },
            headlineContent = { Text(stringResource(R.string.hk_open_access)) },
            supportingContent = { Text(stringResource(R.string.hk_open_access_sub), maxLines = 2) },
            leadingContent = { Icon(AppIcons.Gear, contentDescription = null) },
        )
        if (svcOn) {
            SettingsItem(
                onClick = { overrideOn = !overrideOn; HomeLauncher.setOverrideEnabled(context, overrideOn) },
                headlineContent = { Text(stringResource(if (overrideOn) R.string.hk_pause else R.string.hk_resume)) },
                supportingContent = { Text(stringResource(if (overrideOn) R.string.hk_pause_sub else R.string.hk_resume_sub), maxLines = 2) },
                leadingContent = { Icon(AppIcons.Info, contentDescription = null) },
            )
            SettingsItem(
                onClick = { allApps = !allApps; HomeLauncher.setCatchAllAppsKey(context, allApps) },
                headlineContent = { Text(stringResource(if (allApps) R.string.hk_allapps_on else R.string.hk_allapps_off)) },
                supportingContent = { Text(stringResource(R.string.hk_allapps_sub), maxLines = 2) },
                leadingContent = { Icon(AppIcons.Info, contentDescription = null) },
            )
        }

        // ---- proof that Home reaches Wishy ----
        SectionLabel(stringResource(R.string.dl_section_proof))
        val last = diag.lastHomePressAt
        SettingsItem(
            onClick = { refresh() },
            headlineContent = { Text(stringResource(R.string.dl_last_home)) },
            supportingContent = {
                Text(
                    if (last > 0L) DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString()
                    else stringResource(R.string.dl_last_home_never),
                    maxLines = 2,
                )
            },
            leadingContent = { Icon(AppIcons.Info, contentDescription = null) },
        )
        if (state == HomeLauncher.State.DEFAULT && last == 0L) {
            Text(stringResource(R.string.dl_last_home_warn), color = Wish.TextDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        SettingsItem(
            onClick = onGuide,
            headlineContent = { Text(stringResource(R.string.dl_open_guide)) },
            supportingContent = { Text(stringResource(R.string.dl_open_guide_sub), maxLines = 2) },
            leadingContent = { Icon(AppIcons.Apps, contentDescription = null) },
        )

        // ---- what is installed ----
        if (diag.handlers.isNotEmpty()) {
            SectionLabel(stringResource(R.string.dl_section_installed))
            for (h in diag.handlers) {
                val tags = buildList {
                    if (h.pkg == diag.resolvedPackage || (h.isSelf && state == HomeLauncher.State.DEFAULT)) add(stringResource(R.string.dl_tag_current))
                    if (h.isSystem) add(stringResource(R.string.dl_tag_system))
                }.joinToString(" · ")
                SettingsItem(
                    onClick = { if (!h.isSelf && !HomeLauncher.openOtherLauncher(context, h)) Actions.toast(context, context.getString(R.string.toast_dl_switch_failed)) },
                    headlineContent = { Text(h.label, maxLines = 1) },
                    supportingContent = { Text(listOf(h.pkg, tags).filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 1) },
                    leadingContent = { Icon(Sym.Home, contentDescription = null) },
                )
            }
        }
    }
}
