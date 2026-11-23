'use strict';

const { clientIp } = require('../lib/clientInfo');

// Minimal in-memory sliding-window limiter. Good enough for a single-process
// VPS deployment; swap for a shared store (Redis) if you ever run more than
// one server instance behind a load balancer.
//
// Each call to rateLimit() gets its own isolated `hits` store — important
// now that multiple independent limiters exist (the API-key-gated /api/v1
// limiter, plus separate portal login/register/webhook limiters). They must
// never share state, or traffic on one route would burn another route's
// budget.

const WINDOW_MS = 60_000;

function rateLimit({ max = 60 } = {}) {
  const hits = new Map(); // key -> [timestamps]

  setInterval(() => {
    const cutoff = Date.now() - WINDOW_MS;
    for (const [key, timestamps] of hits) {
      const kept = timestamps.filter((t) => t > cutoff);
      if (kept.length === 0) hits.delete(key);
      else hits.set(key, kept);
    }
  }, WINDOW_MS).unref();

  return (req, res, next) => {
    // Per real client (see lib/clientInfo) — req.ip is always Caddy's loopback.
    const key = clientIp(req);
    const now = Date.now();
    const cutoff = now - WINDOW_MS;
    const timestamps = (hits.get(key) || []).filter((t) => t > cutoff);

    if (timestamps.length >= max) {
      return res.status(429).json({ error: 'rate_limited' });
    }

    timestamps.push(now);
    hits.set(key, timestamps);
    next();
  };
}

module.exports = { rateLimit };
