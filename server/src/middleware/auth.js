'use strict';

const crypto = require('node:crypto');
const { APP_API_KEY } = require('../config');

const expected = Buffer.from(APP_API_KEY);

function requireApiKey(req, res, next) {
  const provided = req.get('X-Api-Key') || '';
  const providedBuf = Buffer.from(provided);

  const match =
    providedBuf.length === expected.length &&
    crypto.timingSafeEqual(providedBuf, expected);

  if (!match) {
    return res.status(401).json({ error: 'invalid_api_key' });
  }
  next();
}

module.exports = { requireApiKey };
