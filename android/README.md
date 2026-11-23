# WhiteSecurity Android App

Kotlin + Jetpack Compose client for WhiteSecurity.ID. Scans installed apps and
user-picked APK files against a local + cloud malware-hash database and flags
risky permission combinations.

**Free for everyone:** installed-app + APK malware scan, spyware/stalkerware detection (`scanner/SpywareDetector`, indicators from Echap stalkerware-indicators, CC BY 4.0, `assets/stalkerware_indicators.json`), self-update from whitesecurity.id (`update/`), real-time scan of newly
installed apps, cloud hash lookup + signature sync, unlimited scan history.

**Premium (license, see `server/`):**
- *Perisai Anti-Penipuan* (`premium/ScamShieldAnalyzer`) — apps that currently
  hold enabled accessibility services, notification listeners, device admin,
  or granted SMS permission; escalated when sideloaded.
- *Pemeriksaan Keamanan Perangkat* (`premium/SecurityCheckup`) — 0–100 score
  from screen lock, USB debugging, developer options, patch age, root signs,
  encryption, and sideloaded-app count, with Settings shortcuts to fix.
- *Scan mendalam* — includes system/preinstalled apps in the scan.

## First-time setup

`local.properties` is gitignored — create it yourself in `android/` if it
doesn't already exist:

```properties
sdk.dir=/path/to/your/android-sdk

REMOTE_BASE_URL=https://api.whitesecurity.id/
REMOTE_API_KEY=<same value as APP_API_KEY in server/.env>
REMOTE_CERT_PINS=<optional, comma-separated sha256/... pins — see server/README.md>
```

`REMOTE_BASE_URL`/`REMOTE_API_KEY` can also be supplied as environment
variables of the same name (useful in CI) — `local.properties` wins if both
are set. Never commit a real `REMOTE_API_KEY`.

## Building

```bash
./gradlew assembleDebug      # debug APK, output in app/build/outputs/apk/debug/
./gradlew assembleRelease    # requires a signing config — see below
```

## Release signing

`app/build.gradle.kts` reads the release keystore from `local.properties`
(or env vars): `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Without these set, `assembleRelease`
produces an unsigned APK.

## Play Store: QUERY_ALL_PACKAGES declaration

This app requests `android.permission.QUERY_ALL_PACKAGES` (declared in
`AndroidManifest.xml`) because an antivirus/scanner app's core function is
enumerating every installed app, not just ones it has a normal visibility
relationship with under Android 11+'s package-visibility rules.

Google Play requires an explicit permission declaration before this is
allowed in a production listing:

1. Play Console → your app → **App content** → **Permissions declaration**.
2. Select the **"Anti-malware, anti-virus, or security"** (or the current
   equivalent "core functionality" category Google offers for this
   permission) justification — do **not** pick a generic category, Play
   review rejects those for this permission.
3. Describe the core use case plainly: "enumerates all installed apps to
   scan them for malware signatures and risky permission combinations."
4. Submit for review before your first production release; approval can take
   longer than a normal review cycle, so don't leave this for the last minute
   before a release deadline.

If this justification is ever rejected or revoked, the fallback is to drop
`QUERY_ALL_PACKAGES` and rely on Android's default package-visibility rules
plus an explicit `<queries>` manifest declaration — that will only see a
subset of installed apps, so scan coverage would need to be called out
clearly in-app.

## Architecture notes

- `WhiteSecurityApp` is a hand-rolled DI container (no Hilt/Koin) — small
  enough that a framework isn't worth the build-time/complexity cost yet.
- `scanner/` has no Android framework dependency beyond `PackageScanner`
  (which wraps `PackageManager`) — `ScanEngine`/`PermissionRiskAnalyzer` are
  plain Kotlin and unit-testable without Robolectric.
- `license/LicenseManager` is the single source of truth client-side for
  feature gating (`isScamShieldAllowed`, `isSecurityCheckupAllowed`,
  `isDeepScanAllowed`); it keeps last-known-good state if the server is
  unreachable rather than hard-failing premium features offline.
- Launcher icon = the shield from the WhiteSecurity logo on #0A0A0B
  (`mipmap-*/ic_launcher_foreground.png`); the full logo used in the app
  header is `drawable-nodpi/ws_logo_full.png`.
- Malware hash matching is local-DB-first, then cloud for
  anything not already known locally — never the reverse, to keep the common
  case (already-known-bad) working fully offline.
