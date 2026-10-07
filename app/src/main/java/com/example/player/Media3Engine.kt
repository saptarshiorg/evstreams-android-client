package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.allowlist.Allowlist
import okhttp3.OkHttpClient

class Media3Engine(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val onPlaybackError: (PlaybackException) -> Unit,
    private val onTracksChanged: (List<StreamTrack>, List<StreamTrack>, List<StreamQuality>) -> Unit
) {

    private val trackSelector = DefaultTrackSelector(context)
    val player: ExoPlayer

    private var loopStartMs: Long? = null
    private var loopEndMs: Long? = null

    init {
        // Fast-start buffering configuration (1500ms initial buffer, 500ms for fast start)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1500,
                /* maxBufferMs = */ 30000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1500
            )
            .build()

        player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError(error)
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
            }
        })
    }

    fun prepareStream(
        item: StreamMediaItem,
        startPositionMs: Long = 0L
    ) {
        val uri = Uri.parse(item.url)

        val dataSourceFactory = if (item.isEncryptedFile && !item.encryptionPassword.isNullOrEmpty()) {
            EncryptedDataSource.Factory(context, item.encryptionPassword)
        } else {
            val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("Mozilla/5.0 (Linux; Android 14) " + Allowlist.DEFAULT_USER_AGENT_SUFFIX)

            // Inject custom headers
            val headers = item.headers.toMutableMap()
            if (!headers.containsKey("Referer")) headers["Referer"] = Allowlist.PRIMARY_WEB_URL + "/"
            if (!headers.containsKey("Origin")) headers["Origin"] = Allowlist.PRIMARY_WEB_URL
            okHttpFactory.setDefaultRequestProperties(headers)

            DefaultDataSource.Factory(context, okHttpFactory)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)

        item.drmConfig?.let {
            mediaItemBuilder.setDrmConfiguration(it.toDrmConfiguration())
        }

        val mediaItem = mediaItemBuilder.build()
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        player.setMediaSource(mediaSource)
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        player.playWhenReady = true
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun setLoop(startMs: Long?, endMs: Long?) {
        this.loopStartMs = startMs
        this.loopEndMs = endMs
    }

    fun checkLoop() {
        val start = loopStartMs ?: return
        val end = loopEndMs ?: return
        if (end > start && player.currentPosition >= end) {
            player.seekTo(start)
        }
    }

    fun selectTrack(trackId: String, trackType: @C.TrackType Int) {
        val currentTracks = player.currentTracks
        for (group in currentTracks.groups) {
            if (group.type == trackType) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.id == trackId) {
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .setOverrideForType(
                                    TrackSelectionOverride(group.mediaTrackGroup, i)
                                )
                        )
                        return
                    }
                }
            }
        }
    }

    fun clearTrackOverride(trackType: @C.TrackType Int) {
        trackSelector.setParameters(
            trackSelector.buildUponParameters().clearOverridesOfType(trackType)
        )
    }

    fun selectQuality(qualityId: String) {
        if (qualityId == "auto") {
            clearTrackOverride(C.TRACK_TYPE_VIDEO)
            return
        }
        val currentTracks = player.currentTracks
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.id == qualityId || "${format.width}x${format.height}" == qualityId) {
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .setOverrideForType(
                                    TrackSelectionOverride(group.mediaTrackGroup, i)
                                )
                        )
                        return
                    }
                }
            }
        }
    }

    private fun extractTracks(tracks: Tracks) {
        val audioList = mutableListOf<StreamTrack>()
        val textList = mutableListOf<StreamTrack>()
        val qualityList = mutableListOf<StreamQuality>()

        // Add auto quality option
        qualityList.add(StreamQuality("auto", "Auto (Adaptive)", isSelected = true))

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val name = format.label ?: format.language ?: "Audio ${audioList.size + 1}"
                        val isSelected = group.isTrackSelected(i)
                        audioList.add(
                            StreamTrack(
                                id = format.id ?: "$i",
                                name = name,
                                language = format.language,
                                isSelected = isSelected
                            )
                        )
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val name = format.label ?: format.language ?: "Subtitle ${textList.size + 1}"
                        val isSelected = group.isTrackSelected(i)
                        textList.add(
                            StreamTrack(
                                id = format.id ?: "$i",
                                name = name,
                                language = format.language,
                                isSelected = isSelected
                            )
                        )
                    }
                }
                C.TRACK_TYPE_VIDEO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val res = if (format.width > 0 && format.height > 0) {
                            "${format.height}p (${format.width}x${format.height})"
                        } else {
                            "Track ${i + 1}"
                        }
                        val bitrate = format.bitrate
                        val label = if (bitrate > 0) "$res - ${bitrate / 1000} kbps" else res
                        val isSelected = group.isTrackSelected(i)
                        qualityList.add(
                            StreamQuality(
                                id = format.id ?: "${format.width}x${format.height}",
                                label = label,
                                bitrate = bitrate,
                                width = format.width,
                                height = format.height,
                                isSelected = isSelected
                            )
                        )
                    }
                }
            }
        }
        onTracksChanged(audioList, textList, qualityList)
    }

    fun release() {
        player.release()
    }
}
