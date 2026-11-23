'use strict';

const express = require('express');
const db = require('../../../db');
const { requireCsrf } = require('../../../middleware/session');
const { parseDbDate, fmtDate, fmtDateTime, timeAgo } = require('../../../lib/dates');
const { describeUserAgent } = require('../../../lib/clientInfo');

const router = express.Router();

const ONLINE_MINUTES = 5;
const CHART_DAYS = 14;

const stats = {
  totalUsers: db.prepare('SELECT COUNT(*) as n FROM users'),
  // Web sessions + app tokens (app_tokens is created by routes/account.js).
  onlineNow: db.prepare(
    `SELECT COUNT(DISTINCT user_id) as n FROM (
       SELECT user_id FROM web_sessions
       WHERE expires_at > datetime('now') AND last_seen_at >= datetime('now', '-${ONLINE_MINUTES} minutes')
       UNION ALL
       SELECT user_id FROM app_tokens
       WHERE expires_at > datetime('now') AND last_used_at >= datetime('now', '-${ONLINE_MINUTES} minutes'))`
  ),
  active24h: db.prepare(
    `SELECT COUNT(DISTINCT user_id) as n FROM (
       SELECT user_id FROM web_sessions WHERE last_seen_at >= datetime('now', '-1 day')
       UNION ALL
       SELECT user_id FROM app_tokens WHERE last_used_at >= datetime('now', '-1 day'))`
  ),
  newUsers7d: db.prepare("SELECT COUNT(*) as n FROM users WHERE created_at >= datetime('now', '-7 days')"),
  devicesActive24h: db.prepare(
    "SELECT COUNT(*) as n FROM license_devices WHERE last_seen_at >= datetime('now', '-1 day')"
  ),
  devicesTotal: db.prepare('SELECT COUNT(*) as n FROM license_devices'),
};

const signupsPerDay = db.prepare(
  `SELECT date(created_at) as day, COUNT(*) as n FROM users
   WHERE created_at >= date('now', '-${CHART_DAYS - 1} days') GROUP BY day`
);

// One row per user with their most recent web activity, the browser/IP of
// their latest session, and a license/device summary.
const userActivity = db.prepare(`
  SELECT u.id, u.email, u.name, u.is_admin, u.created_at, u.last_login_at,
    (SELECT MAX(t) FROM (
       SELECT MAX(last_seen_at) as t FROM web_sessions WHERE user_id = u.id
       UNION ALL SELECT MAX(last_used_at) FROM app_tokens WHERE user_id = u.id)) as last_seen_at,
    (SELECT COUNT(*) FROM web_sessions WHERE user_id = u.id AND expires_at > datetime('now')) +
    (SELECT COUNT(*) FROM app_tokens WHERE user_id = u.id AND expires_at > datetime('now')) as session_count,
    (SELECT COUNT(*) FROM app_tokens WHERE user_id = u.id AND expires_at > datetime('now')) as app_session_count,
    (SELECT ip FROM web_sessions WHERE user_id = u.id ORDER BY COALESCE(last_seen_at, created_at) DESC LIMIT 1) as last_ip,
    (SELECT user_agent FROM web_sessions WHERE user_id = u.id ORDER BY COALESCE(last_seen_at, created_at) DESC LIMIT 1) as last_ua,
    (SELECT COUNT(*) FROM licenses WHERE user_id = u.id AND revoked = 0
       AND (expires_at IS NULL OR expires_at > datetime('now'))) as active_licenses,
    (SELECT COUNT(*) FROM license_devices d JOIN licenses l ON l.id = d.license_id WHERE l.user_id = u.id) as device_count,
    (SELECT MAX(d.last_seen_at) FROM license_devices d JOIN licenses l ON l.id = d.license_id WHERE l.user_id = u.id) as device_last_seen
  FROM users u
  ORDER BY COALESCE(last_seen_at, u.last_login_at, u.created_at) DESC
`);

const recentActivity = db.prepare(`
  SELECT * FROM (
    SELECT 'register' as kind, u.created_at as at, u.email, NULL as detail FROM users u
    UNION ALL
    SELECT 'login', s.created_at, u.email, s.ip FROM web_sessions s JOIN users u ON u.id = s.user_id
    UNION ALL
    SELECT 'app_login', t.created_at, u.email, t.device_label FROM app_tokens t JOIN users u ON u.id = t.user_id
    UNION ALL
    SELECT 'device', d.activated_at, u.email, d.device_id
      FROM license_devices d JOIN licenses l ON l.id = d.license_id LEFT JOIN users u ON u.id = l.user_id
    UNION ALL
    SELECT 'order', o.created_at, u.email, p.name FROM orders o
      JOIN users u ON u.id = o.user_id JOIN products p ON p.id = o.product_id
    UNION ALL
    SELECT 'paid', o.paid_at, u.email, 'Rp' || o.amount FROM orders o
      JOIN users u ON u.id = o.user_id WHERE o.paid_at IS NOT NULL
  ) ORDER BY at DESC LIMIT 30
`);

const deleteUserSessions = db.prepare('DELETE FROM web_sessions WHERE user_id = ?');
const deleteUserAppTokens = db.prepare('DELETE FROM app_tokens WHERE user_id = ?');

function activityState(row, now) {
  const seen = parseDbDate(row.last_seen_at);
  if (seen && now - seen < ONLINE_MINUTES * 60000) return 'online';
  if (seen && now - seen < 86400000) return 'today';
  return 'inactive';
}

router.get('/', (req, res) => {
  const now = Date.now();
  const q = String(req.query.q || '').trim().toLowerCase();
  const filter = ['online', 'today', 'inactive'].includes(req.query.filter) ? req.query.filter : 'all';

  const users = userActivity.all().map((u) => ({
    ...u,
    state: activityState(u, now),
    browser: u.last_ua ? describeUserAgent(u.last_ua) : null,
  }));
  const shown = users.filter((u) =>
    (filter === 'all' || u.state === filter) &&
    (!q || u.email.toLowerCase().includes(q) || (u.name || '').toLowerCase().includes(q))
  );

  const counts = { all: users.length, online: 0, today: 0, inactive: 0 };
  for (const u of users) counts[u.state]++;

  // Fill missing days so the chart always shows a full window.
  const byDay = Object.fromEntries(signupsPerDay.all().map((r) => [r.day, r.n]));
  const chart = [];
  for (let i = CHART_DAYS - 1; i >= 0; i--) {
    const day = new Date(now - i * 86400000).toISOString().slice(0, 10);
    chart.push({ day, n: byDay[day] || 0 });
  }

  res.render('portal/admin/monitoring', {
    stats: {
      totalUsers: stats.totalUsers.get().n,
      onlineNow: stats.onlineNow.get().n,
      active24h: stats.active24h.get().n,
      newUsers7d: stats.newUsers7d.get().n,
      devicesActive24h: stats.devicesActive24h.get().n,
      devicesTotal: stats.devicesTotal.get().n,
    },
    users: shown,
    counts,
    filter,
    q,
    chart,
    chartMax: Math.max(1, ...chart.map((c) => c.n)),
    activity: recentActivity.all(),
    live: req.query.live === '1',
    notice: req.query.notice || null,
    fmtDate,
    fmtDateTime,
    timeAgo,
  });
});

// Ends every web session and app login of a user (e.g. suspicious
// activity). Their licenses and license-activated devices are unaffected.
router.post('/users/:id/logout', requireCsrf, (req, res) => {
  const id = Number(req.params.id);
  const removed = deleteUserSessions.run(id).changes + deleteUserAppTokens.run(id).changes;
  const notice = id === req.user.id
    ? 'Semua sesi Anda diakhiri.'
    : `${removed} sesi diakhiri.`;
  res.redirect('/antivirus/admin/monitoring?notice=' + encodeURIComponent(notice));
});

module.exports = router;
