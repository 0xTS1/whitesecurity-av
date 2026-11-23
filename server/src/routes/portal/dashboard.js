'use strict';

const express = require('express');
const db = require('../../db');
const { requireUser, requireCsrf } = require('../../middleware/session');
const { rateLimit } = require('../../middleware/rateLimit');
const { parseDbDate } = require('../../lib/dates');

const router = express.Router();

const getLicensesForUser = db.prepare(
  'SELECT * FROM licenses WHERE user_id = ? ORDER BY created_at DESC'
);
const getDevicesForUser = db.prepare(
  `SELECT d.* FROM license_devices d
   JOIN licenses l ON l.id = d.license_id
   WHERE l.user_id = ? ORDER BY COALESCE(d.last_seen_at, d.activated_at) DESC`
);
const getRecentOrders = db.prepare(
  `SELECT o.*, p.name as product_name FROM orders o
   JOIN products p ON p.id = o.product_id
   WHERE o.user_id = ? ORDER BY o.created_at DESC LIMIT 10`
);
const getTotalSpent = db.prepare(
  "SELECT COALESCE(SUM(amount), 0) as total FROM orders WHERE user_id = ? AND status = 'paid'"
);
const getSignatureStats = db.prepare(
  'SELECT COUNT(*) as n, MAX(added_at) as lastAdded FROM malware_signatures'
);
const getActiveProducts = db.prepare('SELECT * FROM products WHERE active = 1 ORDER BY price_idr ASC');
const getBySha256 = db.prepare('SELECT threat_name, severity FROM malware_signatures WHERE sha256 = ?');

const SHA256_RE = /^[a-f0-9]{64}$/;
const DAY_MS = 86400000;

const SECURITY_TIPS = [
  'Pasang aplikasi hanya dari Google Play atau sumber resmi pengembangnya.',
  'Periksa izin aplikasi: aplikasi senter tidak butuh akses SMS atau kontak.',
  'Aktifkan verifikasi dua langkah di akun email dan media sosial Anda.',
  'Jangan klik tautan pendek dari SMS atau WhatsApp yang mengaku dari bank.',
  'Perbarui Android dan aplikasi secara rutin — banyak update menambal celah keamanan.',
  'Gunakan password berbeda untuk setiap akun, simpan di password manager.',
  'Hindari Wi-Fi publik tanpa password untuk transaksi perbankan.',
  'File APK dari grup chat adalah jalur penyebaran malware paling umum.',
  'Kunci layar dengan PIN atau biometrik agar data aman jika ponsel hilang.',
  'Cadangkan foto dan dokumen penting — ransomware tak berdaya melawan backup.',
];

// Summarises a license into what the dashboard needs: state + days left +
// how far through its validity window it is (for the progress bar).
function describeLicense(l, now) {
  const expiresMs = parseDbDate(l.expires_at);
  const createdMs = parseDbDate(l.created_at);
  let state = 'active';
  if (l.revoked) state = 'revoked';
  else if (expiresMs !== null && expiresMs < now) state = 'expired';

  const daysLeft = expiresMs === null ? null : Math.max(0, Math.ceil((expiresMs - now) / DAY_MS));
  let remainingPct = 100;
  if (expiresMs !== null && expiresMs > createdMs) {
    remainingPct = Math.round(Math.min(1, Math.max(0, (expiresMs - now) / (expiresMs - createdMs))) * 100);
  }
  return { ...l, state, daysLeft, remainingPct };
}

router.get('/', requireUser, (req, res) => {
  const now = Date.now();
  const licenses = getLicensesForUser.all(req.user.id).map((l) => describeLicense(l, now));
  const devices = getDevicesForUser.all(req.user.id);
  const orders = getRecentOrders.all(req.user.id);
  const sig = getSignatureStats.get();

  const devicesByLicense = {};
  for (const d of devices) (devicesByLicense[d.license_id] ||= []).push(d);

  // The license that best represents "am I protected": an active one that
  // lasts longest (lifetime beats any date).
  const active = licenses.filter((l) => l.state === 'active');
  active.sort((a, b) => (a.daysLeft === null ? Infinity : a.daysLeft) - (b.daysLeft === null ? Infinity : b.daysLeft));
  const primary = active.length ? active[active.length - 1] : null;

  const dayIndex = Math.floor(now / DAY_MS);
  res.render('portal/dashboard', {
    licenses,
    orders,
    devicesByLicense,
    primary,
    stats: {
      activeLicenses: active.length,
      devicesUsed: active.reduce((n, l) => n + (devicesByLicense[l.id] || []).length, 0),
      devicesMax: active.reduce((n, l) => n + l.max_devices, 0),
      signatures: sig.n,
      signaturesUpdated: sig.lastAdded,
      totalSpent: getTotalSpent.get(req.user.id).total,
    },
    tip: SECURITY_TIPS[dayIndex % SECURITY_TIPS.length],
    parseDbDate,
  });
});

// Hash lookup for the dashboard's "Cek File" tool. The browser computes the
// SHA-256 locally (the file itself is never uploaded); this only answers
// whether that hash is a known signature.
router.post('/check-hash', requireUser, rateLimit({ max: 30 }), requireCsrf, (req, res) => {
  const sha256 = String(req.body.sha256 || '').trim().toLowerCase();
  if (!SHA256_RE.test(sha256)) {
    return res.status(400).json({ error: 'invalid_sha256' });
  }
  const row = getBySha256.get(sha256);
  res.json(row
    ? { sha256, malicious: true, threatName: row.threat_name, severity: row.severity }
    : { sha256, malicious: false });
});

router.get('/products', (req, res) => {
  const products = getActiveProducts.all();
  res.render('portal/products', { products, error: req.query.error || null });
});

module.exports = router;
