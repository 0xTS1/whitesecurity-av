'use strict';

const express = require('express');
const db = require('../../../db');

const router = express.Router();

const listUsers = db.prepare(`
  SELECT u.*,
    (SELECT COUNT(*) FROM licenses WHERE user_id = u.id) as license_count,
    (SELECT COUNT(*) FROM orders WHERE user_id = u.id AND status = 'paid') as paid_order_count
  FROM users u ORDER BY u.created_at DESC
`);
const getUser = db.prepare('SELECT * FROM users WHERE id = ?');
const getLicensesForUser = db.prepare('SELECT * FROM licenses WHERE user_id = ? ORDER BY created_at DESC');
const getOrdersForUser = db.prepare(
  `SELECT o.*, p.name as product_name FROM orders o JOIN products p ON p.id = o.product_id
   WHERE o.user_id = ? ORDER BY o.created_at DESC`
);

router.get('/', (req, res) => {
  res.render('portal/admin/users', { users: listUsers.all() });
});

router.get('/:id', (req, res) => {
  const user = getUser.get(Number(req.params.id));
  if (!user) return res.status(404).send('User not found');
  res.render('portal/admin/user_detail', {
    viewedUser: user,
    licenses: getLicensesForUser.all(user.id),
    orders: getOrdersForUser.all(user.id),
  });
});

module.exports = router;
