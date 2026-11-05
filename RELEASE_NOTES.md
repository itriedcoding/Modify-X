# Modify-X 2.0.0

Modify-X is now a storage cleaner. The debloater is gone: no package manager access, no
root, no ADB bridge, no package catalogue. In their place is a cleaner that works on the
files you actually own.

## What it does

- **Junk scan** — temporary files, logs, crash dumps, stale editor backups, Windows
  metadata, zero-byte files and thumbnail caches, matched by name and extension by rules you
  can see and switch off.
- **Duplicate finder** — groups files by exact size, then proves identity with a full
  SHA-256 hash. Previews are decoded from the real files. Each group has a locked keeper
  (shortest path, then oldest) and pre-selected copies.
- **Large files and empty folders** — sorted by real size, with nested empty directories
  detected bottom-up.
- **By-type breakdown** — images, video, audio, documents, archives, other.
- **History** — every cleanup with the byte count Android confirmed, plus refusals.
- **Animated first-run entry** — three steps, then the folder picker; replayable from
  Settings.

## Access model

No storage permission is requested at all. Folders are granted through Android's own file
picker and accessed with the Storage Access Framework, so the app can only touch what you
hand it. Every deletion goes through `DocumentsContract.deleteDocument` and is re-queried
afterwards; a delete that Android does not really perform is reported as a failure.

## Removed

The package catalogue, package actions, root/ADB shells, ADB protocol client, profiles,
boot automation, component and permission editors, APK export, backup/restore and the shell
console. `QUERY_ALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED` and the WorkManager dependency are
gone with them, and the app now declares only `INTERNET` and `REQUEST_INSTALL_PACKAGES`.

## Updates

The updater is unchanged: it reads this release page, verifies the download against the
pinned signing certificate, refuses anything that is not newer, and installs over the
existing app because the signing key is the same.

## Install

`app-release.apk`, versionCode 7. Installing over 1.x replaces the old app in place; its
settings are not reused because the data model changed.
