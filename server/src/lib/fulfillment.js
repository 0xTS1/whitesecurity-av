'use strict';

const db = require('../db');
const { createLicense } = require('./licenseKeys');

const PAID_STATUSES = new Set(['capture', 'settlement']);
const TERMINAL_FAIL = new Set(['deny', 'cancel', 'expire']);

const getOrderByOrderId = db.prepare('SELECT * FROM orders WHERE order_id = ?');
const getProduct = db.prepare('SELECT * FROM products WHERE id = ?');
const getLicenseByKey = db.prepare('SELECT id FROM licenses WHERE license_key = ?');

// Atomic claim: only the caller that actually flips pending->processing
// proceeds to mint a license. This is what makes fulfillment safe against a
// duplicate/retried Midtrans webhook delivery, or the admin's manual
// "mark as paid" override firing on an order a webhook is also processing.
const claimProcessing = db.prepare(
  `UPDATE orders SET status = 'processing', updated_at = datetime('now')
   WHERE order_id = ? AND status = 'pending'`
);
const markPaid = db.prepare(
  `UPDATE orders SET status = 'paid', license_id = ?, midtrans_transaction_id = ?,
   payment_type = ?, paid_at = datetime('now'), updated_at = datetime('now')
   WHERE order_id = ?`
);
const markFailed = db.prepare(
  `UPDATE orders SET status = ?, updated_at = datetime('now')
   WHERE order_id = ? AND status IN ('pending', 'processing')`
);
const revertPending = db.prepare(
  `UPDATE orders SET status = 'pending', updated_at = datetime('now') WHERE order_id = ?`
);

// Single fulfillment path for both the real Midtrans webhook and the
// admin's manual override — midtransStatus is whatever came back from
// getTransactionStatus() (or a synthetic {transaction_status:'settlement'}
// object for the manual override).
function fulfillOrder(orderId, midtransStatus) {
  const order = getOrderByOrderId.get(orderId);
  if (!order) return { ok: false, reason: 'unknown_order' };
  if (order.status === 'paid') return { ok: true, reason: 'already_paid' };

  const txStatus = midtransStatus.transaction_status;

  if (TERMINAL_FAIL.has(txStatus)) {
    markFailed.run(txStatus, orderId);
    return { ok: true, reason: txStatus };
  }
  if (!PAID_STATUSES.has(txStatus)) {
    return { ok: true, reason: 'not_yet_paid' };
  }

  const claimed = claimProcessing.run(orderId);
  if (claimed.changes === 0) {
    return { ok: true, reason: 'already_claimed_or_paid' };
  }

  try {
    const product = getProduct.get(order.product_id);
    const license = createLicense({
      plan: product.slug,
      maxDevices: product.devices,
      days: product.duration_days,
      note: `order ${orderId}`,
      userId: order.user_id,
    });
    const licenseRow = getLicenseByKey.get(license.key);
    markPaid.run(
      licenseRow.id,
      midtransStatus.transaction_id || null,
      midtransStatus.payment_type || null,
      orderId
    );
    return { ok: true, reason: 'fulfilled', licenseKey: license.key };
  } catch (err) {
    revertPending.run(orderId); // let a retry (webhook redelivery or admin override) try again
    throw err;
  }
}

module.exports = { fulfillOrder };
