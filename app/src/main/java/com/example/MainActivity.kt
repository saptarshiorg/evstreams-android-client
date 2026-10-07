package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.player.StreamMediaItem
import com.example.ui.AboutDialog
import com.example.ui.EncryptedFilePickerSheet
import com.example.ui.M3uPlaylistSheet
import com.example.ui.PlayerScreen
import com.example.ui.WebScreen
import com.example.ui.theme.EVSportsTheme

class MainActivity : ComponentActivity() {

    private var activeStreamItem by mutableStateOf<StreamMediaItem?>(null)
    private var isInPipMode by mutableStateOf(false)
    private var showAboutDialog by mutableStateOf(false)
    private var showEncryptedPicker by mutableStateOf(false)
    private var showM3uSheet by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure preferred refresh rate to 90Hz / 120Hz where supported
        configureHighRefreshRate()

        // Handle incoming VIEW intent with media link
        handleIncomingIntent(intent)

        setContent {
            EVSportsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val currentStream = activeStreamItem
                    if (currentStream != null) {
                        PlayerScreen(
                            streamItem = currentStream,
                            onBack = {
                                activeStreamItem = null
                            },
                            onEnterPip = {
                                enterPipMode()
                            }
                        )
                    } else {
                        WebScreen(
                            onOpenNativePlayer = { url, title ->
                                activeStreamItem = StreamMediaItem(url = url, title = title)
                            },
                            onOpenEncryptedPicker = {
                                showEncryptedPicker = true
                            },
                            onOpenM3uSheet = {
                                showM3uSheet = true
                            },
                            onOpenAbout = {
                                showAboutDialog = true
                            }
                        )

                        if (showAboutDialog) {
                            AboutDialog(onDismiss = { showAboutDialog = false })
                        }

                        if (showEncryptedPicker) {
                            EncryptedFilePickerSheet(
                                onDismiss = { showEncryptedPicker = false },
                                onPlayEncryptedFile = { uri, password ->
                                    activeStreamItem = StreamMediaItem(
                                        url = uri.toString(),
                                        title = uri.lastPathSegment?.substringAfterLast('/') ?: "Encrypted Media",
                                        isEncryptedFile = true,
                                        encryptionPassword = password
                                    )
                                }
                            )
                        }

                        if (showM3uSheet) {
                            M3uPlaylistSheet(
                                onDismiss = { showM3uSheet = false },
                                onPlayStream = { url, title ->
                                    activeStreamItem = StreamMediaItem(url = url, title = title)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val dataUri = intent?.data ?: return
        val url = dataUri.toString()
        activeStreamItem = StreamMediaItem(
            url = url,
            title = intent.getStringExtra("title") ?: "EV Stream"
        )
    }

    private fun configureHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val supportedModes = display?.supportedModes
            val highRefreshMode = supportedModes?.maxByOrNull { it.refreshRate }
            if (highRefreshMode != null && highRefreshMode.refreshRate >= 85f) {
                val params = window.attributes
                params.preferredDisplayModeId = highRefreshMode.modeId
                window.attributes = params
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(pipParams)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (activeStreamItem != null) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }
}
