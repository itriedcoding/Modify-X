# Modify-X 1.4.0

A cleanup release. No feature was removed and the auto-updater is untouched: it still
reads this project's release page, verifies the download against the pinned signing
certificate and refuses anything that is not newer.

## What changed

- **One screen header everywhere.** Apps, Presets, History, Settings, Backup, Console,
  Components and App Detail now share the same title/subtitle/back treatment instead of
  each screen inventing its own row. Apps gained the title it was missing.
- **One update UI.** The "update available" card lived twice, in Home and Settings, with
  slightly different wording and progress handling. Both now render a single shared
  component, so they cannot drift apart.
- **One switch row.** The settings toggle moved into the shared component set and is reused
  by the Presets automation section, which previously hand-rolled the same layout.
- **UI state separated from the ViewModel.** `Busy`, `ConsoleState`, `UpdateState` and
  `ImportPreview` moved to their own file; the ViewModel no longer mixes data holders with
  behaviour, and fully-qualified names were replaced with imports.

## Fixes

- Changing the Apps filter while packages were selected left those packages selected but
  invisible, so a batch could act on rows the user could not see. The selection now clears
  when the filter changes.
- The Presets screen read the privilege state outside Compose observation, so the Apply
  buttons kept a stale enabled state after root or the ADB bridge connected. It is now
  collected like every other screen.
- History used horizontal spacers inside a vertical column, which collapsed to zero and
  jammed failed-action messages against the row above.
- "Select all" was only offered on the unfiltered list; it now works in every filter and
  selects exactly the rows on screen.
- The theme resolver had three branches that all produced the same scheme; it is now a
  single default branch with identical results.

## Install

`app-release.apk`, versionCode 6. Updating from 1.3.0 installs in place; the signature and
version checks in the updater are unchanged.
