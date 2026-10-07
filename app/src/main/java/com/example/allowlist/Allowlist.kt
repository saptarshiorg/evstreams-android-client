package com.example.allowlist

import java.net.URI

/**
 * Editable ALLOWLIST of hosts you own or have explicit permission to use.
 * All CORS proxying, header forwarding, and request interception are strictly restricted
 * to these domain targets.
 */
object Allowlist {

    const val PRIMARY_WEB_URL = "https://evstreams.pages.dev"
    const val DEFAULT_USER_AGENT_SUFFIX = "EVSportsApp/1.0"

    /**
     * Set of allowlisted hostnames (exact or suffix match).
     * Add any stream or CDN servers you own here.
     */
    val ALLOWED_HOSTS: Set<String> = setOf(
        "evstreams.pages.dev",
        "docs.google.com",
        "googleusercontent.com",
        "googlevideo.com",
        "pages.dev",
        "workers.dev",
        "cloudflarestream.com",
        "stream.evsports.com",
        "cdn.evsports.com",
        "live.evsports.com"
    )

    /**
     * Checks if a given URL host is permitted under the allowlist.
     */
    fun isHostAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = extractHost(url) ?: return false
        return ALLOWED_HOSTS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun extractHost(url: String): String? {
        return try {
            val uri = URI(url)
            uri.host?.lowercase() ?: extractHostFallback(url)
        } catch (_: Exception) {
            extractHostFallback(url)
        }
    }

    private fun extractHostFallback(url: String): String? {
        val withoutScheme = if (url.contains("://")) {
            url.substringAfter("://")
        } else {
            url
        }
        val cleanHost = withoutScheme.substringBefore('/').substringBefore(':').trim()
        return cleanHost.lowercase().ifEmpty { null }
    }

    /**
     * Recognized media extensions and protocols that should automatically trigger
     * the native video player engine instead of staying inside the web view.
     */
    val MEDIA_EXTENSIONS = listOf(
        ".m3u8",
        ".mpd",
        ".mp4",
        ".mkv",
        ".ts",
        ".flv",
        ".webm",
        ".mov",
        ".avi"
    )

    val MEDIA_PROTOCOLS = listOf(
        "rtmp://",
        "rtmps://",
        "rtsp://",
        "rtsps://",
        "udp://",
        "rtp://"
    )

    fun isMediaStream(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase().trim()
        if (MEDIA_PROTOCOLS.any { lower.startsWith(it) }) return true
        val cleanPath = lower.substringBefore('?').substringBefore('#')
        return MEDIA_EXTENSIONS.any { cleanPath.contains(it) }
    }
}
