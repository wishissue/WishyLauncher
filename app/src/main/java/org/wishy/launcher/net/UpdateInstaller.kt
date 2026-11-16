package org.wishy.launcher.net

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import java.io.File

/**
 * Downloads a release APK and hands it to Android's own installer, which asks the person to confirm and installs it
 * on top of the current version (same package name and signing key, higher version code), keeping all settings.
 * Both functions block: call them from a background thread.
 */
object UpdateInstaller {
    /** Largest APK accepted. The launcher is a few megabytes; this only stops runaway downloads. */
    private const val MAX_APK_BYTES = 120L * 1024 * 1024

    /** Downloads the APK of [info] into the app's cache and returns it. Throws if the release has no APK or the transfer fails. */
    fun download(context: Context, info: UpdateInfo, onProgress: (done: Long, total: Long) -> Unit): File {
        if (info.apkUrl.isEmpty() || !info.apkUrl.startsWith(Repos.RELEASE_DOWNLOAD_PREFIX)) {
            throw HttpsFetcher.FetchException("This release has no APK to download.")
        }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Older downloads are of no use any more.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "WishyLauncher-${info.version.filter { c -> c.isLetterOrDigit() || c == '.' || c == '-' }}.apk")
        HttpsFetcher.download(
            url = info.apkUrl,
            dest = file,
            maxBytes = MAX_APK_BYTES,
            declaredBytes = info.apkBytes,
            allowGithubRedirects = true,
            onProgress = onProgress,
        )
        return file
    }

    /**
     * Gives [apk] to the system installer. Android shows its own confirmation (and, the first time, asks to allow
     * installs from this app). Returns when the session has been handed over; the result arrives at [InstallResultReceiver].
     */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(apk.length())
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("wishy-launcher.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}

/** Receives the installer's progress. Opens the system's confirmation screen and reports a failure in plain words. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                runCatching { confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app is replaced and restarted by the system
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Toast.makeText(context, "Update not installed. $why".trim(), Toast.LENGTH_LONG).show()
            }
        }
    }
}
