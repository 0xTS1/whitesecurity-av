'use strict';

const path = require('node:path');

// Load .env without an extra dependency: Node 22 can read it natively.
try {
  process.loadEnvFile(path.join(__dirname, '..', '.env'));
} catch {
  // .env not present (e.g. in CI) — fall back to real environment variables.
}

const PORT = Number(process.env.PORT || 8787);
const APP_API_KEY = process.env.APP_API_KEY;
const DB_PATH = process.env.DB_PATH || path.join(__dirname, '..', 'data', 'whitesecurity.db');

if (!APP_API_KEY || APP_API_KEY === 'changeme-generate-a-real-random-key') {
  console.error(
    '[config] APP_API_KEY is missing or still set to the example placeholder.\n' +
    '          Set a real random value in server/.env before starting the server.\n' +
    '          Generate one with: node -e "console.log(require(\'crypto\').randomBytes(32).toString(\'hex\'))"'
  );
  process.exit(1);
}

// Web portal (dashboard + Midtrans payments) config. Unlike APP_API_KEY,
// none of this fails the boot — a misconfigured/absent payment setup must
// never take the Android-facing /api/v1 endpoints down with it. Routes that
// need Midtrans check `midtrans.configured` per-request instead.
const NODE_ENV = process.env.NODE_ENV || 'development';
const PORTAL_BASE_URL = process.env.PORTAL_BASE_URL || `http://localhost:${PORT}`;
const SESSION_COOKIE_NAME = process.env.SESSION_COOKIE_NAME || 'wsec_session';
const MIDTRANS_SERVER_KEY = process.env.MIDTRANS_SERVER_KEY || '';
const MIDTRANS_CLIENT_KEY = process.env.MIDTRANS_CLIENT_KEY || '';
const MIDTRANS_IS_PRODUCTION = process.env.MIDTRANS_IS_PRODUCTION === 'true';

module.exports = {
  PORT,
  APP_API_KEY,
  DB_PATH,
  NODE_ENV,
  PORTAL_BASE_URL,
  SESSION_COOKIE_NAME,
  midtrans: {
    serverKey: MIDTRANS_SERVER_KEY,
    clientKey: MIDTRANS_CLIENT_KEY,
    isProduction: MIDTRANS_IS_PRODUCTION,
    configured: Boolean(MIDTRANS_SERVER_KEY && MIDTRANS_CLIENT_KEY),
  },
};
