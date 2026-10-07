package com.example.player

enum class PlaybackEngine {
    MEDIA3,
    LIBVLC
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit to Screen"),
    ZOOM("Zoom / Crop"),
    FILL("Stretch Fill"),
    RATIO_16_9("16:9 Cinema"),
    RATIO_4_3("4:3 Standard")
}

data class StreamTrack(
    val id: String,
    val name: String,
    val language: String? = null,
    val isSelected: Boolean = false
)

data class StreamQuality(
    val id: String,
    val label: String,
    val bitrate: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val isSelected: Boolean = false
)

data class StreamMediaItem(
    val url: String,
    val title: String = "EV Live Stream",
    val headers: Map<String, String> = emptyMap(),
    val drmConfig: WidevineConfig? = null,
    val isEncryptedFile: Boolean = false,
    val encryptionPassword: String? = null,
    val initialEngine: PlaybackEngine = PlaybackEngine.MEDIA3
)
