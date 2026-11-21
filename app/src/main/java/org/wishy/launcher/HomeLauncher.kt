package org.wishy.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Everything about Wishy being (or not being) the HOME app, in one place.
 *
 * Nothing here assumes the manifest is enough. [diagnose] asks Android what the HOME intent really resolves to, what
 * the HOME role says, and which HOME activities are installed, and [recordHomePress] remembers every time the system
 * really delivered a HOME intent to Wishy, so a device where the stock launcher still grabs the Home button is
 * visible as "never reached Wishy" instead of guesswork.
 *
 * Changing the default is never done behind the user's back: Android only lets the user (or a device-owner app) pick
 * the home app, so [changeDefault] opens Android's own picker. See [openHomeSettings].
 */
object HomeLauncher {
    private const val TAG = "WishyHome"
    private const val PREFS = "home_state"
    private const val KEY_LAST_HOME = "last_home_at"
    private const val KEY_OVERRIDE = "override_enabled"
    private const val KEY_ALL_APPS = "catch_all_apps_key"
    private const val KEY_SUPPRESS_UNTIL = "suppress_until"

    /** Present on certified Google TV devices, where Google keeps its own launcher on the Home button. */
    private const val AMATI_FEATURE = "com.google.android.feature.AMATI_EXPERIENCE"

    enum class State {
        /** Wishy holds the HOME role (or the HOME intent resolves to Wishy). */
        DEFAULT,
        /** Another launcher is the default. */
        OTHER,
        /** No default is stored: Android shows its "which launcher?" chooser on every Home press. */
        CHOOSER,
    }

    /** What opening Android's home-app settings ended up showing. */
    enum class Opened {
        /** The "Home app" page itself. */
        HOME_SETTINGS,
        /** The "Default apps" list, where "Home app" is one row. */
        DEFAULT_APPS,
        /** Only the general Settings app. The device has no direct page for this. */
        GENERAL_SETTINGS,
        /** Nothing could be opened. */
        NONE,
    }

    /** One installed HOME activity. */
    class HomeApp(
        val component: ComponentName,
        val label: String,
        val isSystem: Boolean,
        val isSelf: Boolean,
    ) {
        val pkg: String get() = component.packageName
    }

    class Diagnosis(
        val state: State,
        /** Package the HOME intent resolves to right now. For [State.CHOOSER] this is the chooser, not a launcher. */
        val resolvedPackage: String?,
        /** Name of the default launcher when it is another app, else null. */
        val otherLabel: String?,
        val roleAvailable: Boolean,
        val roleHeld: Boolean,
        /** Every installed activity that answers the HOME intent. */
        val handlers: List<HomeApp>,
        /** Wishy's own HOME activity is installed, enabled and answers the HOME intent. */
        val selfListed: Boolean,
        val googleTv: Boolean,
        /** The person turned Wishy's accessibility (Home button override) service on. */
        val serviceOn: Boolean,
        /** Milliseconds since epoch of the last HOME intent delivered to Wishy, 0 when never. */
        val lastHomePressAt: Long,
    ) {
        val others: List<HomeApp> get() = handlers.filter { !it.isSelf }

        /** The launcher to jump to: a system one first, otherwise the first other one. */
        val preferredOther: HomeApp? get() = others.firstOrNull { it.isSystem } ?: others.firstOrNull()
    }

    fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** Android's "pick an app" dialog is an activity of the system, not a launcher. */
    private fun isChooser(pkg: String, className: String): Boolean =
        pkg == "android" || className.contains("ResolverActivity") || className.contains("ChooserActivity")

    private fun roleAvailableAndHeld(context: Context): Pair<Boolean, Boolean> {
        if (Build.VERSION.SDK_INT < 29) return false to false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false to false
        val available = runCatching { rm.isRoleAvailable(RoleManager.ROLE_HOME) }.getOrDefault(false)
        val held = available && runCatching { rm.isRoleHeld(RoleManager.ROLE_HOME) }.getOrDefault(false)
        return available to held
    }

    /** Reads the real state from Android. Cheap (a few PackageManager calls); safe to call often. */
    @Suppress("DEPRECATION")
    fun diagnose(context: Context): Diagnosis {
        val pm = context.packageManager
        val self = context.packageName

        val resolved = runCatching { pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
        val resolvedInfo = resolved?.activityInfo
        val resolvedPkg = resolvedInfo?.packageName
        val chooser = resolvedInfo != null && isChooser(resolvedInfo.packageName, resolvedInfo.name)

        val (roleAvailable, roleHeld) = roleAvailableAndHeld(context)

        val handlers = runCatching { pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY) }
            .getOrDefault(emptyList())
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                val label = runCatching { ri.loadLabel(pm)?.toString() }.getOrNull() ?: ai.packageName
                val system = (ai.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                HomeApp(ComponentName(ai.packageName, ai.name), label, system, ai.packageName == self)
            }
            .distinctBy { it.component }

        val state = when {
            roleHeld || resolvedPkg == self -> State.DEFAULT
            chooser -> State.CHOOSER
            else -> State.OTHER
        }
        val otherLabel = if (state == State.OTHER) {
            handlers.firstOrNull { it.pkg == resolvedPkg }?.label
                ?: runCatching { resolved?.loadLabel(pm)?.toString() }.getOrNull()
                ?: resolvedPkg
        } else null

        return Diagnosis(
            state = state,
            resolvedPackage = resolvedPkg,
            otherLabel = otherLabel,
            roleAvailable = roleAvailable,
            roleHeld = roleHeld,
            handlers = handlers,
            selfListed = handlers.any { it.isSelf } && selfEnabled(context),
            googleTv = pm.hasSystemFeature(AMATI_FEATURE),
            serviceOn = serviceEnabled(context),
            lastHomePressAt = lastHomePressAt(context),
        )
    }

    fun isDefault(context: Context): Boolean = diagnose(context).state == State.DEFAULT

    /** The manifest says enabled, and nothing (an MDM, a stray `pm disable`) switched Wishy's HOME activity off. */
    private fun selfEnabled(context: Context): Boolean {
        val setting = runCatching {
            context.packageManager.getComponentEnabledSetting(ComponentName(context, MainActivity::class.java))
        }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
            setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
            setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
    }

    /* ------------------------- proof that Home reaches Wishy ------------------------- */

    /**
     * Called by [MainActivity] whenever it is started with (or brought back by) a HOME intent. A Home press that the
     * stock launcher swallowed never gets here, which is exactly what the settings page reports.
     */
    fun recordHomePress(context: Context) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_HOME, System.currentTimeMillis()).apply()
        }
        Log.i(TAG, "HOME intent delivered to Wishy")
    }

    fun lastHomePressAt(context: Context): Long =
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_HOME, 0L) }
            .getOrDefault(0L)

    /* ------------------------- changing the default ------------------------- */

    /**
     * The system dialog that makes Wishy the home app (Android 10 and newer, where the device offers the HOME role).
     * Null when it is not available or Wishy already holds the role; then use [openHomeSettings].
     */
    fun roleRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 29) return null
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        val ok = runCatching { rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME) }.getOrDefault(false)
        if (!ok) return null
        return runCatching { rm.createRequestRoleIntent(RoleManager.ROLE_HOME) }.getOrNull()
    }

    private fun start(context: Context, intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (intent.resolveActivity(context.packageManager) == null) {
                Log.i(TAG, "nothing handles ${intent.action}")
                false
            } else {
                context.startActivity(intent)
                true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "could not start ${intent.action}", t)
            false
        }
    }

    /**
     * Opens Android's own "Home app" page, the supported place to pick (or drop) the default launcher. Tries the
     * dedicated page first, then the "Default apps" list, then the general Settings app, and says which one it got.
     */
    fun openHomeSettings(context: Context): Opened {
        if (start(context, Intent(Settings.ACTION_HOME_SETTINGS))) return Opened.HOME_SETTINGS
        if (Build.VERSION.SDK_INT >= 24 && start(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) return Opened.DEFAULT_APPS
        if (start(context, Intent(Settings.ACTION_SETTINGS))) return Opened.GENERAL_SETTINGS
        return Opened.NONE
    }

    /**
     * "Remove default / change launcher". On Android 9 and older a stored "Always" choice for Wishy can still be
     * cleared by the app itself, so that is done first; from Android 10 the platform owns the choice and ignores it,
     * so it is skipped there. Either way the person then picks the new home app in Android's own page, and the status
     * on the settings page refreshes when they come back.
     */
    @Suppress("DEPRECATION")
    fun changeDefault(context: Context): Opened {
        if (Build.VERSION.SDK_INT < 29) {
            runCatching { context.packageManager.clearPackagePreferredActivities(context.packageName) }
        }
        return openHomeSettings(context)
    }

    /**
     * Opens another installed launcher right now without touching the default (a way back to the system launcher
     * from inside Wishy). Returns false when there is none or it would not start.
     */
    fun openOtherLauncher(context: Context, target: HomeApp? = null): Boolean {
        val app = target ?: diagnose(context).preferredOther ?: return false
        val intent = homeIntent().setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // The override must not pull the person straight back from a launcher they asked for.
        suppressOverrideFor(context, 120_000L)
        return start(context, intent)
    }

    /* ------------------------- Home button override (accessibility) ------------------------- */

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The person's switch: when off the service stays installed but ignores every key. On by default once the service is enabled. */
    fun overrideEnabled(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY_OVERRIDE, true) }.getOrDefault(true)
    fun setOverrideEnabled(context: Context, on: Boolean) { runCatching { prefs(context).edit().putBoolean(KEY_OVERRIDE, on).apply() } }

    /** Xiaomi / Mi TV remotes send "All apps" for the Home button, so it is caught by default there. */
    private val xiaomi: Boolean get() = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)

    /** Some remotes (for example Xiaomi) send "All apps" instead of Home. On by default on Xiaomi, off elsewhere unless the person turns it on. */
    fun catchAllAppsKey(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY_ALL_APPS, xiaomi) }.getOrDefault(xiaomi)
    fun setCatchAllAppsKey(context: Context, on: Boolean) { runCatching { prefs(context).edit().putBoolean(KEY_ALL_APPS, on).apply() } }

    fun suppressOverrideFor(context: Context, ms: Long) {
        runCatching { prefs(context).edit().putLong(KEY_SUPPRESS_UNTIL, System.currentTimeMillis() + ms).apply() }
    }

    fun isSuppressed(context: Context): Boolean =
        runCatching { prefs(context).getLong(KEY_SUPPRESS_UNTIL, 0L) > System.currentTimeMillis() }.getOrDefault(false)

    /** True when the person has turned Wishy's accessibility service on in Android's settings. */
    fun serviceEnabled(context: Context): Boolean {
        val me = ComponentName(context, HomeKeyService::class.java)
        val list = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull()
            ?: return false
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Opens Android's Accessibility settings, where the person turns the service on. False if this device has no such page. */
    fun openAccessibilitySettings(context: Context): Boolean = start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
