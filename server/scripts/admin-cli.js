#!/usr/bin/env node
'use strict';

const path = require('node:path');
const db = require(path.join(__dirname, '..', 'src', 'db'));
const { createLicense } = require(path.join(__dirname, '..', 'src', 'lib', 'licenseKeys'));
const { hashPassword } = require(path.join(__dirname, '..', 'src', 'lib', 'passwords'));

function parseArgs(argv) {
  const [command, ...rest] = argv;
  const opts = {};
  const positional = [];
  for (let i = 0; i < rest.length; i++) {
    const tok = rest[i];
    if (tok.startsWith('--')) {
      const key = tok.slice(2);
      const next = rest[i + 1];
      if (next === undefined || next.startsWith('--')) {
        opts[key] = true;
      } else {
        opts[key] = next;
        i++;
      }
    } else {
      positional.push(tok);
    }
  }
  return { command, opts, positional };
}

function cmdGenLicense(opts) {
  const plan = opts.plan || 'premium';
  const maxDevices = Number(opts.devices || 1);
  const days = opts.days !== undefined ? Number(opts.days) : null;
  const note = opts.note || null;

  const license = createLicense({ plan, maxDevices, days, note });

  console.log('License created:');
  console.log(`  key:         ${license.key}`);
  console.log(`  plan:        ${license.plan}`);
  console.log(`  maxDevices:  ${license.maxDevices}`);
  console.log(`  expiresAt:   ${license.expiresAt || 'never'}`);
  if (note) console.log(`  note:        ${note}`);
}

function cmdListLicenses() {
  const rows = db.prepare('SELECT * FROM licenses ORDER BY created_at DESC').all();
  if (rows.length === 0) {
    console.log('(no licenses yet)');
    return;
  }
  for (const r of rows) {
    const used = db
      .prepare('SELECT COUNT(*) AS n FROM license_devices WHERE license_id = ?')
      .get(r.id).n;
    console.log(
      `${r.license_key}  plan=${r.plan}  devices=${used}/${r.max_devices}  ` +
      `expires=${r.expires_at || 'never'}  revoked=${!!r.revoked}` +
      (r.note ? `  note="${r.note}"` : '')
    );
  }
}

function cmdRevokeLicense(positional) {
  const key = positional[0];
  if (!key) {
    console.error('Usage: admin-cli revoke-license <license_key>');
    process.exit(1);
  }
  const result = db.prepare('UPDATE licenses SET revoked = 1 WHERE license_key = ?').run(key);
  console.log(result.changes > 0 ? `Revoked ${key}` : `No license found with key ${key}`);
}

function cmdAddMalwareHash(positional, opts) {
  const [sha256, threatName] = positional;
  const severity = opts.severity || 'medium';
  if (!sha256 || !threatName) {
    console.error('Usage: admin-cli add-malware-hash <sha256> <threat_name> [--severity low|medium|high|critical] [--source name]');
    process.exit(1);
  }
  db.prepare(
    `INSERT INTO malware_signatures (sha256, threat_name, severity, source)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(sha256) DO UPDATE SET threat_name = excluded.threat_name,
                                        severity = excluded.severity,
                                        source = excluded.source`
  ).run(sha256.toLowerCase(), threatName, severity, opts.source || null);
  console.log(`Upserted signature for ${sha256}`);
}

function cmdImportCsv(positional, opts) {
  const file = positional[0];
  if (!file) {
    console.error('Usage: admin-cli import-csv <path.csv> [--source name]');
    console.error('CSV format (no header): sha256,threat_name,severity');
    process.exit(1);
  }
  const fs = require('node:fs');
  const lines = fs.readFileSync(file, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean);
  const stmt = db.prepare(
    `INSERT INTO malware_signatures (sha256, threat_name, severity, source)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(sha256) DO UPDATE SET threat_name = excluded.threat_name,
                                        severity = excluded.severity,
                                        source = excluded.source`
  );
  let count = 0;
  for (const line of lines) {
    const [sha256, threatName, severity] = line.split(',').map((s) => s.trim());
    if (!sha256 || !threatName) continue;
    stmt.run(sha256.toLowerCase(), threatName, severity || 'medium', opts.source || file);
    count++;
  }
  console.log(`Imported/updated ${count} signatures from ${file}`);
}

// Reads a line from stdin without echoing it — used for admin passwords so
// they never land in shell history or `ps` output (unlike a --password flag).
// Falls back to a plain (non-hidden) readline prompt when stdin isn't a TTY
// (e.g. piped input in a script) since raw mode requires a real terminal —
// still never touches argv/history/ps either way.
// Piped stdin (non-TTY) is read once, eagerly, in full — readline's 'line'
// event fires as soon as data arrives regardless of whether a listener is
// attached yet, so attaching a fresh listener per prompt silently drops
// every line after the first. Reading synchronously up front and handing
// out one line per call sidesteps that entirely.
let _stdinLines = null;
let _stdinLineIdx = 0;

function hiddenInput(promptText) {
  if (!process.stdin.isTTY) {
    if (_stdinLines === null) {
      const fs = require('node:fs');
      let data = '';
      try { data = fs.readFileSync(0, 'utf8'); } catch { /* no piped input */ }
      _stdinLines = data.split('\n');
    }
    process.stdout.write(promptText);
    const line = (_stdinLines[_stdinLineIdx++] || '').replace(/\r$/, '');
    process.stdout.write(line + '\n');
    return Promise.resolve(line);
  }
  return new Promise((resolve) => {
    process.stdout.write(promptText);
    const stdin = process.stdin;
    stdin.resume();
    stdin.setRawMode(true);
    let input = '';
    const onData = (chunk) => {
      const char = chunk.toString('utf8');
      if (char === '\n' || char === '\r' || char === '') {
        stdin.setRawMode(false);
        stdin.pause();
        stdin.removeListener('data', onData);
        process.stdout.write('\n');
        resolve(input);
      } else if (char === '') {
        process.stdout.write('\n');
        process.exit(1);
      } else if (char === '') {
        input = input.slice(0, -1);
      } else {
        input += char;
      }
    };
    stdin.on('data', onData);
  });
}

async function cmdCreateAdmin(opts) {
  const email = opts.email;
  if (!email) {
    console.error('Usage: admin-cli create-admin --email you@example.com');
    process.exit(1);
  }
  const existing = db.prepare('SELECT id FROM users WHERE email = ?').get(email);
  if (existing) {
    console.error(`A user with email ${email} already exists.`);
    process.exit(1);
  }
  const password = await hiddenInput('Password: ');
  if (password.length < 8) {
    console.error('Password must be at least 8 characters.');
    process.exit(1);
  }
  const confirm = await hiddenInput('Confirm password: ');
  if (confirm !== password) {
    console.error('Passwords do not match.');
    process.exit(1);
  }
  db.prepare('INSERT INTO users (email, password_hash, is_admin) VALUES (?, ?, 1)')
    .run(email, hashPassword(password));
  console.log(`Admin user created: ${email}`);
}

function cmdAddProduct(opts) {
  const slug = opts.slug;
  const name = opts.name;
  const devices = Number(opts.devices || 1);
  const days = Number(opts.days);
  const price = Number(opts.price);
  if (!slug || !name || !days || !price) {
    console.error(
      'Usage: admin-cli add-product --slug <slug> --name "Display Name" ' +
      '[--devices 1] --days <n> --price <idr> [--description "text"]'
    );
    process.exit(1);
  }
  db.prepare(
    `INSERT INTO products (slug, name, description, devices, duration_days, price_idr)
     VALUES (?, ?, ?, ?, ?, ?)
     ON CONFLICT(slug) DO UPDATE SET name = excluded.name,
                                      description = excluded.description,
                                      devices = excluded.devices,
                                      duration_days = excluded.duration_days,
                                      price_idr = excluded.price_idr`
  ).run(slug, name, opts.description || null, devices, days, price);
  console.log(`Upserted product "${slug}": ${name} — ${devices} device(s), ${days} days, Rp${price}`);
}

function usage() {
  console.log(`WhiteSecurity admin CLI

Usage: node scripts/admin-cli.js <command> [options]

Commands:
  gen-license [--plan premium] [--devices 1] [--days 365] [--note "text"]
  list-licenses
  revoke-license <license_key>
  add-malware-hash <sha256> <threat_name> [--severity medium] [--source name]
  import-csv <path.csv> [--source name]
  create-admin --email you@example.com   (prompts for password, hidden input)
  add-product --slug <slug> --name "..." [--devices 1] --days <n> --price <idr> [--description "..."]
`);
}

const { command, opts, positional } = parseArgs(process.argv.slice(2));

switch (command) {
  case 'gen-license': cmdGenLicense(opts); break;
  case 'list-licenses': cmdListLicenses(); break;
  case 'revoke-license': cmdRevokeLicense(positional); break;
  case 'add-malware-hash': cmdAddMalwareHash(positional, opts); break;
  case 'import-csv': cmdImportCsv(positional, opts); break;
  case 'create-admin':
    cmdCreateAdmin(opts).catch((e) => { console.error(e); process.exit(1); });
    break;
  case 'add-product': cmdAddProduct(opts); break;
  default: usage(); process.exit(command ? 1 : 0);
}
