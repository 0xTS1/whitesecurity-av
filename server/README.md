# WhiteSecurity Server

Backend for the WhiteSecurity.ID Android app: license activation/verification
and malware hash lookups. Single-process Node.js app, controlled with plain
bash scripts (no pm2/systemd/docker required, though you can wrap it with
systemd later if you want it to survive reboots automatically).

## Requirements

- Node.js **>= 22.5** (uses the built-in `node:sqlite` module — no native
  dependencies to compile on your VPS)
- A VPS/server reachable by your Android app over HTTPS (put this behind a
  reverse proxy like nginx/Caddy with a real TLS cert — never ship the app
  talking to plain HTTP)

## First-time setup

```bash
cd server
npm install
cp .env.example .env
# Generate a real API key and put it in .env (APP_API_KEY=...):
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

The same `APP_API_KEY` value must be embedded in the Android app's build
config (`REMOTE_API_KEY` in `android/app/build.gradle.kts`) — it's a shared
secret between the app and this server, not a per-user credential.

## Running the server

```bash
./scripts/manage.sh start     # start in background, logs to whitesecurity-server.log
./scripts/manage.sh status
./scripts/manage.sh logs      # tail -f the log
./scripts/manage.sh restart
./scripts/manage.sh stop
```

## Managing licenses (CLI)

The original bash/CLI workflow still works exactly as before — useful for
quick one-offs or scripting even now that the web portal (below) exists:

```bash
./scripts/gen-license.sh --devices 2 --days 365 --note "customer@example.com"
node scripts/admin-cli.js list-licenses
node scripts/admin-cli.js revoke-license WSEC-XXXX-XXXX-XXXX-XXXX
```

## Web portal (user + admin dashboard, Midtrans payments)

A server-rendered dashboard now lives under `/portal` — entirely separate
from `/api/v1` (different auth, no `X-Api-Key`, cookie-based sessions
instead). It does not replace the CLI above; use whichever's convenient.

**Customer-facing** (`/portal/register`, `/portal/login`, `/portal`,
`/portal/products`, `/portal/orders`): email/password accounts, see your own
license status + order history, buy/renew a plan via Midtrans.

**Admin** (`/portal/admin/...`, requires an admin account — see below):
overview stats, manage licenses (create/revoke/unrevoke, same underlying
function the CLI's `gen-license` uses), manage purchasable products/plans,
view orders/payments with a manual "mark as paid" override (for when a
Midtrans webhook is lost/delayed — goes through the same idempotent
fulfillment path as the real webhook, safe to click more than once), manage
malware signatures (add single hash / paste CSV lines), and browse users.

### Bootstrapping an admin account

**There is no public sign-up path to admin — by design.** The only way to
create one is this CLI command, run on the server itself, which prompts for
the password via hidden terminal input (never an `--password` flag, so it
never lands in shell history or `ps aux`):

```bash
node scripts/admin-cli.js create-admin --email you@whitesecurity.id
```

### Setting up purchasable plans

The portal ships with zero products — add at least one before anyone can
buy anything (also doable from `/portal/admin/products` once you have an
admin account):

```bash
node scripts/admin-cli.js add-product --slug premium-1y \
  --name "Premium 1 Device / 1 Year" --devices 1 --days 365 --price 99000 \
  --description "Real-time monitoring, cloud threat lookup, unlimited scan history"
```

Re-running with the same `--slug` updates that product (upsert) rather than
creating a duplicate.

### Midtrans setup

1. Get `Server Key` + `Client Key` from the Midtrans dashboard (sandbox keys
   first — `https://dashboard.sandbox.midtrans.com`).
2. Set in `.env`: `MIDTRANS_SERVER_KEY`, `MIDTRANS_CLIENT_KEY`,
   `MIDTRANS_IS_PRODUCTION=false` (flip to `true` only once you're using
   real production keys), and `PORTAL_BASE_URL` to this server's real public
   URL.
3. In the Midtrans dashboard, configure the **Payment Notification URL** to
   `{PORTAL_BASE_URL}/portal/payments/notification` — this is how Midtrans
   tells the server a payment succeeded. The order is fulfilled (license
   minted and bound to the buyer's account) only after this server
   independently re-checks the transaction status with Midtrans
   server-to-server — it never trusts the notification body alone.
4. Restart the server (`./scripts/manage.sh restart`) after editing `.env` —
   nothing here fails boot if left unset, but `/portal/orders` and the
   webhook will return a friendly error until configured, so the Android API
   stays unaffected either way.
5. Set `NODE_ENV=production` in `.env` **only once the portal is actually
   served over HTTPS** (behind nginx/Caddy) — this flags the session cookie
   `Secure`, so logins silently stop working over plain HTTP otherwise.

## Malware signatures

This ships with **zero real malware signatures**. Seeding only adds the
standard EICAR test file hash, used to verify the detection pipeline works:

```bash
./scripts/seed-malware.sh
```

For real protection, load a hash list you have the rights to use:

```bash
node scripts/admin-cli.js add-malware-hash <sha256> "Threat.Name" --severity high
node scripts/admin-cli.js import-csv signatures.csv   # lines: sha256,threat_name,severity
```

## API

All `/api/v1/*` routes require header `X-Api-Key: <APP_API_KEY>`.

- `GET /healthz` — no auth, for uptime checks
- `POST /api/v1/license/activate` `{licenseKey, deviceId}` — binds a device to
  a license (up to its `max_devices` limit), returns plan/expiry
- `POST /api/v1/license/verify` `{licenseKey, deviceId}` — re-checks an
  already-activated device
- `GET /api/v1/malware/check/:sha256` — single hash lookup
- `POST /api/v1/malware/bulk-check` `{hashes: [...]}` — up to 200 hashes
- `GET /api/v1/malware/updates?sinceId=<id>&limit=<n>` — delta sync: returns
  every signature added after `sinceId` (up to `limit`, max 1000) plus
  `lastId` to persist as the next cursor. Pass `sinceId=0` for a first sync.

## Certificate pinning (optional)

The Android app can optionally pin this server's TLS certificate
(`REMOTE_CERT_PINS` in `android/app/build.gradle.kts`/`local.properties`).
Compute the pin for whatever cert nginx/Caddy actually serves:

```bash
openssl s_client -connect api.whitesecurity.id:443 -servername api.whitesecurity.id </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64
```

Include the pin for **both** the leaf cert and its issuing CA/intermediate
(run the same command against the intermediate cert too) — pinning only the
leaf breaks the app the moment you renew/rotate the certificate.

## Production notes

- Put this behind nginx/Caddy for TLS; don't expose port 8787 directly.
- The rate limiter is in-process/in-memory — fine for one server instance.
- `data/whitesecurity.db` is the SQLite database; back it up (it holds your
  license and device-activation records).
