'use strict';

// Requests reach node via Caddy on loopback (Caddy sits behind Cloudflare),
// so the socket address is always 127.0.0.1. Caddy overwrites X-Real-IP on
// every request: CF-Connecting-IP when the request came from a Cloudflare
// edge range, otherwise the real peer address (see /etc/caddy/Caddyfile,
// snippet antivirus_real_ip). So X-Real-IP is trustworthy here — but only
// when the socket itself is loopback, i.e. the request really came through
// Caddy.
function clientIp(req) {
  const remote = req.socket.remoteAddress || '';
  const viaLocalProxy = remote === '127.0.0.1' || remote === '::1' || remote === '::ffff:127.0.0.1';
  if (viaLocalProxy) {
    const real = String(req.get('X-Real-IP') || '').trim();
    if (real) return real.slice(0, 64);
  }
  return remote.replace(/^::ffff:/, '').slice(0, 64);
}

function userAgent(req) {
  return String(req.get('User-Agent') || '').slice(0, 300);
}

// Short "Chrome · Android" style label from a User-Agent string.
function describeUserAgent(ua) {
  if (!ua) return 'Tidak diketahui';
  const browser =
    /Edg\//.test(ua) ? 'Edge' :
    /OPR\/|Opera/.test(ua) ? 'Opera' :
    /SamsungBrowser/.test(ua) ? 'Samsung Internet' :
    /Firefox\//.test(ua) ? 'Firefox' :
    /Chrome\//.test(ua) ? 'Chrome' :
    /Safari\//.test(ua) ? 'Safari' :
    /curl|wget|python|okhttp/i.test(ua) ? 'Bot/Script' : 'Lainnya';
  const os =
    /Android/.test(ua) ? 'Android' :
    /iPhone|iPad|iPod/.test(ua) ? 'iOS' :
    /Windows/.test(ua) ? 'Windows' :
    /Mac OS X/.test(ua) ? 'macOS' :
    /Linux/.test(ua) ? 'Linux' : '';
  return os ? `${browser} · ${os}` : browser;
}

module.exports = { clientIp, userAgent, describeUserAgent };
