'use strict';

const express = require('express');
const db = require('../../../db');

const router = express.Router();

const stats = {
  userCount: db.prepare('SELECT COUNT(*) as n FROM users'),
  activeLicenseCount: db.prepare(
    "SELECT COUNT(*) as n FROM licenses WHERE revoked = 0 AND (expires_at IS NULL OR expires_at > datetime('now'))"
  ),
  revenueThisMonth: db.prepare(
    "SELECT COALESCE(SUM(amount), 0) as total FROM orders WHERE status = 'paid' AND paid_at >= date('now', 'start of month')"
  ),
  pendingOrderCount: db.prepare("SELECT COUNT(*) as n FROM orders WHERE status = 'pending'"),
};
// app_downloads is created by routes/portal/download.js, which loads first.
const downloadStats = db.prepare(
  "SELECT COUNT(*) as total, COALESCE(SUM(created_at >= datetime('now', '-7 days')), 0) as week FROM app_downloads"
);

router.get('/', (req, res) => {
  res.render('portal/admin/index', {
    userCount: stats.userCount.get().n,
    activeLicenseCount: stats.activeLicenseCount.get().n,
    revenueThisMonth: stats.revenueThisMonth.get().total,
    pendingOrderCount: stats.pendingOrderCount.get().n,
    downloads: downloadStats.get(),
  });
});

router.use('/monitoring', require('./monitoring'));
router.use('/users', require('./users'));
router.use('/licenses', require('./licenses'));
router.use('/products', require('./products'));
router.use('/orders', require('./orders'));
router.use('/malware', require('./malware'));
router.use('/source', require('./source'));

module.exports = router;
