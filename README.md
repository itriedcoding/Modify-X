# Modify-X

An Android debloater that shows you exactly what it is doing, executes real platform
commands, and verifies that each change actually happened.

No accounts, no ads, no analytics, no third-party privilege app, no fake data. The only
network request the app makes is reading this project's own GitHub release page.

## What it does

Modify-X changes packages on **your own device** through a privileged shell. It has two
backends, both implemented in this repository:

| Backend | How it works | Requirements |
| --- | --- | --- |
| **Root** | Runs `su -c <command>` as UID 0 | A rooted device with a working `su` |
| **ADB bridge** | Talks the ADB wire protocol to the phone's own `adbd` and runs `shell:` services as the shell user | USB debugging enabled, `adb tcpip 5555` run once from a computer |

Without either, the app runs read-only: it can still list, search and inspect every
installed package, but it refuses to touch anything instead of pretending.

## The ADB bridge

Shizuku is not used and not required. Modify-X contains its own ADB host client:

- **Protocol** — `AdbProtocol` encodes and decodes ADB's 24-byte packets (CNXN, AUTH,
  OPEN, OKAY, WRTE, CLSE) with the real framing, checksum and magic fields.
- **Authentication** — the app generates an RSA-2048 key on first use, stores it in its
  private files directory, and signs adbd's 20-byte token with PKCS#1 v1.5 SHA-1, exactly
  as the `adb` client does. adbd shows its fingerprint prompt once; the fingerprint is
  displayed in the app so you can compare it before allowing.
- **Execution** — commands run through a real `shell:` service on the `shell` UID, with
  the exit status carried back in the stream and verified like every other action.
- **Recovery** — the port is configurable, the connection can be re-established from the
  Home tab, and the last failure is shown verbatim instead of being swallowed.

One-time setup: enable Developer options → USB debugging, connect the phone to a computer
and run `adb tcpip 5555`, then tap Connect in Modify-X. `adb tcpip` resets on reboot, so
the phone needs USB debugging still enabled; the app reconnects on launch when the port is
reachable. Wireless-debugging *pairing* (the Android 11+ six-digit flow) is not
implemented, because it needs a SPAKE2 exchange that is outside this protocol subset.

## Updates

Settings → Updates follows `github.com/itriedcoding/Modify-X/releases` and installs a
newer build in place:

- The version comes from the GitHub release API, with the plain `/releases/latest`
  redirect as a fallback that needs no API quota.
- The downloaded APK is accepted **only** if it is signed with the project's release key
  and carries a higher version than the installed app. Anything else is rejected before
  the installer is involved.
- With root or the ADB bridge the update installs silently; otherwise the system
  installer opens.
- Automatic checks run at most once a day and can be turned off.

## Themes

Settings → Theme ships the wallpaper-based palette, a Material default, and **themes you
create yourself**: pick a hue and saturation, name it, and the app derives a full
Material 3 scheme (primary, containers, secondary, tertiary, surfaces, outline) from that
seed. Themes are stored on the device, can be deleted, and combine with System/Light/Dark
and an AMOLED-black surface toggle.

## Features

- **Full package inventory** read from `PackageManager`: label, package name, icon,
  version, target/min SDK, UID, APK size, install and update time, installer,
  launchable state, system/updated-system flags, last-used time.
- **Removed packages stay visible.** The app diffs `pm list packages -u --user 0`
  against `pm list packages --user 0`, so anything uninstalled for your user can be
  found and restored later, complete with its real label from the local metadata cache.
- **Verified actions.** Every command's exit code is checked *and* the resulting device
  state is re-read. A `pm uninstall --user 0` that returns 0 but leaves the package
  installed is reported as a failure, not a success.
- **Curated bloatware catalog** (208 packages across Google, Samsung, Xiaomi,
  OPPO/Realme/OnePlus, Vivo, Huawei/Honor, Motorola, carriers and telemetry agents)
  with per-package risk levels and notes, filtered to what is actually installed.
- **Protection guards.** Core Android packages (SystemUI, telephony, package installer,
  launchers, WebView, …) are blocked by default. Risky catalog entries need an explicit
  opt-in. The override lives in Settings and is off out of the box.
- **Unused apps.** With usage access granted, Android's real usage stats mark apps you
  have not opened in 60 days, reachable from the Unused filter.
- **Batch operations** with progress: uninstall for user, disable, restore, enable.
- **Component control.** Browse every activity, service, receiver and provider of a
  package and disable or enable it individually (`pm enable/disable pkg/component`),
  with the new state read back from the package manager.
- **Runtime permissions.** Dangerous permissions can be granted or revoked
  (`pm grant` / `pm revoke`) and are re-checked afterwards.
- **App standby buckets.** Read and set the background bucket
  (`am get/set-standby-bucket`) from Exempted to Never.
- **APK export.** Copy an app's APKs to `Download/Modify-X` before removing it, verified
  by reading the copied file size back.
- **Per-app detail view**: permissions and grant state, component counts,
  signing-certificate SHA-256, APK paths, running processes, last-used time, and
  app data/cache size (root only — the adb shell cannot read other apps' private
  directories).
- **Profiles**: apply a whole preset group at once, save your own selection, or run the
  automation profile immediately instead of waiting for a reboot.
- **Boot automation** that survives long batches: the profile is applied by WorkManager
  after boot, not inside a broadcast receiver's short window.
- **Backups**: export/import JSON snapshots of the debloat state, keep local snapshots,
  and restore exactly the packages a backup says were removed.
- **History with undo**: every action is logged with its command, exit status and
  verification result. Reversible actions get a one-tap inverse.
- **Shell console**: run arbitrary commands through the active backend and see the raw
  output, exit code and duration.
- **Material 3 UI** with dynamic color, custom themes, light/dark/AMOLED and a
  searchable, sortable, filterable package list.

## Safety model

- `pm uninstall --user 0 <pkg>` removes the package **for the current user only**. The
  APK stays on the system partition and `pm install-existing --user 0 <pkg>` brings it
  back. This is the default removal mode; it is reversible.
- "Uninstall completely" is offered only for non-system apps and is destructive.
- Protected packages cannot be acted on unless you explicitly enable the override.
- Every batch shows how many selected entries will be skipped, and why.
- History, themes and snapshots live in the app's private storage. Nothing is uploaded.

Debloating can break features you rely on. If something stops working, open History and
undo, or use a backup to restore.

## Permissions

| Permission | Why |
| --- | --- |
| `QUERY_ALL_PACKAGES` | Listing the full package inventory is the point of the app |
| `RECEIVE_BOOT_COMPLETED` | Re-applying the automation profile after a reboot |
| `REQUEST_INSTALL_PACKAGES` | Installing app updates |
| `INTERNET` | Reading the public GitHub release page for updates |

`WAKE_LOCK` and `FOREGROUND_SERVICE` are contributed by WorkManager so deferred work can
finish. Usage access is optional and granted in system settings; without it the Unused
filter reports that it is unavailable. No privilege-app permission is requested.

## Building

Requirements: JDK 17, Android SDK with platform 36 and build-tools 36.0.0.

```bash
./gradlew :app:assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.

Release builds are signed with `keystore/modifyx-release.jks` (alias `modifyx`,
password `modifyx`). The key is deliberately public so that community builds stay
update-compatible with the published APK — it is not a secret and provides no security
value beyond signature continuity. Override it with `MODIFYX_KEYSTORE`,
`MODIFYX_KEYSTORE_PASSWORD`, `MODIFYX_KEY_ALIAS` and `MODIFYX_KEY_PASSWORD` if you build
your own distribution.

## Project layout

```
app/src/main/java/dev/modifyx/
├── core/       privilege detection, ADB client and protocol, shells, update checker, worker
├── data/       package repository, catalog, actions, controls, themes, usage, backups
└── ui/         Compose theme/palette, view model, screens
```

The data layer never fabricates values: if the platform cannot supply something (for
example app data size without root, or usage stats without access), the UI says so
instead of showing a placeholder.

## License

MIT. See [LICENSE](LICENSE).
