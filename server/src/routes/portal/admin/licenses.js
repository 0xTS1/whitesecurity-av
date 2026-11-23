'use strict';

const express = require('express');
const db = require('../../../db');
const { createLicense } = require('../../../lib/licenseKeys');
const { requireCsrf } = require('../../../middleware/session');

const router = express.Router();

const listLicenses = db.prepare(`
  SELECT l.*, u.email as owner_email,
    (SELECT COUNT(*) FROM license_devices WHERE license_id = l.id) as device_count
  FROM licenses l LEFT JOIN users u ON u.id = l.user_id
  ORDER BY l.created_at DESC
`);
const revoke = db.prepare('UPDATE licenses SET revoked = 1 WHERE id = ?');
const unrevoke = db.prepare('UPDATE licenses SET revoked = 0 WHERE id = ?');

router.get('/', (req, res) => {
  res.render('portal/admin/licenses', { licenses: listLicenses.all() });
});

router.post('/', requireCsrf, (req, res) => {
  const plan = req.body.plan || 'premium';
  const maxDevices = Number(req.body.devices || 1);
  const days = req.body.days ? Number(req.body.days) : null;
  const note = req.body.note || null;
  createLicense({ plan, maxDevices, days, note });
  res.redirect('/antivirus/admin/licenses');
});

router.post('/:id/revoke', requireCsrf, (req, res) => {
  revoke.run(Number(req.params.id));
  res.redirect('/antivirus/admin/licenses');
});

router.post('/:id/unrevoke', requireCsrf, (req, res) => {
  unrevoke.run(Number(req.params.id));
  res.redirect('/antivirus/admin/licenses');
});

module.exports = router;
