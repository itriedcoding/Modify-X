# Modify-X

A storage cleaner for Android that shows you what is actually on the device, proves which
files are duplicates, and deletes only what you confirm — then checks that the deletion
really happened.

No accounts, no ads, no analytics, no root, no storage permission. The only network
request the app makes is reading this project's own GitHub release page.

## What it does

| | |
| --- | --- |
| **Junk** | Temporary files, logs, crash dumps, editor backups, `Thumbs.db`/`desktop.ini`, zero-byte files and thumbnail caches, matched by name and extension |
| **Duplicates** | Files with the same size **and** the same full SHA-256 hash, grouped with an image preview and an automatic keeper |
| **Large files** | Anything over a threshold you choose (50–500 MB by default), sorted by size |
| **Empty folders** | Directories with no files and no non-empty subdirectories, including nested ones |
| **By type** | How much space images, video, audio, documents and archives actually take |
| **History** | Every cleanup session with the confirmed bytes freed and any refusals |

## How it accesses files

Android has no legal way for an ordinary app to browse your whole storage. Modify-X does
not pretend otherwise:

- You pick folders through the system file picker (`ACTION_OPEN_DOCUMENT_TREE`). Android
  grants this app read and write access **to those folders only**, and the grant survives
  reboots via `takePersistableUriPermission`.
- **No storage permission is declared in the manifest.** The app literally cannot see a
  file outside the folders you granted.
- Deletion uses `DocumentsContract.deleteDocument`. After every attempt the document is
  re-queried: if Android still serves it, the cleanup is reported as a failure rather than
  counted as freed space.
- Some locations (the root of internal storage, `Download` itself, `Android/data`) cannot
  be granted by Android 11+. Pick their subfolders instead.

## Duplicate detection

Files are grouped by exact size first, so hashing only happens for real candidates. Each
candidate is streamed through SHA-256. Two files are shown as duplicates only when both the
size and the digest match — never by file name, date or a visual guess. Files larger than
512 MB are skipped and the count is reported, because hashing them would take minutes.

The keeper of a group is the copy with the shortest path, then the oldest modification
time. It is checked and locked in the UI; copies start pre-selected.

## Junk matching

Rules live in one place ([JunkRules.kt](app/src/main/java/dev/modifyx/data/JunkRules.kt)) and
are visible and switchable in Settings:

- Temporary and partial downloads: `.tmp`, `.temp`, `.part`, `.partial`, `.crdownload`
- Logs and crash dumps: `.log`, `.dmp`, `.stackdump`, `.hprof`, `hs_err_pid*`
- Stale backups: `.bak`, `.old`, `.orig`, `.sav`, `file~`
- OS metadata: `Thumbs.db`, `ehthumbs.db`, `desktop.ini`, `.DS_Store`
- Zero-byte files, except `.nomedia`, which is meaningful
- Thumbnail caches inside `.thumbnails` folders (off by default)

A file that matches no rule is never offered for deletion.

## Onboarding

First launch is an animated three-step entry: what the app does, how the access model works,
and the folder picker. It can be skipped and replayed from Settings.

## Privacy

Files are read and deleted on the device. The app stores folder URIs, preferences, custom
themes and a local cleanup log in its private storage. `INTERNET` is used only for the
update check; there is no upload path in the code.

## Updates

Settings → Updates follows `github.com/itriedcoding/Modify-X/releases`. A downloaded APK is
installed only if it is signed with the project's release key and has a higher version than
the installed app; anything else is rejected before the installer sees it. Automatic checks
run at most once a day and can be turned off.

## Building

Requirements: JDK 17, Android SDK with platform 36 and build-tools 36.0.0.

```bash
./gradlew :app:testReleaseUnitTest :app:assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.

Release builds are signed with `keystore/modifyx-release.jks` (alias `modifyx`, password
`modifyx`). The key is deliberately public so community builds stay update-compatible with
the published APK. Override it with `MODIFYX_KEYSTORE`, `MODIFYX_KEYSTORE_PASSWORD`,
`MODIFYX_KEY_ALIAS` and `MODIFYX_KEY_PASSWORD` for your own distribution.

## Project layout

```
app/src/main/java/dev/modifyx/
├── core/       update checker (GitHub releases, signature pinning)
├── data/       settings, scan models, junk rules, SAF scanner, cleanup engine, history
└── ui/         Compose theme, ViewModel, onboarding, screens, shared components
```

## License

MIT. See [LICENSE](LICENSE).
