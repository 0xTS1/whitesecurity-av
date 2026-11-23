# WhiteSecurity AV

Open-source Android **antivirus & anti-stalkerware scanner** with its license /
malware-hash-lookup backend, built by [whitesecurity.id](https://whitesecurity.id).

The app scans installed apps and user-picked APK files against a local + cloud
malware-hash database and flags risky permission combinations — with a focus on
**spyware / stalkerware detection** and practical device-hardening checks.

> Designed to be privacy-respecting and defensive: it requests only the minimum
> permissions needed to scan, ships no real malware samples (only the standard
> EICAR test string), and never acts on other apps without the user.

## Repository layout

| Path | What it is |
|------|------------|
| [`android/`](android/) | Kotlin + Jetpack Compose client app |
| [`server/`](server/) | Node.js backend: license activation/verification + malware-hash lookup API |

Each folder has its own `README.md` with setup instructions.

## Features

**Free**
- Installed-app and APK malware scan (local + cloud hash lookup)
- Spyware / stalkerware detection — indicators from
  [Echap stalkerware-indicators](https://github.com/AssoEchap/stalkerware-indicators) (CC BY 4.0)
- Real-time scan of newly installed apps, signature sync, scan history

**Premium** (license-gated via `server/`)
- *Perisai Anti-Penipuan* — flags apps holding accessibility, notification-listener,
  device-admin, or SMS access, escalated when sideloaded
- *Pemeriksaan Keamanan Perangkat* — 0–100 device security score with fix shortcuts
- Deep scan including system / preinstalled apps

## Permissions

The app requests only: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`,
`REQUEST_INSTALL_PACKAGES` (self-update), and `QUERY_ALL_PACKAGES` (to enumerate
installed apps for scanning). It has a normal launcher icon and requests no SMS,
call-log, microphone, contacts, or accessibility permissions of its own.

## Security notes

- Secrets (`.env`, `local.properties`, keystores) are git-ignored — never commit them.
- Run the server behind a TLS-terminating reverse proxy (nginx / Caddy); never
  ship the app talking to plain HTTP.

## License

See [`LICENSE`](LICENSE). Stalkerware indicators are used under CC BY 4.0 from the
Echap project.
