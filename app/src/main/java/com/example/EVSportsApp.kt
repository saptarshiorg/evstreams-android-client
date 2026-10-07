package com.example

import android.app.Application
import com.example.allowlist.Allowlist
import com.example.network.CorsInterceptorClient
import com.example.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import java.net.InetAddress

class EVSportsApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var corsInterceptor: CorsInterceptorClient
        private set

    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        corsInterceptor = CorsInterceptorClient(this)
        networkMonitor = NetworkMonitor(this)

        // Warm up DNS and preconnect to primary web URL and stream host
        applicationScope.launch {
            try {
                InetAddress.getAllByName("evstreams.pages.dev")
                val warmupRequest = Request.Builder()
                    .url(Allowlist.PRIMARY_WEB_URL)
                    .head()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) " + Allowlist.DEFAULT_USER_AGENT_SUFFIX)
                    .build()
                corsInterceptor.okHttpClient.newCall(warmupRequest).execute().close()
            } catch (_: Exception) {
                // Background warmup, ignore network errors
            }
        }
    }

    companion object {
        lateinit var instance: EVSportsApp
            private set
    }
}
