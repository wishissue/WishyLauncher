package org.wishy.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Home button override for TVs whose own launcher keeps the Home key (certified Google TV). Android only lets the
 * user choose the home app and Google TV ignores that choice for the remote's Home key, so, like other third party
 * launchers, Wishy listens for the key through an accessibility service the person turns on themselves and opens
 * itself when it is pressed. The service reads no screen content (canRetrieveWindowContent is false), looks at no
 * key except Home (and, only if the person switched it on, the "All apps" key some remotes send instead), and
 * sends nothing anywhere.
 */
class HomeKeyService : AccessibilityService() {
    private var stockHomes: Set<ComponentName> = emptySet()
    private var lastOpen = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshStockHomes()
        // Right after boot Android starts this service on its own; that is the moment to put Wishy first. Turning the
        // service on later from Settings (long after boot) does not pull the person away from the settings page.
        if (android.os.SystemClock.elapsedRealtime() < 3 * 60_000L && HomeLauncher.overrideEnabled(this)) openWishy()
    }

    private fun refreshStockHomes() {
        stockHomes = runCatching { HomeLauncher.diagnose(this).others.map { it.component }.toSet() }.getOrDefault(emptySet())
    }

    /**
     * Second way in, for remotes whose Home key never reaches an accessibility service: the moment another
     * launcher's HOME screen comes to the front (Home press, wake from standby, boot), bring Wishy forward. Never
     * while the person paused the override or just asked to open the other launcher themselves.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (pkg == packageName) return
        if (!HomeLauncher.overrideEnabled(this) || HomeLauncher.isSuppressed(this)) return
        if (stockHomes.none { it.packageName == pkg && (it.className == cls || cls.endsWith("." + it.className.substringAfterLast('.'))) }) {
            // The list can be stale after an install; look again once before deciding it is not a launcher.
            refreshStockHomes()
            if (stockHomes.none { it.packageName == pkg && it.className == cls }) return
        }
        val now = System.currentTimeMillis()
        if (now - lastOpen < 1500) return
        lastOpen = now
        openWishy()
    }

    private fun openWishy() {
        runCatching {
            HomeLauncher.recordHomePress(this)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }.onFailure { Log.w("WishyHome", "could not open Wishy", it) }
    }

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val isHome = code == KeyEvent.KEYCODE_HOME || (code == KeyEvent.KEYCODE_ALL_APPS && HomeLauncher.catchAllAppsKey(this))
        if (!isHome || !HomeLauncher.overrideEnabled(this)) return false
        // Act once per press, on release; swallow both halves so the stock launcher does not also react.
        if (event.action == KeyEvent.ACTION_UP) { HomeLauncher.suppressOverrideFor(this, 0L); openWishy() }
        return true
    }
}
