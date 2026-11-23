'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');
const { DB_PATH } = require('./config');

fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });

const db = new DatabaseSync(DB_PATH);
db.exec('PRAGMA journal_mode = WAL;');
db.exec('PRAGMA foreign_keys = ON;');

db.exec(`
  CREATE TABLE IF NOT EXISTS licenses (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    license_key  TEXT UNIQUE NOT NULL,
    plan         TEXT NOT NULL DEFAULT 'premium',
    max_devices  INTEGER NOT NULL DEFAULT 1,
    expires_at   TEXT,
    revoked      INTEGER NOT NULL DEFAULT 0,
    note         TEXT,
    created_at   TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS license_devices (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    license_id    INTEGER NOT NULL REFERENCES licenses(id) ON DELETE CASCADE,
    device_id     TEXT NOT NULL,
    activated_at  TEXT NOT NULL DEFAULT (datetime('now')),
    last_seen_at  TEXT,
    UNIQUE(license_id, device_id)
  );

  CREATE TABLE IF NOT EXISTS malware_signatures (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    sha256       TEXT UNIQUE NOT NULL,
    threat_name  TEXT NOT NULL,
    severity     TEXT NOT NULL DEFAULT 'medium',
    source       TEXT,
    added_at     TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS users (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    email          TEXT UNIQUE NOT NULL,
    password_hash  TEXT NOT NULL,
    name           TEXT,
    is_admin       INTEGER NOT NULL DEFAULT 0,
    created_at     TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS web_sessions (
    id          TEXT PRIMARY KEY,
    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    expires_at  TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS products (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    slug           TEXT UNIQUE NOT NULL,
    name           TEXT NOT NULL,
    description    TEXT,
    devices        INTEGER NOT NULL DEFAULT 1,
    duration_days  INTEGER NOT NULL,
    price_idr      INTEGER NOT NULL,
    active         INTEGER NOT NULL DEFAULT 1,
    created_at     TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS orders (
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id                 TEXT UNIQUE NOT NULL,
    user_id                  INTEGER NOT NULL REFERENCES users(id),
    product_id               INTEGER NOT NULL REFERENCES products(id),
    amount                   INTEGER NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'pending',
    midtrans_transaction_id  TEXT,
    payment_type             TEXT,
    license_id               INTEGER REFERENCES licenses(id),
    created_at               TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at               TEXT NOT NULL DEFAULT (datetime('now')),
    paid_at                  TEXT
  );
`);

// SQLite has no migration framework here (see README) — schema changes to
// *existing* tables must be additive and idempotent by hand. This checks
// PRAGMA table_info before ALTER TABLE so re-running it on an
// already-migrated DB (e.g. every server restart) never throws.
function ensureColumn(table, column, ddl) {
  const cols = db.prepare(`PRAGMA table_info(${table})`).all();
  if (!cols.some((c) => c.name === column)) {
    db.exec(`ALTER TABLE ${table} ADD COLUMN ${ddl}`);
  }
}

ensureColumn('licenses', 'user_id', 'user_id INTEGER REFERENCES users(id)');
// Activity tracking for /antivirus/admin/monitoring.
ensureColumn('users', 'last_login_at', 'last_login_at TEXT');
ensureColumn('web_sessions', 'last_seen_at', 'last_seen_at TEXT');
ensureColumn('web_sessions', 'ip', 'ip TEXT');
ensureColumn('web_sessions', 'user_agent', 'user_agent TEXT');

module.exports = db;
module.exports.ensureColumn = ensureColumn;
