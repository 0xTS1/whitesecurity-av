'use strict';

const express = require('express');
const db = require('../db');

const router = express.Router();

const getLicense = db.prepare('SELECT * FROM licenses WHERE license_key = ?');
const getDevice = db.prepare(
  'SELECT * FROM license_devices WHERE license_id = ? AND device_id = ?'
);
const countDevices = db.prepare(
  'SELECT COUNT(*) AS n FROM license_devices WHERE license_id = ?'
);
const insertDevice = db.prepare(
  'INSERT INTO license_devices (license_id, device_id, last_seen_at) VALUES (?, ?, datetime(\'now\'))'
);
const touchDevice = db.prepare(
  'UPDATE license_devices SET last_seen_at = datetime(\'now\') WHERE id = ?'
);

function isExpired(license) {
  if (!license.expires_at) return false;
  return new Date(license.expires_at).getTime() < Date.now();
}

function evaluate(license) {
  if (!license) return { valid: false, reason: 'not_found' };
  if (license.revoked) return { valid: false, reason: 'revoked' };
  if (isExpired(license)) return { valid: false, reason: 'expired' };
  return { valid: true };
}

function licenseBody(license) {
  return {
    valid: true,
    plan: license.plan,
    expiresAt: license.expires_at,
    maxDevices: license.max_devices,
  };
}

function body(req) {
  return req.body && typeof req.body === 'object' ? req.body : {};
}

// Binds a device to a license (first call on a fresh install), subject to
// the license's max_devices limit. Idempotent for a device already bound.
router.post('/activate', (req, res) => {
  const { licenseKey, deviceId } = body(req);
  if (!licenseKey || !deviceId) {
    return res.status(400).json({ error: 'licenseKey_and_deviceId_required' });
  }

  const license = getLicense.get(licenseKey);
  const check = evaluate(license);
  if (!check.valid) {
    return res.status(403).json({ valid: false, reason: check.reason });
  }

  const existing = getDevice.get(license.id, deviceId);
  if (existing) {
    touchDevice.run(existing.id);
    return res.json(licenseBody(license));
  }

  const used = countDevices.get(license.id).n;
  if (used >= license.max_devices) {
    return res.status(403).json({ valid: false, reason: 'device_limit_reached' });
  }

  insertDevice.run(license.id, deviceId);
  return res.json(licenseBody(license));
});

// Re-checks an already-activated device. The app calls this periodically to
// detect expiry/revocation without re-sending the license key from the user.
router.post('/verify', (req, res) => {
  const { licenseKey, deviceId } = body(req);
  if (!licenseKey || !deviceId) {
    return res.status(400).json({ error: 'licenseKey_and_deviceId_required' });
  }

  const license = getLicense.get(licenseKey);
  const check = evaluate(license);
  if (!check.valid) {
    return res.status(403).json({ valid: false, reason: check.reason });
  }

  const existing = getDevice.get(license.id, deviceId);
  if (!existing) {
    return res.status(403).json({ valid: false, reason: 'device_not_activated' });
  }

  touchDevice.run(existing.id);
  return res.json(licenseBody(license));
});

module.exports = router;
