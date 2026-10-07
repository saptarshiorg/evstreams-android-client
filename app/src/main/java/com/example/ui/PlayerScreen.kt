package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.EVSportsApp
import com.example.player.AspectRatioMode
import com.example.player.Media3Engine
import com.example.player.PlaybackEngine
import com.example.player.StreamMediaItem
import com.example.player.StreamQuality
import com.example.player.StreamTrack
import com.example.player.VlcEngine
import com.example.ui.theme.EvGoldAccent
import com.example.ui.theme.EvRedPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    streamItem: StreamMediaItem,
    onBack: () -> Unit,
    onEnterPip: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Keep screen on during playback
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var activeEngine by remember { mutableStateOf(streamItem.initialEngine) }
    var engineSwitchNotice by remember { mutableStateOf<String?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var audioTracks by remember { mutableStateOf<List<StreamTrack>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<StreamTrack>>(emptyList()) }
    var videoQualities by remember { mutableStateOf<List<StreamQuality>>(emptyList()) }

    var aspectMode by remember { mutableStateOf(AspectRatioMode.FIT) }
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    // Swipe HUD states
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolumeLevel by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var showVolumeHud by remember { mutableStateOf(false) }

    var currentBrightness by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f)
    }
    var showBrightnessHud by remember { mutableStateOf(false) }

    // Seek Double-Tap ripple
    var showDoubleTapSeekForward by remember { mutableStateOf(false) }
    var showDoubleTapSeekBackward by remember { mutableStateOf(false) }

    // A-B Loop
    var loopStartMs by remember { mutableStateOf<Long?>(null) }
    var loopEndMs by remember { mutableStateOf<Long?>(null) }

    // Sleep Timer (seconds remaining)
    var sleepTimerRemaining by remember { mutableIntStateOf(0) }

    // Menu dropdowns
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSubtitlesMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }

    // Media3 Engine
    val media3Engine = remember {
        Media3Engine(
            context = context,
            okHttpClient = EVSportsApp.instance.corsInterceptor.okHttpClient,
            onPlaybackError = { error ->
                // Automatic fallback to libVLC on playback error!
                engineSwitchNotice = "ExoPlayer error: ${error.errorCodeName}. Switching to libVLC Engine…"
                scope.launch {
                    delay(1200)
                    activeEngine = PlaybackEngine.LIBVLC
                    engineSwitchNotice = null
                }
            },
            onTracksChanged = { a, s, q ->
                audioTracks = a
                subtitleTracks = s
                videoQualities = q
            }
        )
    }

    // libVLC Engine
    val vlcEngine = remember {
        VlcEngine(
            context = context,
            onPlaybackError = { errMsg ->
                engineSwitchNotice = "VLC Error: $errMsg"
            },
            onTracksAvailable = { a, s ->
                audioTracks = a
                subtitleTracks = s
            }
        )
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(showControls) {
        if (showControls && !isLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Periodic time polling and A-B loop check
    LaunchedEffect(activeEngine, isPlaying) {
        while (true) {
            if (activeEngine == PlaybackEngine.MEDIA3) {
                currentPositionMs = media3Engine.player.currentPosition
                durationMs = media3Engine.player.duration.coerceAtLeast(0L)
                isPlaying = media3Engine.player.isPlaying
                isBuffering = media3Engine.player.playbackState == Player.STATE_BUFFERING
                media3Engine.checkLoop()
            } else {
                currentPositionMs = vlcEngine.mediaPlayer.time.coerceAtLeast(0L)
                durationMs = vlcEngine.mediaPlayer.length.coerceAtLeast(0L)
                isPlaying = vlcEngine.mediaPlayer.isPlaying
                isBuffering = !vlcEngine.mediaPlayer.isPlaying && currentPositionMs == 0L
                if (loopEndMs != null && loopStartMs != null && currentPositionMs >= loopEndMs!!) {
                    vlcEngine.mediaPlayer.time = loopStartMs!!
                }
            }
            delay(500)
        }
    }

    // Sleep timer countdown
    LaunchedEffect(sleepTimerRemaining) {
        if (sleepTimerRemaining > 0) {
            delay(1000)
            sleepTimerRemaining -= 1
            if (sleepTimerRemaining <= 0) {
                // Pause playback when sleep timer expires
                if (activeEngine == PlaybackEngine.MEDIA3) {
                    media3Engine.player.pause()
                } else {
                    vlcEngine.mediaPlayer.pause()
                }
                isPlaying = false
            }
        }
    }

    // Prepare stream on start or when engine switches
    LaunchedEffect(activeEngine, streamItem) {
        if (activeEngine == PlaybackEngine.MEDIA3) {
            vlcEngine.mediaPlayer.pause()
            media3Engine.prepareStream(streamItem, currentPositionMs)
        } else {
            media3Engine.player.pause()
            vlcEngine.prepareStream(streamItem, currentPositionMs)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            media3Engine.release()
            vlcEngine.release()
        }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2) {
                                // Seek backward 10s
                                val target = (currentPositionMs - 10000L).coerceAtLeast(0L)
                                if (activeEngine == PlaybackEngine.MEDIA3) media3Engine.player.seekTo(target)
                                else vlcEngine.mediaPlayer.time = target
                                currentPositionMs = target
                                showDoubleTapSeekBackward = true
                                scope.launch {
                                    delay(700)
                                    showDoubleTapSeekBackward = false
                                }
                            } else {
                                // Seek forward 10s
                                val target = if (durationMs > 0) (currentPositionMs + 10000L).coerceAtMost(durationMs)
                                else currentPositionMs + 10000L
                                if (activeEngine == PlaybackEngine.MEDIA3) media3Engine.player.seekTo(target)
                                else vlcEngine.mediaPlayer.time = target
                                currentPositionMs = target
                                showDoubleTapSeekForward = true
                                scope.launch {
                                    delay(700)
                                    showDoubleTapSeekForward = false
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            showVolumeHud = false
                            showBrightnessHud = false
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val screenWidth = size.width
                        if (change.position.x < screenWidth / 2) {
                            // Left screen drag: Brightness
                            val delta = -dragAmount / 600f
                            currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                            activity?.let { act ->
                                val lp = act.window.attributes
                                lp.screenBrightness = currentBrightness
                                act.window.attributes = lp
                            }
                            showBrightnessHud = true
                        } else {
                            // Right screen drag: Volume
                            val delta = if (dragAmount < 0) 1 else -1
                            val newVol = (currentVolumeLevel + delta).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            currentVolumeLevel = newVol
                            showVolumeHud = true
                        }
                    }
                }
            }
    ) {
        // Video Render View
        if (activeEngine == PlaybackEngine.MEDIA3) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = media3Engine.player
                        useController = false
                        resizeMode = when (aspectMode) {
                            AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioMode.RATIO_16_9, AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.player = media3Engine.player
                    playerView.resizeMode = when (aspectMode) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.RATIO_16_9, AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        vlcEngine.attachSurface(this)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Double-Tap Seek Ripples
        if (showDoubleTapSeekBackward) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = null, tint = Color.White)
                        Text("-10s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showDoubleTapSeekForward) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 48.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = null, tint = Color.White)
                        Text("+10s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Brightness / Volume HUD
        if (showVolumeHud) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = EvGoldAccent)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Volume: ${((currentVolumeLevel.toFloat() / maxVolume) * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (showBrightnessHud) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = EvGoldAccent)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Brightness: ${(currentBrightness * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Engine Fallback / Switching Notification Banner
        engineSwitchNotice?.let { notice ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 70.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    color = EvRedPrimary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = notice,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Buffering Indicator
        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EvRedPrimary, strokeWidth = 3.dp)
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls || isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isLocked) Color.Transparent else Color.Black.copy(alpha = 0.45f))
            ) {
                if (isLocked) {
                    // Only display floating Unlock Button
                    IconButton(
                        onClick = {
                            isLocked = false
                            showControls = true
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .testTag("unlock_controls_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = EvRedPrimary)
                    }
                } else {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("player_back_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = streamItem.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (activeEngine == PlaybackEngine.MEDIA3) "Media3 ExoPlayer (HW)" else "libVLC Engine (HW)",
                                    color = EvGoldAccent,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Engine Switcher Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (activeEngine == PlaybackEngine.MEDIA3) EvRedPrimary else Color(0xFF2C3E50),
                                modifier = Modifier
                                    .clickable {
                                        activeEngine = if (activeEngine == PlaybackEngine.MEDIA3) {
                                            PlaybackEngine.LIBVLC
                                        } else {
                                            PlaybackEngine.MEDIA3
                                        }
                                    }
                                    .testTag("switch_engine_button")
                            ) {
                                Text(
                                    text = if (activeEngine == PlaybackEngine.MEDIA3) "Media3" else "libVLC",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Aspect Ratio Toggle
                            IconButton(onClick = {
                                aspectMode = when (aspectMode) {
                                    AspectRatioMode.FIT -> AspectRatioMode.ZOOM
                                    AspectRatioMode.ZOOM -> AspectRatioMode.FILL
                                    AspectRatioMode.FILL -> AspectRatioMode.RATIO_16_9
                                    AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_4_3
                                    AspectRatioMode.RATIO_4_3 -> AspectRatioMode.FIT
                                }
                            }) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                            }

                            // PiP Button
                            IconButton(onClick = onEnterPip) {
                                Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                            }

                            // Settings Menu
                            Box {
                                IconButton(onClick = { showSettingsMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = Color.White)
                                }

                                DropdownMenu(
                                    expanded = showSettingsMenu,
                                    onDismissRequest = { showSettingsMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Quality / Bitrate") },
                                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                                        onClick = {
                                            showSettingsMenu = false
                                            showQualityMenu = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Audio Track (${audioTracks.size})") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                                        onClick = {
                                            showSettingsMenu = false
                                            showAudioMenu = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Subtitles (${subtitleTracks.size})") },
                                        leadingIcon = { Icon(Icons.Default.Subtitles, contentDescription = null) },
                                        onClick = {
                                            showSettingsMenu = false
                                            showSubtitlesMenu = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Speed: ${playbackSpeed}x") },
                                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                                        onClick = {
                                            showSettingsMenu = false
                                            showSpeedMenu = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (loopStartMs != null) "Clear A-B Loop" else "Set A-B Loop") },
                                        leadingIcon = { Icon(Icons.Default.Replay, contentDescription = null) },
                                        onClick = {
                                            showSettingsMenu = false
                                            if (loopStartMs == null) {
                                                loopStartMs = currentPositionMs
                                                loopEndMs = (currentPositionMs + 5000L).coerceAtMost(durationMs)
                                                media3Engine.setLoop(loopStartMs, loopEndMs)
                                            } else {
                                                loopStartMs = null
                                                loopEndMs = null
                                                media3Engine.setLoop(null, null)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (sleepTimerRemaining > 0) "Timer: ${sleepTimerRemaining / 60}m" else "Sleep Timer") },
                                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                        onClick = {
                                            showSettingsMenu = false
                                            showSleepTimerMenu = true
                                        }
                                    )
                                }

                                // Quality Menu
                                DropdownMenu(
                                    expanded = showQualityMenu,
                                    onDismissRequest = { showQualityMenu = false }
                                ) {
                                    videoQualities.forEach { quality ->
                                        DropdownMenuItem(
                                            text = { Text(quality.label) },
                                            trailingIcon = if (quality.isSelected) { { Icon(Icons.Default.Check, null) } } else null,
                                            onClick = {
                                                media3Engine.selectQuality(quality.id)
                                                showQualityMenu = false
                                            }
                                        )
                                    }
                                }

                                // Audio Tracks Menu
                                DropdownMenu(
                                    expanded = showAudioMenu,
                                    onDismissRequest = { showAudioMenu = false }
                                ) {
                                    if (audioTracks.isEmpty()) {
                                        DropdownMenuItem(text = { Text("Default Audio") }, onClick = { showAudioMenu = false })
                                    } else {
                                        audioTracks.forEach { track ->
                                            DropdownMenuItem(
                                                text = { Text(track.name) },
                                                trailingIcon = if (track.isSelected) { { Icon(Icons.Default.Check, null) } } else null,
                                                onClick = {
                                                    if (activeEngine == PlaybackEngine.MEDIA3) {
                                                        media3Engine.selectTrack(track.id, C.TRACK_TYPE_AUDIO)
                                                    } else {
                                                        vlcEngine.selectAudioTrack(track.id.toIntOrNull() ?: -1)
                                                    }
                                                    showAudioMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Subtitles Menu
                                DropdownMenu(
                                    expanded = showSubtitlesMenu,
                                    onDismissRequest = { showSubtitlesMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Disable Subtitles") },
                                        onClick = {
                                            if (activeEngine == PlaybackEngine.MEDIA3) media3Engine.clearTrackOverride(C.TRACK_TYPE_TEXT)
                                            else vlcEngine.selectSubtitleTrack(-1)
                                            showSubtitlesMenu = false
                                        }
                                    )
                                    subtitleTracks.forEach { track ->
                                        DropdownMenuItem(
                                            text = { Text(track.name) },
                                            trailingIcon = if (track.isSelected) { { Icon(Icons.Default.Check, null) } } else null,
                                            onClick = {
                                                if (activeEngine == PlaybackEngine.MEDIA3) media3Engine.selectTrack(track.id, C.TRACK_TYPE_TEXT)
                                                else vlcEngine.selectSubtitleTrack(track.id.toIntOrNull() ?: -1)
                                                showSubtitlesMenu = false
                                            }
                                        )
                                    }
                                }

                                // Speed Menu
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                        DropdownMenuItem(
                                            text = { Text("${speed}x") },
                                            trailingIcon = if (playbackSpeed == speed) { { Icon(Icons.Default.Check, null) } } else null,
                                            onClick = {
                                                playbackSpeed = speed
                                                if (activeEngine == PlaybackEngine.MEDIA3) media3Engine.setSpeed(speed)
                                                else vlcEngine.setSpeed(speed)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }

                                // Sleep Timer Menu
                                DropdownMenu(
                                    expanded = showSleepTimerMenu,
                                    onDismissRequest = { showSleepTimerMenu = false }
                                ) {
                                    listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "60 minutes").forEach { (minutes, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                sleepTimerRemaining = minutes * 60
                                                showSleepTimerMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Center Play / Pause
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(64.dp)
                                .clickable {
                                    if (isPlaying) {
                                        if (activeEngine == PlaybackEngine.MEDIA3) media3Engine.player.pause()
                                        else vlcEngine.mediaPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        if (activeEngine == PlaybackEngine.MEDIA3) media3Engine.player.play()
                                        else vlcEngine.mediaPlayer.play()
                                        isPlaying = true
                                    }
                                }
                                .testTag("center_play_pause_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    // Bottom Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Slider / Progress bar
                        if (durationMs > 0) {
                            Slider(
                                value = currentPositionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                                onValueChange = { newPos ->
                                    currentPositionMs = newPos.toLong()
                                    if (activeEngine == PlaybackEngine.MEDIA3) media3Engine.player.seekTo(newPos.toLong())
                                    else vlcEngine.mediaPlayer.time = newPos.toLong()
                                },
                                valueRange = 0f..durationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = EvRedPrimary,
                                    activeTrackColor = EvRedPrimary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .testTag("player_progress_slider")
                            )
                        } else {
                            // Live stream indicator
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(EvRedPrimary)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (isPlaying) {
                                            if (activeEngine == PlaybackEngine.MEDIA3) media3Engine.player.pause()
                                            else vlcEngine.mediaPlayer.pause()
                                            isPlaying = false
                                        } else {
                                            if (activeEngine == PlaybackEngine.MEDIA3) media3Engine.player.play()
                                            else vlcEngine.mediaPlayer.play()
                                            isPlaying = true
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                val timeText = if (durationMs > 0) {
                                    "${formatDuration(currentPositionMs)} / ${formatDuration(durationMs)}"
                                } else {
                                    "LIVE"
                                }
                                Text(
                                    text = timeText,
                                    color = if (durationMs > 0) Color.White else EvRedPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )

                                if (loopStartMs != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "[A-B Loop Active]",
                                        color = EvGoldAccent,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Lock Controls
                                IconButton(
                                    onClick = {
                                        isLocked = true
                                        showControls = false
                                    },
                                    modifier = Modifier.testTag("lock_controls_button")
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = "Lock Controls", tint = Color.White)
                                }

                                // Fullscreen / Rotate Screen
                                IconButton(onClick = {
                                    activity?.let { act ->
                                        val currentOrientation = act.resources.configuration.orientation
                                        act.requestedOrientation = if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
