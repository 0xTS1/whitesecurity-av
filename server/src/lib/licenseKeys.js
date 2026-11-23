'use strict';

const crypto = require('node:crypto');
const db = require('../db');

const insertLicense = db.prepare(
  `INSERT INTO licenses (license_key, plan, max_devices, expires_at, note, user_id)
   VALUES (?, ?, ?, ?, ?, ?)`
);

function genLicenseKey() {
  const group = () => crypto.randomBytes(2).toString('hex').toUpperCase();
  return `WSEC-${group()}-${group()}-${group()}-${group()}`;
}

// Single place that mints a license — used by admin-cli.js (manual grants)
// and by the payment fulfillment path (src/lib/fulfillment.js) alike.
function createLicense({ plan = 'premium', maxDevices = 1, days = null, note = null, userId = null }) {
  const expiresAt = days === null ? null : new Date(Date.now() + days * 86400_000).toISOString();
  const key = genLicenseKey();
  insertLicense.run(key, plan, maxDevices, expiresAt, note, userId);
  return { key, plan, maxDevices, expiresAt };
}

module.exports = { genLicenseKey, createLicense };
