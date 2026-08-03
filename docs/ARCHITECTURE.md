# Flux Browser Architecture and Lifecycle

## Composition

`BrowserWindow` creates one `ApplicationContext`. The context owns services and
supplies dependency-constructed controllers to FXMLLoader. Controllers do not
open JDBC connections, execute SQL, or perform blocking network transfers.

The main ownership chain is:

```text
BrowserWindow
└── ApplicationContext
    ├── PersistenceService ── HikariCP ── PostgreSQL repositories
    ├── DownloadManager ───── bounded worker pool
    ├── ResourceMonitor ───── one paused/resumed sampler
    ├── FaviconService ────── bounded cache + HttpClient
    ├── BrowserUiState
    └── BrowserController
        ├── TabManager ────── BrowserTab ── optional WebView
        └── SidebarPanelController ── lazy child FXML controllers
```

Static UI structure remains in FXML and presentation remains in CSS. The shell
FXML is loaded at startup. The five sidebar content documents are loaded on
first selection and cached for the lifetime of the window.

## Lazy rendering

`BrowserTab` owns at most one WebView/WebEngine pair. Creating a Start Page tab
or restoring an inactive session tab allocates no WebView. Navigation or display
creates it. Suspension cancels loading, clears popup handling, loads a null page,
and drops the WebView, WebEngine, and WebHistory references. Selection resumes
the tab by creating a new engine and reloading its retained address.

`TabManager` is the sole owner of open and recently closed tabs. Closing a tab
removes controller listeners before disposing its engine. The last closed tab
is replaced with a lazy Start Page tab.

## Thread ownership

- JavaFX nodes, observable UI lists, WebViews, dialogs, and Timelines belong to
  the JavaFX Application Thread.
- Persistence operations run on one daemon scheduled executor. HikariCP owns
  its JDBC pool, and repository methods use try-with-resources.
- Downloads run in a fixed three-thread daemon pool and stream into `.part`
  files. UI updates are handed back through `Platform.runLater`.
- OSHI resource sampling runs once per second on one daemon thread only while
  GX Control is visible.
- Java's HttpClient performs favicon requests asynchronously.

## Bounded state

| Resource | Bound |
| --- | ---: |
| Favicon images | 256 least-recently-used entries |
| Recently closed tabs | 20 snapshots |
| Terminal live-download tasks | 100 tasks |
| CPU chart | 60 samples |
| Memory chart | 60 samples |
| Sidebar views | 5 known panel documents |

Persistent bookmark, history, download, and session search results also use
fixed query limits. These bounds protect UI retention; PostgreSQL remains the
authoritative metadata store.

## Shutdown sequence

The window first saves geometry. `ApplicationContext.close()` then stops the
auto-suspension Timeline, closes lazy sidebar content and its listeners, saves
the current tab session, detaches tab listeners, disposes all WebViews, cancels
and drains downloads, stops OSHI sampling, flushes persistence work, closes the
HikariCP pool, and emits the final performance summary. Normal shutdown does not
call `System.exit`.

## Persistence contract

DDL, constraints, indexes, and seed values exist only in numbered SQL files in
`docker/postgres/init/`. Java contains JDBC repository operations and schema
version validation only. Phase 6 does not change schema version 3.

## Diagnostics

`PerformanceTracker` retains only aggregate count, total time, and maximum time
per operation plus on-demand JVM heap values. It covers application-context and
persistence startup, FXML loading, CSS/layout, tab creation/closure, and database
operations. Startup and shutdown summaries are logged at INFO. It intentionally
does not retain operation arguments or browsing data.
