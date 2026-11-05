# Modify-X 1.2.0

Custom themes, in-app updates, APK export and unused-app detection.

## New in 1.2.0

- **Create your own themes.** Pick a hue and saturation in Settings → Theme; the app
  derives a complete Material 3 colour scheme from that seed, including containers,
  secondary/tertiary tones, surfaces and outline. Themes can be renamed, switched and
  deleted, and work with System/Light/Dark plus a new AMOLED-black surface toggle.
- **Updates from the release page.** Settings → Updates checks
  `github.com/itriedcoding/Modify-X/releases`, shows the published notes, downloads the
  APK and installs it. A download is only accepted when it is signed with the project's
  release key and is newer than the installed build, so a tampered file cannot take over
  the app. Checks run at most daily and can be disabled. This is the app's only network
  request.
- **Export APK.** In an app's detail screen, copy its base APK and splits to
  `Download/Modify-X` before removing it, with the copied size read back to confirm.
- **Unused apps.** With usage access granted, the Unused filter lists apps you have not
  opened in 60 days, and the detail screen shows a real last-used time. Without access
  the app says so instead of guessing.
- **Fewer footguns.** A stalled silent Shizuku install now falls back to the system
  installer.

## Install

Download `app-release.apk`. Root (Magisk, KernelSU) or Shizuku is required for any
package change; without either the app is read-only.

## Notes

- Uninstall uses `pm uninstall --user 0`, so packages can be restored from History.
- Backups contain package names and states only.
- The release keystore is committed on purpose: every build is signed with the same key
  so updates install over each other and the in-app updater can verify them.
