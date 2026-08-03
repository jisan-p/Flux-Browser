# Phase 6 Review Guide

Phase 6 hardens Flux for extended use. It adds lazy WebViews and independently
lazy sidebar FXML documents, bounded in-memory retention, explicit lifecycle
cleanup, privacy-safe performance diagnostics, lifecycle tests, and release
readiness documentation.

PostgreSQL schema version remains `3`. Phase 6 adds no DDL or migration, so do
not remove or recreate the existing database volume.

## Delivered behavior

- A Start Page tab has no WebView. Restored background tabs also remain without
  a WebView until selected; navigating/displaying a page creates one.
- Closing a page cancels loading and releases WebView, WebEngine, WebHistory,
  popup, download, and visit-handler references. Suspension releases the three
  rendering/history objects while retaining tab-level handlers for resume.
- The sidebar shell loads at startup, but GX Control, bookmarks, history,
  downloads, and settings each load from separate FXML only on first use.
- GX process sampling runs only while GX Control is visible. Automatic tab
  suspension continues independently of whether GX Control has been opened.
- Closed-tab UI listeners are detached. Sidebar resource/tab/download listeners
  are detached at shutdown. Executors, HikariCP, streams, and WebViews follow
  explicit close paths.
- Favicon retention is capped at 256 least-recently-used entries, recently
  closed tabs at 20, terminal live-download tasks at 100, and each chart at 60
  samples.
- INFO logs report aggregate startup persistence, context, FXML, CSS/layout,
  total startup, tab create/close, database-operation, and heap measurements.
  They never record URLs, titles, search terms, or file paths.
- User, architecture, performance, and cross-platform test documentation is in
  `docs/`.

## 1. Prerequisites

From the Flux project directory:

```bash
cd /home/mark/Projects/Java/Flux-Browser
docker compose up -d postgres
docker compose ps
```

Expected: `postgres` reports `healthy`.

Do not run `docker compose down -v`. Phase 6 uses the existing version 3 schema.

## 2. Automated verification

Run from a graphical desktop terminal:

```bash
mvn clean verify
```

Expected per-suite results:

```text
BrowserTabLifecycleTest:              Tests run: 2, Failures: 0, Errors: 0
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
PerformanceTrackerTest:                Tests run: 2, Failures: 0, Errors: 0
Total:                                 Tests run: 52, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

On a genuinely headless Linux session, the four JavaFX smoke tests do not run.
Expected total:

```text
Tests run: 48, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Validate every FXML document separately:

```bash
xmllint --noout src/main/resources/org/custombrowser/ui/*.fxml
```

Expected: exit code `0` and no output.

Run the disposable real-PostgreSQL repository test where the current user can
access Docker:

```bash
FLUX_RUN_DB_TESTS=true \
  mvn -Dtest=PostgresPersistenceIntegrationTest test
```

Expected:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

If Docker socket access is denied, run the command from a terminal where your
normal user has Docker access. Do not run Maven with sudo. Put any resulting
error and the exact command in `failure_report.txt`.

## 3. Startup and lazy sidebar check

Start Flux:

```bash
mvn javafx:run
```

Expected console lines include one line for heap and one line for each startup
operation. Values vary by computer, but counts must be `1`:

```text
Flux performance [startup]: heap=... MiB committed=... MiB max=... MiB
Flux performance [startup]: startup.context count=1 ...
Flux performance [startup]: startup.css-layout count=1 ...
Flux performance [startup]: startup.fxml count=1 ...
Flux performance [startup]: startup.persistence count=1 ...
Flux performance [startup]: startup.total count=1 ...
```

Before opening a sidebar panel:

- [ ] Start Page appears normally and no page loads in the background.
- [ ] CPU/RAM sampling messages or updates are absent.
- [ ] Navigation, title bar, tab strip, and sidebar remain responsive.

Open the panels in this order: Bookmarks, History, Downloads, Settings, GX
Control. Close each before opening the next.

Expected:

- [ ] Each first open displays its controls with no FXML/controller error.
- [ ] Reopening a panel is immediate and preserves its current search text.
- [ ] GX charts begin updating approximately once per second only while GX
      Control is visible, then stop changing when it closes.
- [ ] Automatic suspension still works when enabled even if GX Control is
      closed for the full inactivity interval.

## 4. Lazy WebView and session restore check

Create 20 tabs with `Ctrl+T` and leave every tab on Start Page.

Expected:

- [ ] Tab creation remains responsive.
- [ ] No network page loads or blank white WebView surfaces appear.
- [ ] Closing all 20 tabs one by one leaves exactly one replacement Start Page
      tab and does not degrade as the cycle repeats.

Now load the local fixture in five tabs, select the third tab, and close Flux
normally. Start Flux again.

```bash
python3 -m http.server 8765 --bind 127.0.0.1 \
  --directory src/test/resources/manual
```

Use this address:

```text
http://127.0.0.1:8765/phase2-browser-test.html
```

Expected after restart:

- [ ] The selected tab loads.
- [ ] Other restored tabs show retained titles/addresses but do not load until
      selected.
- [ ] Selecting each restored tab loads its retained address once.
- [ ] Suspending a background loaded tab releases it; selecting it recreates
      the view and reloads the address with its retained zoom.

## 5. Repeated lifecycle and bounded-growth check

Use the local fixture to avoid internet variability:

1. Open 20 tabs, navigate five, and close all but one.
2. Repeat that cycle five times.
3. Open GX Control for 70 seconds, then close it.
4. Run and cancel/retry local downloads as needed; download files are not
   deleted by metadata cleanup.
5. Leave Flux idle for 30 seconds, then close it normally.

Expected:

- [ ] The UI remains responsive during tab and download activity.
- [ ] CPU and memory charts stop at 60 moving samples.
- [ ] Closed page content never reappears and closed-tab operation speed does
      not progressively deteriorate.
- [ ] Heap may fluctuate due to JVM/WebKit caches but does not grow directly
      with every closed-tab cycle without settling.
- [ ] No `RejectedExecutionException`, `IllegalStateException`, leaked-stream,
      HikariCP, or JavaFX-thread exception appears.

Optional same-user JDK inspection from a second terminal:

```bash
jps -l
jcmd <PID> GC.heap_info
jcmd <PID> Thread.print
```

Expected during execution: one `flux-persistence`, up to three `flux-download`,
and one `flux-resource-monitor` daemon thread. After normal exit, the Flux JVM
and all these threads disappear. If attach is denied, use the same user and JDK
that launched Flux; do not use sudo.

## 6. Shutdown diagnostics

Close Flux using its title-bar close button after creating and closing several
tabs and opening at least one database-backed panel.

Expected console lines include:

```text
Flux performance [shutdown]: heap=... MiB committed=... MiB max=... MiB
Flux performance [shutdown]: database.operation count=<positive> ...
Flux performance [shutdown]: tab.close count=<positive> ...
Flux performance [shutdown]: tab.create count=<positive> ...
```

Expected behavior:

- [ ] The window closes without needing Ctrl+C or a forced kill.
- [ ] PostgreSQL session/tab state and window geometry are restored next start.
- [ ] No URL, title, search term, downloaded filename, database password, or
      JDBC password is present in performance lines.

## 7. Cross-platform gate

On every available target OS, follow `docs/CROSS_PLATFORM_CHECKLIST.md` and
record the result. Phase 6 is accepted only after graphical smoke checks pass on
Windows, Linux, and macOS and a 30-minute browsing session shuts down cleanly.

Native platform packaging is intentionally excluded. A platform that was not
actually tested must be recorded as `NOT TESTED`, not assumed to pass.

## Phase 6 acceptance

- [ ] Automated tests and FXML validation match Section 2.
- [ ] Lazy WebViews and lazy sidebar panels match Sections 3 and 4.
- [ ] Repeated lifecycle behavior and resource bounds match Section 5.
- [ ] Startup/shutdown diagnostics and privacy checks match Section 6.
- [ ] Available cross-platform results are recorded honestly.
- [ ] No regression or exception remains in `failure_report.txt`.
