'use strict';

const { rateLimit } = require('./rateLimit');

// Independent limiter instances (each rateLimit() call gets its own `hits`
// Map — see rateLimit.js) so traffic on one of these never shares budget
// with another.
module.exports = {
  loginLimiter: rateLimit({ max: 10 }),
  registerLimiter: rateLimit({ max: 5 }),
  webhookLimiter: rateLimit({ max: 120 }),
};
