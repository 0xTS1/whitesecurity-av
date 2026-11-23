'use strict';

const fs = require('node:fs');
const path = require('node:path');
const express = require('express');
const db = require('../../db');
const { clientIp, userAgent } = require('../../lib/clientInfo');
const { fmtDate } = require('../../lib/dates');

const router = express.Router();

// Published by scripts/publish-apk.sh — kept outside public/ so the file is
// only served through the route below (proper headers + download count).
const DOWNLOAD_DIR = path.join(__dirname, '..', '..', '..', 'downloads');
const MANIFEST = path.join(DOWNLOAD_DIR, 'latest.json');

db.exec(`
  CREATE TABLE IF NOT EXISTS app_downloads (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    version     TEXT NOT NULL,
    ip          TEXT,
    user_agent  TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
  );
`);
const recordDownload = db.prepare('INSERT INTO app_downloads (version, ip, user_agent) VALUES (?, ?, ?)');

// { version, versionCode, file, sha256, size, releasedAt, minAndroid }
function latestRelease() {
  try {
    const meta = JSON.parse(fs.readFileSync(MANIFEST, 'utf8'));
    const file = path.join(DOWNLOAD_DIR, path.basename(meta.file));
    if (!fs.existsSync(file)) return null;
    return { ...meta, filePath: file };
  } catch {
    return null;
  }
}

router.get('/download', (req, res) => {
  const release = latestRelease();
  res.render('portal/download', { release, fmtDate });
});

router.get('/download/apk', (req, res) => {
  const release = latestRelease();
  if (!release) return res.status(404).send('Aplikasi belum tersedia untuk diunduh.');
  recordDownload.run(release.version, clientIp(req), userAgent(req));
  res.setHeader('Content-Type', 'application/vnd.android.package-archive');
  res.setHeader('Cache-Control', 'no-store');
  res.download(release.filePath, `WhiteSecurity-v${release.version}.apk`);
});

// Lets the Android app (or anyone) check for a newer build.
router.get('/download/latest.json', (req, res) => {
  const release = latestRelease();
  if (!release) return res.status(404).json({ error: 'not_available' });
  const { filePath, ...meta } = release;
  res.json({ ...meta, url: '/antivirus/download/apk' });
});

module.exports = router;
