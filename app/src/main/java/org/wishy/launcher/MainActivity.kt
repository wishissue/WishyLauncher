package org.wishy.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.wishy.launcher.ui.LauncherApp
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** Bumped whenever a package is installed/removed so the app grid rescans. */
    var rescanTick by mutableIntStateOf(0)
        private set

    /** Bumped whenever the system delivers a HOME intent to the already running launcher (the Home button). */
    var homeTick by mutableIntStateOf(0)
        private set

    private var receiverRegistered = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            // Removals always rescan (the app may be on the grid). For installs and
            // changes, skip the full scan unless the package is actually launchable.
            val launchable = pkg != null && (
                packageManager.getLeanbackLaunchIntentForPackage(pkg) != null ||
                    packageManager.getLaunchIntentForPackage(pkg) != null
                )
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED || launchable) {
                rescanTick++
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // Read the chosen UI language synchronously from SharedPreferences — cheap,
        // and off the DataStore (a runBlocking DataStore read here blocked the main
        // thread and made language changes hang until a restart). LauncherApp keeps
        // this mirror in sync with the DataStore config and triggers recreate().
        val lang = newBase.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
            .getString(LOCALE_KEY, "").orEmpty()
        super.attachBaseContext(if (lang.isEmpty()) newBase else applyLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate action=${intent?.action} categories=${intent?.categories} saved=${savedInstanceState != null}")
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) HomeLauncher.recordHomePress(this)
        // The home screen must never fail to start: a problem reading downloaded wallpapers must not take it down.
        runCatching { org.wishy.launcher.art.Wallpapers.refreshDownloads(this) }

        // A home screen never exits on Back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op */ }
        })

        // Package changes only refresh the grid; if the system refuses the receiver the launcher still has to start.
        receiverRegistered = runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                packageReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addDataScheme("package")
                },
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { Log.w(TAG, "package receiver not registered", it) }.isSuccess

        setContent {
            LauncherApp(rescanTick, homeTick)
        }
    }

    /**
     * singleTask: a Home press while Wishy is running arrives here, not in onCreate. Record it (so Settings can show
     * that Home really reaches Wishy) and let the UI fall back to the plain home screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val home = intent.hasCategory(Intent.CATEGORY_HOME)
        Log.i(TAG, "onNewIntent action=${intent.action} home=$home")
        if (home) {
            HomeLauncher.recordHomePress(this)
            homeTick++
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy finishing=$isFinishing changingConfig=$isChangingConfigurations")
        if (receiverRegistered) runCatching { unregisterReceiver(packageReceiver) }
        receiverRegistered = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WishyHome"
        private const val LOCALE_PREFS = "locale"
        private const val LOCALE_KEY = "lang"

        /** Mirror the chosen language so attachBaseContext can read it synchronously. */
        fun persistLocale(context: Context, lang: String) {
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .edit().putString(LOCALE_KEY, lang).apply()
        }

        fun currentLocalePref(context: Context): String =
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .getString(LOCALE_KEY, "").orEmpty()

        fun applyLocale(context: Context, lang: String): Context {
            val locale = if (lang.contains('-')) {
                val parts = lang.split('-')
                Locale(parts[0], parts.getOrElse(1) { "" })
            } else Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }
}
