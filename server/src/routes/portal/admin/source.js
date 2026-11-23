'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { execFile } = require('node:child_process');
const express = require('express');
const { requireCsrf } = require('../../../middleware/session');

const router = express.Router();

// Built by scripts/export-source.sh; never under public/ — only served here,
// behind requireAdmin (mounted in admin/index.js).
const ROOT = path.join(__dirname, '..', '..', '..', '..');
const EXPORT_DIR = path.join(ROOT, 'private-exports');
const SCRIPT = path.join(ROOT, 'scripts', 'export-source.sh');
const FILE_RE = /^whitesecurity-(android|server)-src-[A-Za-z0-9.\-]+\.zip$/;

function listExports() {
  try {
    return fs.readdirSync(EXPORT_DIR)
      .filter((f) => FILE_RE.test(f))
      .map((f) => {
        const st = fs.statSync(path.join(EXPORT_DIR, f));
        return { name: f, kind: f.includes('-android-') ? 'android' : 'server', size: st.size, mtime: st.mtime };
      })
      .sort((a, b) => a.kind.localeCompare(b.kind));
  } catch {
    return [];
  }
}

router.get('/', (req, res) => {
  res.render('portal/admin/source', { files: listExports(), notice: req.query.notice || null });
});

router.get('/download/:name', (req, res) => {
  const name = req.params.name;
  if (!FILE_RE.test(name)) return res.status(404).send('Not found');
  const file = path.join(EXPORT_DIR, name);
  if (!fs.existsSync(file)) return res.status(404).send('Not found');
  res.setHeader('Cache-Control', 'no-store');
  res.download(file, name);
});

router.post('/rebuild', requireCsrf, (req, res) => {
  execFile(SCRIPT, [], { timeout: 120000 }, (err) => {
    const notice = err ? 'Gagal membuat ulang arsip: ' + err.message.split('\n')[0] : 'Arsip source code diperbarui dari kode terbaru.';
    res.redirect('/antivirus/admin/source?notice=' + encodeURIComponent(notice));
  });
});

module.exports = router;
