# Phase 5 Review Guide

Phase 5 adds honest process-wide GX metrics, bounded live charts, manual and automatic tab suspension, heuristic Hot Tabs, selective GX Cleaner actions, and additional appearance presets.

PostgreSQL schema version remains `3`. No database volume reset is required: Phase 5 uses timestamps and status columns already created by the standalone Phase 3 SQL scripts. Java contains only JDBC queries and commands; it contains no schema DDL.

## Delivered behavior

- While GX Control is open, OSHI samples the complete Flux process once per second on a daemon executor; sampling pauses when the panel closes.
- GX Control shows process CPU, resident memory (RSS), JVM heap/non-heap use, and open-tab count.
- CPU and resident-memory charts retain at most 60 samples and have animation disabled.
- Metrics are explicitly labeled process-wide. Flux does not claim per-WebView CPU or RAM measurement.
- Hot Tabs uses recency, navigation count, and current load state as an explicitly labeled activity heuristic.
- Manual suspension is limited to eligible background pages and displays a page-state-loss warning.
- A suspended tab retains URL, title, favicon, and zoom, but releases its `WebView`, `WebEngine`, DOM, form, script, and media state.
- Selecting or explicitly resuming a suspended tab creates a new WebView and reloads its retained URL.
- Optional auto-suspension supports a persisted 1–60 minute inactivity threshold.
- Active, pinned, loading, Start Page, already-suspended, and Keep Active tabs are never auto-suspended.
- GX Cleaner previews exact database/cache counts before confirmation.
- Cleaner can independently remove history older than 30 days, completed-download metadata older than 30 days, the in-memory favicon cache, stale saved sessions, and recently-closed-tab metadata older than 30 days.
- Cleaner never deletes downloaded files or the newest saved browser session.
- Orange and blue accents plus Circuit and Sunset wallpapers were added to Easy Setup.

## 1. Prerequisites

From the Flux project:

```bash
cd /home/mark/Projects/Java/Flux-Browser
docker compose up -d postgres
docker compose ps
```

Expected: the `postgres` service is `healthy`.

Do not run `docker compose down -v` for Phase 5. There is no new schema initialization script or migration.

## 2. Automated verification

Run this from a graphical desktop terminal:

```bash
mvn clean verify
```

Expected:

```text
FaviconServiceTest:                    Tests run: 5, Failures: 0, Errors: 0
DownloadDetectorTest:                  Tests run: 4, Failures: 0, Errors: 0
GxCleanerServiceTest:                  Tests run: 2, Failures: 0, Errors: 0
ResourceMonitorTest:                   Tests run: 1, Failures: 0, Errors: 0
TabSuspensionPolicyTest:               Tests run: 3, Failures: 0, Errors: 0
NavigationResolverTest:               Tests run: 10, Failures: 0, Errors: 0
DatabaseConfigTest:                    Tests run: 4, Failures: 0, Errors: 0
PostgresPersistenceIntegrationTest:    Tests run: 1, Skipped: 1
SqlInitializationContractTest:         Tests run: 2, Failures: 0, Errors: 0
BrowsingDataServiceTest:               Tests run: 1, Failures: 0, Errors: 0
BrowserUiSmokeTest:                    Tests run: 4, Failures: 0, Errors: 0
BrowserUiStateTest:                    Tests run: 8, Failures: 0, Errors: 0
WindowResizeSupportTest:               Tests run: 3, Failures: 0, Errors: 0
Total:                                 Tests run: 48, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

On a genuinely headless Linux session, the four JavaFX smoke tests do not run. Expected total:

```text
Tests run: 44, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Run the disposable PostgreSQL integration test explicitly:

```bash
FLUX_RUN_DB_TESTS=true \
  mvn -Dtest=PostgresPersistenceIntegrationTest test
```

Expected:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This test verifies that the existing schema supports Phase 5 Cleaner cutoff queries in addition to the previous persistence round trips. If Docker socket access is denied, run it from a terminal where your user has Docker access. Do not use `sudo mvn`.

## 3. Start the local fixture and Flux

In terminal 1:

```bash
mkdir -p /tmp/flux-phase5-server
cp src/test/resources/manual/phase2-browser-test.html \
  /tmp/flux-phase5-server/index.html
cp src/test/resources/manual/phase4-download.zip \
  /tmp/flux-phase5-server/phase4-download.zip
python3 -m http.server 8765 \
  --bind 127.0.0.1 \
  --directory /tmp/flux-phase5-server
```

Expected:

```text
Serving HTTP on 127.0.0.1 port 8765 ...
```

In terminal 2:

```bash
mvn javafx:run
```

Open:

```text
http://127.0.0.1:8765/
```

Expected: `Flux Phase 2 Test` loads and includes an `Unsaved suspension test value` field.

## 4. Process metrics and bounded charts

Open **GX Control** from the left sidebar and leave it open for at least 10 seconds.

Expected:

- [ ] CPU displays a value from `0.0%` through `100.0%` and is labeled `PROCESS`.
- [ ] Memory displays a positive value labeled `RSS`.
- [ ] JVM details display `HEAP used / committed` and `NON-HEAP` values.
- [ ] Open Tabs equals the visible number of browser tabs.
- [ ] CPU and resident-memory charts gain approximately one point per second.
- [ ] Navigation and window controls remain responsive while charts update.
- [ ] The panel says JavaFX cannot provide per-WebView CPU/RAM measurements.

Leave Flux open for more than 60 seconds.

Expected: the charts scroll by discarding old points instead of continuously widening or becoming noticeably slower.

## 5. Manual suspension and reload semantics

Create two more tabs and load the local fixture in each, leaving one fixture tab in the background. In that background page, type:

```text
UNSAVED-FLUX-STATE
```

into the suspension test field. Return to GX Control.

First select the currently active tab in Hot Tabs and click **SUSPEND**.

Expected:

```text
ACTIVE TAB CANNOT BE SUSPENDED
```

Now select the background fixture tab and click **SUSPEND**.

Expected:

- [ ] A confirmation warns that unsaved forms, media, scripts, and page state will be lost.
- [ ] After confirmation, status says `TAB SUSPENDED · PAGE STATE RELEASED`.
- [ ] The tab strip shows the suspended `◌` indicator.
- [ ] Hot Tabs retains the tab title and reports `SUSPENDED`.

Select the suspended tab from the tab strip.

Expected:

- [ ] The `◌` indicator disappears.
- [ ] The retained local URL reloads.
- [ ] Zoom and title are retained after the reload completes.
- [ ] The suspension test field is empty; `UNSAVED-FLUX-STATE` was intentionally discarded with the old WebView.

## 6. Suspension protection and automatic suspension

With at least three loaded page tabs:

1. Pin one background tab.
2. Select another background tab in Hot Tabs and click **KEEP ACTIVE**.
3. Enable **Auto-suspend inactive tabs** and move the threshold to `1 MINUTES`.
4. Leave those background tabs untouched for at least 75 seconds.

Expected:

- [ ] The pinned tab remains loaded.
- [ ] The Keep Active row retains `KEEP ACTIVE` and remains loaded.
- [ ] The currently active tab remains loaded.
- [ ] An ordinary inactive background tab becomes `SUSPENDED`.
- [ ] The status reports the number of automatically suspended tabs.

Disable auto-suspension after this check. Close and restart Flux, then reopen GX Control.

Expected: the enabled/disabled choice and minute threshold have persisted. Individual Keep Active markers are intentionally tab-lifetime state and do not survive a restart.

## 7. Hot Tabs honesty check

Navigate repeatedly in one tab, load another tab once, and leave a third tab suspended.

Expected:

- [ ] Loading/recently used tabs generally rank above idle tabs.
- [ ] Suspended tabs generally rank last.
- [ ] Every row says `ACTIVITY` rather than claiming CPU, RAM, or network use.
- [ ] The explanation explicitly identifies the ranking as a heuristic.

The exact numeric activity score is intentionally not fixed because it decays with elapsed time.

## 8. GX Cleaner exact database check

Insert three isolated records older than 30 days:

```bash
docker compose exec -T postgres \
  psql -U postgres -d flux_browser -c \
  "INSERT INTO flux_browser.visits (title, url, visited_at) VALUES ('Phase 5 expired visit', 'https://phase5-cleaner.invalid/history', now() - interval '31 days'); INSERT INTO flux_browser.downloads (source_url, file_name, target_path, status, bytes_downloaded, total_bytes, started_at, completed_at) VALUES ('https://phase5-cleaner.invalid/download', 'phase5-cleaner.zip', '/tmp/phase5-cleaner.zip', 'COMPLETED', 10, 10, now() - interval '31 days', now() - interval '31 days'); INSERT INTO flux_browser.recently_closed_tabs (url, title, closed_at) VALUES ('https://phase5-cleaner.invalid/session', 'Phase 5 old closed tab', now() - interval '31 days');"
```

Expected:

```text
INSERT 0 1
INSERT 0 1
INSERT 0 1
```

Open GX Control and click **PREVIEW**.

Expected: history, completed downloads, and session-item preview counts are each at least `1`. The favicon count may be `0` or higher depending on pages visited during this run.

Keep all four Cleaner categories checked and click **CLEAN SELECTED**, then confirm.

Expected:

- [ ] Status reports at least `REMOVED 3 ITEMS`.
- [ ] No file at `/tmp/phase5-cleaner.zip` is deleted or created.
- [ ] Current open tabs and the current saved session remain available.

Verify the isolated database records are gone:

```bash
docker compose exec -T postgres \
  psql -U postgres -d flux_browser -Atc \
  "SELECT (SELECT count(*) FROM flux_browser.visits WHERE url = 'https://phase5-cleaner.invalid/history'), (SELECT count(*) FROM flux_browser.downloads WHERE source_url = 'https://phase5-cleaner.invalid/download'), (SELECT count(*) FROM flux_browser.recently_closed_tabs WHERE url = 'https://phase5-cleaner.invalid/session');"
```

Expected exactly:

```text
0|0|0
```

## 9. Cleaner category isolation

Repeat the three inserts from section 8. In GX Cleaner, uncheck **Completed download metadata** and **Old recently-closed tab metadata**, leaving only history and favicon cache selected. Run Cleaner.

Expected:

- [ ] The Phase 5 history record is removed.
- [ ] The Phase 5 download and recently-closed records remain.
- [ ] This proves unchecked categories are not deleted.

Remove the two remaining test records after the check:

```bash
docker compose exec -T postgres \
  psql -U postgres -d flux_browser -c \
  "DELETE FROM flux_browser.downloads WHERE source_url = 'https://phase5-cleaner.invalid/download'; DELETE FROM flux_browser.recently_closed_tabs WHERE url = 'https://phase5-cleaner.invalid/session';"
```

Expected:

```text
DELETE 1
DELETE 1
```

## 10. Expanded appearance presets

Open Easy Setup and test Orange, Blue, Circuit, and Sunset.

Expected:

- [ ] Orange and Blue change borders, active-tab highlights, buttons, and chart lines.
- [ ] Circuit and Sunset change the Start Page background.
- [ ] Text remains readable and controls remain reachable at 1024×720.
- [ ] The selected accent and wallpaper remain selected after restarting Flux.

## 11. Known media limitation remains

Phase 5 does not replace JavaFX WebView. YouTube and other modern adaptive-streaming video sites may still fail to play even when their pages load. This is intentionally deferred and is not a Phase 5 regression.

## Review result

Record each failed checkbox or unexpected command output in `failure_report.txt`. Include the complete exception plus the command or UI action that produced it.
