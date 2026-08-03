# Phase 4 Review Guide

Phase 4 adds asynchronous downloads, privacy/data controls, per-site popup policy, and coordinated shutdown. PostgreSQL schema version remains `3`; Phase 4 uses the downloads and settings structures already created in Phase 3.

## Delivered behavior

- Conservative detection of HTTP(S) navigations ending in common archive, installer, document, package, and disk-image extensions.
- JavaFX save chooser with a remembered last directory.
- Cross-platform filename sanitization, reserved-name protection, a 180-character bound, and collision-safe `name (n).ext` destinations.
- Streaming Java HTTP client transfers on a bounded background executor.
- Redirect handling, connection/request timeouts, HTTP status validation, temporary `.part` files, and atomic completion moves where supported.
- Live active-download list with byte progress and Cancel, Retry, Open, and Reveal controls.
- Persistent PostgreSQL download state: queued/running/completed/cancelled/failed, byte counts, destination, completion time, and failure message.
- Cancellation and failure remove the Flux-created partial file but never delete an existing completed file.
- Metadata deletion never deletes downloaded files.
- Per-site JavaScript popup policy: Open once, Always allow, or Block. Persistent decisions can be reset in Settings.
- Confirmed external-protocol handling remains in place for `mailto:`, `tel:`, and `magnet:`.
- Browsing-data controls for history, download metadata, current-process cookies, favicon cache, and saved sessions/recently closed tabs.
- Saved-session clearing suppresses another session write during that process shutdown, so cleared tabs do not immediately reappear.
- Graceful shutdown cancels queued/running downloads, closes partial streams, disposes WebViews, drains persistence operations, closes the Hikari pool, and stops executors.

## Honest JavaFX limitations

- JavaFX WebView has no supported general download callback and does not expose response `Content-Disposition` headers. Flux detects known filename extensions. A generated download URL without a recognizable extension may remain unsupported.
- JavaFX has no supported public API to clear WebView’s internal HTTP cache. Flux does not use reflective access or claim that other clear-data actions clear that cache.
- Cookie clearing affects cookies held by the current Flux process. Flux does not persist cookies in PostgreSQL.
- Reveal opens the containing directory. Selecting the exact file is platform-dependent.
- Open and Reveal require desktop integration supplied by the operating system.

## 1. Prerequisites

From the Flux project:

```bash
cd /home/mark/Projects/Java/Flux-Browser
docker compose up -d postgres
docker compose ps
```

Expected: the `postgres` service is `healthy`.

Phase 4 adds no schema file and does not require deleting the Phase 3 volume.

## 2. Automated verification

Run:

```bash
mvn clean verify
```

Expected in a graphical desktop session:

```text
FaviconServiceTest:                    Tests run: 5, Failures: 0, Errors: 0
DownloadDetectorTest:                  Tests run: 4, Failures: 0, Errors: 0
NavigationResolverTest:               Tests run: 10, Failures: 0, Errors: 0
DatabaseConfigTest:                    Tests run: 4, Failures: 0, Errors: 0
PostgresPersistenceIntegrationTest:    Tests run: 1, Skipped: 1
SqlInitializationContractTest:         Tests run: 2, Failures: 0, Errors: 0
BrowsingDataServiceTest:               Tests run: 1, Failures: 0, Errors: 0
BrowserUiStateTest:                    Tests run: 8, Failures: 0, Errors: 0
WindowResizeSupportTest:               Tests run: 3, Failures: 0, Errors: 0
BrowserUiSmokeTest:                    Tests run: 3, Failures: 0, Errors: 0
Total:                                 Tests run: 41, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

On headless Linux:

```text
Tests run: 38, Failures: 0, Errors: 0, Skipped: 1
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

The integration test now round-trips completed-download metadata in addition to Phase 3 data. If Docker socket access is denied, run it from a terminal where your user already has Docker access. Do not use `sudo mvn`.

Validate Compose:

```bash
docker compose config --quiet
```

Expected: exit status `0` and no output.

## 3. Start the local Phase 4 server

In a separate terminal:

```bash
mkdir -p /tmp/flux-phase4-server
mkdir -p /tmp/flux-phase4-downloads
cp src/test/resources/manual/phase2-browser-test.html \
  /tmp/flux-phase4-server/index.html
cp src/test/resources/manual/phase4-download.zip \
  /tmp/flux-phase4-server/phase4-download.zip
python3 -m http.server 8765 \
  --bind 127.0.0.1 \
  --directory /tmp/flux-phase4-server
```

Expected:

```text
Serving HTTP on 127.0.0.1 port 8765 ...
```

Keep this terminal running during the download, popup, and cookie checks.

## 4. Start Flux

In the Flux project terminal:

```bash
mvn javafx:run
```

Open:

```text
http://127.0.0.1:8765/
```

Expected: the `Flux Phase 2 Test` fixture loads. It now also contains a download link and cookie controls for Phase 4.

## 5. Successful download and collision handling

Click `Download Phase 4 fixture`.

Expected:

- [ ] WebView navigation is cancelled and a native `Save download` chooser opens.
- [ ] Suggested filename is `phase4-download.zip`.
- [ ] Cancelling the chooser creates no file and no download row.

Click it again and choose:

```text
/tmp/flux-phase4-downloads/phase4-download.zip
```

Expected:

- [ ] Downloads opens automatically.
- [ ] Active Transfers shows Queued/Running and then Completed.
- [ ] Progress shows byte activity without freezing navigation, tabs, or window controls.
- [ ] No `.part` file remains after completion.
- [ ] Saved Metadata contains a Completed row.

Verify the fixture:

```bash
wc -c /tmp/flux-phase4-downloads/phase4-download.zip
```

Expected:

```text
187 /tmp/flux-phase4-downloads/phase4-download.zip
```

Download to the same selected filename again.

Expected: Flux preserves the first file and writes `phase4-download (1).zip`.

```bash
ls -l /tmp/flux-phase4-downloads
```

Expected: both completed files exist and neither has a `.part` suffix.

## 6. HTTP failure and Retry

Enter:

```text
http://127.0.0.1:8765/missing.zip
```

Choose:

```text
/tmp/flux-phase4-downloads/missing.zip
```

Expected:

- [ ] The task becomes Failed.
- [ ] Its message contains `HTTP 404`.
- [ ] `/tmp/flux-phase4-downloads/missing.zip.part` does not remain.

Make the missing server file available:

```bash
cp src/test/resources/manual/phase4-download.zip \
  /tmp/flux-phase4-server/missing.zip
```

Select the failed task and click Retry.

Expected: it returns to Queued/Running, reaches Completed, and writes `missing.zip` without creating another metadata row.

## 7. Cancellation and shutdown cleanup

Create a sparse large test response in the server directory:

```bash
truncate -s 1073741824 /tmp/flux-phase4-server/large.iso
```

Enter:

```text
http://127.0.0.1:8765/large.iso
```

Save it as:

```text
/tmp/flux-phase4-downloads/large.iso
```

Immediately select it and click Cancel.

Expected:

- [ ] The UI remains responsive.
- [ ] Status becomes Cancelled.
- [ ] `large.iso.part` is removed.
- [ ] A pre-existing completed file would not be overwritten or deleted.

Verify partial cleanup:

```bash
test ! -e /tmp/flux-phase4-downloads/large.iso.part
```

Expected: exit status `0` and no output.

Repeat the large download, then close Flux while it is Running.

Expected:

- [ ] Flux closes without hanging indefinitely.
- [ ] The task metadata ends as Cancelled.
- [ ] The `.part` file is removed.
- [ ] No Flux Java process remains after shutdown.

## 8. Open, Reveal, and metadata safety

- [ ] Select a Completed task and click Open; the OS handles the file type.
- [ ] Click Reveal; the containing folder opens.
- [ ] Open/Reveal on a non-completed task does nothing destructive.
- [ ] Delete Selected removes only its PostgreSQL metadata row.
- [ ] Clear All requires confirmation.
- [ ] Clear All refuses while a task is Queued or Running.
- [ ] Clearing metadata never removes files from `/tmp/flux-phase4-downloads`.

Database verification:

```bash
docker compose exec -T postgres psql \
  -U postgres \
  -d flux_browser \
  -c "SELECT file_name, status, bytes_downloaded, total_bytes, target_path, failure_message FROM flux_browser.downloads ORDER BY started_at DESC;"
```

Expected: rows match the statuses, sizes, destinations, and failures shown in Downloads.

## 9. Per-site popup policy

On the local fixture, click `Open popup tab`.

Expected: Flux asks whether `127.0.0.1` may open a popup.

1. Select Open once.
2. Click the fixture button again.

Expected: a new prompt appears because Open once is not persisted.

3. Select Block.
4. Click again.

Expected: no tab opens and no new prompt appears.

5. Open Settings and click Reset Popup Permissions.
6. Click the fixture popup again and select Always allow.
7. Click it one more time.

Expected: the final popup opens in a selected Flux tab without another prompt.

Restart Flux and repeat from the same site.

Expected: Always allow remains effective because its per-site setting is persisted.

## 10. Cookies and browsing-data controls

On the local fixture:

1. Click Set test cookie.
2. Click Show cookies.

Expected: the page displays `flux_phase4=present`.

Open Settings and click Clear Cookies, confirm, then return to the page and click Show cookies.

Expected:

- [ ] Settings reports how many cookies were cleared.
- [ ] The fixture displays `(empty)`.
- [ ] No cookie value is written to PostgreSQL.

Other controls:

- [ ] Clear Favicon Cache immediately reports completion; current tab image references may remain until reload.
- [ ] Clear History confirms and empties persistent visits.
- [ ] Clear Download Metadata confirms and leaves downloaded files intact.
- [ ] Clear Saved Session confirms and reports completion.
- [ ] Settings explicitly states that JavaFX WebView internal cache clearing is unsupported.

Saved-session privacy check:

1. Keep several tabs open.
2. Click Clear Saved Session.
3. Close Flux normally.
4. Start Flux again.

Expected: Flux starts with one new Start Page tab rather than restoring the cleared session.

## 11. Mid-session service failure

While Flux is open, stop the local HTTP server during a large transfer.

Expected: the transfer becomes Failed, the UI remains usable, and Retry succeeds after restarting the local server.

For PostgreSQL recovery, close Flux, stop PostgreSQL, and run Flux:

```bash
docker compose stop postgres
mvn javafx:run
```

Expected: the mandatory PostgreSQL startup dialog appears and the main window does not open. Restore it with:

```bash
docker compose start postgres
docker compose ps
```

Expected: PostgreSQL returns to healthy and Flux starts normally with no credentials printed in the error dialog.

## Failure reporting

If a command or checklist item fails, replace `failure_report.txt` with:

1. The exact command or checklist item.
2. Complete console output or observed UI behavior.
3. The expected behavior from this guide.
4. `java -version`, `mvn -version`, and `docker version`.
5. `docker compose ps` and relevant PostgreSQL logs for persistence failures.
6. Download source URL, chosen destination, status, and whether a `.part` file remained.
7. Operating system and desktop session type.
