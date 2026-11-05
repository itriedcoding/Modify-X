# Third-party components

Modify-X no longer bundles or depends on Shizuku, or on any other privilege-providing app.
The privileged transport (ADB wire protocol, RSA authentication, shell streams) is
implemented in `app/src/main/java/dev/modifyx/core/adb/`.

## Libraries

- Jetpack Compose (UI, Material 3, foundation)
- AndroidX core, activity, lifecycle, navigation, datastore, work
- Kotlin standard library and kotlinx.coroutines

All of the above are used under the Apache License 2.0.
