package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.EVSportsApp
import com.example.R
import com.example.allowlist.Allowlist
import com.example.ui.theme.EvRedPrimary

enum class WebScreenState {
    LOADING,
    CONTENT,
    OFFLINE
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(
    onOpenNativePlayer: (url: String, title: String) -> Unit,
    onOpenEncryptedPicker: () -> Unit,
    onOpenM3uSheet: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isNetworkConnected by EVSportsApp.instance.networkMonitor.isConnected.collectAsState()

    var webProgress by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    var screenState by remember { mutableStateOf(WebScreenState.LOADING) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val pullToRefreshState = rememberPullToRefreshState()

    BackHandler(enabled = webViewInstance?.canGoBack() == true) {
        webViewInstance?.goBack()
    }

    LaunchedEffect(isNetworkConnected) {
        if (!isNetworkConnected && screenState == WebScreenState.LOADING) {
            screenState = WebScreenState.OFFLINE
        } else if (isNetworkConnected && screenState == WebScreenState.OFFLINE) {
            screenState = WebScreenState.LOADING
            webViewInstance?.reload()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ev_sports_logo),
                            contentDescription = "EV Sports Logo",
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Live Sports Portal",
                                style = MaterialTheme.typography.labelSmall,
                                color = EvRedPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Open Direct Stream / M3U Sheet
                        IconButton(
                            onClick = onOpenM3uSheet,
                            modifier = Modifier.testTag("open_m3u_sheet_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Play Stream / M3U",
                                tint = EvRedPrimary
                            )
                        }

                        // Open Encrypted .enc Picker
                        IconButton(
                            onClick = onOpenEncryptedPicker,
                            modifier = Modifier.testTag("open_enc_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Decrypt .enc",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Reload Button
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            webViewInstance?.reload()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // About Button
                        IconButton(
                            onClick = onOpenAbout,
                            modifier = Modifier.testTag("open_about_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Thin Animated Red Progress Bar (#E50914)
                if (webProgress in 0.01f..0.99f) {
                    LinearProgressIndicator(
                        progress = { webProgress },
                        color = EvRedPrimary,
                        trackColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                isRefreshing = true
                webViewInstance?.reload()
            },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = screenState, label = "ScreenStateCrossfade") { state ->
                when (state) {
                    WebScreenState.OFFLINE -> {
                        // Branded No Internet Screen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ev_sports_logo),
                                    contentDescription = "EV Sports Logo",
                                    modifier = Modifier.size(96.dp)
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = EvRedPrimary,
                                    modifier = Modifier.size(48.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = stringResource(id = R.string.no_internet_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = stringResource(id = R.string.no_internet_msg),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        screenState = WebScreenState.LOADING
                                        webViewInstance?.reload()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EvRedPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("retry_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.retry),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    WebScreenState.LOADING, WebScreenState.CONTENT -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Persistent Single WebView
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )

                                        isVerticalScrollBarEnabled = false
                                        isHorizontalScrollBarEnabled = false
                                        overScrollMode = View.OVER_SCROLL_NEVER

                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            mediaPlaybackRequiresUserGesture = false
                                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                            cacheMode = WebSettings.LOAD_DEFAULT
                                            allowFileAccess = true
                                            allowContentAccess = true
                                            textZoom = 100
                                            userAgentString = "${userAgentString} ${Allowlist.DEFAULT_USER_AGENT_SUFFIX}"
                                        }

                                        CookieManager.getInstance().setAcceptCookie(true)
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                webProgress = newProgress / 100f
                                                if (newProgress >= 100) {
                                                    isRefreshing = false
                                                    screenState = WebScreenState.CONTENT
                                                }
                                            }
                                        }

                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val url = request?.url?.toString() ?: return false

                                                // Intercept media links and open in Native Video Player
                                                if (Allowlist.isMediaStream(url)) {
                                                    onOpenNativePlayer(url, "EV Sports Stream")
                                                    return true
                                                }

                                                // Allow allowlisted navigation
                                                if (Allowlist.isHostAllowed(url)) {
                                                    return false
                                                }

                                                // Non-allowlisted external links open in external browser
                                                return try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    ctx.startActivity(intent)
                                                    true
                                                } catch (_: Exception) {
                                                    false
                                                }
                                            }

                                            override fun shouldInterceptRequest(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): WebResourceResponse? {
                                                if (request != null) {
                                                    val intercepted = EVSportsApp.instance.corsInterceptor.interceptRequest(request)
                                                    if (intercepted != null) {
                                                        return intercepted
                                                    }
                                                }
                                                return super.shouldInterceptRequest(view, request)
                                            }

                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                super.onPageStarted(view, url, favicon)
                                                if (screenState != WebScreenState.CONTENT) {
                                                    screenState = WebScreenState.LOADING
                                                }
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                isRefreshing = false
                                                screenState = WebScreenState.CONTENT
                                            }

                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: WebResourceError?
                                            ) {
                                                if (request?.isForMainFrame == true) {
                                                    screenState = WebScreenState.OFFLINE
                                                }
                                            }
                                        }

                                        loadUrl(Allowlist.PRIMARY_WEB_URL)
                                        webViewInstance = this
                                    }
                                },
                                update = { webView ->
                                    webViewInstance = webView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Shimmer / Initial Loading Placeholder
                            if (state == WebScreenState.LOADING && webProgress < 0.6f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ev_sports_logo),
                                            contentDescription = "EV Sports Logo",
                                            modifier = Modifier.size(80.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = stringResource(id = R.string.loading),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
