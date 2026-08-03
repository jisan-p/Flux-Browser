# Flux Browser

Flux Browser is a lightweight desktop browser built with Java 21, JavaFX, FXML, and CSS. The project is being developed phase-by-phase toward an Opera GX-inspired interface while retaining JavaFX `WebView` as its browser engine.

## Implemented through Phase 6

- Standard Maven project targeting Java 21.
- JavaFX controls, FXML, and WebView integration.
- URL and DuckDuckGo search resolution.
- Lightweight application composition root for controller dependencies.
- Unit tests plus display-aware JavaFX/FXML and local-page smoke tests.
- Docker Compose PostgreSQL development service.
- Reproducible Maven build/test container.
- Undecorated Flux GX window shell with draggable title bar and native window actions.
- GX-inspired tab strip, navigation bar, vertical sidebar, overlay/docked panels, and start page.
- Editable, reorderable, and persistent Speed Dial.
- Easy Setup controls for accent, wallpaper, UI scale, sidebar visibility, panel docking, and reduced motion.
- Independent `WebView`, history, load state, title, favicon, zoom, and failure state for every tab.
- New, close, reopen, duplicate, pin, reorder, close-others, close-right, middle-click close, and popup-created tabs.
- Find in page, copy address, open externally, print, bounded zoom, and per-page retry UI.
- Asynchronous favicon discovery/loading with timeouts, caching, size limits, and fallback behavior.
- Omnibox autocomplete from Speed Dial and in-memory tab history.
- Confirmed routing for `mailto:`, `tel:`, and `magnet:` links; unsupported schemes are blocked.
- Mandatory PostgreSQL startup validation through HikariCP and JDBC.
- Standalone SQL initialization scripts for settings, bookmark folders/bookmarks, visits, downloads metadata, Speed Dial, sessions/tabs, recently closed tabs, and window state.
- Persistent appearance settings, Speed Dial ordering, browsing history, bookmarks, tab/session state, closed tabs, zoom, pin state, and window geometry.
- Lazy session restoration: only the selected restored tab loads at startup.
- Searchable bookmarks, history, downloads metadata, and settings sidebar pages with selected-item deletion and confirmed clear-data actions.
- Actionable startup dialog when PostgreSQL is unavailable or the Phase 3 schema has not been initialized.
- Asynchronous HTTP downloads streamed into `.part` files with timeouts, redirects, sanitized filenames, and collision-safe destinations.
- Live download progress with cancel, retry, open, reveal, persisted metadata, and failure recovery.
- Conservative WebView download-link detection for common archive, installer, document, and disk-image extensions.
- Per-site popup decisions with ask, allow, block, persistent “always allow,” and permission reset.
- Browsing-data controls for history, download metadata, current-process cookies, favicon cache, and saved session data.
- Graceful shutdown that disposes WebViews, cancels transfers, drains persistence work, and closes HikariCP without using `System.exit` as normal lifecycle control.
- OSHI-backed process CPU, resident-memory, JVM heap/non-heap, and open-tab metrics sampled outside the JavaFX thread while GX Control is open.
- Bounded live CPU and memory charts in the GX Control sidebar with explicit process-wide labeling.
- Manual background-tab suspension that releases the WebView while retaining URL, title, favicon, and zoom metadata.
- Optional inactivity-based auto-suspension with persistent timeout settings and protection for active, pinned, loading, Start Page, and Keep Active tabs.
- Hot Tabs activity ranking based on recency, navigation, and load-state heuristics rather than invented per-tab hardware readings.
- GX Cleaner preview and confirmed selective cleanup for 30-day-old history, completed download metadata, favicon cache, stale saved sessions, and old recently-closed-tab metadata; the newest saved session is protected.
- Expanded orange/blue accent presets plus Circuit and Sunset wallpapers.
- Lazy WebView allocation: Start Page and inactive restored tabs do not create a
  rendering engine until a page must be displayed.
- Independently lazy-loaded GX Control, bookmarks, history, downloads, and
  settings FXML panels.
- Bounded favicon (256 entries), recently closed tab (20 entries), live chart
  (60 points), and completed download task (100 entries) retention.
- Aggregate startup, CSS/layout, tab lifecycle, database operation, and JVM heap
  diagnostics in the application log without retaining browsing data.
- Explicit listener detachment and executor/resource shutdown paths for tabs,
  sidebar panels, downloads, monitoring, persistence, and WebViews.

## Documentation

- [User guide](docs/USER_GUIDE.md)
- [Architecture and lifecycle](docs/ARCHITECTURE.md)
- [Performance testing](docs/PERFORMANCE_TESTING.md)
- [Cross-platform smoke-test checklist](docs/CROSS_PLATFORM_CHECKLIST.md)

## Prerequisites

For normal desktop development:

- JDK 21
- Maven 3.9 or newer
- Docker with Docker Compose

The JavaFX application runs on the host so native desktop windows work consistently across Windows, Linux, and macOS. Docker supplies PostgreSQL and an optional reproducible headless Maven build.

## First Phase 3 database initialization

PostgreSQL initialization scripts only execute when the data volume is empty. If the existing development volume was created during Phase 0, recreate it once so the Phase 3 scripts run:

```bash
docker compose down -v
docker compose up -d postgres
docker compose ps
```

`docker compose down -v` permanently deletes the local Flux development database. Do not run it when the volume contains data you need.

Expected PostgreSQL state: the service is `healthy`, database `flux_browser` contains schema version `3`, and six default Speed Dial entries exist.

## Build and run

```bash
mvn clean verify
mvn javafx:run
```

PostgreSQL must be healthy before `mvn javafx:run`. Flux intentionally shows a startup error instead of using an in-memory or embedded database fallback.

On a Linux machine without `DISPLAY` or `WAYLAND_DISPLAY`, the JavaFX UI smoke tests are skipped. Run the same `mvn clean verify` command from a graphical session to execute them.

## PostgreSQL development service

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Development connection values:

```text
Database: flux_browser
Host:     localhost
Port:     5432
User:     postgres
Password: 1234
Schema:   flux_browser
```

The official PostgreSQL image executes numbered SQL files in `docker/postgres/init/` when it initializes a new data volume. All database DDL and seed logic must remain in those SQL files. Java code may connect through JDBC but must not create or alter database objects.

PostgreSQL only runs initialization scripts when its data directory is empty. During development, applying changed initialization scripts to a fresh database requires removing the existing Compose volume first. That operation destroys local browser data and should only be run intentionally.

Inspect service health:

```bash
docker compose ps
docker compose logs postgres
```

## Reproducible container build

The optional `flux-build` profile compiles the project and runs non-graphical tests in a Java 21/Maven container:

```bash
docker compose --profile build run --rm flux-build
```

The PostgreSQL service is started automatically and must become healthy before the build service runs.

## Configuration contract

Persistence reads these environment variables:

```text
FLUX_DB_URL=jdbc:postgresql://localhost:5432/flux_browser
FLUX_DB_USER=postgres
FLUX_DB_PASSWORD=1234
FLUX_DB_SCHEMA=flux_browser
```

Do not commit production credentials. The fixed password in Compose is for local development only.

## Current browser controls

- Enter a domain, full URL, local address, or search query.
- Navigate backward and forward.
- Reload or stop the current page.
- Return to the Flux start page.
- Use `Ctrl`/`Command` + `L` to focus the address bar.
- Use `Ctrl`/`Command` + `T`, `W`, or `Shift+T` to create, close, or reopen tabs.
- Use `Ctrl`/`Command` + `Tab` or `Shift+Tab` to cycle tabs.
- Use `Ctrl`/`Command` + `1` through `9` to select tabs; `9` selects the final tab.
- Use `Ctrl`/`Command` + `R` to reload or stop and `Alt` + `Left`/`Right` for history.
- Use `Ctrl`/`Command` + `F` to find in the current page and `P` to print.
- Use `Ctrl`/`Command` + `+`, `-`, or `0` to control page zoom.
- Use `Ctrl`/`Command` + `Shift+B`, `H`, `J`, or `,` for bookmarks, history, downloads, or settings panels.
- Use `F11` to enter or leave full screen.
- Open GX Control, bookmarks, history, downloads metadata, and settings pages from the sidebar.
- Add, edit, remove, and reorder Speed Dial entries from the start page.
- Change persistent appearance settings from Easy Setup.
- Use the omnibox star to save the current page as a bookmark.
- Search, open, delete, or clear bookmarks and persistent browsing history from the sidebar.
- Select likely download links or enter a direct archive/document URL to open the save chooser.
- Use Downloads to monitor progress and cancel, retry, open, or reveal transfers.
- Use Settings to clear supported browsing-data categories and reset popup permissions.
- Use GX Control to monitor process resources, inspect heuristic Hot Tabs, suspend background tabs, and mark tabs Keep Active.
- Enable automatic suspension and select an inactivity window from 1–60 minutes.
- Preview GX Cleaner counts before deleting only the selected 30-day-old metadata categories.

## JavaFX WebView limitations

JavaFX WebView has no supported general download callback and does not expose response `Content-Disposition` headers. Flux therefore detects common download filename extensions in navigations. A server-generated download URL without a recognizable extension may not be intercepted.

JavaFX also provides no supported public API for clearing its internal HTTP cache. Flux does not use unsupported reflective access and does not claim that clearing history, cookies, or favicon data clears WebView’s internal cache. Cookie clearing affects cookies held by the current Flux process. Clearing download metadata never deletes downloaded files.

JavaFX WebView is not Chromium and cannot reliably play every modern adaptive-streaming or DRM video site. In particular, YouTube playback can remain stuck even when the page itself loads. Use the operating-system browser for sites that exceed WebView's media support.

JavaFX does not expose trustworthy per-WebView CPU or memory measurements. GX Control therefore labels OSHI measurements as process-wide, and Hot Tabs is explicitly an activity heuristic. Suspending a tab discards DOM, form, media, and script state; resuming reloads the saved URL.
