package org.wishy.launcher.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import org.wishy.launcher.R
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.wishy.launcher.data.LAYOUT_DOCK
import org.wishy.launcher.data.LAYOUT_GRID
import org.wishy.launcher.data.LauncherConfig

/** Title at the top of every Settings page: a back arrow in a tile (Back returns to the previous page) and the page name. */
@Composable
internal fun ScreenHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.padding(start = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            onClick = { onBack?.invoke() },
            enabled = onBack != null,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Wish.PrimaryContainer,
                focusedContainerColor = Wish.Primary,
                contentColor = Wish.OnPrimaryContainer,
                focusedContentColor = Wish.OnPrimary,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(Sym.ArrowBack, stringResource(R.string.back), tint = Wish.OnPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Wish.Text, maxLines = 1)
    }
}

/**
 * A flat miniature of the home screen that follows the Display settings: layout (carousel, grid or dock),
 * roundness and spacing. It animates when a setting changes, so the effect is visible before leaving the page.
 */
@Composable
internal fun LayoutPreview(config: LauncherConfig) {
    val radius = listOf(2, 4, 7, 10, 14)[config.cornerRadius.coerceIn(0, 4)].dp
    val gap = listOf(2, 3, 5, 7, 10)[config.spacing.coerceIn(0, 4)].dp
    val shape = RoundedCornerShape(radius)
    val tile = Wish.Primary.copy(alpha = 0.75f)
    val tileDim = Wish.Primary.copy(alpha = 0.35f)
    Box(
        Modifier
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(120.dp)
            .background(Wish.Surface, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .animateContentSize(),
    ) {
        when (config.layout) {
            LAYOUT_GRID -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(2) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(5) { Box(Modifier.weight(1f).fillMaxSize().background(tile, shape)) }
                    }
                }
            }
            LAYOUT_DOCK -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.width(60.dp).height(6.dp).background(tileDim, RoundedCornerShape(3.dp)))
                Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    repeat(6) { Box(Modifier.weight(1f).fillMaxSize().background(tile, shape)) }
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.width(60.dp).height(6.dp).background(tileDim, RoundedCornerShape(3.dp)))
                Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Box(Modifier.weight(1.5f).fillMaxSize().background(tile, shape))
                    repeat(3) { Box(Modifier.weight(1f).fillMaxSize().background(tileDim, shape)) }
                }
            }
        }
    }
}

/** Android-style on/off switch: a pill track and a thumb that slides. Show it as the trailing part of a toggle row. */
@Composable
internal fun SwitchPill(checked: Boolean) {
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, tween(160), label = "switchThumb")
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .background(if (checked) Wish.Primary else Wish.Outline, CircleShape),
    ) {
        Box(
            Modifier
                .padding(start = x, top = 2.dp)
                .size(24.dp)
                .background(if (checked) Wish.OnPrimary else Wish.TextDim, CircleShape),
        )
    }
}

/** A flat miniature of the status bar: time, date and the VPN icon, in glass or plain, as currently set. */
@Composable
internal fun StatusBarPreview(config: LauncherConfig) {
    val now = java.util.Date()
    val time = java.text.SimpleDateFormat(if (config.h24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(now)
    val dateFmt = org.wishy.launcher.data.DATE_FORMATS.getOrElse(config.dateFormat) { "" }
    val date = if (dateFmt.isEmpty()) "" else java.text.SimpleDateFormat(dateFmt, java.util.Locale.getDefault()).format(now)
    Box(
        Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxWidth().height(84.dp)
            .background(Wish.PrimaryContainer, RoundedCornerShape(16.dp)).padding(12.dp),
    ) {
        if (config.showStatusBar) {
            Row(
                Modifier.fillMaxWidth().height(40.dp)
                    .background(if (config.statusBarGlass) Wish.Surface.copy(alpha = 0.6f) else Wish.Surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(time, style = MaterialTheme.typography.titleMedium, color = Wish.Text)
                if (date.isNotEmpty()) Text(date, style = MaterialTheme.typography.bodyMedium, color = Wish.TextDim)
                Box(Modifier.weight(1f))
                if (config.showVpnButton) Icon(AppIcons.Vpn, null, tint = Wish.Primary, modifier = Modifier.size(20.dp))
                Icon(AppIcons.Wifi, null, tint = Wish.TextDim, modifier = Modifier.size(20.dp))
            }
        }
    }
}
