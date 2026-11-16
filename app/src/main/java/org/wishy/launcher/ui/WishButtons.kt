package org.wishy.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.wishy.launcher.R

/** Big flat button. [filled] = the main action (primary blue); otherwise a quiet tonal button. */
@Composable
internal fun WishButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(if (compact) 40.dp else 48.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (filled) Wish.Primary else Wish.SurfaceHigh,
            contentColor = if (filled) Wish.OnPrimary else Wish.Text,
            focusedContainerColor = if (filled) Wish.OnPrimaryContainer else Wish.Outline,
            focusedContentColor = if (filled) Wish.OnPrimary else Wish.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Wish.Text))),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 16.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, null, modifier = Modifier.size(22.dp))
            Text(label, fontSize = if (compact) 15.sp else 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = WishFont, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (trailingIcon != null) Icon(trailingIcon, null, modifier = Modifier.size(22.dp))
        }
    }
}

