package org.wishy.launcher.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.wishy.launcher.Actions
import org.wishy.launcher.R
import java.net.Inet4Address
import java.net.NetworkInterface

/** One choice on the "pick your look" step: a few colours so the card and the backdrop can preview it. */
private class Look(val id: String, val name: String, val c1: Color, val c2: Color, val accent: Color, val corner: Int)

private val LOOKS = listOf(
    Look("wishy", "Wishy", Color(0xFF0B1B33), Color(0xFF0166FF), Color(0xFF66A3FF), 8),
    Look("classic", "Classic", Color(0xFF111827), Color(0xFF374151), Color(0xFF8AB4F8), 10),
)

private const val STEPS = 6

/**
 * First-launch wizard. Six short steps: welcome, pick a look (previewed live on the backdrop), make Wishy the
 * home app (including certified Google TV devices, where ADB is required), VPN button, online features, tips.
 * [onVpnChosen] receives the picked VPN package, or null to hide the VPN icon.
 */
@Composable
fun SetupWizard(
    onDone: () -> Unit,
    onVpnChosen: (String?) -> Unit,
    currentThemeId: String = "wishy",
    onThemeChosen: (String) -> Unit = {},
    onOnlineChosen: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var gtvPage by remember { mutableIntStateOf(0) } // carousel page on the Google-TV step
    var look by remember { mutableStateOf(LOOKS.firstOrNull { it.id == currentThemeId } ?: LOOKS[0]) }
    var defaultHome by remember { mutableStateOf(defaultHomePackage(context)) }
    val isDefault = defaultHome == context.packageName
    val isGoogleTv = remember {
        // "Amati" feature = certified Google TV experience (home app locked by Google)
        context.packageManager.hasSystemFeature("com.google.android.feature.AMATI_EXPERIENCE")
    }
    val ip = remember { deviceIp() }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { defaultHome = defaultHomePackage(context) }

    fun requestDefault() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !rm.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                val ok = runCatching {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }.isSuccess
                if (ok) return
            }
        }
        runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure {
                Actions.toast(context, context.getString(R.string.wizard_no_chooser))
            }
    }

    // The backdrop drifts slowly and re-tints to whichever look is highlighted, so choosing feels immediate.
    val c1 by animateColorAsState(look.c1, tween(450), label = "c1")
    val c2 by animateColorAsState(look.c2, tween(450), label = "c2")
    val accent by animateColorAsState(look.accent, tween(450), label = "accent")
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "driftValue",
    )
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseValue",
    )
    val backdrop = Brush.linearGradient(
        colors = listOf(c1, lerp(c1, c2, 0.35f + 0.5f * drift), c2),
        start = Offset(0f, 0f),
        end = Offset(1800f * (0.7f + 0.3f * drift), 1100f),
    )

    Box(Modifier.fillMaxSize().background(backdrop)) {
        // Soft accent discs: decoration that also tells you which look is selected.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 30.dp, bottom = 10.dp)
                .size(260.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .background(accent.copy(alpha = 0.16f), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 10.dp, top = 6.dp)
                .size(150.dp)
                .background(accent.copy(alpha = 0.10f), CircleShape)
        )

        Row(
            Modifier.fillMaxSize().padding(horizontal = 52.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            // ---- left: where you are ----
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_wishy_mark),
                        contentDescription = null,
                        modifier = Modifier.size(46.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
                    )
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
                Progress(step)
                Text(
                    stringResource(R.string.wizard_step, step + 1, STEPS),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                    label = "wizardTitle",
                ) { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(stepTitle(s)),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        stepSubtitle(s)?.let { sub ->
                            Text(
                                stringResource(sub),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }

            // ---- right: the thing to do ----
            Box(Modifier.weight(1.15f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(150)) { w -> dir * w / 6 } + fadeIn(tween(150))) togetherWith
                            (slideOutHorizontally(tween(100)) { w -> -dir * w / 6 } + fadeOut(tween(100)))
                    },
                    label = "wizardStep",
                ) { s ->
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (s) {
                            0 -> {
                                Feature(stringResource(R.string.wizard_feat_1), accent)
                                Feature(stringResource(R.string.wizard_feat_2), accent)
                                Feature(stringResource(R.string.wizard_feat_3), accent)
                                Body(stringResource(R.string.wizard_privacy_body))
                                NavRow(nextLabel = stringResource(R.string.next)) { step = 1 }
                            }
                            1 -> {
                                val selectedFocus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { delay(120); runCatching { selectedFocus.requestFocus() } }
                                val pick = { l: Look -> look = l; onThemeChosen(if (l.id == "classic") CLASSIC_THEME_ID else l.id) }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LOOKS.take(4).forEach { l ->
                                        LookCard(l, l.id == look.id, if (l.id == look.id) Modifier.focusRequester(selectedFocus) else Modifier) { pick(l) }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LOOKS.drop(4).forEach { l ->
                                        LookCard(l, l.id == look.id, if (l.id == look.id) Modifier.focusRequester(selectedFocus) else Modifier) { pick(l) }
                                    }
                                }
                                NavRow(backAction = { step = 0 }, nextLabel = stringResource(R.string.next), autoFocus = false) { step = 2 }
                            }
                            2 -> {
                                if (isDefault) {
                                    Body(stringResource(R.string.wizard_already_default))
                                    NavRow(backAction = { step = 1 }, nextLabel = stringResource(R.string.next)) { step = 3 }
                                } else if (isGoogleTv) {
                                    // Carousel: 3 short pages so it fits and stays centred at any DPI.
                                    when (gtvPage) {
                                        0 -> {
                                            Body(stringResource(R.string.wizard_gtv_intro))
                                            Body(stringResource(R.string.wizard_gtv_adb))
                                            CodeLine("adb connect ${ip ?: "<TV-IP>"}:5555")
                                            CodeLine("adb shell cmd package set-home-activity org.wishy.launcher/.MainActivity")
                                        }
                                        1 -> {
                                            Body(stringResource(R.string.wizard_gtv_overlay))
                                            CodeLine("adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME")
                                            CodeLine("adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx")
                                            CodeLine("adb shell pm disable-user --user 0 com.google.android.tvlauncher")
                                            CodeLine("adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith")
                                        }
                                        else -> {
                                            Body(stringResource(R.string.wizard_gtv_which))
                                            Body(stringResource(R.string.wizard_gtv_remap))
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Button(onClick = { requestDefault() }) { Text(stringResource(R.string.wizard_try_dialog)) }
                                                Button(onClick = { defaultHome = defaultHomePackage(context) }) { Text(stringResource(R.string.wizard_check_again)) }
                                            }
                                        }
                                    }
                                    PagerRow(
                                        page = gtvPage,
                                        count = 3,
                                        onPrev = { if (gtvPage > 0) gtvPage-- else step = 1 },
                                        onNext = { if (gtvPage < 2) gtvPage++ else step = 3 },
                                    )
                                } else {
                                    Body(stringResource(R.string.wizard_aosp_body))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(onClick = { requestDefault() }) { Text(stringResource(R.string.wizard_set_default)) }
                                        Button(onClick = { defaultHome = defaultHomePackage(context) }) { Text(stringResource(R.string.wizard_check_again)) }
                                    }
                                    Body(stringResource(R.string.wizard_no_dialog_hint))
                                    NavRow(backAction = { step = 1 }, nextLabel = stringResource(R.string.wizard_skip)) { step = 3 }
                                }
                            }
                            3 -> {
                                val vpns = remember { vpnApps(context) }
                                if (vpns.isEmpty()) {
                                    Body(stringResource(R.string.wizard_vpn_none))
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        vpns.forEach { (pkg, label) ->
                                            Button(onClick = { onVpnChosen(pkg); step = 4 }) { Text(label) }
                                        }
                                    }
                                }
                                NavRow(backAction = { step = 2 }, nextLabel = stringResource(R.string.wizard_vpn_skip)) { onVpnChosen(null); step = 4 }
                            }
                            4 -> {
                                val yesFocus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { delay(120); runCatching { yesFocus.requestFocus() } }
                                ChoiceCard(
                                    stringResource(R.string.wizard_online_yes),
                                    stringResource(R.string.wizard_online_yes_sub),
                                    Modifier.focusRequester(yesFocus),
                                ) { onOnlineChosen(true); step = 5 }
                                ChoiceCard(
                                    stringResource(R.string.wizard_online_no),
                                    stringResource(R.string.wizard_online_no_sub),
                                    Modifier,
                                ) { onOnlineChosen(false); step = 5 }
                                Row(Modifier.padding(top = 4.dp)) {
                                    Button(onClick = { step = 3 }) { Text(stringResource(R.string.back)) }
                                }
                            }
                            else -> {
                                Tip(stringResource(R.string.wizard_tip_1), stringResource(R.string.wizard_tip_1_sub), accent)
                                Tip(stringResource(R.string.wizard_tip_2), stringResource(R.string.wizard_tip_2_sub), accent)
                                Tip(stringResource(R.string.wizard_tip_3), stringResource(R.string.wizard_tip_3_sub), accent)
                                if (!isDefault) Body(stringResource(R.string.wizard_rerun_note))
                                NavRow(backAction = { step = 4 }, nextLabel = stringResource(R.string.wizard_start)) { onDone() }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun stepTitle(step: Int): Int = when (step) {
    0 -> R.string.wizard_welcome_title
    1 -> R.string.wizard_look_title
    2 -> R.string.wizard_home_title
    3 -> R.string.wizard_vpn_title
    4 -> R.string.wizard_online_title
    else -> R.string.wizard_done_title
}

private fun stepSubtitle(step: Int): Int? = when (step) {
    0 -> R.string.wizard_welcome_body
    1 -> R.string.wizard_look_sub
    3 -> R.string.wizard_vpn_body
    4 -> R.string.wizard_online_sub
    else -> null
}

/** Id the theme system uses for "no theme, classic layout". Mirrors ThemeStorage.CLASSIC_ID. */
private val CLASSIC_THEME_ID: String get() = org.wishy.launcher.theme.ThemeStorage.CLASSIC_ID

/* ------------------------------ pieces ------------------------------ */

@Composable
private fun Progress(step: Int) {
    Row(
        Modifier.padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(STEPS) { i ->
            val w by animateDpAsState(if (i == step) 36.dp else 12.dp, tween(150), label = "pill")
            Box(
                Modifier
                    .width(w)
                    .height(6.dp)
                    .background(if (i <= step) Color.White else Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun Feature(text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(12.dp).background(accent, CircleShape))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
private fun Tip(title: String, sub: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(5.dp).height(38.dp).background(accent, RoundedCornerShape(3.dp)))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun LookCard(look: Look, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(112.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, Color.White)) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Color.White)),
        ),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Brush.linearGradient(listOf(look.c1, look.c2)), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) {
                        Box(Modifier.size(20.dp).background(look.accent, RoundedCornerShape(look.corner.dp)))
                    }
                }
            }
            Text(look.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ChoiceCard(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.2f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White))),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
    )
}

@Composable
private fun CodeLine(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF8AB4F8),
        )
    }
}

@Composable
private fun NavRow(
    backAction: (() -> Unit)? = null,
    nextLabel: String,
    autoFocus: Boolean = true,
    nextAction: () -> Unit,
) {
    // Focus the primary (next) button on entry so every step opens with a button selected.
    val nextFocus = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { nextFocus.requestFocus() }
        }
    }
    Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (backAction != null) {
            Button(onClick = backAction) { Text(stringResource(R.string.back)) }
        }
        Button(onClick = nextAction, modifier = Modifier.focusRequester(nextFocus)) {
            Text(nextLabel)
        }
    }
}

/** Carousel controls: Prev / "n / N" / Next. On the ends they step out. */
@Composable
private fun PagerRow(page: Int, count: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val nextFocus = remember { FocusRequester() }
    LaunchedEffect(page) {
        delay(80)
        runCatching { nextFocus.requestFocus() }
    }
    Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onPrev) {
            Text(if (page > 0) "◄" else stringResource(R.string.back))
        }
        Text(
            "${page + 1} / $count",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Button(onClick = onNext, modifier = Modifier.focusRequester(nextFocus)) {
            Text(if (page < count - 1) "►" else stringResource(R.string.wizard_skip))
        }
    }
}

/* ------------------------------ helpers ------------------------------ */

fun defaultHomePackage(context: Context): String? {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager
        .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
}

private fun deviceIp(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
        ?.hostAddress
}.getOrNull()

/** Installed VPN apps = anything implementing android.net.VpnService. */
private fun vpnApps(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    return pm.queryIntentServices(Intent("android.net.VpnService"), 0)
        .mapNotNull { ri ->
            val si = ri.serviceInfo ?: return@mapNotNull null
            if (si.packageName == context.packageName) return@mapNotNull null
            val label = runCatching { ri.loadLabel(pm).toString() }.getOrNull() ?: si.packageName
            si.packageName to label
        }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
}
