## Modify-X — Android debloater

A local, verified Android debloater. Install the APK below and grant root or Shizuku
access to change packages; without either, the app is read-only by design.

### Highlights

- **Real debloating with verification.** Every command is executed through `su` or a
  Shizuku-provided shell user service, then the device state is re-read. If the state did
  not actually change, the action is reported as failed.
- **Removed apps stay visible and restorable.** The package list is diffed against
  `pm list packages -u` so anything uninstalled for user 0 can be found and brought back.
- **~190 curated bloatware packages** across Google, Samsung, Xiaomi, OPPO/Realme/OnePlus,
  Vivo, Huawei/Honor, Motorola, carriers and telemetry agents — each with a risk rating
  and a note, filtered to what is installed on your device.
- **Protected core packages** (SystemUI, telephony, package installer, launchers, WebView)
  are blocked unless you explicitly enable the override.
- **Batch actions** — uninstall, disable, restore, enable — with per-item progress.
- **Profiles and boot automation** to re-apply a chosen set after a reboot.
- **Backups** as portable JSON, local snapshots, and restore of exactly the packages a
  backup says were removed.
- **History with undo** for every reversible action.
- **App inspector**: permissions with grant state, component counts, signing-certificate
  SHA-256, APK paths, running processes, and data size where root allows it.
- **Shell console** for arbitrary commands against the active backend.

### Requirements

- Android 8.0 (API 26) or newer.
- Root **or** [Shizuku](https://shizuku.rikka.app/) running with permission granted for
  any package modification.

### Notes

- The app requests no network permission. Nothing leaves the device.
- Release APKs are signed with the project key in `keystore/modifyx-release.jks` so
  future versions install as updates.
- `pm uninstall --user 0` is reversible and is the default removal mode. "Uninstall
  completely" exists only for non-system apps and is destructive.
