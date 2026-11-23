'use strict';

const crypto = require('node:crypto');
const express = require('express');
const db = require('../../db');
const { hashPassword, verifyPassword } = require('../../lib/passwords');
const { verifyToken } = require('../../lib/csrf');
const { serializeCookie } = require('../../lib/cookies');
const { loginLimiter, registerLimiter } = require('../../middleware/portalRateLimit');
const { SESSION_COOKIE_NAME, NODE_ENV } = require('../../config');
const { clientIp, userAgent } = require('../../lib/clientInfo');

const router = express.Router();

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const SESSION_DAYS = 30;

const getUserByEmail = db.prepare('SELECT * FROM users WHERE email = ?');
const insertUser = db.prepare(
  'INSERT INTO users (email, password_hash, name) VALUES (?, ?, ?)'
);
const insertSession = db.prepare(
  `INSERT INTO web_sessions (id, user_id, expires_at, last_seen_at, ip, user_agent)
   VALUES (?, ?, datetime('now', '+${SESSION_DAYS} days'), datetime('now'), ?, ?)`
);
const touchLastLogin = db.prepare("UPDATE users SET last_login_at = datetime('now') WHERE id = ?");
const deleteSession = db.prepare('DELETE FROM web_sessions WHERE id = ?');

function createSessionCookie(req, res, userId) {
  const token = crypto.randomBytes(32).toString('hex');
  insertSession.run(token, userId, clientIp(req), userAgent(req));
  touchLastLogin.run(userId);
  res.setHeader(
    'Set-Cookie',
    serializeCookie(SESSION_COOKIE_NAME, token, {
      maxAgeSec: SESSION_DAYS * 86400,
      secure: NODE_ENV === 'production',
    })
  );
}

router.get('/register', (req, res) => {
  if (req.user) return res.redirect('/antivirus');
  res.render('portal/register', { error: req.query.error || null });
});

router.post('/register', registerLimiter, (req, res) => {
  const email = String(req.body.email || '').trim().toLowerCase();
  const password = String(req.body.password || '');
  const name = String(req.body.name || '').trim() || null;

  if (!EMAIL_RE.test(email)) {
    return res.redirect('/antivirus/register?error=' + encodeURIComponent('Email tidak valid.'));
  }
  if (password.length < 8) {
    return res.redirect('/antivirus/register?error=' + encodeURIComponent('Password minimal 8 karakter.'));
  }
  if (getUserByEmail.get(email)) {
    return res.redirect('/antivirus/register?error=' + encodeURIComponent('Email sudah terdaftar.'));
  }

  const result = insertUser.run(email, hashPassword(password), name);
  createSessionCookie(req, res, result.lastInsertRowid);
  res.redirect('/antivirus');
});

router.get('/login', (req, res) => {
  if (req.user) return res.redirect('/antivirus');
  res.render('portal/login', { error: req.query.error || null, next: req.query.next || '/antivirus' });
});

router.post('/login', loginLimiter, (req, res) => {
  const email = String(req.body.email || '').trim().toLowerCase();
  const password = String(req.body.password || '');
  const next = typeof req.body.next === 'string' && req.body.next.startsWith('/antivirus')
    ? req.body.next
    : '/antivirus';

  const user = getUserByEmail.get(email);
  if (!user || !verifyPassword(password, user.password_hash)) {
    return res.redirect(
      '/antivirus/login?error=' + encodeURIComponent('Email atau password salah.') +
      '&next=' + encodeURIComponent(next)
    );
  }

  createSessionCookie(req, res, user.id);
  res.redirect(next);
});

router.post('/logout', (req, res) => {
  if (req.sessionId && verifyToken(req.sessionId, req.body._csrf)) {
    deleteSession.run(req.sessionId);
  }
  res.setHeader('Set-Cookie', serializeCookie(SESSION_COOKIE_NAME, '', { maxAgeSec: 0 }));
  res.redirect('/antivirus/login');
});

module.exports = router;
