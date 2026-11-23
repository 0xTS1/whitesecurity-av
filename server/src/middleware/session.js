'use strict';

const db = require('../db');
const { parseCookie } = require('../lib/cookies');
const { issueToken, verifyToken } = require('../lib/csrf');
const { SESSION_COOKIE_NAME } = require('../config');

const getSession = db.prepare(
  `SELECT s.id as session_id, u.* FROM web_sessions s
   JOIN users u ON u.id = s.user_id
   WHERE s.id = ? AND s.expires_at > datetime('now')`
);
// At most one write per session per minute, however many requests it makes.
const touchSession = db.prepare(
  `UPDATE web_sessions SET last_seen_at = datetime('now')
   WHERE id = ? AND (last_seen_at IS NULL OR last_seen_at < datetime('now', '-60 seconds'))`
);

// Mounted once on all of /portal. Attaches req.user (or null) from the
// session cookie — does NOT itself block unauthenticated requests (that's
// requireUser/requireAdmin below), so public portal pages can still read
// req.user to e.g. show a login link vs. a dashboard link.
function attachUser(req, res, next) {
  const cookies = parseCookie(req.headers.cookie);
  const token = cookies[SESSION_COOKIE_NAME];
  req.user = token ? getSession.get(token) || null : null;
  req.sessionId = req.user ? token : null;
  if (req.sessionId) touchSession.run(req.sessionId);

  // Available to every EJS template under /portal without passing it
  // through every single res.render() call.
  res.locals.user = req.user;
  res.locals.csrfToken = req.user ? issueToken(req.sessionId) : null;
  next();
}

function requireUser(req, res, next) {
  if (!req.user) return res.redirect('/antivirus/login');
  next();
}

function requireAdmin(req, res, next) {
  if (!req.user) return res.redirect('/antivirus/login');
  if (!req.user.is_admin) return res.status(403).send('Forbidden');
  next();
}

// Guards state-changing POST/PUT/DELETE routes that require an established
// session (everything under /portal except the Midtrans webhook, which is
// signature-verified instead, and login/register, which run pre-session).
function requireCsrf(req, res, next) {
  if (!req.sessionId || !verifyToken(req.sessionId, req.body && req.body._csrf)) {
    return res.status(403).send('Invalid or missing CSRF token.');
  }
  next();
}

module.exports = { attachUser, requireUser, requireAdmin, requireCsrf };
