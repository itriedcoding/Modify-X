# Third-party components

## Shizuku

- Bundled APK: `app/src/main/assets/shizuku.apk`
- Version: 13.6.0.r1086.2650830c (moe.shizuku.privileged.api, versionCode 1086)
- Upstream: https://github.com/RikkaApps/Shizuku
- License: Apache License 2.0
- SHA-256: `6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f`
- Signing certificate SHA-256: `268b5590e868fb08bae7e0ac413564cd1ff88f5ccff74af9dbd0dc918e30db30` (CN=Rikka)

Modify-X ships the unmodified official release so the phone never has to download it.
`ShizukuInstaller` refuses to install the asset unless the checksum and signing certificate
both match the values above, and a unit test fails the build if the bundled file changes.

## Libraries

Jetpack Compose, AndroidX (core, activity, lifecycle, navigation, datastore, work),
Kotlin coroutines and the Shizuku API/provider are used under the Apache License 2.0.
