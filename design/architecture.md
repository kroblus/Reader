# LightReader module architecture

## Current dependency direction

```text
:app (Compose shell, resource mapping, platform workers and adapters)
  -> :feature-download (download ViewModel and feature gateway)
  -> :feature-reader (reader navigation policy and page-turn coordination)
  -> :core-data (Room, repositories, persistent task contracts)
  -> :core-formats (TXT/EPUB import and cleaning)
  -> :core-reader (reader models, parsing, pagination, cache)

:core-data -> :core-formats -> :core-reader
:feature-download -> :core-data, :core-reader
:feature-reader -> :core-reader
:macrobenchmark -> :app
```

The core modules keep their existing Kotlin package names so Room schemas, saved preferences,
and reader state do not require a data migration just because code moved between Gradle modules.

## Feature responsibilities

- `ReaderViewModel`: reading session, pagination, bookmarks, search and reader navigation orchestration.
- `:feature-reader`: pure character-offset progress mapping and bounded cross-chapter turn coordination.
- `LibraryViewModel`: shelf aggregation, local imports, duplicate decision flow, metadata editing.
- `:feature-download/DownloadViewModel`: webpage preview, download task controls and web-book refresh.
- `AiSettingsViewModel`: encrypted API key and explicit AI fallback configuration.
- `SettingsViewModel`: reader/application preferences and display mode toggles.

Feature ViewModels emit typed messages or navigation events to `ReaderApp`. The app shell is the
only place where those events become snackbars or navigation actions, which prevents repositories
and core modules from reaching into Compose state.

`DownloadRepository` depends only on `WebBookPreviewSource` and `DownloadTaskScheduler` contracts
from `:core-data`; the Jsoup site adapters and WorkManager scheduler implement them in `:app`.
The download feature consumes a `DownloadFeatureGateway`; `:app` maps the gateway's typed failures
and notices to localized strings, so the feature module neither knows Android resources nor a web site.

## Verification

```powershell
.\gradlew.bat :core-reader:testDebugUnitTest :core-formats:testDebugUnitTest `
  :core-data:assemble :feature-reader:testDebugUnitTest :feature-download:testDebugUnitTest :app:testDebugUnitTest `
  :app:connectedQaAndroidTest
```

`connectedQaAndroidTest` always targets the isolated `com.lightreader.app.qa` package. Do not run
the debug instrumentation package against a device holding real reader data.
