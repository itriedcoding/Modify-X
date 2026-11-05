# Modify-X

An Android debloater that shows you exactly what it is doing, executes real platform
commands, and verifies that each change actually happened.

No accounts, no INTERNET permission, no ads, no analytics, no fake data.

## What it does

Modify-X removes, disables and restores packages on **your own device** through a
privileged shell. Two backends are supported:

| Backend | How it works | Requirements |
| --- | --- | --- |
| **Root** | Runs `su -c <command>` as UID 0 | A rooted device with a working `su` |
| **Shizuku** | Runs commands as the `shell` user, exactly like `adb shell` | [Shizuku](https://shizuku.rikka.app/) installed and running, permission granted |

Without one of those, the app runs read-only: it can still list, search and inspect
every installed package, but it refuses to touch anything instead of pretending.

## Shizuku setup without a browser

The official **Shizuku 13.6.0** APK is bundled inside Modify-X, so setting up Shizuku
needs no network access and no third-party app store:

- The Home tab offers **Install Shizuku** whenever Shizuku is missing.
- Before installing, the APK must match its pinned SHA-256 **and** the Rikka release
  signing certificate; anything else is refused.
- With root the install runs silently (`pm install`); without root the system installer
  opens with one confirmation.
- A unit test fails the build if the bundled asset ever changes.

See [THIRD_PARTY.md](THIRD_PARTY.md) for provenance and the Apache-2.0 attribution.

## Features

- **Full package inventory** read from `PackageManager`: label, package name, icon,
  version, target/min SDK, UID, APK size, install and update time, installer,
  launchable state, system/updated-system flags.
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
- **Batch operations** with progress: uninstall for user, disable, restore, enable.
- **Component control.** Browse every activity, service, receiver and provider of a
  package and disable or enable it individually (`pm enable/disable pkg/component`),
  with the new state read back from the package manager.
- **Runtime permissions.** Dangerous permissions can be granted or revoked
  (`pm grant` / `pm revoke`) and are re-checked afterwards.
- **App standby buckets.** Read and set the background bucket
  (`am get/set-standby-bucket`) from Exempted to Never.
- **Per-app detail view**: declared permissions and their grant state, component counts,
  signing-certificate SHA-256, APK paths, running processes, and app data/cache size
  (root only — the adb shell cannot read other apps' private directories).
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
- **Material 3 UI** with dynamic color, light/dark themes and a searchable, sortable,
  filterable package list.

## Safety model

- `pm uninstall --user 0 <pkg>` removes the package **for the current user only**. The
  APK stays on the system partition and `pm install-existing --user 0 <pkg>` brings it
  back. This is the default removal mode; it is reversible.
- "Uninstall completely" is offered only for non-system apps and is destructive.
- Protected packages cannot be acted on unless you explicitly enable the override.
- Every batch shows how many selected entries will be skipped, and why.
- History and snapshots live in the app's private storage. The app has **no INTERNET
  permission**, so nothing can leave the device.

Debloating can break features you rely on. If something stops working, open History and
undo, or use a backup to restore.

## Permissions

| Permission | Why |
| --- | --- |
| `QUERY_ALL_PACKAGES` | Listing the full package inventory is the point of the app |
| `RECEIVE_BOOT_COMPLETED` | Re-applying the automation profile after a reboot |
| `REQUEST_INSTALL_PACKAGES` | Installing the bundled Shizuku APK |
| `moe.shizuku.manager.permission.API_V23` | Talking to a running Shizuku service |

`WAKE_LOCK` and `FOREGROUND_SERVICE` are contributed by WorkManager so deferred work
can finish. There is no `INTERNET` permission.

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
├── core/       privilege detection, root/Shizuku shells, Shizuku installer, automation worker
├── data/       package repository, catalog, actions, controls, history, profiles, backups
└── ui/         Compose theme, view model, screens
app/src/main/assets/shizuku.apk   bundled, checksum-pinned Shizuku release
```

The data layer never fabricates values: if the platform cannot supply something (for
example app data size without root), the UI says so instead of showing a placeholder.

## License

MIT. See [LICENSE](LICENSE). Bundled Shizuku is Apache-2.0; see
[THIRD_PARTY.md](THIRD_PARTY.md).
