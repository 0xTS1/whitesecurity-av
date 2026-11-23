'use strict';

const express = require('express');
const db = require('../../../db');
const { requireCsrf } = require('../../../middleware/session');

const router = express.Router();

const listProducts = db.prepare('SELECT * FROM products ORDER BY price_idr ASC');
const upsertProduct = db.prepare(`
  INSERT INTO products (slug, name, description, devices, duration_days, price_idr)
  VALUES (?, ?, ?, ?, ?, ?)
  ON CONFLICT(slug) DO UPDATE SET name = excluded.name,
                                   description = excluded.description,
                                   devices = excluded.devices,
                                   duration_days = excluded.duration_days,
                                   price_idr = excluded.price_idr
`);
const toggleActive = db.prepare('UPDATE products SET active = 1 - active WHERE id = ?');

router.get('/', (req, res) => {
  res.render('portal/admin/products', { products: listProducts.all() });
});

router.post('/', requireCsrf, (req, res) => {
  const slug = String(req.body.slug || '').trim();
  const name = String(req.body.name || '').trim();
  const devices = Number(req.body.devices || 1);
  const days = Number(req.body.days);
  const price = Number(req.body.price);
  if (slug && name && days && price) {
    upsertProduct.run(slug, name, req.body.description || null, devices, days, price);
  }
  res.redirect('/antivirus/admin/products');
});

router.post('/:id/toggle', requireCsrf, (req, res) => {
  toggleActive.run(Number(req.params.id));
  res.redirect('/antivirus/admin/products');
});

module.exports = router;
