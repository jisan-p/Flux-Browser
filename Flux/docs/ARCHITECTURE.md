# Flux: architectural blueprint and MVP matrix

## 1. Min browser deconstruction

This analysis was completed against the supplied `../min` checkout before implementing Flux. The checkout identifies itself as Min 1.35.7 in `package.json`. It is an Electron application: `main/` manages native windows and web contents, `js/` coordinates the browser UI, `css/` styles it, `pages/` contains internal pages, and `ext/` contains larger browser subsystems. Flux uses these architectural ideas; it does not bundle Min or copy its implementation.

The browsing flow is `searchbar → urlParser.parse → browserUI → webviews → navigation/load/title events → tab state and places`. `js/browserUI.js` coordinates add, select, close, and popup actions. `js/tabState/tab.js` separates persistent tab attributes from transient loading/audio state. `js/navbar/navigationButtons.js` queries navigation availability when selection or location changes. `js/webviews.js` forwards page lifecycle events; `js/navbar/progressBar.js` presents loading state. `js/places/places.js` uses asynchronous messages to the places service, and `js/util/database.js` defines a Dexie/IndexedDB store that combines visited pages, bookmarks, tags, and search metadata.

| Capability | Evidence in the supplied Min source | Flux MVP decision |
| --- | --- | --- |
| Address versus search resolution | `js/util/urlParser.js`, `js/util/searchEngine.js` | Retain. Normalize HTTP(S), hostnames, local development addresses, and international domains; encode or
dinary searches for DuckDuckGo. |
| Open, select, close tabs | `js/browserUI.js`, `js/tabState/tab.js`, `js/navbar/tabBar.js` | Retain. One controller/WebView per tab; closing the last tab creates a fresh Speed Dial. |
| Back, forward, reload, stop | `js/navbar/navigationButtons.js`, `js/webviews.js`, `js/keybindings.js` | Retain through WebEngine/WebHistory and desktop keyboard shortcuts. Add a native FXML Home view. |
| Page title, progress, errors | `js/webviews.js`, `js/navbar/progressBar.js`, `pages/error/` | Retain real worker progress, title fallback, failed-load recovery, and cancellation. |
| Bookmarks and tags | `js/navbar/bookmarkStar.js`, `js/searchbar/bookmarkManager.js` | Retain a bookmark star and searchable library; replace tags with a single folder/category. |
| Browsing history | `js/places/places.js`, `js/places/placesService.js`, `js/searchbar/historyViewer.js` | Retain successful HTTP(S) visits in newest-first order, search, individual deletion, and clear-all. |
| New-tab page | `js/newTabPage.js`, `pages/newtab/` | Adapt to a custom FXML Speed Dial with editable persisted shortcuts. Min's source here handles a background image; the GX tiles are a Flux addition. |
| Popup/new-tab requests | `js/browserUI.js`, `main/viewManager.js` | Retain WebEngine popup routing to ordinary tabs. |
| Tasks, focus mode, session restore | `js/taskOverlay/`, `js/focusMode.js`, `js/sessionRestore.js` | Omit from this MVP. Tabs last for the current application session. |
| Ad/tracker filtering, HTTPS lists, phishing rules | `main/filtering.js`, `ext/`, `pages/phishing/` | Omit custom filtering/security engines. Use WebKit/JDK's normal networking and certificate checks. |
| Password managers and keychain integration | `js/passwordManager/`, `main/keychainService.js` | Omit credential vaults and autofill. |
| Full-text indexing, instant answers, plugins | `js/places/fullTextSearch.js`, `js/searchbar/` | Omit indexing and the plugin pipeline; SQL filters cover titles, URLs, and folders. |
| Reader, translation, PDF viewer, downloads | `reader/`, `pages/translateService/`, `pages/pdfViewer/`, `main/download.js` | Omit dedicated subsystems. This is a lightweight WebView browser, not a full Chromium replacement. |
| Electron IPC and process management | `main/main.js`, `main/viewManager.js` | Replace with one Java process, JavaFX events, and a JDBC executor. |

## 2. Component diagram

```mermaid
flowchart LR
    App[FluxBrowser] --> Window[BrowserWindow.fxml / BrowserController]
    Window --> Sidebar[Sidebar.fxml / SidebarController]
    Window --> Chips[TabHeader.fxml / TabHeaderController]
    Window --> Tabs[WebTab.fxml / WebTabController]
    Tabs --> Engine[JavaFX WebView + WebEngine]
    Tabs --> Home[SpeedDial.fxml / SpeedDialController]
    Home --> Tiles[DialTile.fxml / DialTileController]
    Window --> Library[Library.fxml / LibraryController]
    Library --> Rows[LibraryRow.fxml / LibraryRowController]
    Window --> Settings[Settings.fxml / SettingsController]
    Window --> Dialogs[EntryDialog.fxml + MessageDialog.fxml]
    Tabs --> HistoryDAO
    Library --> HistoryDAO
    Library --> BookmarkDAO
    Home --> SpeedDialDAO
    HistoryDAO --> DB[DatabaseManager / single background executor]
    BookmarkDAO --> DB
    SpeedDialDAO --> DB
    DB --> PG[(PostgreSQL)]
```

## 3. Package structure

```text
Flux/
├── pom.xml
├── database/schema.sql
├── config/database.properties.example
├── docs/
│   ├── ARCHITECTURE.md
│   ├── UI_SCENEBUILDER_GUIDE.md
│   └── PRESENTATION.md
├── src/main/java/com/flux/browser/
│   ├── FluxBrowser.java
│   ├── controller/          # One dedicated controller per FXML component
│   ├── db/                  # Connection manager and three concrete JDBC DAOs
│   ├── model/               # Immutable records
│   └── util/                # URL resolution, FXML loading, owned dialogs
├── src/main/resources/com/flux/browser/
│   ├── view/                # FXML screens, rows, chips, tiles, and dialogs
│   └── style.css
└── src/test/java/com/flux/browser/
    ├── BrowserChecks.java
    ├── DatabaseChecks.java
    └── BrowserSmokeChecks.java
```

## 4. Persistence and threading decisions

* PostgreSQL owns `history`, `bookmarks`, and `speed_dial`. The brief uses both `quick_dial` and `speed_dial`; **`speed_dial` is the single canonical table**, representing the same feature.
* Each history row is one successful HTTP(S) page-load visit. Reloads and Back/Forward loads count as visits; tab selection, Home, failed loads, cancelled loads, and title-only updates do not.
* Bookmarks and speed dials have unique URLs and editable titles. Bookmarks also have a category. Timestamps use `TIMESTAMPTZ`; Java stores `Instant` and formats dates in the machine's local time zone.
* The idempotent schema seeds the speed-dial table only when that table is first created. Deleted seed tiles stay deleted after a restart.
* All DAO methods return `CompletableFuture`. Schema initialization, connections, statements, and result iteration run on one dedicated executor. Each operation closes its JDBC resources with try-with-resources. No ORM, connection pool, or generic repository is necessary at this scale.
* UI callbacks re-enter the JavaFX Application Thread with `Platform.runLater`. A single queue orders writes and subsequent reads, including a history clear behind already queued visits. A new visit completed after a clear is a new history entry.
* Database failure does not prevent browsing. A visible storage status, disabled persistence actions, starter shortcuts, and a Settings reconnect action explain the current state. Failed writes are reported; they are not silently buffered or represented as saved.
* Closing the window shuts down the executor without blocking JavaFX. Already accepted work drains on the worker; connection and socket timeouts bound failed-network waits.

## 5. UI and engine decisions

All application-authored controls, including dynamic tab chips, list-row graphics, tile contents, dialogs, and the load-error panel, originate in FXML. Controllers load instances, bind state, and attach them to FXML-defined containers. Standard JavaFX control skins provide their usual internal rendering.

The shell uses the requested carbon palette (`#121214`, `#1c1c1f`, `#2c2c30`) with a magenta accent (`#fa1e4e`) and optional cyan (`#00ffff`). A narrow sidebar, custom tab chips, compact address strip, and uncluttered Speed Dial form the presentation view. SVGPath geometry inside FXML provides lightweight decorative artwork without image downloads or icon dependencies.

Home is a native overlay within each tab. It retains that tab's last web page and WebHistory; Back from Home returns to that page. History/Bookmarks/Settings are shell panels over the selected tab. Returning to browsing preserves the underlying page. WebEngine and all its properties stay on the JavaFX thread.

URL entry accepts HTTP(S) only, avoiding execution of address-bar `javascript:`/`data:` payloads and accidental external protocol launches. This does not replace the normal JavaScript execution of visited websites. The application does not expose Java objects to webpage JavaScript or disable certificate validation.

## 6. Dependency references

The Maven build targets Java 21 and pins OpenJFX 21.0.12 and PostgreSQL JDBC 42.7.13, checked against the [OpenJFX 21 release notes](https://gluonhq.com/products/javafx/openjfx-21-release-notes/) and [pgJDBC downloads](https://jdbc.postgresql.org/download/). JavaFX's Maven plugin selects native artifacts for the host platform; see the [official Maven setup](https://openjfx.io/openjfx-docs/#maven). The threading and page lifecycle design follows the [JavaFX web API](https://openjfx.io/javadoc/21/javafx.web/javafx/scene/web/package-summary.html).
