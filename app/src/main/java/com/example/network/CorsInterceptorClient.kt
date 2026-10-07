package com.example.network

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.example.allowlist.Allowlist
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * High-performance CORS & Header interceptor using OkHttp.
 * Intercepts requests for allowlisted hosts, enforces correct Referer/Origin,
 * injects permissive CORS response headers, supports HTTP Range requests,
 * and rewrites relative .m3u8 playlist URIs to ensure child segments and AES keys
 * are routed appropriately.
 */
class CorsInterceptorClient(context: Context) {

    private val cacheDir = File(context.cacheDir, "evsports_http_cache")
    private val diskCache = Cache(cacheDir, 50L * 1024L * 1024L) // 50MB disk cache

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cache(diskCache)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Inspects incoming WebResourceRequest. If host is allowlisted, forwards via OkHttp
     * with custom headers and returns WebResourceResponse with CORS headers injected.
     */
    fun interceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url ?: return null
        val urlString = uri.toString()

        // Only intercept requests for allowlisted hosts
        if (!Allowlist.isHostAllowed(urlString)) {
            return null
        }

        // We only intercept GET and HEAD methods for CORS proxying
        val method = request.method
        if (!method.equals("GET", ignoreCase = true) && !method.equals("HEAD", ignoreCase = true)) {
            return null
        }

        try {
            val reqBuilder = Request.Builder()
                .url(urlString)

            // Forward incoming headers while ensuring proper Referer and Origin
            val headers = request.requestHeaders ?: emptyMap()
            headers.forEach { (key, value) ->
                if (!key.equals("Host", ignoreCase = true)) {
                    reqBuilder.header(key, value)
                }
            }

            // Ensure proper Referer / Origin
            if (!headers.containsKey("Referer") && !headers.containsKey("referer")) {
                reqBuilder.header("Referer", Allowlist.PRIMARY_WEB_URL + "/")
            }
            if (!headers.containsKey("Origin") && !headers.containsKey("origin")) {
                reqBuilder.header("Origin", Allowlist.PRIMARY_WEB_URL)
            }
            if (!headers.containsKey("User-Agent") && !headers.containsKey("user-agent")) {
                reqBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) " + Allowlist.DEFAULT_USER_AGENT_SUFFIX)
            }

            val okResponse: Response = okHttpClient.newCall(reqBuilder.build()).execute()
            if (!okResponse.isSuccessful && okResponse.code !in 300..399) {
                // If not successful, let default WebView handle or return response
            }

            val body = okResponse.body ?: return null
            val rawContentType = okResponse.header("Content-Type") ?: "application/octet-stream"
            val mimeType = extractMimeType(rawContentType, urlString)
            val encoding = extractEncoding(rawContentType)

            // Inject CORS response headers
            val responseHeaders = mutableMapOf<String, String>()
            okResponse.headers.forEach { (name, value) ->
                responseHeaders[name] = value
            }
            responseHeaders["Access-Control-Allow-Origin"] = "*"
            responseHeaders["Access-Control-Allow-Methods"] = "GET, HEAD, OPTIONS"
            responseHeaders["Access-Control-Allow-Headers"] = "*"
            responseHeaders["Access-Control-Expose-Headers"] = "Content-Length, Content-Range, Accept-Ranges, Date, ETag"

            var responseStream: InputStream = body.byteStream()

            // If this is an HLS playlist (.m3u8), rewrite relative URLs so child segments
            // and encryption keys are fully qualified
            if (urlString.contains(".m3u8", ignoreCase = true) || mimeType.contains("mpegurl", ignoreCase = true)) {
                val rawText = body.string()
                val rewrittenText = rewriteM3u8Urls(rawText, urlString)
                val bytes = rewrittenText.toByteArray(Charsets.UTF_8)
                responseStream = ByteArrayInputStream(bytes)
                responseHeaders["Content-Length"] = bytes.size.toString()
            }

            val statusCode = okResponse.code
            val message = okResponse.message.ifBlank { "OK" }

            return WebResourceResponse(
                mimeType,
                encoding,
                statusCode,
                message,
                responseHeaders,
                responseStream
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Rewrites relative segment and key URLs in .m3u8 playlists into absolute URLs.
     */
    private fun rewriteM3u8Urls(playlistContent: String, baseUrl: String): String {
        val lines = playlistContent.lines()
        val baseUri = Uri.parse(baseUrl)
        val parentPath = baseUrl.substringBeforeLast('/', baseUrl) + "/"

        val output = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                output.append(line).append("\n")
                continue
            }

            if (trimmed.startsWith("#EXT-X-KEY:") || trimmed.startsWith("#EXT-X-MAP:")) {
                // Check if URI is enclosed: URI="something"
                val uriRegex = Regex("""URI="([^"]+)"""")
                val match = uriRegex.find(trimmed)
                if (match != null) {
                    val originalSubUrl = match.groupValues[1]
                    val absoluteSubUrl = resolveUrl(parentPath, baseUri, originalSubUrl)
                    val replaced = trimmed.replace(
                        """URI="$originalSubUrl"""",
                        """URI="$absoluteSubUrl""""
                    )
                    output.append(replaced).append("\n")
                    continue
                }
            }

            if (!trimmed.startsWith("#")) {
                // Segment URL line
                val absoluteSegment = resolveUrl(parentPath, baseUri, trimmed)
                output.append(absoluteSegment).append("\n")
            } else {
                output.append(line).append("\n")
            }
        }
        return output.toString()
    }

    private fun resolveUrl(parentPath: String, baseUri: Uri, relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute
        }
        if (relativeOrAbsolute.startsWith("/")) {
            val scheme = baseUri.scheme ?: "https"
            val host = baseUri.host ?: ""
            return "$scheme://$host$relativeOrAbsolute"
        }
        return parentPath + relativeOrAbsolute
    }

    private fun extractMimeType(contentTypeHeader: String, url: String): String {
        val part = contentTypeHeader.split(";").firstOrNull()?.trim()
        if (!part.isNullOrBlank() && part.contains("/")) {
            return part
        }
        return when {
            url.contains(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
            url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
            url.contains(".mp4", ignoreCase = true) -> "video/mp4"
            url.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
            url.contains(".ts", ignoreCase = true) -> "video/mp2t"
            url.contains(".webm", ignoreCase = true) -> "video/webm"
            url.contains(".csv", ignoreCase = true) -> "text/csv"
            url.contains(".json", ignoreCase = true) -> "application/json"
            url.contains(".js", ignoreCase = true) -> "application/javascript"
            url.contains(".css", ignoreCase = true) -> "text/css"
            url.contains(".html", ignoreCase = true) -> "text/html"
            else -> "application/octet-stream"
        }
    }

    private fun extractEncoding(contentTypeHeader: String): String {
        val parts = contentTypeHeader.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("charset=", ignoreCase = true)) {
                return trimmed.substringAfter("=").trim()
            }
        }
        return "utf-8"
    }
}
