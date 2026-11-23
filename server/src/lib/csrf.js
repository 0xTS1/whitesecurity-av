'use strict';

const crypto = require('node:crypto');
const { APP_API_KEY } = require('../config');

// Token = HMAC-SHA256(sessionId) keyed by APP_API_KEY. This needs no extra
// storage (unlike a random per-session CSRF secret) since it's derived from
// the session id we already have. Reusing APP_API_KEY here is safe — it's
// only ever used as HMAC key material, never transmitted — but keep it
// conceptually separate from Android's X-Api-Key auth; they serve unrelated
// purposes and must not be conflated.
function issueToken(sessionId) {
  return crypto.createHmac('sha256', APP_API_KEY).update(sessionId).digest('hex');
}

function verifyToken(sessionId, token) {
  if (!sessionId || !token) return false;
  const expected = Buffer.from(issueToken(sessionId), 'hex');
  const actual = Buffer.from(String(token), 'hex');
  return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
}

module.exports = { issueToken, verifyToken };
