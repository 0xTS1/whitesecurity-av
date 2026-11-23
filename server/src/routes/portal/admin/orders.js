'use strict';

const express = require('express');
const db = require('../../../db');
const { fulfillOrder } = require('../../../lib/fulfillment');
const { requireCsrf } = require('../../../middleware/session');

const router = express.Router();

const listOrders = db.prepare(`
  SELECT o.*, u.email as user_email, p.name as product_name
  FROM orders o JOIN users u ON u.id = o.user_id JOIN products p ON p.id = o.product_id
  ORDER BY o.created_at DESC
`);

router.get('/', (req, res) => {
  res.render('portal/admin/orders', { orders: listOrders.all() });
});

// Ops escape hatch if a Midtrans webhook is ever lost/delayed — goes
// through the exact same idempotent fulfillOrder() as the real webhook, so
// it's a no-op on an already-paid order and safe to click more than once.
router.post('/:orderId/mark-paid', requireCsrf, (req, res) => {
  try {
    fulfillOrder(req.params.orderId, { transaction_status: 'settlement', payment_type: 'manual' });
  } catch (err) {
    console.error('[admin/orders] manual fulfillment failed', req.params.orderId, err);
  }
  res.redirect('/antivirus/admin/orders');
});

module.exports = router;
