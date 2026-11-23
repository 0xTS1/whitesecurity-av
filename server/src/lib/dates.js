'use strict';

// SQLite's datetime('now') gives "YYYY-MM-DD HH:MM:SS" in UTC with no zone
// marker; JS would read that as local time. CLI-made rows are full ISO.
function parseDbDate(value) {
  if (!value) return null;
  const iso = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value) ? value.replace(' ', 'T') + 'Z' : value;
  const ms = new Date(iso).getTime();
  return Number.isNaN(ms) ? null : ms;
}

function fmtDate(value) {
  const ms = parseDbDate(value);
  return ms ? new Date(ms).toLocaleDateString('id-ID', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'Asia/Jakarta' }) : '—';
}

function fmtDateTime(value) {
  const ms = parseDbDate(value);
  return ms ? new Date(ms).toLocaleString('id-ID', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Jakarta' }) : '—';
}

function timeAgo(value) {
  const ms = parseDbDate(value);
  if (!ms) return 'belum pernah';
  const m = Math.round((Date.now() - ms) / 60000);
  if (m < 2) return 'baru saja';
  if (m < 60) return m + ' menit lalu';
  if (m < 1440) return Math.round(m / 60) + ' jam lalu';
  return Math.round(m / 1440) + ' hari lalu';
}

module.exports = { parseDbDate, fmtDate, fmtDateTime, timeAgo };
