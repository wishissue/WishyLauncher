package org.wishy.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wishy.launcher.R
import org.wishy.launcher.art.BannerRenderer
import org.wishy.theme.core.BannerSpec
import org.wishy.theme.core.SurpriseDesign
import kotlin.random.Random

/**
 * "Surprise me": shows a handful of random banners made from the logo being edited. The person can look at them,
 * generate a new set, pick a favourite and use it (it is then saved with Done like any manual design).
 */
@Composable
internal fun SurpriseDialog(logo: EditorLogo, onUse: (BannerSpec) -> Unit, onDismiss: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    var picked by remember { mutableIntStateOf(0) }
    val specs = remember(round) {
        SurpriseDesign.variations(COUNT, Random(System.nanoTime() + round), logo.image.mono, logo.brandHex, logo.dominant, logo.image.aspect)
    }
    val previews by produceState<List<Bitmap>>(emptyList(), specs) {
        value = withContext(Dispatchers.Default) { specs.map { BannerRenderer.render(it, logo.image, null, 480, 270) } }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            ((view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window)
                ?.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
        }
        Column(Modifier.fillMaxSize().background(Wish.Bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.sp_title), color = Wish.Text, fontSize = 20.sp)
            Text(stringResource(R.string.sp_hint), color = Wish.TextDim, fontSize = 13.sp)
            val first = initialFocus()
            for (row in 0 until COUNT / COLS) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (col in 0 until COLS) {
                        val i = row * COLS + col
                        Surface(
                            onClick = { picked = i },
                            modifier = Modifier.weight(1f).aspectRatio(16f / 9f).let { if (i == 0) it.focusRequester(first) else it },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            border = ClickableSurfaceDefaults.border(
                                border = if (picked == i) Border(BorderStroke(3.dp, Wish.Primary)) else Border.None,
                                focusedBorder = Border(BorderStroke(3.dp, Wish.Text)),
                            ),
                        ) {
                            val p = previews.getOrNull(i)
                            Box(Modifier.fillMaxSize()) {
                                if (p != null) Image(p.asImageBitmap(), contentDescription = stringResource(R.string.sp_option, i + 1), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            }
                        }
                    }
                }
            }
            ButtonRow {
                WishButton(stringResource(R.string.sp_use)) { onUse(specs[picked.coerceIn(0, specs.lastIndex)]) }
                WishButton(stringResource(R.string.sp_again), filled = false) { round++; picked = 0 }
                WishButton(stringResource(R.string.sp_cancel), filled = false) { onDismiss() }
            }
        }
    }
}

private const val COLS = 3
private const val COUNT = 6
