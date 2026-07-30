# Phase 3 Review Guide

Phase 3 makes PostgreSQL the required persistence service for Flux Browser and replaces the bookmarks, history, downloads metadata, and settings placeholders with database-backed sidebar pages.

## Delivered behavior

- HikariCP connection pooling and JDBC repositories behind repository interfaces.
- Environment-driven database configuration with documented development defaults.
- Mandatory startup validation for PostgreSQL and schema version `3`.
- A dedicated startup error dialog when PostgreSQL is unavailable or uninitialized; production startup never silently substitutes an in-memory database.
- Numbered standalone SQL files for all DDL, indexes, and seed data. Java contains JDBC DML only.
- PostgreSQL tables for:
  - settings;
  - bookmark folders and bookmarks;
  - visits;
  - downloads metadata;
  - Speed Dial entries;
  - browser sessions and ordered session tabs;
  - recently closed tabs;
  - window state;
  - schema version.
- Persistent accent, wallpaper, interface scale, sidebar visibility, dock mode, reduced motion, and Speed Dial ordering.
- Persistent tab order, selected tab, address, title, pin state, zoom, recently closed tabs, window bounds, maximized state, and full-screen state.
- Lazy session restoration: restored background URLs are not loaded until their tabs are selected.
- Persistent browsing history and bookmarks included in omnibox suggestions.
- Searchable bookmarks and history with open, selected-item deletion, and confirmed clear-all actions.
- Searchable downloads metadata with deletion and clear actions. Actual download transfer handling remains Phase 4.
- Searchable settings sections with browsing-data controls and PostgreSQL connection status.
- Debounced background persistence so database work does not block the JavaFX Application Thread.

## Expected Phase 3 limitations

- PostgreSQL is mandatory when running the real application.
- JavaFX UI tests explicitly use a test-only no-op persistence adapter; this adapter is never selected by production startup.
- A fresh PostgreSQL data volume is required because the official image only executes initialization scripts for an empty data directory.
- Download rows will normally remain empty until the Phase 4 download manager creates metadata.
- Bookmark folders are represented in the database, with one seeded default folder. Folder-management UI is deferred.
- Session pages are restored by URL; JavaFX WebView form contents, JavaScript state, cookies, and scroll positions are not serialized.
- Only the selected restored tab loads immediately. Selecting a restored background tab triggers its first load.
- GX Control metrics and suspension remain Phase 5.

## 1. Recreate the development database

This step is required if the current Compose volume was created before Phase 3.

Warning: the first command permanently removes the local Flux PostgreSQL volume and any data currently stored in it.

```bash
cd /home/mark/Projects/Java/Flux-Browser
docker compose down -v
docker compose up -d postgres
docker compose ps
```

Expected:

```text
NAME                    SERVICE    STATUS
flux-browser-postgres-1 postgres   Up ... (healthy)
```

The generated container name can vary, but the `postgres` service must be `healthy`.

Inspect initialization output:

```bash
docker compose logs postgres
```

Expected:

- `001_create_flux_schema.sql`, `002_create_browser_tables.sql`, `003_create_browser_indexes.sql`, and `004_seed_browser_defaults.sql` are executed in that order.
- The final log contains `database system is ready to accept connections`.
- There are no lines beginning with `ERROR:`.

## 2. Verify schema and seed values

Schema version:

```bash
docker compose exec -T postgres psql \
  -U postgres \
  -d flux_browser \
  -c "SELECT version FROM flux_browser.schema_version ORDER BY version;"
```

Expected:

```text
 version
---------
       3
(1 row)
```

Table count:

```bash
docker compose exec -T postgres psql \
  -U postgres \
  -d flux_browser \
  -c "SELECT count(*) AS phase3_tables FROM information_schema.tables WHERE table_schema = 'flux_browser';"
```

Expected:

```text
 phase3_tables
---------------
            11
(1 row)
```

Seed counts:

```bash
docker compose exec -T postgres psql \
  -U postgres \
  -d flux_browser \
  -c "SELECT (SELECT count(*) FROM flux_browser.settings) AS settings, (SELECT count(*) FROM flux_browser.speed_dial_entries) AS speed_dials, (SELECT count(*) FROM flux_browser.bookmark_folders) AS bookmark_folders, (SELECT count(*) FROM flux_browser.window_state) AS window_rows;"
```

Expected:

```text
 settings | speed_dials | bookmark_folders | window_rows
----------+-------------+------------------+-------------
        6 |           6 |                1 |           1
(1 row)
```

## 3. Automated verification

Run:

```bash
mvn clean verify
```

Expected in a graphical desktop session:

```text
FaviconServiceTest:                    Tests run: 5, Failures: 0, Errors: 0
NavigationResolverTest:               Tests run: 10, Failures: 0, Errors: 0
DatabaseConfigTest:                    Tests run: 4, Failures: 0, Errors: 0
PostgresPersistenceIntegrationTest:    Tests run: 1, Skipped: 1
SqlInitializationContractTest:         Tests run: 2, Failures: 0, Errors: 0
BrowserUiStateTest:                    Tests run: 8, Failures: 0, Errors: 0
WindowResizeSupportTest:               Tests run: 3, Failures: 0, Errors: 0
BrowserUiSmokeTest:                    Tests run: 3, Failures: 0, Errors: 0
Total:                                 Tests run: 36, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

On headless Linux, `BrowserUiSmokeTest` reports zero executed methods:

```text
Tests run: 33, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

The PostgreSQL integration test is opt-in so ordinary builds do not unexpectedly start containers. Run it explicitly:

```bash
FLUX_RUN_DB_TESTS=true \
  mvn -Dtest=PostgresPersistenceIntegrationTest test
```

Expected:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This test starts its own disposable PostgreSQL 17 container, runs the real numbered initialization scripts, verifies settings and Speed Dial seeds, and round-trips bookmarks, visits, session tabs, zoom, and window geometry.

If it reports permission denied for `/var/run/docker.sock`, run it from a terminal where your user already has Docker access. Do not use `sudo mvn`, because that can create root-owned Maven build files.

Validate Compose independently:

```bash
docker compose config --quiet
```

Expected: exit status `0` and no output.

## 4. Start Flux

With PostgreSQL healthy:

```bash
mvn javafx:run
```

Expected on the first Phase 3 launch:

| Item | Expected value |
|---|---|
| Startup | Main Flux window opens without a database dialog |
| Initial tabs | One selected Start Page tab |
| Speed Dials | Six seeded entries |
| Accent | Red |
| Wallpaper | Grid |
| Interface scale | `13` |
| Sidebar | Visible |
| Settings database status | `CONNECTED · POSTGRESQL` |
| Bookmarks | `0 BOOKMARKS` |
| History | Empty until a page succeeds |
| Downloads | `0 DOWNLOADS` |

## 5. Settings and Speed Dial persistence

In Easy Setup:

1. Select cyan accent.
2. Select Neon wallpaper.
3. Set interface scale to `15`.
4. Disable reduced motion if currently enabled, then enable it.
5. Switch sidebar panels to overlay mode.

On the Start Page:

1. Add a Speed Dial named `Example` with address `example.com`.
2. Move it left once.

Close Flux normally, then run:

```bash
mvn javafx:run
```

Expected:

- [ ] Cyan accent is restored.
- [ ] Neon wallpaper is restored.
- [ ] Interface scale is approximately `15`.
- [ ] Reduced motion remains enabled.
- [ ] Overlay panel mode is restored.
- [ ] `Example` exists in its reordered Speed Dial position.

Optional database verification:

```bash
docker compose exec -T postgres psql \
  -U postgres \
  -d flux_browser \
  -c "SELECT setting_key, setting_value FROM flux_browser.settings ORDER BY setting_key; SELECT position, title, url FROM flux_browser.speed_dial_entries ORDER BY position;"
```

Expected: the six setting rows reflect the UI choices, and the Speed Dial query includes `Example` in the same order shown by Flux.

## 6. Bookmark persistence

1. Navigate to `https://example.com`.
2. Click the star inside the omnibox.
3. Open the Bookmarks sidebar with `Ctrl/Command+Shift+B`.

Expected:

- [ ] The status changes to `1 BOOKMARKS`.
- [ ] The list contains the page title and `https://example.com/`.
- [ ] Searching for `example` keeps the row; searching for unrelated text hides it.
- [ ] Double-clicking the row or selecting it and pressing `OPEN` navigates the active tab.
- [ ] After restarting Flux, the bookmark remains.
- [ ] `DELETE SELECTED` removes only the selected bookmark.
- [ ] `CLEAR ALL` requests confirmation and removes all bookmarks only after confirmation.

## 7. Persistent browsing history

Navigate successfully to at least three different HTTP(S) pages, then open History with `Ctrl/Command+H`.

Expected:

- [ ] Every successful navigation appears with its title and URL, newest first.
- [ ] Search filters both titles and URLs.
- [ ] Double-clicking a visit opens it in the active tab.
- [ ] `DELETE SELECTED` removes only that visit.
- [ ] `CLEAR ALL` asks for confirmation.
- [ ] Cancel preserves the rows.
- [ ] Confirm removes every visit.
- [ ] History survives a restart until explicitly cleared.
- [ ] Stored history and bookmarks appear as omnibox suggestions.

Database verification:

```bash
docker compose exec -T postgres psql \
  -U postgres \
  -d flux_browser \
  -c "SELECT title, url, visited_at FROM flux_browser.visits ORDER BY visited_at DESC LIMIT 10;"
```

Expected: the remaining sidebar visits match the returned rows.

## 8. Tab and window session restoration

1. Open three tabs.
2. Load a different address in each.
3. Pin the first tab.
4. Set the second tab to 125% zoom.
5. Select the second tab.
6. Close the third tab so it becomes recently closed.
7. Resize and move the Flux window.
8. Close Flux normally.
9. Start it again with `mvn javafx:run`.

Expected:

- [ ] Two open tabs return in the original order.
- [ ] The first tab is pinned.
- [ ] The second tab is selected and retains 125% zoom.
- [ ] Only the selected restored tab begins loading at startup.
- [ ] Selecting the pinned background tab triggers its deferred load.
- [ ] `Ctrl/Command+Shift+T` restores the previously closed third tab.
- [ ] The normal window bounds return on the same monitor.
- [ ] Maximized/full-screen state is restored when Flux was closed in that state.

Database verification after closing Flux:

```bash
docker compose exec -T postgres psql \
  -U postgres \
  -d flux_browser \
  -c "SELECT position, title, url, pinned, selected, zoom, start_page FROM flux_browser.session_tabs ORDER BY position; SELECT title, url, pinned, zoom FROM flux_browser.recently_closed_tabs ORDER BY closed_at DESC; SELECT x, y, width, height, maximized, fullscreen FROM flux_browser.window_state;"
```

Expected: the rows match the session, recently closed stack, and window state used in the checklist.

## 9. Internal downloads and settings pages

- [ ] Downloads search, `DELETE SELECTED`, and `CLEAR ALL` are reachable.
- [ ] An empty Phase 3 database shows `0 DOWNLOADS`.
- [ ] The page explains that transfer handling begins in Phase 4.
- [ ] Settings search for `appearance` shows the appearance card.
- [ ] Settings search for `privacy` or `history` shows browsing-data controls.
- [ ] Clearing history/download metadata from Settings uses the same confirmation behavior as their individual pages.
- [ ] Settings shows `CONNECTED · POSTGRESQL`.

## 10. Required database failure behavior

Close Flux, then stop PostgreSQL:

```bash
docker compose stop postgres
mvn javafx:run
```

Expected after the connection timeout:

- A dialog titled `Flux Browser startup failed` appears.
- Its header is `PostgreSQL is required`.
- It tells you to run `docker compose up -d postgres`.
- The main browser window does not open.
- The database password is not shown.
- Flux does not silently start with temporary data.

Restore the service:

```bash
docker compose start postgres
docker compose ps
```

Expected: PostgreSQL returns to `healthy`, and `mvn javafx:run` opens the persisted browser state.

## Failure reporting

If a command or manual check fails, replace the contents of `failure_report.txt` with:

1. The exact command or checklist item.
2. The complete output or observed behavior.
3. The expected behavior from this guide.
4. `java -version`, `mvn -version`, and `docker version`.
5. `docker compose ps` and the relevant `docker compose logs postgres` section for database failures.
6. Operating system and desktop session type.
7. For visual failures, the Flux window size and affected sidebar/tab.
