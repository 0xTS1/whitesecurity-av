'use strict';

const crypto = require('node:crypto');
const express = require('express');
const db = require('../../db');
const config = require('../../config');
const midtrans = require('../../lib/midtrans');
const { requireUser, requireCsrf } = require('../../middleware/session');

const router = express.Router();

const getProduct = db.prepare('SELECT * FROM products WHERE id = ? AND active = 1');
const insertOrder = db.prepare(
  `INSERT INTO orders (order_id, user_id, product_id, amount, status)
   VALUES (?, ?, ?, ?, 'pending')`
);
const getOrdersForUser = db.prepare(
  `SELECT o.*, p.name as product_name FROM orders o
   JOIN products p ON p.id = o.product_id
   WHERE o.user_id = ? ORDER BY o.created_at DESC`
);

router.get('/', requireUser, (req, res) => {
  const orders = getOrdersForUser.all(req.user.id);
  res.render('portal/orders', { orders });
});

router.post('/', requireUser, requireCsrf, async (req, res) => {
  if (!config.midtrans.configured) {
    return res.redirect('/antivirus/products?error=' + encodeURIComponent('Pembayaran belum dikonfigurasi di server.'));
  }

  const product = getProduct.get(Number(req.body.productId));
  if (!product) {
    return res.redirect('/antivirus/products?error=' + encodeURIComponent('Produk tidak ditemukan.'));
  }

  const orderId = `WSEC-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`;
  insertOrder.run(orderId, req.user.id, product.id, product.price_idr);

  try {
    const transaction = await midtrans.createSnapTransaction({
      orderId,
      grossAmount: product.price_idr,
      customer: {
        first_name: req.user.name || req.user.email,
        email: req.user.email,
      },
    });
    res.redirect(transaction.redirect_url);
  } catch (err) {
    console.error('[portal/orders] midtrans create transaction failed', err);
    res.redirect('/antivirus/products?error=' + encodeURIComponent('Gagal membuat transaksi pembayaran. Coba lagi.'));
  }
});

module.exports = router;
