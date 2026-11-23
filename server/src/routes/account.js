'use strict';

const crypto = require('node:crypto');
const express = require('express');
const db = require('../db');
const { hashPassword, verifyPassword } = require('../lib/passwords');
const { rateLimit } = require('../middleware/rateLimit');
const { parseDbDate } = require('../lib/dates');

// Account API for the Android app — same users table as the web portal, so
// one account works in both. Mounted under /api/v1 (X-Api-Key already
// required). Web uses cookie sessions; the app gets a bearer token instead.

const router = express.Router();

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const TOKEN_DAYS = 90;

db.exec(`
  CREATE TABLE IF NOT EXISTS app_tokens (
    token_hash    TEXT PRIMARY KEY,
    user_id       INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id     TEXT,
    device_label  TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now')),
    last_used_at  TEXT,
    expires_at    TEXT NOT NULL
  );
`);

const getUserByEmail = db.prepare('SELECT * FROM users WHERE email = ?');
const insertUser = db.prepare('INSERT INTO users (email, password_hash, name) VALUES (?, ?, ?)');
const touchLastLogin = db.prepare("UPDATE users SET last_login_at = datetime('now') WHERE id = ?");
const insertToken = db.prepare(
  `INSERT INTO app_tokens (token_hash, user_id, device_id, device_label, last_used_at, expires_at)
   VALUES (?, ?, ?, ?, datetime('now'), datetime('now', '+${TOKEN_DAYS} days'))`
);
const getToken = db.prepare(
  `SELECT t.token_hash, t.device_id, u.* FROM app_tokens t JOIN users u ON u.id = t.user_id
   WHERE t.token_hash = ? AND t.expires_at > datetime('now')`
);
const touchToken = db.prepare(
  `UPDATE app_tokens SET last_used_at = datetime('now')
   WHERE token_hash = ? AND (last_used_at IS NULL OR last_used_at < datetime('now', '-60 seconds'))`
);
const deleteToken = db.prepare('DELETE FROM app_tokens WHERE token_hash = ?');

const getLicenses = db.prepare('SELECT * FROM licenses WHERE user_id = ? ORDER BY created_at DESC');
const getDevices = db.prepare(
  'SELECT device_id, activated_at, last_seen_at FROM license_devices WHERE license_id = ? ORDER BY activated_at'
);
const getOrders = db.prepare(
  `SELECT o.order_id, o.amount, o.status, o.created_at, o.paid_at, p.name as product_name
   FROM orders o JOIN products p ON p.id = o.product_id
   WHERE o.user_id = ? ORDER BY o.created_at DESC LIMIT 20`
);

const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');
const str = (v, max = 200) => String(v == null ? '' : v).trim().slice(0, max);

function issueToken(userId, deviceId, deviceLabel) {
  const token = 'wsat_' + crypto.randomBytes(32).toString('hex');
  insertToken.run(sha256(token), userId, deviceId || null, deviceLabel || null);
  touchLastLogin.run(userId);
  return token;
}

function publicUser(u) {
  return { id: u.id, email: u.email, name: u.name, createdAt: u.created_at, isAdmin: Boolean(u.is_admin) };
}

function requireAppUser(req, res, next) {
  const m = /^Bearer\s+(wsat_[a-f0-9]{64})$/.exec(req.get('Authorization') || '');
  const row = m ? getToken.get(sha256(m[1])) : null;
  if (!row) return res.status(401).json({ error: 'unauthorized' });
  touchToken.run(row.token_hash);
  req.appUser = row;
  req.appTokenHash = row.token_hash;
  next();
}

function maskDevice(id) {
  return id.length > 10 ? id.slice(0, 4) + '••••' + id.slice(-4) : id;
}

function licenseState(l) {
  if (l.revoked) return 'revoked';
  const exp = parseDbDate(l.expires_at);
  if (exp !== null && exp < Date.now()) return 'expired';
  return 'active';
}

router.post('/register', rateLimit({ max: 5 }), (req, res) => {
  const b = req.body || {};
  const email = str(b.email).toLowerCase();
  const password = String(b.password || '');
  const name = str(b.name, 80) || null;
  if (!EMAIL_RE.test(email)) return res.status(400).json({ error: 'invalid_email', message: 'Email tidak valid.' });
  if (password.length < 8) return res.status(400).json({ error: 'weak_password', message: 'Password minimal 8 karakter.' });
  if (password.length > 200) return res.status(400).json({ error: 'weak_password', message: 'Password terlalu panjang.' });
  if (getUserByEmail.get(email)) return res.status(409).json({ error: 'email_taken', message: 'Email sudah terdaftar. Silakan masuk.' });

  const id = insertUser.run(email, hashPassword(password), name).lastInsertRowid;
  const token = issueToken(id, str(b.deviceId, 100), str(b.deviceLabel, 100));
  res.status(201).json({ token, user: publicUser(getUserByEmail.get(email)) });
});

router.post('/login', rateLimit({ max: 10 }), (req, res) => {
  const b = req.body || {};
  const email = str(b.email).toLowerCase();
  const user = getUserByEmail.get(email);
  if (!user || !verifyPassword(String(b.password || ''), user.password_hash)) {
    return res.status(401).json({ error: 'invalid_credentials', message: 'Email atau password salah.' });
  }
  const token = issueToken(user.id, str(b.deviceId, 100), str(b.deviceLabel, 100));
  res.json({ token, user: publicUser(user) });
});

// Everything the in-app account dashboard shows, in one call.
router.get('/me', requireAppUser, (req, res) => {
  const u = req.appUser;
  const deviceId = str(req.query.deviceId, 100) || u.device_id || '';
  const licenses = getLicenses.all(u.id).map((l) => {
    const devices = getDevices.all(l.id);
    return {
      licenseKey: l.license_key,
      plan: l.plan,
      maxDevices: l.max_devices,
      expiresAt: l.expires_at,
      state: licenseState(l),
      activeOnThisDevice: devices.some((d) => d.device_id === deviceId),
      devices: devices.map((d) => ({
        device: maskDevice(d.device_id),
        thisDevice: d.device_id === deviceId,
        activatedAt: d.activated_at,
        lastSeenAt: d.last_seen_at,
      })),
    };
  });
  const orders = getOrders.all(u.id).map((o) => ({
    orderId: o.order_id,
    product: o.product_name,
    amount: o.amount,
    status: o.status,
    createdAt: o.created_at,
    paidAt: o.paid_at,
  }));
  res.json({ user: publicUser(u), licenses, orders });
});

router.post('/logout', requireAppUser, (req, res) => {
  deleteToken.run(req.appTokenHash);
  res.json({ ok: true });
});

module.exports = router;
