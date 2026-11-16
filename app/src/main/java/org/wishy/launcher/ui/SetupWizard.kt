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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.wishy.launcher.Actions
import org.wishy.launcher.R
import java.net.Inet4Address
import java.net.NetworkInterface

/** One choice on the "pick your look" step. [bg], [tile] and [accent] draw a small flat preview of the home screen. */
private class Look(val id: String, val name: String, val bg: Color, val tile: Color, val accent: Color, val corner: Int)

private val LOOKS = listOf(
    Look("wishy-home", "Wishy", Color(0xFF080B12), Color(0xFF111725), Color(0xFFA8C8FF), 10),
    Look("classic", "Classic", Color(0xFF111827), Color(0xFF374151), Color(0xFF8AB4F8), 4),
)

private const val STEPS = 6

private val StepIcons: List<() -> ImageVector> = listOf(
    { Sym.RocketLaunch }, { Sym.Palette }, { Sym.Home }, { Sym.VpnKey }, { Sym.Cloud }, { Sym.Check },
)

/**
 * First-launch greeter. Six steps, one idea each: welcome, pick a look, make Wishy the home app, VPN button,
 * online features, done. Big icon, big title, a little text, big buttons. Flat WishIssue surfaces, no gradients.
 * [onVpnChosen] receives the picked VPN package, or null to hide the VPN icon.
 */
@Composable
fun SetupWizard(
    onDone: () -> Unit,
    onVpnChosen: (String?) -> Unit,
    currentThemeId: String = org.wishy.launcher.theme.ThemeStorage.DEFAULT_ID,
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
            .onFailure { Actions.toast(context, context.getString(R.string.wizard_no_chooser)) }
    }

    Box(Modifier.fillMaxSize().background(Wish.Bg)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Progress(step)
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { w -> dir * w / 5 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> -dir * w / 5 } + fadeOut(tween(180)))
                },
                contentAlignment = Alignment.Center,
                label = "wizardStep",
            ) { s ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Hero(s)
                    Text(
                        stringResource(stepTitle(s)),
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = WishFont,
                        color = Wish.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    stepSubtitle(s)?.let { sub ->
                        Text(
                            stringResource(sub),
                            fontSize = 17.sp,
                            fontFamily = WishFont,
                            color = Wish.TextDim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Column(
                        Modifier.padding(top = 16.dp, bottom = 16.dp).widthIn(max = 640.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (s) {
                            0 -> {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Chip(AppIcons.Bolt, stringResource(R.string.wz_chip_fast), Modifier.weight(1f))
                                    Chip(Sym.Shield, stringResource(R.string.wz_chip_private), Modifier.weight(1f))
                                    Chip(Sym.Palette, stringResource(R.string.wz_chip_themes), Modifier.weight(1f))
                                }
                                NavRow(nextLabel = stringResource(R.string.wz_start)) { step = 1 }
                            }
                            1 -> {
                                val selectedFocus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { delay(200); runCatching { selectedFocus.requestFocus() } }
                                val pick = { l: Look -> look = l; onThemeChosen(if (l.id == "classic") CLASSIC_THEME_ID else l.id) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    LOOKS.forEach { l ->
                                        LookCard(l, l.id == look.id, if (l.id == look.id) Modifier.weight(1f).focusRequester(selectedFocus) else Modifier.weight(1f)) { pick(l) }
                                    }
                                }
                                NavRow(backAction = { step = 0 }, nextLabel = stringResource(R.string.next), autoFocus = false) { step = 2 }
                            }
                            2 -> {
                                if (isDefault) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Sym.Check, null, tint = Wish.Success, modifier = Modifier.size(28.dp))
                                        Text(stringResource(R.string.wz_home_done), fontSize = 20.sp, fontFamily = WishFont, color = Wish.Success)
                                    }
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
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                WishButton(stringResource(R.string.wizard_try_dialog), modifier = Modifier.weight(1f), filled = false) { requestDefault() }
                                                WishButton(stringResource(R.string.wizard_check_again), modifier = Modifier.weight(1f), filled = false) { defaultHome = defaultHomePackage(context) }
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
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        WishButton(stringResource(R.string.wz_home_set), modifier = Modifier.weight(1f), icon = Sym.Home) { requestDefault() }
                                        WishButton(stringResource(R.string.wizard_check_again), modifier = Modifier.weight(1f), filled = false) { defaultHome = defaultHomePackage(context) }
                                    }
                                    Body(stringResource(R.string.wizard_no_dialog_hint))
                                    NavRow(backAction = { step = 1 }, nextLabel = stringResource(R.string.wizard_skip), autoFocus = false) { step = 3 }
                                }
                            }
                            3 -> {
                                val vpns = remember { vpnApps(context) }
                                if (vpns.isEmpty()) {
                                    Body(stringResource(R.string.wizard_vpn_none))
                                } else {
                                    vpns.forEach { (pkg, label) ->
                                        ChoiceCard(Sym.VpnKey, label, null, Modifier) { onVpnChosen(pkg); step = 4 }
                                    }
                                }
                                NavRow(backAction = { step = 2 }, nextLabel = stringResource(R.string.wizard_vpn_skip)) { onVpnChosen(null); step = 4 }
                            }
                            4 -> {
                                val yesFocus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { delay(200); runCatching { yesFocus.requestFocus() } }
                                ChoiceCard(
                                    Sym.Cloud,
                                    stringResource(R.string.wizard_online_yes),
                                    stringResource(R.string.wizard_online_yes_sub),
                                    Modifier.focusRequester(yesFocus),
                                ) { onOnlineChosen(true); step = 5 }
                                ChoiceCard(
                                    Sym.Shield,
                                    stringResource(R.string.wizard_online_no),
                                    stringResource(R.string.wizard_online_no_sub),
                                    Modifier,
                                ) { onOnlineChosen(false); step = 5 }
                                WishButton(stringResource(R.string.back), filled = false, icon = Sym.ArrowBack) { step = 3 }
                            }
                            else -> {
                                Tip(stringResource(R.string.wizard_tip_1), stringResource(R.string.wizard_tip_1_sub))
                                Tip(stringResource(R.string.wizard_tip_2), stringResource(R.string.wizard_tip_2_sub))
                                Tip(stringResource(R.string.wizard_tip_3), stringResource(R.string.wizard_tip_3_sub))
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
    0 -> R.string.wz_welcome_sub
    1 -> R.string.wz_look_sub
    else -> null
}

/** Id the theme system uses for "no theme, classic layout". Mirrors ThemeStorage.CLASSIC_ID. */
private val CLASSIC_THEME_ID: String get() = org.wishy.launcher.theme.ThemeStorage.CLASSIC_ID

internal val WishFont: FontFamily = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

/* ------------------------------ pieces ------------------------------ */

/** The big picture on top of each step: the turning logo on the welcome step, a springy icon tile elsewhere. */
@Composable
private fun Hero(step: Int) {
    if (step == 0) {
        WishyLogo(size = 100.dp, turnMillis = 5000)
        return
    }
    var shown by remember(step) { mutableStateOf(false) }
    LaunchedEffect(step) { shown = true }
    val scale by animateFloatAsState(
        if (shown) 1f else 0.6f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "heroScale",
    )
    val done = step == STEPS - 1
    Box(
        Modifier
            .size(88.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (done) Wish.Success.copy(alpha = 0.18f) else Wish.PrimaryContainer, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            StepIcons[step](),
            contentDescription = null,
            tint = if (done) Wish.Success else Wish.OnPrimaryContainer,
            modifier = Modifier.size(46.dp),
        )
    }
}

@Composable
private fun Progress(step: Int) {
    Row(
        Modifier.padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(STEPS) { i ->
            val w by animateDpAsState(if (i == step) 40.dp else 10.dp, tween(250), label = "dotW")
            Box(
                Modifier
                    .width(w)
                    .height(8.dp)
                    .background(if (i <= step) Wish.Primary else Wish.Outline, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Wish.Surface, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = Wish.Primary, modifier = Modifier.size(30.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = WishFont, color = Wish.Text, maxLines = 1)
    }
}

@Composable
private fun Tip(title: String, sub: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Wish.Surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Sym.Lightbulb, null, tint = Wish.Primary, modifier = Modifier.size(26.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = WishFont, color = Wish.Text)
            Text(sub, fontSize = 14.sp, fontFamily = WishFont, color = Wish.TextDim, maxLines = 2)
        }
    }
}

/** A flat mini home screen in the look's colours, with a check that pops in when it is the chosen one. */
@Composable
private fun LookCard(look: Look, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Wish.Surface,
            focusedContainerColor = Wish.SurfaceHigh,
            contentColor = Wish.Text,
            focusedContentColor = Wish.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, Wish.Primary)) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Wish.Primary)),
        ),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(96.dp).background(look.bg, RoundedCornerShape(10.dp)).padding(8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(50.dp).height(5.dp).background(look.accent, RoundedCornerShape(3.dp)))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { Box(Modifier.size(width = 46.dp, height = 34.dp).background(look.tile, RoundedCornerShape(look.corner.dp))) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { Box(Modifier.size(width = 46.dp, height = 16.dp).background(look.tile.copy(alpha = 0.55f), RoundedCornerShape(look.corner.dp))) }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(Modifier.size(24.dp).background(Wish.Primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Sym.Check, null, tint = Wish.OnPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(
                look.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = WishFont,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, sub: String?, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Wish.Surface,
            focusedContainerColor = Wish.SurfaceHigh,
            contentColor = Wish.Text,
            focusedContentColor = Wish.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Wish.Primary))),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).background(Wish.PrimaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Wish.OnPrimaryContainer, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = WishFont)
                if (sub != null) Text(sub, fontSize = 14.sp, fontFamily = WishFont, color = Wish.TextDim, maxLines = 2)
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = WishFont, color = Wish.TextDim, textAlign = TextAlign.Center)
}

@Composable
private fun CodeLine(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Wish.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Wish.Primary, softWrap = true)
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
            delay(200)
            runCatching { nextFocus.requestFocus() }
        }
    }
    Row(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (backAction != null) {
            WishButton(stringResource(R.string.back), modifier = Modifier.weight(1f).widthIn(min = 120.dp), filled = false, icon = Sym.ArrowBack, onClick = backAction)
            WishButton(nextLabel, modifier = Modifier.weight(1.4f).widthIn(min = 160.dp).focusRequester(nextFocus), trailingIcon = Sym.ArrowForward, onClick = nextAction)
        } else {
            WishButton(nextLabel, modifier = Modifier.widthIn(min = 200.dp, max = 320.dp).focusRequester(nextFocus), trailingIcon = Sym.ArrowForward, onClick = nextAction)
        }
    }
}

/** Carousel controls: back arrow / "n / N" / forward arrow. On the ends they step out. */
@Composable
private fun PagerRow(page: Int, count: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val nextFocus = remember { FocusRequester() }
    LaunchedEffect(page) {
        delay(120)
        runCatching { nextFocus.requestFocus() }
    }
    Row(
        Modifier.padding(top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WishButton(if (page > 0) "" else stringResource(R.string.back), filled = false, icon = Sym.ArrowBack, onClick = onPrev)
        Text("${page + 1} / $count", fontSize = 16.sp, fontFamily = WishFont, color = Wish.TextDim)
        WishButton(
            if (page < count - 1) "" else stringResource(R.string.wizard_skip),
            modifier = Modifier.focusRequester(nextFocus),
            trailingIcon = Sym.ArrowForward,
            onClick = onNext,
        )
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
