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

- `ReaderViewModel`: reading session, pagination, bookmarks and reader navigation orchestration.
- `:feature-reader`: bounded caches, a single book/chapter/request identity, character-offset progress mapping and cross-chapter turn coordination.
- `BookSearchViewModel`: cancellable paged search, completion status and small saved navigation context; `BookSearchRepository` verifies original UTF-16 text and binds cursors to content versions.
- `LibraryViewModel`: shelf aggregation, local imports, duplicate decision flow, metadata editing.
- `:feature-download/DownloadViewModel`: webpage preview, download task controls and web-book refresh.
- `AiSettingsViewModel`: encrypted API key and explicit AI fallback configuration.
- `SettingsViewModel`: reader/application preferences and display mode toggles.
- `BackupRestoreViewModel`: SAF launch results, validated restore previews and serialized cancellation; `ReaderBackupRepository` owns independent versioned ZIP archives, durable file preparation and transactional restoration.

Feature ViewModels emit typed messages or navigation events to `ReaderApp`. The app shell is the
only place where those events become snackbars or navigation actions, which prevents repositories
and core modules from reaching into Compose state.

`DownloadRepository` depends only on `WebBookPreviewSource` and `DownloadTaskScheduler` contracts
from `:core-data`; the Jsoup site adapters and WorkManager scheduler implement them in `:app`.
The download feature consumes a `DownloadFeatureGateway`; `:app` maps the gateway's typed failures
and notices to localized strings, so the feature module neither knows Android resources nor a web site.

## Persistence boundaries

Reader pages and reparsable text may be evicted independently. A cached-page hit still resolves the
matching chapter body. Only the current session request may commit content, errors or loading state;
the previous session's final serialized progress write has a separate lifetime. A first valid viewport
restarts any chapter request that began before layout. Page records retain the actual platform font
metrics used by pagination, and Canvas uses that same snapshot.

Room 8→9 adds execution generation, WorkRequest owner, retry deadline and retry count to download
tasks. Creation is transactional/idempotent; workers claim and publish conditionally against that
authority. Private files are prepared before the short database commit, and cleanup only removes
unreferenced files owned by the task. Startup reconciliation repairs commit/enqueue interruptions.

Custom typography uses a separate DataStore snapshot. Panel drafts preview locally and persist on
release/close; field changes merge against their UI baseline under the repository mutex. Theme-only
changes preserve pagination fingerprints.

Manual backup archive version 1 is separate from Room schema 9. Recovery reconstructs local paths
and maps chapter identities without rewriting body text. It never restores a raw database, keys,
search caches or incomplete downloads. Exact format limits, duplicate policy and cancellation
semantics are documented in [backup and search contracts](../docs/implementation/backup-and-search-contracts.md).

## Verification

```powershell
.\gradlew.bat :core-reader:testDebugUnitTest :core-formats:testDebugUnitTest `
  :core-data:assemble :feature-reader:testDebugUnitTest :feature-download:testDebugUnitTest :app:testDebugUnitTest `
  :app:connectedQaAndroidTest
```

`connectedQaAndroidTest` always targets the isolated `com.lightreader.app.qa` package. Do not run
the debug instrumentation package against a device holding real reader data.

The full `scripts/run-reader-user-tests.ps1` runner additionally owns new-process and fresh-install
backup phases, which cannot run under per-test data clearing. Benchmark target and driver are also
isolated packages; the driver instruments itself so cold-start measurements can stop only the target.
