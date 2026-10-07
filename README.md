# EV Sports Android App

**Developer:** RishiEvolutionX Technologies Limited  
**Package:** `com.rishievolutionx.evsports`  
**Web Portal:** `https://evstreams.pages.dev`  
**Copyright:** © 2026 RishiEvolutionX Technologies Limited. All rights reserved.

---

## Architecture & Features

1. **Seamless Web Portal Integration**
   - Clean Material 3 design, edge-to-edge UI.
   - Single persistent WebView instance preserved across screen orientation changes.
   - High display refresh rate (90Hz / 120Hz) configured where supported.
   - Custom User-Agent suffix `EVSportsApp/1.0`.
   - Thin animated top progress bar (`#E50914`), splash cross-fade, and branded offline recovery screen.

2. **On-Device CORS & Header Interceptor**
   - `WebViewClient.shouldInterceptRequest` backed by OkHttp connection pool and disk cache.
   - Enforces `Origin`, `Referer`, and `User-Agent` headers for allowlisted hosts in `Allowlist.kt`.
   - Injects `Access-Control-Allow-Origin: *` and expose headers.
   - Rewrites relative `.m3u8` playlist URLs into absolute URLs for child segments and keys.

3. **Dual Native Player Engine (VLC-Style)**
   - **Primary Engine:** AndroidX Media3 (ExoPlayer) with fast-start buffering (`1500ms`), adaptive bitrate, and hardware decoding.
   - **Fallback Engine:** libVLC (`org.videolan.android:libvlc-all`) with hardware acceleration and automatic software fallback.
   - Automatic fallback to libVLC when ExoPlayer encounters unsupported codecs or network dropouts.
   - Supported protocols: HLS, MPEG-DASH, RTMP, RTSP, RTP, UDP/multicast, progressive HTTP/HTTPS.
   - Supported formats: H.264, H.265/HEVC, VP8/VP9, AV1, MPEG-2/4; AAC, MP3, AC3/E-AC3, DTS, FLAC, Opus; MKV, MP4, AVI, TS, FLV, WebM, MOV.
   - Gesture controls: Left screen vertical swipe for Brightness HUD, right screen vertical swipe for Volume HUD, double-tap seek (10s back/forward).
   - Audio tracks, subtitle selector, quality bitrate switch, speed selector (0.5x - 2.0x), A-B loop marker, sleep timer, Picture-in-Picture (PiP), lock screen toggle.

4. **Standards-Based Security & Decryption**
   - Standard HLS AES-128 `#EXT-X-KEY` support.
   - Widevine DRM via Media3 standard DRM configuration with license server URLs and headers.
   - On-the-fly PBKDF2WithHmacSHA256 AES-256 decrypted streaming (`EncryptedDataSource`) for `.enc` files with zero unencrypted disk storage.

---

## Build & Install Guide

### 1. Prerequisites
- JDK 17 or higher
- Android SDK 36 (minSdk 24)
- Gradle 8.13+

### 2. ABI Splits & APK Size Optimization
libVLC includes native pre-compiled C/C++ libraries (`.so`) for ARM and x86 architectures. A universal APK containing all architectures adds ~25 to 30 MB.

In `app/build.gradle.kts`, ABI splits are configured:
```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        isUniversalApk = true
    }
}
```

### 3. Assembling APKs
Run the following Gradle commands:
```bash
# Build universal and split debug APKs:
gradle assembleDebug

# Build release APKs:
gradle assembleRelease
```

Generated APKs are located in:
`app/build/outputs/apk/debug/` or `app/build/outputs/apk/release/`
- `app-arm64-v8a-release.apk` (~18-22 MB, recommended for 99% of modern phones)
- `app-armeabi-v7a-release.apk` (~17-20 MB, older 32-bit ARM phones)
- `app-universal-release.apk` (~40-48 MB, works across all devices)

### 4. Install via ADB
```bash
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```
