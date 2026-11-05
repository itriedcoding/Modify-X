# Third-party components

Modify-X uses only standard AndroidX and Kotlin libraries. There is no root library, no
privilege-providing app, no analytics or crash SDK, and no networking library: the update
check is a plain `HttpURLConnection` against this project's own GitHub release page.

## Libraries

- Jetpack Compose (UI, Material 3, foundation, animation)
- AndroidX core, activity, lifecycle, navigation, datastore
- Kotlin standard library and kotlinx.coroutines

All of the above are used under the Apache License 2.0. File access uses the platform
Storage Access Framework (`DocumentsContract`), which is part of Android itself.
