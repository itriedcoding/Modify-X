# Modify-X 2.0.1

Automatic scanning, so the app keeps itself current without you pressing the button.

## New

- **Auto-scan.** When Modify-X opens or returns to the foreground, it scans by itself if the
  last result is older than the chosen interval (default 6 hours). The interval can be set
  to hourly, every 6 hours or daily in Settings → Scan options, and auto-scan can be turned
  off entirely.
- **Scan on folder grant.** Adding a folder triggers a scan immediately when auto-scan is
  enabled, so a newly granted folder is never left unexamined.
- **Live storage figures.** The free/used/total numbers on Home now refresh after a scan or
  a cleanup instead of keeping the value from when the screen first composed.

## Honest limits

Auto-scan is foreground only: it runs while you are using the app. There is no background
service, no exact alarm and no battery-draining watchdog, because Android would throttle
them anyway and the app only has access to folders you granted in this session's context.
Nothing scans while the app is closed.

## Checks

The due-time rule lives in `AutoScanPolicy` and is unit-tested: disabled never scans, a
missing timestamp is due immediately, a fresh result is not due, an old one is, and the
interval is clamped to 1–168 hours. The full suite and the release build pass, and the APK
is inspected for version, permissions and signer before publishing.

## Install

`app-release.apk`, versionCode 8. Installs over 2.0.0 with the same signing key.
