# Cloudflare Worker CORS Proxy for EV Sports

This Worker serves as a serverless CORS reverse proxy for the allowlisted EV Sports media streams and data files. It handles OPTIONS preflight, forwards appropriate `Origin`, `Referer`, and `User-Agent` headers, supports HTTP Range requests, and strictly rejects hosts not found in `ALLOWED_HOSTS`.

## Deployment Instructions

1. Install Wrangler CLI:
   ```bash
   npm install -g wrangler
   ```

2. Log in to your Cloudflare account:
   ```bash
   wrangler login
   ```

3. Deploy the worker:
   ```bash
   cd cloudflare-worker
   wrangler deploy
   ```

4. Cloudflare will provide a URL like:
   `https://evsports-cors-proxy.<your-subdomain>.workers.dev`

## How to Point the App and Web Site at the Worker

### In the Web Site (`https://evstreams.pages.dev`)
Wrap cross-origin stream URLs:
```javascript
const PROXY_BASE = "https://evsports-cors-proxy.<your-subdomain>.workers.dev/?url=";
const streamUrl = PROXY_BASE + encodeURIComponent("https://your-stream-cdn.com/live/sports.m3u8");
player.load(streamUrl);
```

### In the Android App
The Android app includes a native on-device OkHttp interceptor (`CorsInterceptorClient.kt`) which handles CORS and header injection directly on-device without needing an external worker. If you prefer routing through your Cloudflare Worker proxy, add your worker URL to `Allowlist.kt`:
```kotlin
val ALLOWED_HOSTS = setOf(
    "evsports-cors-proxy.<your-subdomain>.workers.dev",
    // ...
)
```
