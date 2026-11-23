'use strict';

const crypto = require('node:crypto');
const config = require('../config');

function hosts() {
  return config.midtrans.isProduction
    ? { snap: 'https://app.midtrans.com', api: 'https://api.midtrans.com' }
    : { snap: 'https://app.sandbox.midtrans.com', api: 'https://api.sandbox.midtrans.com' };
}

function authHeader() {
  return 'Basic ' + Buffer.from(config.midtrans.serverKey + ':').toString('base64');
}

// Creates a Snap transaction and returns its hosted-payment redirect URL —
// the browser just navigates there, no client-side JS/Snap.js needed.
async function createSnapTransaction({ orderId, grossAmount, customer }) {
  const res = await fetch(`${hosts().snap}/snap/v1/transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: authHeader() },
    body: JSON.stringify({
      transaction_details: { order_id: orderId, gross_amount: grossAmount },
      customer_details: customer,
    }),
  });
  if (!res.ok) throw new Error(`midtrans_snap_${res.status}`);
  return res.json(); // { token, redirect_url }
}

// Server-to-server re-check — fulfillment must act on THIS, never on the
// webhook notification body's transaction_status alone (that body can be
// replayed/spoofed even past signature checks on stale data).
async function getTransactionStatus(orderId) {
  const res = await fetch(`${hosts().api}/v2/${encodeURIComponent(orderId)}/status`, {
    headers: { Authorization: authHeader() },
  });
  if (!res.ok) throw new Error(`midtrans_status_${res.status}`);
  return res.json();
}

// signature_key = sha512(order_id + status_code + gross_amount + ServerKey).
// gross_amount MUST be used exactly as Midtrans sent it (a string like
// "150000.00") — never reformatted/renumbered before hashing.
function verifyNotificationSignature(body) {
  const { order_id, status_code, gross_amount, signature_key } = body || {};
  if (!order_id || !status_code || !gross_amount || !signature_key) return false;
  const expected = crypto
    .createHash('sha512')
    .update(String(order_id) + String(status_code) + String(gross_amount) + config.midtrans.serverKey)
    .digest('hex');
  const a = Buffer.from(expected, 'utf8');
  const b = Buffer.from(String(signature_key), 'utf8');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

module.exports = { createSnapTransaction, getTransactionStatus, verifyNotificationSignature };
