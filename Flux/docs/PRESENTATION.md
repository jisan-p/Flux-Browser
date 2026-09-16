# Five-minute Flux demonstration

## Before the audience arrives

1. Follow the database setup in [README.md](../README.md). Run `mvn clean verify` once so dependencies are downloaded.
2. Launch `mvn javafx:run` from `Flux` using a native arm64 JDK on an M3 Mac. Confirm the footer says **STORAGE CONNECTED**. Keep the window around 1280 × 820 for a clear layout. The launcher defaults to a 1 GiB Java heap; native WebKit memory is additional, so close unused heavy tabs before the demo.
3. Try `https://example.org/` and `https://openjfx.io/` on the presentation network. Keep a familiar, simple website ready; complex sign-in and streaming sites are poor live-demo dependencies.
4. Open `src/main/resources/com/flux/browser/view/SpeedDial.fxml` in Scene Builder and `SpeedDialController.java` in the IDE. Open `BrowserWindow.fxml` in a second Scene Builder tab/window if available.
5. Keep a terminal in `Flux`, plus `database/schema.sql` and `docs/ARCHITECTURE.md` in the editor. Save a bookmark named **Demo reference** in the **Presentation** folder.

## Timed walkthrough

| Time | Action | Explain |
| --- | --- | --- |
| 0:00–0:35 | Show the Speed Dial, sidebar, and architecture diagram. | “Flux extracts Min's essential browsing loop: an address becomes a page, page events update a tab, and saved data goes to storage asynchronously. The interface is FXML, the macOS engine is native WKWebView, and persistence is PostgreSQL/JDBC.” |
| 0:35–1:20 | Press Ctrl/Cmd+L, enter `example.org`, and press Enter. Open a new tab, search `JavaFX WebView`, and switch between tabs. Use Back, Forward, Reload, and Home. | “A hostname becomes HTTPS; ordinary text becomes an encoded search. Blank tabs allocate no WebView. First navigation creates the tab's native browser page and history. Tab switches retain the page, and Back from native Home returns to it.” |
| 1:20–2:05 | Click the bookmark star on a website. Open Bookmarks, filter the title, edit its folder to **Presentation**, and reopen it. Show History and its newest-first timestamps. | “Prepared JDBC operations use two readers and one ordered writer, with bounded queues. Database I/O stays off JavaFX. The library refreshes after a save completes, and successful page loads add visits.” |
| 2:05–2:40 | Return Home, add a Speed Dial site, edit its label, and open it. Briefly show the three tables in `schema.sql`. | “Bookmarks and shortcuts have unique addresses and survive restart. History is a visit log. The brief's quick dial and speed dial are one table here.” |
| 2:40–4:10 | In Scene Builder, edit the static subtitle in `SpeedDial.fxml` from **A fresh tab. An open world. Make it yours.** to **Built live. Ready to explore.** Change the main VBox spacing slightly. Save. Close Flux normally and rerun `mvn javafx:run`. | “This text and layout belong to FXML, so no Java source changes are needed. Maven copies the modified resource when we relaunch. The database bookmark and shortcut remain after the restart.” |
| 4:10–4:40 | Open Settings, choose Cyan, and change the page zoom on an open website. Return Home. | “CSS looked-up colors theme the shell, tab chips, and home components. The slider changes the current page's zoom.” |
| 4:40–5:00 | Show `WebTabController.loadState` and `HistoryDAO.saveVisit` briefly. | “The controller listens to BrowserPage, snapshots the final title/address, and submits a database operation. The result returns to JavaFX through `Platform.runLater`. The app keeps the browsing core deliberately small.” |

## Scene Builder edit details

Use the static hero subtitle for the live edit: the controller never replaces its text. `clockLabel`, `dialCount`, tile titles, active-tab titles, and connection labels are data-driven; their preview values are replaced at runtime. Keep every `fx:id`, `fx:controller`, and `onAction` unchanged during cosmetic edits.

Scene Builder's preview is a layout preview, not the running browser. Live FXML changes are not automatically hot-reloaded into an existing scene. Save the source FXML, stop the app, and relaunch with Maven. Do not edit `target/classes`, which is overwritten during the build. See the full [UI and controller guide](UI_SCENEBUILDER_GUIDE.md) for every component.

## Optional SQL evidence

Run against your demo database in psql:

```sql
SELECT title, url, visit_timestamp FROM history ORDER BY visit_timestamp DESC, id DESC LIMIT 5;
SELECT title, url, category, created_at FROM bookmarks ORDER BY created_at DESC;
SELECT title, url, position FROM speed_dial ORDER BY position, id;
```

These read-only queries make the persistent data visible without exposing credentials. Closing/reopening Flux is the simplest persistence demonstration.

## Recovery during a demo

* If the internet fails, use Home, tabs, library search, theme controls, and Scene Builder. Saved library data still works if your local PostgreSQL server is running.
* If storage is offline, check the Settings connection status, start PostgreSQL, verify the local configuration, and use **Reconnect storage**. Do not say an offline bookmark action was saved.
* If a website does not render correctly, use a previously checked simple site. Explain that WebKit does not provide every feature of a full Chromium browser.
* If an FXML edit fails to load, restore the changed property in Scene Builder, save, and relaunch. Keep the edit to static text, spacing, padding, or colors.

## Questions to be ready for

**Why PostgreSQL instead of Min's IndexedDB?** The project requires JDBC/PostgreSQL. Separate history, bookmark, and shortcut tables keep the model clear and demonstrate prepared statements and asynchronous work.

**How does the application use multiple threads?** Two workers read saved data while one worker keeps writes ordered. Each executor queues at most 64 waiting tasks, and idle workers retire after 30 seconds. Each operation owns its JDBC connection. The UI reuses unchanged tiles and rows, pauses hidden Home clocks, and combines page event bursts into one toolbar update per display pulse. These changes reduce application work; they do not guarantee that every website runs smoothly.

**Why no connection pool?** The small, bounded worker count already limits simultaneous connections. Each operation closes its connection instead of sharing it between threads. A pool would be justified by measured connection overhead, not by the number of CPU cores alone.

**Does the app use multiple renderer processes?** On macOS, yes: native WKWebView uses system-managed WebKit helper processes. JavaFX still owns the shell, while JNI messages cross asynchronously to AppKit. WebKit chooses the process allocation. New Home tabs allocate no web content; closing a loaded tab releases its native view. The non-macOS JavaFX compatibility engine remains in-process.

**How can we measure the improvement?** The engine-neutral `web-media-check` profile measures local H.264 playback and optional GitHub/YouTube visits, including available decoder/frame-callback counters. The older `performance-check` explicitly selects JavaFX WebView for historical comparison. Read the conditions and limitations in `VERIFICATION.md`.

**Which renderer is used?** Modern macOS uses Metal for the JavaFX shell. WKWebView renders pages and video through the system compositor independently. A faster shell pulse does not prove a video's displayed frame rate.

**What survives restart?** Bookmarks, visit history, and Speed Dial. Open tabs, accent selection, and page zoom belong to the current session.

**What was omitted from Min?** Full-text indexing, filtering lists, task workspaces, password-manager integrations, downloads, reader/translation/PDF subsystems, and session restore. The complete matrix is in `ARCHITECTURE.md`.
