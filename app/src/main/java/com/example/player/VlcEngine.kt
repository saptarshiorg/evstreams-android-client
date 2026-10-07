package com.example.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import com.example.allowlist.Allowlist
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class VlcEngine(
    private val context: Context,
    private val onPlaybackError: (String) -> Unit,
    private val onTracksAvailable: (List<StreamTrack>, List<StreamTrack>) -> Unit
) {

    private val libVLC: LibVLC
    val mediaPlayer: MediaPlayer

    init {
        val options = arrayListOf(
            "--network-caching=1500",
            "--live-caching=1500",
            "--file-caching=1500",
            "--avcodec-hw=any",
            "--http-reconnect",
            "--sout-keep",
            "--user-agent=Mozilla/5.0 (Linux; Android 14) " + Allowlist.DEFAULT_USER_AGENT_SUFFIX
        )
        libVLC = LibVLC(context, options)
        mediaPlayer = MediaPlayer(libVLC)

        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.EncounteredError -> {
                    onPlaybackError("libVLC encountered an error during playback")
                }
                MediaPlayer.Event.Playing -> {
                    fetchTracks()
                }
                MediaPlayer.Event.EndReached -> {
                    // stream finished
                }
            }
        }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        val vout = mediaPlayer.vlcVout
        vout.setVideoView(surfaceView)
        if (!vout.areViewsAttached()) {
            vout.attachViews()
        }
    }

    fun detachSurface() {
        val vout = mediaPlayer.vlcVout
        if (vout.areViewsAttached()) {
            vout.detachViews()
        }
    }

    fun prepareStream(
        item: StreamMediaItem,
        startPositionMs: Long = 0L
    ) {
        try {
            val media = Media(libVLC, Uri.parse(item.url))
            // Apply per-stream options
            media.addOption(":network-caching=1500")
            media.addOption(":http-user-agent=Mozilla/5.0 (Linux; Android 14) " + Allowlist.DEFAULT_USER_AGENT_SUFFIX)

            val referer = item.headers["Referer"] ?: (Allowlist.PRIMARY_WEB_URL + "/")
            media.addOption(":http-referrer=$referer")

            mediaPlayer.media = media
            media.release()

            mediaPlayer.play()
            if (startPositionMs > 0) {
                mediaPlayer.time = startPositionMs
            }
        } catch (e: Exception) {
            onPlaybackError(e.message ?: "Failed to open in libVLC")
        }
    }

    fun setSpeed(rate: Float) {
        mediaPlayer.rate = rate
    }

    fun selectAudioTrack(trackId: Int) {
        mediaPlayer.setAudioTrack(trackId)
    }

    fun selectSubtitleTrack(trackId: Int) {
        mediaPlayer.setSpuTrack(trackId)
    }

    private fun fetchTracks() {
        val audioTracks = mutableListOf<StreamTrack>()
        val spuTracks = mutableListOf<StreamTrack>()

        mediaPlayer.audioTracks?.forEach { track ->
            audioTracks.add(
                StreamTrack(
                    id = track.id.toString(),
                    name = track.name.ifBlank { "Audio Track ${track.id}" },
                    isSelected = mediaPlayer.audioTrack == track.id
                )
            )
        }

        mediaPlayer.spuTracks?.forEach { track ->
            spuTracks.add(
                StreamTrack(
                    id = track.id.toString(),
                    name = track.name.ifBlank { "Subtitle Track ${track.id}" },
                    isSelected = mediaPlayer.spuTrack == track.id
                )
            )
        }

        onTracksAvailable(audioTracks, spuTracks)
    }

    fun release() {
        detachSurface()
        mediaPlayer.stop()
        mediaPlayer.release()
        libVLC.release()
    }
}
