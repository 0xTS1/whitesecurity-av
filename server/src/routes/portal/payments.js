'use strict';

const express = require('express');
const config = require('../../config');
const midtrans = require('../../lib/midtrans');
const { fulfillOrder } = require('../../lib/fulfillment');
const { webhookLimiter } = require('../../middleware/portalRateLimit');

const router = express.Router();

// Midtrans calls this server-to-server — no session, no CSRF (there is no
// browser involved). Trust is established by the signature check below,
// not by anything a browser-based attacker could replay through a form.
router.post('/notification', webhookLimiter, async (req, res) => {
  if (!config.midtrans.configured) {
    // Without a real server key, a signature check against an empty key
    // would accept anything — reject up front instead of ever reaching it.
    return res.status(503).json({ error: 'payments_not_configured' });
  }

  const body = req.body || {};

  if (!midtrans.verifyNotificationSignature(body)) {
    console.error('[portal/payments] rejected notification with invalid signature', body.order_id);
    return res.status(403).json({ error: 'invalid_signature' });
  }

  // Defense in depth: never fulfill based on the notification body's
  // transaction_status alone — always re-check server-to-server.
  let status;
  try {
    status = await midtrans.getTransactionStatus(body.order_id);
  } catch (err) {
    console.error('[portal/payments] status re-check failed', body.order_id, err);
    return res.status(502).json({ error: 'status_check_failed' });
  }

  try {
    const result = fulfillOrder(status.order_id, status);
    if (!result.ok) {
      return res.status(404).json({ error: result.reason });
    }
    res.json({ ok: true, reason: result.reason });
  } catch (err) {
    console.error('[portal/payments] fulfillment error', status.order_id, err);
    res.status(500).json({ error: 'fulfillment_failed' });
  }
});

module.exports = router;
