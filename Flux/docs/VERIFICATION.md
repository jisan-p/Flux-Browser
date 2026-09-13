# Verification record

Verified in this workspace on September 12, 2026, on macOS 26.3 / Apple Silicon. PostgreSQL checks used a newly initialized, isolated PostgreSQL 18.3 cluster and its disposable `flux_test` database. The supplied Min checkout was not modified.

## Build and runtime coverage

| Check | Result |
| --- | --- |
| Java 21.0.10 + JavaFX 21.0.12, clean build and core checks | Passed |
| Temurin JDK 26 + JavaFX 26.0.2, browser UI checks | Passed |
| PostgreSQL 18.3 with JDBC 42.7.13 | Passed real storage integration checks |
| `mvn javafx:run` on the default JDK 26 | Launched with the supplied application module descriptor |
| Database unavailable | Browser navigation, tabs, settings, and starter shortcuts remained usable; storage was visibly offline |
| Real HTTPS page | `https://example.org/` rendered with the title **Example Domain** |
| FXML/controller inventory | All 11 FXML files declare an existing dedicated controller and loaded in JavaFX |

The build automatically selects JavaFX 26.0.2 for JDK 24+. JavaFX 21's older JavaScript bridge failed on the local JDK 26 during development; the modern profile resolves that compatibility issue. The application module descriptor also ensures the JavaFX Maven plugin places the modern `jdk.jsobject` dependency on the module path. The normal launcher uses named JavaFX modules; the standalone UI test helper uses the classpath and prints JavaFX's informational configuration warning.

A longer run on the macOS GPU path emitted native JavaFX texture-allocation errors. The final launch and UI-check configuration therefore selects JavaFX's built-in software renderer (`prism.order=sw`). The README documents the optional hardware-rendering override and its CPU/GPU tradeoff.

## Automated checks

`BrowserChecks` runs automatically during `mvn test` / `mvn verify`. It covers blank/Home input, hostnames, HTTP(S) normalization, default ports, localhost and IP addresses, international domain conversion, path spaces, encoded searches, title fallback, input limits, malformed addresses, embedded URL credentials, and unsupported address-bar schemes.

`DatabaseChecks` uses real PostgreSQL connections. It verifies the JDBC worker thread, history insert/search/delete/clear, newest-first ordering, literal search text containing quotes and wildcard characters, bookmark upsert/edit/delete, preserved creation timestamps, folder search, shortcut CRUD, committed state across a new manager, repeat schema initialization, seed deletion without reseeding, and an unavailable database. Clearing history was checked to preserve bookmarks.

`BrowserSmokeChecks` launches a real JavaFX window and a local HTTP fixture server. It verifies navigation by address and in-page link, title updates, Back/Forward/Reload, Home and return, new/select/close tabs, closing the final tab, JavaScript popup routing, bookmark toggling, bookmark editor CRUD, history display/clear confirmation, shortcut editor CRUD and navigation, accent selection, current-tab zoom, stopped loads, failed-load recovery, and returning to a failed page through Home. Cancelled and failed URLs were checked to have no saved visits. A separate offline run verifies browsing without persistence.

The tests use no JUnit/TestFX/Testcontainers framework. Only the UI profile needs a desktop display; the core checks are headless. Integration profiles require explicitly named disposable databases and are not part of a routine `mvn test` run.

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
