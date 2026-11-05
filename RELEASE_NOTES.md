# Modify-X 1.3.0

Shizuku is gone. The privileged path is now implemented inside Modify-X.

## New in 1.3.0

- **Built-in ADB bridge.** The app now contains its own ADB host client: packet framing,
  RSA-2048 key generation, token signing and real `shell:` streams to the phone's own
  adbd. No Shizuku install, no third-party privilege app, no extra permission.
  Enable USB debugging, run `adb tcpip 5555` from a computer once, then connect from the
  Home tab and approve the fingerprint adbd shows (the same value is displayed in the app).
- **Root still works** exactly as before, and is preferred when available.
- **Honest states.** The Home card reports whether root or the bridge is active, keeps the
  last connection error verbatim, allows the port to be changed, and can disconnect and
  reconnect without restarting the app.
- **Reconnect on launch** (optional) so the bridge comes back by itself after the first
  successful authorisation.
- Removed: the bundled Shizuku APK, the Shizuku provider, the privilege permission, the
  AIDL user service and the Shizuku dependency. The app is smaller and has fewer
  moving parts.

## Limitations worth knowing

- `adb tcpip` is reset by a reboot, so the bridge needs USB debugging kept on and the
  command re-run after a restart (root users are unaffected).
- Android 11+ wireless-debugging pairing (the six-digit code) is not implemented; it
  requires SPAKE2 key exchange, which is outside this protocol subset. The bridge uses the
  classic TCP handshake instead.

## Install

Download `app-release.apk`. Root or the ADB bridge is required for any package change;
without either the app is read-only.

## Notes

- Uninstall uses `pm uninstall --user 0`, so packages can be restored from History.
- The release keystore is committed on purpose so updates install over each other and the
  in-app updater can verify them.
