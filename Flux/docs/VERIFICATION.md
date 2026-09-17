# Verification record

Originally verified on September 12, 2026; the performance changes below were checked on September 13–14 on this Mac. Hardware inspection confirmed Apple M3, 8 logical CPUs, 8 GiB RAM, and native arm64 JDKs. PostgreSQL checks used an isolated PostgreSQL 18.3 cluster and its disposable `flux_test` database. The supplied Min checkout was not modified.

## GX-style appearance (September 17)

The installed Opera GX Speed Dial and Easy Setup were inspected using macOS accessibility and window captures after permission was granted. Flux screenshots were then inspected at normal and compact sizes. The supported customization controls, defaults, and explicit differences from Opera GX are listed in [CUSTOMIZATION.md](CUSTOMIZATION.md).

- Appearance validation checks passed for hostile CSS/font values, remote asset references, numeric limits, old-session migration and named-preset persistence.
- `AppearanceUiChecks` passed: actual FXML controls, light/dark changes, named presets, a 940 × 650 drawer, native WKWebView reflow beside Easy Setup, restored viewport width, and appearance persistence across relaunch with tab restoration disabled.
- The native offline `BrowserSmokeChecks` suite passed with the redesigned shell. The final-tab check now expects an empty Home address field, matching the intentional UI change; the internal Home location remains unchanged.
- `DeveloperToolsChecks` passed with real Web Inspector panels and Console evaluation after the redesign.
- `FeatureUiChecks` passed: lazy session restore, workspaces/focus, arithmetic, reader, translation destination, native domain blocking, download bytes, PDFKit and switching between PDFs and web pages.
- The final `mvn -q clean verify` passed, including compilation of the native bridge and all headless checks.

The default Home and Easy Setup screenshots are retained in `docs/images/flux-gx-*.png`. No user database or browser session was used for automated UI fixtures. The external Opera reference window was used only for visual inspection. This does not establish full Opera GX feature or pixel parity, live-site speed improvements, or audible-output testing.

## Version 1 macOS scope and Developer Tools (September 17)

Version 1 targets macOS with native WKWebView. Windows/Linux code, FXML, dependencies and compatibility diagnostics remain in the repository for a future version; the normal v1 application entry point requires native macOS WebKit. Earlier compatibility-engine results below are historical, not v1 platform support claims.

Validation on this Mac:

- `mvn -q clean verify` passed, including the native library build, feature checks and browser validation checks.
- `DeveloperToolsChecks` passed through the named-module launcher. A local HTTP fixture opened Apple's actual Web Inspector frontend with Elements, Network and Console. The inspector Console evaluated `window.fixtureValue + 1` in the inspected page and returned 43. The Tools button, app-local native Option+Cmd+I event, FXML F12 event, toggle, separate tab ownership and disposal checks passed.
- The native offline `BrowserSmokeChecks` suite passed through the v1 application entry point: FXML, navigation, tabs, popups, stop/errors, settings and browsing with unavailable storage.

These checks did not exercise physical right-click input, every inspector panel, or multiple macOS/WebKit versions. Direct inspector controls use guarded WebKit private selectors; remote inspectability uses the public macOS 13.3+ API. Compatibility/Eruda work is preserved but not part of this release's validation.

Run the inspector check from `Flux` without a database or external website:

```sh
mvn -q -Pmodule-ui-check -Dflux.mainClass=com.flux.browser/com.flux.browser.DeveloperToolsChecks test-compile javafx:run
```

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

## Foreground media follow-up

The strict foreground run passed on September 16 at 17:54 (machine-reported time), JDK 26 / JavaFX 26.0.2, native WKWebView, Retina 2×. Both local and YouTube samples reported `hiddenMs=0` and only the initial visible event.

| Observation | Local 1080p60 fixture | YouTube `aqz-KE-bpKQ`, requested hd1080 |
| --- | ---: | ---: |
| Decoded dimensions at sample end | 1920×1080 | 1920×1080 |
| Sample duration | 10.060 s | 10.056 s |
| Media time advanced | 10.058 s | 9.690 s |
| Video-frame callback count | 327 | 577 |
| Playback-quality total / dropped delta | 607 / 4 | 534 / 0 |
| Stalled / waiting events during sample | 0 / 0 | 2 / 0 |
| FX queue wait p95 / maximum | 0.40 / 3.23 ms | 0.17 / 4.66 ms |

The YouTube document completed navigation in 3.687 s; requested 1080p playback readiness was observed another 1.090 s later. Its frame callback rate was approximately 57 callbacks/second over the sample. Video-frame callbacks, playback-quality counters, media time, and physical presentation are different measurements; their counts are not interchangeable. This supports working foreground high-resolution playback and responsive shell input, not a universal 60 FPS or stall-free guarantee. The ten-second observation is not a long-duration stress test.


`WebMediaChecks` can explicitly bring Flux forward with `FLUX_CHECK_FOREGROUND=true`. It records every page visibility transition and the total hidden time, and fails a foreground sample if the document becomes hidden. This prevents a backgrounded test from being reported as a foreground playback result. Activation is a guarded test hook; normal browsing never uses it to steal focus.

The YouTube probe waits separately for playback readiness after document navigation. Set `FLUX_YOUTUBE_QUALITY=hd1080` (or `hd720`) to request a quality through available player capabilities. The check verifies actual decoded video dimensions before sampling and fails if that resolution is not reached within 30 seconds. It does not assume that a quality request was honored. These player methods are site-specific diagnostic hooks, not production browsing behavior. `FLUX_YOUTUBE_ONLY=true` skips the GitHub/search observations for targeted playback checks.

```sh
FLUX_EXPECT_STORAGE=false FLUX_DB_URL=jdbc:postgresql://127.0.0.1:1/flux_test \
  FLUX_MEDIA_FILE=/private/tmp/flux-1080p60.mp4 FLUX_CHECK_SITES=true \
  FLUX_YOUTUBE_ONLY=true FLUX_CHECK_FOREGROUND=true FLUX_YOUTUBE_QUALITY=hd1080 \
  FLUX_YOUTUBE_URL='https://www.youtube.com/watch?v=aqz-KE-bpKQ' \
  mvn -Pweb-media-check verify
```

The launcher retains the recently added `javafx.animation.fullspeed` and `prism.showfps` switches as opt-in Maven properties (`flux.fullspeed`, `flux.showFps`, both false by default). Installed JavaFX 26.0.2 bytecode and [OpenJFX PrismSettings](https://github.com/openjdk/jfx/blob/master/modules/javafx.graphics/src/main/java/com/sun/prism/impl/PrismSettings.java) confirm that fullspeed disables Prism VSync. It affects the JavaFX shell, not WKWebView's compositor; it is not a suitable default video-performance fix.

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

Windows and Linux are deferred beyond v1. Their compatibility implementation and Maven dependencies are preserved, but those platforms still need their own release work and desktop tests. Scene Builder's GUI was not automated; the FXML itself was loaded by JavaFX, and the guide provides the live-edit workflow. Site compatibility remains dependent on WebKit's supported web APIs and media formats. This is a functional desktop MVP, not a substitute for a full Chromium browser.

To reproduce the checks, use the commands and disposable database instructions in [README.md](../README.md). The five-minute demonstration is in [PRESENTATION.md](PRESENTATION.md), and every UI/controller binding is documented in [UI_SCENEBUILDER_GUIDE.md](UI_SCENEBUILDER_GUIDE.md).

## Expanded browser tools — September 16, 2026

The omitted-feature implementation is documented in [FEATURES.md](FEATURES.md). Checks use generated local HTTP/PDF fixtures and isolated session profiles; normal user tabs, history and credentials are not test fixtures.

| Check | Observed result |
| --- | --- |
| Core `mvn test` | Session save/load and malformed-state validation, plugin URL encoding, arithmetic, HTTPS origin validation, domain boundaries and Readability resource loading passed. |
| Existing native UI suite | Navigation, popup handling, shortcuts, tab lifecycle, stop/error recovery, settings and offline browsing passed with sessions disabled for isolation. |
| Normal named-module launcher | The new feature suite passed through `module-ui-check` with `flux.mainClass` set to `com.flux.browser/com.flux.browser.FeatureUiChecks`, including Gson reflection, FXML and JNI exports. |
| New `feature-ui-check` | Lazy restore and restart, workspace move/focus behavior, arithmetic, reader extraction, translation new-tab URL, actual WebKit blocking, downloaded byte equality, replacing a fixture file, PDFKit rendering and return to web browsing passed. |
| macOS Keychain | Save/read/update/delete passed with a uniquely named disposable login under `https://flux-feature-test.invalid`; the test entry was removed and absence verified. Existing logins were not queried. |
| Live filter update | AdGuard DNS Filter downloaded successfully; 30,000 supported domain rules imported into a temporary profile. |
| PostgreSQL `database-check` | New content-only term search, replacing indexed text and purging text when deleting visits passed, alongside existing JDBC concurrency/CRUD checks. |
| PDF visual review | Corrected initial scroll position after native viewport layout. An OS screenshot confirmed first-page text is visible. `snapshot` uses PDFKit's page raster API for PDFs, because NSView cache capture omits PDFKit's compositor tiles. |
| Compact UI | Reviewed Browser tools at 940×650, with privacy controls accessible through scrolling. |

The local foreground 1920×1080 H.264/60 fixture was rerun with the expanded feature build. In a 10.112-second instrumented sample, the page reported **zero waiting/stalled events**, 529 video-frame callbacks and 3 dropped frames in the playback-quality counter. JavaFX queue latency was **0.49 ms p95**; pulse interval was **17.70 ms p95**. These are distinct diagnostic counters, not a physical-screen FPS guarantee. This regression run did not retest public GitHub/YouTube; the earlier public-site measurements above remain separate evidence.

Run the deterministic feature checks through `mvn test`. For desktop fixtures:

```sh
mvn -Pfeature-ui-check verify
```

Optional feature checks are explicit:

```sh
FLUX_CHECK_FILTERS=true mvn -Pfeature-ui-check verify
FLUX_CHECK_KEYCHAIN=true mvn -Pfeature-ui-check verify
FLUX_CHECK_PDF_SCREEN=true mvn -Pfeature-ui-check verify
```

The Keychain option creates and removes one test login. The screen option brings only Flux forward and captures its test window; macOS screen-capture permissions may apply. PDF page rasters and JavaFX scene captures do not need that optional screen check. Ordinary feature runs do not inspect external vaults.

The Bitwarden/1Password command adapters have not been verified against signed-in accounts. Live phishing-warning responses, particular known-host HTTPS upgrade decisions and translated third-party page content were not asserted; the tests verify the configured controls and translation destination. Downloads have no resumable state after app exit. See the feature guide for the implemented limits.

### GX shell polish — 17 September 2026

`mvn -q verify` passed. Native `BrowserSmokeChecks` passed with unreachable test storage (no user database changes). `AppearanceUiChecks` exercises tab duplicate/close-right/close-others/reopen, Focus mode menu restrictions, starter shortcut disabled actions, Settings search/empty results, direct theme and mode selection, compact layout, Home customization, native viewport reflow, preset persistence and relaunch. Screenshots are written to `target/screenshots/gx-*.png`.

The Settings view was inspected at 1400×900 and 940×650. Theme previews wrap and the card column scrolls vertically at the smaller size. The top accent follows the selected tab; shell menus use the current palette. Native webpage context menus remain supplied by macOS WebKit.

### Page actions and workspace access — 17 September 2026

`FeatureUiChecks` now opens the actual AppKit context menu on local pages. It checks native Copy/Inspect Element retention, selected-text search in a new tab, disabled search without a selection, Reader extraction, translation destination, and Save Page document bytes. It also verifies the isolated selection handler is inaccessible from page scripts and the Workspaces button sits below the window controls. The fixture uses a disposable profile and test download destinations. `FeatureChecks` checks plain-text search semantics and URL encoding.

Webpage actions use AppKit's public menu hook and existing WebKit downloads; no private context-menu delegate or polling timer is introduced. Translation and search open new tabs subject to Focus mode and tab limits. The page identity and URL are checked before applying a menu action. The JavaFX fallback Reader/Translate controls are retained, hidden in the native macOS build.

The native offline `BrowserSmokeChecks` also passed, covering navigation, popups, tab lifecycle, native keyboard shortcuts, errors and Settings. The test keyboard hook explicitly foregrounds Flux before posting its app-local event.
