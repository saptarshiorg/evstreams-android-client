package com.example.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem

/**
 * Standard, authorized Widevine DRM configuration.
 * Strictly relies on Media3 standard DRM APIs with source-provided license server and headers.
 * Does not circumvent or tamper with DRM.
 */
data class WidevineConfig(
    val licenseServerUrl: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val multiSession: Boolean = false
) {
    fun toDrmConfiguration(): MediaItem.DrmConfiguration {
        val builder = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri(Uri.parse(licenseServerUrl))
            .setLicenseRequestHeaders(requestHeaders)
            .setMultiSession(multiSession)
        return builder.build()
    }
}
