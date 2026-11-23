'use strict';

const path = require('node:path');
const express = require('express');
const { PORT } = require('./config');
const { requireApiKey } = require('./middleware/auth');
const { rateLimit } = require('./middleware/rateLimit');
const { attachUser, requireAdmin } = require('./middleware/session');
const licenseRoutes = require('./routes/license');
const malwareRoutes = require('./routes/malware');

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '64kb' }));
app.use(express.urlencoded({ extended: false, limit: '64kb' }));

app.get('/healthz', (req, res) => {
  res.json({ status: 'ok', service: 'whitesecurity-server' });
});

// --- Android app API — untouched by everything below this block. ---
app.use('/api/v1', requireApiKey, rateLimit({ max: 60 }));
app.use('/api/v1/license', licenseRoutes);
app.use('/api/v1/malware', malwareRoutes);
app.use('/api/v1/account', require('./routes/account'));

// --- Web dashboard (users + admin) + Midtrans payments. Server-rendered,
// same-origin, cookie-session auth — entirely separate from /api/v1 above.
// Served under /antivirus publicly; the "portal" name only lives on disk
// (routes/portal/, views/portal/) — that's unrelated to the URL prefix. ---
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));
app.use('/antivirus/assets', express.static(path.join(__dirname, '..', 'public', 'portal')));
app.use('/antivirus', attachUser);
app.use('/antivirus', require('./routes/portal/auth'));
app.use('/antivirus', require('./routes/portal/dashboard'));
app.use('/antivirus', require('./routes/portal/download'));
app.use('/antivirus/orders', require('./routes/portal/orders'));
app.use('/antivirus/payments', require('./routes/portal/payments'));
app.use('/antivirus/admin', requireAdmin, require('./routes/portal/admin'));

app.use((req, res) => {
  res.status(404).json({ error: 'not_found' });
});

// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  console.error('[unhandled]', err);
  res.status(500).json({ error: 'internal_error' });
});

// HOST=127.0.0.1 in .env keeps the port reachable only via the reverse proxy.
const HOST = process.env.HOST || '0.0.0.0';
app.listen(PORT, HOST, () => {
  console.log(`WhiteSecurity server listening on ${HOST}:${PORT}`);
});
