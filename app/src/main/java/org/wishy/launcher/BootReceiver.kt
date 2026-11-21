package org.wishy.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts Wishy when the TV finishes booting, so it is the first thing on screen. Android 10 and newer limit what a
 * receiver may start, so on those versions the Home button service (which is exempt once the person has turned it
 * on) also opens Wishy right after boot. Does nothing while the person has paused the override.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != "android.intent.action.QUICKBOOT_POWERON" && a != "com.htc.intent.action.QUICKBOOT_POWERON") return
        if (!HomeLauncher.overrideEnabled(context)) return
        runCatching {
            HomeLauncher.recordHomePress(context)
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Log.i("WishyHome", "boot start refused by the system: ${it.message}") }
    }
}
