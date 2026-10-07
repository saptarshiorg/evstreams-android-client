/**
 * EV Sports Cloudflare Worker CORS Proxy
 * 
 * Secure reverse proxy with strict allowlist enforcement, HTTP Range support,
 * OPTIONS preflight handling, and CORS header rewriting for live sports streaming.
 * Developer: RishiEvolutionX Technologies Limited
 */

// Host allowlist strictly limited to approved streaming hosts
const ALLOWED_HOSTS = [
  'evstreams.pages.dev',
  'docs.google.com',
  'googleusercontent.com',
  'googlevideo.com',
  'pages.dev',
  'workers.dev',
  'cloudflarestream.com',
  'stream.evsports.com',
  'cdn.evsports.com',
  'live.evsports.com'
];

function isAllowedHost(urlStr) {
  try {
    const parsed = new URL(urlStr);
    const host = parsed.hostname.toLowerCase();
    return ALLOWED_HOSTS.some(allowed => host === allowed || host.endsWith('.' + allowed));
  } catch (e) {
    return false;
  }
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // 1. Handle CORS OPTIONS Preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, HEAD, POST, OPTIONS',
          'Access-Control-Allow-Headers': request.headers.get('Access-Control-Request-Headers') || '*',
          'Access-Control-Max-Age': '86400',
        },
      });
    }

    // 2. Extract destination URL from query parameter ?url=... or /proxy?url=...
    const targetUrl = url.searchParams.get('url');
    if (!targetUrl) {
      return new Response(
        JSON.stringify({
          error: 'Missing url query parameter',
          usage: 'https://your-worker.workers.dev/?url=https://stream.host/path.m3u8',
          allowedHosts: ALLOWED_HOSTS
        }),
        {
          status: 400,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*'
          }
        }
      );
    }

    // 3. Reject hosts outside allowlist
    if (!isAllowedHost(targetUrl)) {
      return new Response(
        JSON.stringify({
          error: 'Forbidden: Host not in allowlist',
          requested: targetUrl
        }),
        {
          status: 403,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*'
          }
        }
      );
    }

    // 4. Forward request to target host
    const newHeaders = new Headers(request.headers);
    newHeaders.set('Origin', 'https://evstreams.pages.dev');
    newHeaders.set('Referer', 'https://evstreams.pages.dev/');
    newHeaders.set('User-Agent', 'Mozilla/5.0 (Linux; Android 14) EVSportsApp/1.0');
    newHeaders.delete('Host');

    try {
      const response = await fetch(targetUrl, {
        method: request.method,
        headers: newHeaders,
        redirect: 'follow',
      });

      // 5. Clone and inject permissive CORS headers
      const modifiedHeaders = new Headers(response.headers);
      modifiedHeaders.set('Access-Control-Allow-Origin', '*');
      modifiedHeaders.set('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
      modifiedHeaders.set('Access-Control-Allow-Headers', '*');
      modifiedHeaders.set('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Accept-Ranges, Date, ETag');

      return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: modifiedHeaders,
      });
    } catch (err) {
      return new Response(
        JSON.stringify({ error: 'Proxy fetch failed', message: err.message }),
        {
          status: 502,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*'
          }
        }
      );
    }
  },
};
