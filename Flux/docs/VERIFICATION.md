# Verification record

Originally verified on September 12, 2026; the performance changes below were checked on September 13–14 on this Mac. Hardware inspection confirmed Apple M3, 8 logical CPUs, 8 GiB RAM, and native arm64 JDKs. PostgreSQL checks used an isolated PostgreSQL 18.3 cluster and its disposable `flux_test` database. The supplied Min checkout was not modified.

## Native macOS WebKit replacement (September 16)

The user explicitly approved replacing JavaFX WebView while retaining the JavaFX interface. macOS now defaults to WKWebView; `-Dflux.engine=javafx` selects the compatibility engine. A locally compiled JNI bridge embeds the native view inside the existing NSWindow, dispatches all AppKit work asynchronously, and returns observable state to JavaFX. WebKit owns its helper processes; no page pixels pass through JavaFX WebView. The Java heap remains capped at 1 GiB; native/helper-process memory is additional.

The clean JDK 26 build and `database-check,ui-check` suite passed with PostgreSQL on an isolated port. Checks cover navigation, Back/Forward/Home/Reload/Stop, errors, native popups and their `window.opener`, script-driven popup close, zoom, shell panels, closing the final tab, offline browsing, live HTTPS, and bookmark/history/Speed Dial CRUD. A native Cmd+L event posted within Flux's AppKit event queue reached the JavaFX omnibox. OS-wide Robot injection did not produce reliable input in this environment, so the test does not claim physical keyboard automation. Native page-only screenshots were inspected; JavaFX scene snapshots cover the shell and exclude the native NSView. Full-display capture was unavailable.

The media fixture is a 12-second 1920×1080 H.264 video at 60 fps, served over localhost with byte-range support. Each sample observes ten seconds of playback, independent JavaFX queue wait, page animation callbacks, and available video counters. At Retina 2×, initial native playback reported 615 total frames / 3 dropped frames and 535 video-frame callbacks in 10.08 seconds; JavaFX queue p95 was 0.37 ms. This demonstrates working video counters and responsive shell dispatch, not zero dropped frames or a physical-display FPS guarantee.

Initial live-site observations (individual runs, different cache/site state; not controlled speedup ratios):

| Observation | JavaFX WebView + Metal | Native WKWebView |
| --- | ---: | ---: |
| GitHub observed full navigation | 10.75 s | 1.38 s |
| GitHub observed first DOM readiness | 0.64 s | 0.56 s |
| GitHub FX queue p95 during sample | 327 ms | 0.36 ms |
| YouTube search observed full navigation | 6.13 s | 7.63 s |
| Media Source Extensions | Absent | Present |
| VP9 `canPlayType` | Empty | `probably` |
| `requestVideoFrameCallback` | Absent | Present |

The initial native YouTube search ran while WebKit reported the page hidden, and the sample video (`jNQXAC9IVRw`) was only 320×240. It played, but had one startup stall and advanced 3.67 seconds during the ten-second observation; it is not evidence of instant startup or 1080p60 YouTube playback. Its decoded-frame counter reported 57 total / 0 dropped; the FX queue p95 remained 0.25 ms. Public-site timing depends on network, cache, player state, visibility, advertising, and consent. Site success is reported as an observation, not asserted by the local fixture's pass result.

A second run using the final Flux-specific native profile observed GitHub full navigation in 3.63 s (response start was 3.09 s), search DOM readiness at 2.53 s / full load at 9.01 s, and the YouTube `aqz-KE-bpKQ` document at 2.56 s. YouTube selected **854×480**, not 1080p60; playback advanced 9.45 s in a 10.09 s observation, reported 248 total frames / 0 dropped, and two stalled events. WebKit reported the page hidden during this sample, so its animation rate is not a foreground rendering measurement. The FX queue p95 was 0.63 ms. The local 1080p60 repeat reported 616 total frames / 4 dropped, with FX queue p95 0.44 ms. These results support the engine replacement and responsive shell, while leaving high-quality YouTube startup/playback dependent on further foreground testing and network conditions.

The retained JavaFX compatibility engine passed its offline UI suite. The native UI suite also passed through the normal named-module Maven launcher, including the two required JavaFX exports. Late callbacks are discarded after a page is disposed; the JNI bridge leaves the shared AppKit thread’s JVM attachment under Glass ownership.

Native UI checks also passed on arm64 JDK 21.0.10 / JavaFX 21.0.12, including HTTPS and app-local Cocoa shortcut routing. The native compositor is independent of the JavaFX shell's software pipeline on that runtime.

Reproduce from `Flux` (ffmpeg is only needed to generate the test fixture):

```sh
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc2=size=1920x1080:rate=60 \
  -t 12 -c:v libx264 -preset ultrafast -crf 28 -pix_fmt yuv420p \
  -movflags +faststart /private/tmp/flux-1080p60.mp4
FLUX_EXPECT_STORAGE=false FLUX_DB_URL=jdbc:postgresql://127.0.0.1:1/flux_test \
  FLUX_MEDIA_FILE=/private/tmp/flux-1080p60.mp4 FLUX_CHECK_SITES=true \
  mvn -Pweb-media-check verify
```

To check the normal named-module launcher, use the same disposable/offline database variables with `mvn -Pmodule-ui-check test-compile javafx:run`. This patches test classes into the application module for that run only; the ordinary launcher still starts `FluxBrowser`.

Use `FLUX_YOUTUBE_URL` to choose another public video. Keep the test window visible. Native page snapshots and shell screenshots are separate artifacts under `target/screenshots/`. The JNI library targets the running JDK architecture and is packaged under `native/<os.arch>/`; Xcode Command Line Tools are required to build it. macOS 12 is the deployment target; the executed platform is macOS 26.3 on M3, not every older macOS release.

## Earlier JavaFX WebView measurements (September 13–14)

The browser now creates WebKit only on a tab's first web navigation. Blank tabs use native FXML only. Loaded tabs stay attached to the scene but are hidden and unmanaged when inactive, avoiding scene reconstruction on every switch. Hidden home clocks stop, hidden shortcut queries wait until needed, unchanged FXML tiles/rows are reused, and bursts of page events update the toolbar once per JavaFX pulse.

Database work uses two reader threads and one ordered writer, with 64 queued tasks per executor and idle threads expiring after 30 seconds. At most three database connections run concurrently. Overload returns a failed future, never runs JDBC on the caller/UI thread, and never silently drops a write. A completed write future is the point at which callers request refreshed data. Closing skips queued reads and drains accepted writes without blocking JavaFX.

`PerformanceChecks` serves a local page containing 180 cards and requestAnimationFrame scrolling. It opens 12 blank tabs, closes 11, loads three animated tabs, and measures 120 event-queue probes with 12 tab switches and six resizes. The stage starts at 1280 × 820. Runs were sequential on the native arm64 JDK 26 / JavaFX 26.0.2. The original implementation was checked in an isolated copy of commit `d101e5f`; the fixture workload and combined input metric stayed the same as timing diagnostics were expanded.

| Observation | Original software renderer | Updated software renderer |
| --- | ---: | ---: |
| Startup to shown window | 1705.6 ms | 919.3 ms |
| Median new-tab command | 30.9 ms | 19.5 ms |
| 95th-percentile new-tab command | 365.3 ms | 76.9 ms |
| WebViews allocated for 12 blank tabs | 12 | 0 |
| 95th-percentile input probe | 19.3 ms | 11.4 ms |
| 95th-percentile JavaFX pulse interval | 22.6 ms | 19.2 ms |
| Maximum JavaFX pulse interval | 102.0 ms | 111.1 ms |
| Maximum Java heap | 2048 MiB | 1024 MiB |

These are individual local runs, not a statistically established speedup or a guarantee for arbitrary websites. Startup includes class loading; the tail of 11 new-tab samples is especially sensitive to warmup. An input probe measures queue wait plus synchronous command execution, not the time until pixels are displayed. Pulse intervals are also not compositor FPS. Occasional long pulses remain.

The same updated code was also tested with both available GPU pipelines; verbose Prism output confirmed actual `ES2Pipeline` and `MTLPipeline` initialization:

| Renderer | Input probe p95 | Pulse interval p95 | Maximum pulse interval |
| --- | ---: | ---: | ---: |
| Software (`sw`), then-selected default | 11.4 ms | 19.2 ms | 111.1 ms |
| OpenGL (`es2,sw`) | 128.2 ms | 17.1 ms | 283.3 ms |
| Metal (`mtl,es2,sw`) | 201.9 ms | 26.5 ms | 345.8 ms |

Both GPU runs completed without uncaught rendering exceptions, but queued input stalled more on this workload. Software was selected at that point. Subsequent real-media tests exposed poor playback and GPU texture failures; the September 16 native replacement below supersedes that decision. The 64 MiB initial / 1024 MiB maximum heap leaves room for other allocations; **native WebKit, thread stacks, and graphics memory are additional**. Loaded background tabs retain documents and scripts to preserve forms and navigation. There is no automatic page eviction or total application memory ceiling.

JavaFX already uses background loading and rendering threads. WebEngine/DOM/JavaScript calls still obey its [JavaFX Application Thread requirement](https://openjfx.io/javadoc/26/javafx.web/javafx/scene/web/WebEngine.html). Those earlier changes did not add renderer subprocesses. The September 16 macOS replacement now uses native WKWebView and its system-managed helper processes.

Reproduce the local workload from `Flux` (no live database is needed):

```sh
FLUX_EXPECT_STORAGE=false FLUX_DB_URL=jdbc:postgresql://127.0.0.1:1/flux_test \
  mvn -Pperformance-check verify
```

For renderer comparisons add `-Dflux.rendering=es2,sw`, or `-Dflux.rendering=mtl,es2,sw` on macOS with the modern JavaFX profile. The profile checks that blank tabs allocate no WebViews, animation/pulses continue, and no uncaught rendering exceptions occur; measured latency values are reported without a hardware-dependent pass threshold.

## Build and runtime coverage

| Check | Result |
| --- | --- |
| Java 21.0.10 + JavaFX 21.0.12, clean build and core checks | Passed |
| Temurin JDK 26 + JavaFX 26.0.2, browser UI checks | Passed |
| PostgreSQL 18.3 with JDBC 42.7.13 | Passed real storage integration checks |
| `mvn javafx:run` on the default JDK 26 | Launched with the supplied application module descriptor |
| Database unavailable | Browser navigation, tabs, settings, and starter shortcuts remained usable; storage was visibly offline |
| Real HTTPS page | `https://example.org/` rendered with the title **Example Domain** |
| FXML/controller inventory | All original 12 FXML files declare an existing dedicated controller and loaded in JavaFX |

The build automatically selects JavaFX 26.0.2 for JDK 24+. JavaFX 21's older JavaScript bridge failed on the local JDK 26 during development; the modern profile resolves that compatibility issue. The application module descriptor also ensures the JavaFX Maven plugin places the modern `jdk.jsobject` dependency on the module path. The normal launcher uses named JavaFX modules; the standalone UI test helper uses the classpath and prints JavaFX's informational configuration warning.

Earlier development runs emitted texture-allocation errors on the JavaFX WebView GPU path. September 16 YouTube tests reproduced `RTTexture.createGraphics()` null-pointer errors with Metal. Native WKWebView bypasses that rendering path entirely.

## Automated checks

`BrowserChecks` runs automatically during `mvn test` / `mvn verify`. It covers blank/Home input, hostnames, HTTP(S) normalization, default ports, localhost and IP addresses, international domain conversion, path spaces, encoded searches, title fallback, input limits, malformed addresses, embedded URL credentials, and unsupported address-bar schemes.

`DatabaseChecks` uses real PostgreSQL connections. It verifies background readers/writers, two concurrent reads, read/write independence, queue limits, initialization ordering, isolation from failures in an earlier connection generation, close/rejection behavior, ordered write drain, history insert/search/delete/clear, newest-first ordering, literal search text containing quotes and wildcard characters, bookmark upsert/edit/delete, preserved creation timestamps, folder search, shortcut CRUD, committed state across a new manager, repeat schema initialization, seed deletion without reseeding, and an unavailable database. Clearing history was checked to preserve bookmarks.

`BrowserSmokeChecks` launches a real JavaFX window and a local HTTP fixture server. It verifies navigation by address and in-page link, title updates, Back/Forward/Reload, Home and return, new/select/close tabs, closing the final tab, JavaScript popup routing, bookmark toggling, bookmark editor CRUD, history display/clear confirmation, shortcut editor CRUD and navigation, accent selection, current-tab zoom, stopped loads, failed-load recovery, and returning to a failed page through Home. Cancelled and failed URLs were checked to have no saved visits. A separate offline run verifies browsing without persistence.

The September 14 clean `database-check,ui-check` verification passed on JDK 26, including live HTTPS. A separate clean Java 21 `ui-check` run passed with storage unavailable and live HTTPS enabled. UI checks also verify that fresh Home tabs allocate no WebView and that an unchanged Speed Dial refresh preserves tile identity.

The tests use no JUnit/TestFX/Testcontainers framework. UI/performance profiles need a desktop display; core checks are headless. Database/UI integration profiles require explicitly named disposable databases and are not part of a routine `mvn test` run.

## Visual review

App-only screenshots were generated from the rendered JavaFX scene and inspected. The review covered:

* Speed Dial at 1280 × 820: complete hero, search, six shortcuts, and footer.
* Bookmarks and history: row alignment, readable titles/URLs, metadata, and visible actions.
* Cyan settings: accent controls, slider, storage card, and consistent theme.
* Compact 940 × 650 window: tile wrapping and vertical scrolling with the shell intact.
* Failed-load screen: attempted address, recovery text, Retry and Home actions.
* Live HTTPS content within the browser shell.

The retained [Speed Dial screenshot](images/flux-speed-dial.png) is included in the README. Full UI runs write screenshots to `target/screenshots/`; a clean build removes generated screenshots until that profile runs again.

## Scope of these results

Windows and Linux were not executed in this environment. Maven's platform-native dependency selection is configured, but those platforms still need their own desktop smoke test. Scene Builder's GUI was not automated; the FXML itself was loaded by JavaFX, and the guide provides the live-edit workflow. Site compatibility remains dependent on WebKit's supported web APIs and media formats. This is a functional desktop MVP, not a substitute for a full Chromium browser.

To reproduce the checks, use the commands and disposable database instructions in [README.md](../README.md). The five-minute demonstration is in [PRESENTATION.md](PRESENTATION.md), and every UI/controller binding is documented in [UI_SCENEBUILDER_GUIDE.md](UI_SCENEBUILDER_GUIDE.md).
