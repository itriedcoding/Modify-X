# Modify-X 1.1.0

Shizuku can now be installed from inside the app, and three new per-app controls join the
existing debloat actions.

## New in 1.1.0

- **One-tap Shizuku setup.** The official Shizuku 13.6.0 APK is bundled, so installing it
  needs no network access and no browser. The installer verifies the APK checksum and the
  Rikka signing certificate before doing anything. With root the install is silent; without
  root the system installer opens with a single confirmation. A "Grant Shizuku" action
  follows once the service is running.
- **Component control.** Every activity, service, receiver and provider of a package can be
  enabled or disabled individually (`pm enable/disable pkg/component`), with the resulting
  state read back from the platform.
- **Runtime permissions.** Dangerous permissions can be granted or revoked
  (`pm grant` / `pm revoke`) and are re-checked afterwards.
- **App standby buckets.** Read and set the bucket (`am get/set-standby-bucket`) from
  Exempted down to Never, so a debloated app can also be stopped from waking the device.
- **Automation that finishes.** Boot automation now runs as WorkManager work instead of
  inside a broadcast receiver, so a large profile is no longer cut off after a few seconds.
  A "Run automation now" action was added to Presets.
- **Runs on older Android.** Package queries used API 33-only overloads and would have
  crashed on Android 8–12; both paths are now version-guarded.
- **Navigation fixes.** Console and Backup screens have a back button; the duplicate
  Play Books catalog entry is gone.

## Install

Download `app-release.apk` and allow installation for your file manager. Root (Magisk,
KernelSU) or Shizuku is required for any change; without either the app is read-only.

## Notes

- Uninstall uses `pm uninstall --user 0`, so packages can be restored from History.
- Backups contain package names and states only. The app has no network permission.
- The release keystore is committed on purpose: every build is signed with the same key so
  updates install over each other.
