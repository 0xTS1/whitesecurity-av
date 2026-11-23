'use strict';

// Only the portal's single session cookie needs parsing/serializing, so a
// tiny hand-rolled helper avoids pulling in `cookie-parser` for one cookie.

function parseCookie(header) {
  const out = {};
  if (!header) return out;
  for (const part of header.split(';')) {
    const i = part.indexOf('=');
    if (i === -1) continue;
    const name = part.slice(0, i).trim();
    const value = part.slice(i + 1).trim();
    if (name) out[name] = decodeURIComponent(value);
  }
  return out;
}

function serializeCookie(name, value, { maxAgeSec, secure } = {}) {
  let s = `${name}=${encodeURIComponent(value)}; Path=/; HttpOnly; SameSite=Lax`;
  if (maxAgeSec !== undefined) s += `; Max-Age=${maxAgeSec}`;
  if (secure) s += '; Secure';
  return s;
}

module.exports = { parseCookie, serializeCookie };
