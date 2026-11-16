package org.wishy.launcher.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.withContext
import org.wishy.launcher.R
import org.wishy.launcher.art.Wallpapers
import org.wishy.launcher.data.LauncherConfig
import java.io.File

/**
 * The first page of Settings, laid out like Android's own: the two things people change most (look and
 * wallpaper) are big banners, everyday pages are regular rows, and system items are small rows without a
 * description. Bigger means more important; nothing is cropped, the page scrolls.
 */
@Composable
internal fun SettingsHome(
    config: LauncherConfig,
    dark: Boolean,
    onThemes: () -> Unit,
    onBanners: () -> Unit,
    onWallpaper: () -> Unit,
    onDisplay: () -> Unit,
    onStatusBar: () -> Unit,
    onApps: () -> Unit,
    onCategories: () -> Unit,
    onLauncher: () -> Unit,
    onAndroidSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val themed = config.themeId != org.wishy.launcher.theme.ThemeStorage.CLASSIC_ID
    val caps = rememberThemeCaps(config.themeId)
    // A theme that ignores the user's wallpaper, or has no sections or status icons, would make these entries dead ends.
    val showWallpaper = !(themed && caps.wallpaper == "ignored")
    val showSections = !themed || caps.sections
    val showStatusBar = !themed || !caps.ownsStatusBar || caps.statusIcons || caps.clock || caps.date
    // A small copy of the wallpaper in use, for the banner. Off the main thread.
    val thumb by produceState<ImageBitmap?>(null, config.wallpaperId, config.useCustomWallpaper, dark) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (config.useCustomWallpaper) {
                    val f = File(context.filesDir, "wallpaper.jpg")
                    if (!f.exists()) null
                    else BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
                } else {
                    Wallpapers.byId(config.wallpaperId)?.let { Wallpapers.decode(context, it, dark, maxWidth = 480)?.asImageBitmap() }
                }
            }.getOrNull()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, color = Wish.Text)
        }
        val first = initialFocus()

        // ---- Big banners: the important ones ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
            Banner(
                title = stringResource(R.string.item_themes),
                sub = stringResource(R.string.item_themes_sub),
                modifier = Modifier.weight(1f).focusRequester(first),
                onClick = onThemes,
            ) {
                Box(Modifier.fillMaxSize().background(Wish.PrimaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Sym.Palette, null, tint = Wish.OnPrimaryContainer, modifier = Modifier.size(44.dp))
                }
            }
            if (showWallpaper) Banner(
                title = stringResource(R.string.item_wallpaper),
                sub = stringResource(R.string.item_wallpaper_sub),
                modifier = Modifier.weight(1f),
                onClick = onWallpaper,
            ) {
                val t = thumb
                if (t != null) {
                    Image(t, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(Wish.PrimaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Sym.Wallpaper, null, tint = Wish.OnPrimaryContainer, modifier = Modifier.size(44.dp))
                    }
                }
            }
        }

        // ---- Regular rows ----
        SectionLabel(stringResource(R.string.group_content))
        SettingsItem(
            onClick = onApps,
            headlineContent = { Text(stringResource(R.string.item_app)) },
            supportingContent = { Text(stringResource(R.string.item_app_sub), maxLines = 1) },
            leadingContent = { Icon(AppIcons.Apps, contentDescription = null) },
        )
        if (showSections) SettingsItem(
            onClick = onCategories,
            headlineContent = { Text(stringResource(R.string.item_sections)) },
            supportingContent = { Text(stringResource(R.string.item_sections_sub), maxLines = 1) },
            leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
        )
        SettingsItem(
            onClick = onBanners,
            headlineContent = { Text(stringResource(R.string.item_banners)) },
            supportingContent = { Text(stringResource(R.string.item_banners_sub), maxLines = 1) },
            leadingContent = { Icon(AppIcons.Image, contentDescription = null) },
        )

        SectionLabel(stringResource(R.string.group_appearance))
        SettingsItem(
            onClick = onDisplay,
            headlineContent = { Text(stringResource(R.string.item_display)) },
            supportingContent = { Text(stringResource(R.string.item_display_sub), maxLines = 1) },
            leadingContent = { Icon(AppIcons.Display, contentDescription = null) },
        )
        if (showStatusBar) SettingsItem(
            onClick = onStatusBar,
            headlineContent = { Text(stringResource(R.string.item_statusbar)) },
            supportingContent = { Text(stringResource(R.string.item_statusbar_sub), maxLines = 1) },
            leadingContent = { Icon(AppIcons.Wifi, contentDescription = null) },
        )

        // ---- Small rows: system, no description ----
        SectionLabel(stringResource(R.string.group_system))
        SettingsItem(
            onClick = onLauncher,
            headlineContent = { Text(stringResource(R.string.item_launcher_settings)) },
            leadingContent = { Icon(AppIcons.Apps, contentDescription = null) },
        )
        SettingsItem(
            onClick = onAndroidSettings,
            headlineContent = { Text(stringResource(R.string.item_android_settings)) },
            leadingContent = { Icon(AppIcons.Gear, contentDescription = null) },
        )
        SettingsItem(
            onClick = onAbout,
            headlineContent = { Text(stringResource(R.string.item_about_updates)) },
            leadingContent = { Icon(AppIcons.Info, contentDescription = null) },
        )
    }
}

/** A big card: a picture area on top (its content is yours), title and one line below. Border shows focus; the text never scales. */
@Composable
private fun Banner(
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    picture: @Composable () -> Unit,
) {
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
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Wish.Primary))),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(14.dp))) { picture() }
            Column(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Wish.TextDim, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}
