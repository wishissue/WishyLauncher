package org.wishy.launcher.net

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The launcher's only network code. Rules:
 *  - https only (an http URL is refused, never upgraded silently);
 *  - normal platform certificate and host-name checks, no custom trust managers;
 *  - redirects are followed by hand, at most 3, and only to https on the SAME host and port;
 *  - hard size caps, enforced while reading, not just from the Content-Length header;
 *  - short timeouts, no cookies, no credentials, and no identifying headers beyond a fixed User-Agent.
 * Call from a background thread.
 */
object HttpsFetcher {
    private const val CONNECT_MS = 10_000
    private const val READ_MS = 20_000
    private const val MAX_REDIRECTS = 3
    private const val USER_AGENT = "WishyLauncher"

    class FetchException(message: String) : IOException(message)

    fun getText(url: String, maxBytes: Int): String {
        val out = java.io.ByteArrayOutputStream()
        open(url) { input, _ -> copyLimited(input, out, maxBytes.toLong()) }
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * Downloads to [dest] (replaced). On any failure [dest] is deleted. [declaredBytes], if > 0, caps the size.
     *
     * [allowGithubRedirects] is for GitHub release files only: a release asset address answers with a redirect to
     * GitHub's own storage hosts (`*.githubusercontent.com`), so redirects between GitHub hosts are accepted for it.
     * Everything else stays https only. [onProgress] gets (bytes so far, total or -1) from the calling thread.
     */
    fun download(
        url: String,
        dest: File,
        maxBytes: Long,
        declaredBytes: Long = 0,
        allowGithubRedirects: Boolean = false,
        onProgress: ((Long, Long) -> Unit)? = null,
    ) {
        val cap = if (declaredBytes in 1..maxBytes) declaredBytes else maxBytes
        dest.parentFile?.mkdirs()
        try {
            dest.outputStream().use { out -> open(url, allowGithubRedirects) { input, len ->
                if (len > cap) throw FetchException("File is larger than allowed.")
                copyLimited(input, out, cap, len, onProgress)
            } }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }

    private fun isGithubHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "github.com" || h == "api.github.com" || h.endsWith(".githubusercontent.com") || h == "githubusercontent.com"
    }

    private fun open(startUrl: String, allowGithubRedirects: Boolean = false, read: (java.io.InputStream, Long) -> Unit) {
        var url = startUrl
        val first = URL(startUrl)
        if (!first.protocol.equals("https", true)) throw FetchException("Only https is allowed.")
        for (hop in 0..MAX_REDIRECTS) {
            val u = URL(url)
            if (!u.protocol.equals("https", true)) throw FetchException("Only https is allowed.")
            val sameHost = u.host.equals(first.host, true) && u.port == first.port
            val githubToGithub = allowGithubRedirects && isGithubHost(first.host) && isGithubHost(u.host) &&
                (u.port == -1 || u.port == 443)
            if (!sameHost && !githubToGithub) throw FetchException("Redirect to another host refused.")
            val c = u.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = CONNECT_MS
                c.readTimeout = READ_MS
                c.useCaches = false
                c.setRequestProperty("User-Agent", USER_AGENT)
                c.setRequestProperty("Accept-Encoding", "identity")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw FetchException("Bad redirect.")
                    url = URL(u, loc).toString()
                    continue
                }
                if (code != 200) throw FetchException("Server answered $code.")
                val contentLength = c.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                c.inputStream.use { read(it, contentLength) }
                return
            } finally {
                c.disconnect()
            }
        }
        throw FetchException("Too many redirects.")
    }

    private fun copyLimited(
        input: java.io.InputStream,
        out: java.io.OutputStream,
        cap: Long,
        length: Long = -1L,
        onProgress: ((Long, Long) -> Unit)? = null,
    ) {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        var lastReport = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > cap) throw FetchException("File is larger than allowed.")
            out.write(buf, 0, n)
            // Report about every 64 KB so the screen is not redrawn for each small read.
            if (onProgress != null && total - lastReport >= 64 * 1024) { lastReport = total; onProgress(total, length) }
        }
        onProgress?.invoke(total, length)
    }
}
