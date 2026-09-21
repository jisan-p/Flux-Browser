# Flux UI and Scene Builder field guide

This guide covers all 16 FXML views and their dedicated controllers. It is designed for a live demonstration in which you open the actual layout, explain the Java binding, change a visual property, and relaunch the app.

## 1. Open the correct files

All source views are under `src/main/resources/com/flux/browser/view/`. All controllers are under `src/main/java/com/flux/browser/controller/`. The shared stylesheet is `src/main/resources/com/flux/browser/style.css`.

Open **BrowserWindow.fxml** in Scene Builder to see the shell. Open **SpeedDial.fxml** separately to edit the home composition. Every view declares its own `fx:controller`. Shell views use `stylesheets="@../style.css"` to locate the stylesheet relative to their FXML file; the bare `WebContent.fxml` inherits the surrounding scene's styles at runtime. Use a Scene Builder installation that understands JavaFX 21 controls or later; there are no third-party control libraries to import.

Scene Builder previews the layout without running the application's persistence and navigation setup. The preview does not connect to PostgreSQL or act as a working web browser. Some text is illustrative preview content that Java replaces at runtime. Dynamic tiles, tab chips, and list rows have their own FXML files: open those components directly when you want to edit their appearance.

Save changes in **src/main/resources**, close the running Flux window, and run `mvn javafx:run` again. Maven copies updated resources into `target/classes`; the running scene does not hot-reload existing nodes. Do not edit generated files in `target/classes`.

## 2. How an FXML control reaches Java

```xml
<TextField fx:id="addressBar"
           promptText="Search the web or enter an address"
           onAction="#navigate"/>
```

`fx:id="addressBar"` connects the object to `@FXML private TextField addressBar` in `BrowserController`. The `onAction="#navigate"` attribute calls that controller's `@FXML` method when Enter is pressed. The root's `fx:controller` supplies the fully qualified Java class name. The FXMLLoader injects fields, resolves action handlers, then invokes `initialize()` if present.

Runtime dependencies arrive separately through `configure(...)`. This keeps FXML loading independent from database setup and lets reusable components have their own ordinary controllers. For example, loading `WebTab.fxml` also loads `SpeedDial.fxml`; `fx:include fx:id="speedDial"` injects both the included root as `speedDial` and its controller as `speedDialController` into `WebTabController`.

**Keep `fx:id`, `fx:controller`, and event-handler names unchanged when making cosmetic edits.** Change text, alignment, spacing, padding, sizes, style classes, or SVG geometry. Renaming an injected field or action requires a corresponding Java edit and rebuild. Controllers never manufacture the application's Label/Button/Pane layouts in Java; they load FXML instances into existing containers. The library uses FXML-backed list rows; Browser tools uses standard text-only ListCell rendering for providers, downloads, and search results.

## 3. Component inventory

| FXML | Controller | Responsibility | Created by |
| --- | --- | --- | --- |
| `BrowserWindow.fxml` | `BrowserController` | Main window, tabs container, navigation, panels, status bar | `FluxBrowser.start` |
| `Sidebar.fxml` | `SidebarController` | Home, bookmarks, history, settings shortcuts | Included by BrowserWindow |
| `TabHeader.fxml` | `TabHeaderController` | One selectable/closable tab chip | BrowserController for each tab |
| `WebTab.fxml` | `WebTabController` | Tab lifecycle, native home view, and failed-load overlay | BrowserController for each tab |
| `NativeWebContent.fxml` | `NativeWebContentController` | FXML viewport for macOS WKWebView | NativeWebPage on first navigation |
| `WebContent.fxml` | `WebContentController` | One lazily allocated WebView | WebTabController when an engine is first needed |
| `SpeedDial.fxml` | `SpeedDialController` | Home hero, clock, search, shortcut container | Included by each WebTab |
| `DialTile.fxml` | `DialTileController` | One shortcut's open/edit/delete controls | SpeedDialController per record |
| `Library.fxml` | `LibraryController` | Searchable bookmarks/history screen | Included by BrowserWindow |
| `LibraryRow.fxml` | `LibraryRowController` | One bookmark or visit row | LibraryController's ListCell |
| `Features.fxml` | `FeaturesController` | Workspaces, privacy, search, passwords, downloads and page tools | Included by BrowserWindow |
| `Reader.fxml` | `ReaderController` | Extracted article text, font size and original-page action | Reader mode action |
| `EasySetup.fxml` | `EasySetupController` | Live appearance controls, local media and named presets | BrowserController; Home overlay or native-page side panel |
| `Settings.fxml` | `SettingsController` | Theme, current-tab zoom, storage connection | Included by BrowserWindow |
| `EntryDialog.fxml` | `EntryDialogController` | Add/edit bookmark or shortcut | `Dialogs.edit` |
| `MessageDialog.fxml` | `MessageDialogController` | Confirmation, webpage alert, or webpage prompt | `Dialogs.confirm/alert/prompt` |

## 4. BrowserWindow.fxml → BrowserController.java

The outer `StackPane` (`root`) contains an FXML-declared `WallpaperCanvas`, the `shell` BorderPane, and a resize grip. The shell holds a 46-pixel left rail with vertical Mac window controls and the included sidebar. Its center contains tab/navigation controls, the content layout, and an optional status bar. `gx.css` overrides the retained base stylesheet at runtime. The canvas is original background artwork, not a browser engine; it redraws on size/appearance changes only.

The tab strip is an HBox containing `tabScroll`, the `tabHeaders` HBox inside its ScrollPane, the `newTabButton`, an empty draggable title area, while window controls remain in the left rail. The empty `tabHeaders` preview is intentional: `createTab()` loads a `TabHeader.fxml` instance and a `WebTab.fxml` instance together. The Java `Tab` record retains both roots and both controllers. All tab roots stay in `tabHost` until closed; only the selected root is visible and managed. Switching tabs therefore preserves the WebView's scene association instead of detaching and reattaching it.

The navigation HBox contains `backButton`, `forwardButton`, `reloadButton`, `stopButton`, the Home button, the address box, Tools, and Easy Setup. Reload and Stop occupy the same slot. Home leaves the address input empty. The address box groups `schemeLabel`, `addressBar`, and `bookmarkButton`. SVGPath graphics keep navigation icons sharp without an icon-font dependency. `loadProgress` uses the active engine's estimated progress and becomes zero when idle.

The content StackPane contains `tabHost`, the included `library`, and the included `settings`. Library and Settings start with both `visible="false"` and `managed="false"`. BrowserController reveals only the desired panel and hides the selected tab's rendering while a panel covers it. Dismissing the panel restores the same page.

| FXML action or field | Java behavior |
| --- | --- |
| `#newTab` | Creates a fresh tab and focuses its address field |
| `addressBar` / `#navigate` | Calls `UrlResolver.resolve`, then the active tab's `load` method |
| `#back`, `#forward`, `#reload`, `#stopLoading`, `#home` | Route to the active tab; Back also dismisses an open shell panel |
| `#toggleBookmark` | Saves/removes the current HTTP(S) URL asynchronously |
| `#settings` | Shows the included Settings panel |
| `#minimize`, `#maximize`, `#quit` | Apply the corresponding Stage action |
| `#beginDrag`, `#dragWindow`, `#titleClicked` | Drag and double-click the empty title region |
| `#beginResize`, `#resizeWindow` | Delegate bottom-right grip gestures to `WindowResizeSupport`, which also handles all outer edges/corners and constrains resizing to usable screen bounds |
| `databaseStatus`, `statusText`, `tabCount` | Display connection state, short feedback, and actual open-tab count |

`tabChanged(...)` schedules an `AnimationTimer` that combines bursts of page events into one `refreshChrome()` call per JavaFX pulse, then stops until another event arrives. Explicit shell actions can also refresh immediately. `refreshChrome()` copies the selected tab's navigation availability, title, URL, progress, and state into these controls. It preserves a focused address field while the user is typing. The bookmark lookup uses a request counter so a late database result from another page cannot paint the wrong star. `perform(...)` applies JDBC completions using `Platform.runLater`; it never calls `Future.get()` on JavaFX. `updateActiveContent()` pauses Home activity while a shell panel covers it or the window is minimized.

**Safe live edit:** select the navigation HBox and change its spacing or padding, or adjust the title-strip height. Keep `tabHost` and the included panels intact. `FluxBrowser.java` uses `WindowGeometry` to center and size the initial window within the display's usable bounds, so editing a root preview size does not change those launch settings. Keep the outer four-point gutter available for edge resizing and the shell/content containers' minimum width/height at zero so their children can reflow on small screens. Settings categories use a separate ScrollPane to remain reachable in short windows.

## 5. Sidebar.fxml → SidebarController.java

The sidebar is a 46-pixel VBox with the Flux SVG mark, Home, downloads, bookmarks, history, browser tools, Settings and customization actions. `VBox.vgrow="ALWAYS"` on the spacer keeps Settings anchored to the bottom as the window grows.

Injected buttons are `homeButton`, `bookmarksButton`, `historyButton`, and `settingsButton`. Their `#home`, `#bookmarks`, `#history`, and `#settings` handlers delegate to BrowserController after `configure` supplies it. `select(String)` removes the `selected` style class from the rail buttons and adds it to the matching destination. When browsing a website, none of the section icons is selected.

Each icon button has an accessible label and tooltip. `.sidebar-button.selected` paints a translucent accent background and left border, and changes the SVG stroke color.

**Safe live edit:** change the VBox spacing or the `.sidebar` padding. If changing the rail width, edit its min/pref/max widths together. Change the decorative Flux SVGPath without touching its action. The Flux label is decorative text, not a CPU/RAM indicator.

## 6. TabHeader.fxml → TabHeaderController.java

Each tab chip is an HBox with a small StackPane (home mark and loading spinner), `titleLabel`, and `closeButton`. Its preferred width is 190; the title ellipsizes instead of forcing the entire strip wider. The tooltip exposes the full page title and address.

`configure` binds `titleLabel.textProperty()` to the corresponding WebTab's title. The spinner follows `loadingProperty`; the icon follows the inverse. `titleTooltip` binds to the full title plus URL. The accessible text on the close button includes the tab title. `selected(boolean)` applies the **`:selected` pseudo-class**, which paints the selected tab with the current accent color.

`#clicked` selects on a primary click and closes on a middle click. `#close` invokes the supplied close action. Closing the final tab causes BrowserController to create a fresh Speed Dial. `dispose()` unbinds properties and removes callbacks when a tab closes.

**Safe live edit:** change the preferred tab width, spacing, or `.tab-title` font. The text “Speed Dial” in Scene Builder is a placeholder; runtime title binding replaces it. Runtime pseudo-class selection is not a fixed style class stored in FXML.

## 7. WebTab.fxml → WebTabController.java

This StackPane initially contains the included `speedDial` and `errorPane`. `page()` lazily creates an engine-neutral BrowserPage. Native macOS uses `NativeWebContent.fxml`; compatibility mode uses `WebContent.fxml`. Page properties feed the tab chip and toolbar. Failed navigation hides the native view and reveals the FXML error panel; Home retains the document but pauses media.

| Method/event | Result |
| --- | --- |
| `page()` / `initializePage()` | Allocate once, apply zoom, attach viewport and wire properties |
| `load(address)` | Load through BrowserPage and retain the attempted URL for retry |
| `SCHEDULED` / `RUNNING` | Loading indicator and estimated progress |
| `SUCCEEDED` | Resolve title/address and asynchronously save an HTTP(S) visit |
| `FAILED` / `CANCELLED` | Error recovery or stopped state; no successful visit |
| `back()` / `forward()` | Use the engine's session history |
| `home()` | Pause media and show FXML Speed Dial |
| `adopt()` | Attach a configured popup page, preserving its opener |
| `setActive()` | Hide/show native content and pause hidden Home clocks |
| `dispose()` | Release engine resources and listeners |

**Safe live edit:** change error text, spacing, or button labels. Keep lazy page creation; do not include an eager WebView in WebTab. Website styling belongs to WebKit.

### NativeWebContent.fxml → NativeWebContentController.java

The `viewport` StackPane has ID `nativeWebView`. It defines the webpage rectangle in the JavaFX layout; Scene Builder previews an empty rectangle, not a website. `NativeWebPage` tracks its logical scene bounds and asynchronously positions a native `FluxViewport` container in the same macOS window. WKWebView and PDFKit lay out inside that container, so opening or closing Web Inspector cannot expand them across the browser controls. Hiding a tab, Home, or a covering shell panel also hides the container. Keep the viewport untransformed and rectangular: native content does not participate in JavaFX effects, clipping, or scene snapshots. UI verification saves native page snapshots separately. Page alerts/prompts and file pickers use AppKit sheets; the shell's library editors remain FXML dialogs.

## 8. WebContent.fxml → WebContentController.java

Preserved for future Windows/Linux work and compatibility test launchers (`-Dflux.engine=javafx`), this component is a single `WebView` root with `fx:id="webView"`. `WebContentController` receives it through `@FXML` and exposes it with `view()`. It has no navigation handlers or persistence dependencies; WebTabController owns that behavior after loading the component.

Open this file separately in Scene Builder to inspect the WebView. It is intentionally absent from the initial WebTab preview. Keep the WebView as the root and keep its ID and controller unchanged: WebTabController uses that same node for visibility, focus, zoom, and disposal. No website is loaded by the FXML alone.

**Safe live edit:** inspect or adjust a WebView-specific visual property, then test a real page after relaunch. Runtime zoom overrides a preview zoom. Do not load this component on a background thread: JavaFX requires WebView and WebEngine creation and access on its Application Thread.

## 9. SpeedDial.fxml → SpeedDialController.java

The root ScrollPane fits its content to width and disables horizontal scrolling. Its content is a centered VBox with a maximum width and padding over the shared procedural wallpaper. This keeps the composition centered on a large display while allowing vertical scrolling in a smaller window.

The layout contains an optional clock/date row, a centered search box, the Speed Dial grid and a customization action. `clockLabel` and `dateLabel` update when Home becomes active; the minute timer only runs when the clock is enabled and Home is active. `setActive(false)` stops the Timeline for hidden tabs, shell panels, and minimized windows; disposal stops it permanently.

`searchField` and the arrow button both call `#search`, which uses the same browser URL resolver as the top omnibox. `addDialButton` calls `#add`, which opens the FXML entry editor. `tiles` is a TilePane populated from the DAO. Its preferred columns, tile dimensions, and gaps are FXML layout properties; it wraps when the window narrows.

`refresh()` marks the shortcut data dirty. `loadIfNeeded()` reads asynchronously only when Home is active and no query is already pending. Hidden Homes defer the work until shown; a completion for a newly hidden Home is discarded and marked for refresh. Before storage connects, or if it is offline, the active Home renders the six read-only starter destinations and an explanatory `dialNote`. A request number discards stale async responses. When saved shortcuts are loaded, the redundant note is hidden; an empty persisted list displays an invitation to add the first site. The DAO never reseeds a table simply because it became empty.

`render(...)` returns immediately for unchanged data. Otherwise it reuses nodes for unchanged shortcut records, loads `DialTile.fxml` only for new or changed records, updates the TilePane order and `dialCount`, and releases removed entries from its cache. Scene Builder's home preview does not show database tiles; open the tile FXML separately to design one.

**Best live edit:** adjust `.home-content` padding or the search box styling in `gx.css`. Easy Setup applies tile sizing, column limits and visibility at runtime, so those preferences take precedence over preview values.

## 10. DialTile.fxml → DialTileController.java

One tile is a VBox containing a large `openButton` with a centered wordmark graphic, followed by a caption/action HBox. The graphic uses `monogram` for the site name; the footer contains `titleLabel`, `editButton` and `deleteButton`. The readable host is retained in a hidden `domainLabel`. Open/edit/delete are separate buttons so editing a shortcut does not also navigate.

`configure` copies the record's title and readable host into labels, selects a deterministic card color from its host, and attaches optional short hover effects. It applies the `alternate` style to every other position for a second accent. Negative IDs denote read-only launch shortcuts, so their edit/delete buttons are disabled. Positive database IDs support `#edit` and `#delete`; `#open` routes the saved address through BrowserController.

The tile's title, host and wordmark are data-driven. The visual structure is entirely FXML, including the Button's graphic. Java assigns values and callbacks rather than building the layout programmatically.

**Safe live edit:** change the monogram background radius, tile border radius, title font, or VBox padding. Keep the preferred tile dimensions consistent with `SpeedDial.fxml`'s TilePane. A very long site name will ellipsize within its available space.

## 11. Library.fxml → LibraryController.java

This VBox is the single library screen in two modes: Bookmarks and History. It has a caption/dismiss row, heading/subtitle, a filter/action row, `resultLabel`, and the growing `entries` ListView. It starts hidden in BrowserWindow. `show(boolean bookmarkMode)` switches labels, filter hint, and relevant actions; the same layout is reused.

In bookmark mode, `addButton` is shown and `clearButton` is hidden. In history mode the reverse applies. `filterField` changes restart a 200 ms PauseTransition before reading the database, so typing does not schedule a query per keystroke. Each refresh increments a request counter; old results cannot replace newer filters. Queries use literal matching of title/address/folder text and return at most 500 records, reported explicitly in the result label when the cap is reached.

`entries` uses a standard ListCell to virtualize the list. The cell loads `LibraryRow.fxml` once and reconfigures its controller only when the assigned record changes. Unchanged query results do not replace the ListView's items. Hidden libraries do not apply late results or launch refresh queries. The row width follows the cell width, preventing long URLs from producing a wide horizontal list. Empty cells clear their graphic to avoid recycled-row artifacts. The `emptyLabel` distinguishes an empty library, no matches, loading, and unavailable storage.

`#add` opens the bookmark editor. `#refresh` reads again. `#clear` shows an FXML confirmation for deleting **all** saved visits, even when the list is filtered. `#dismiss` restores the selected webpage. Clearing history never removes bookmarks or Speed Dial.

**Safe live edit:** adjust list padding, the subtitle font, action spacing, or the placeholder's appearance. Headings and data-status text are replaced by the selected mode. To edit a visible row's structure, open LibraryRow directly.

## 12. LibraryRow.fxml → LibraryRowController.java

The row is an HBox with an accent icon, a growing open button whose graphic holds title and URL labels, a metadata VBox, an Edit button, and a Delete button. `LibraryRowController.configure` accepts either a Bookmark or a HistoryEntry and fills the fields accordingly.

Bookmarks display a star, folder/category, and creation time. History rows display a visit indicator and the visit timestamp, with editing hidden. Both display dates in the machine's local timezone. `initialize()` binds title and URL max widths to `openButton` once, so cell reuse does not rebuild the bindings. Long content truncates within the row; the full address remains in the record and is used by navigation.

`#open` navigates the active tab to the row's address. `#edit` opens the bookmark entry editor. `#delete` removes that bookmark/visit asynchronously and refreshes the library after success. Row reconfiguration replaces the referenced record, so reused cells act on their current item.

**Safe live edit:** change the title/URL spacing, row padding, metadata column width, or button labels. Keep the growing open-button column flexible; fixing every column to a large width would make the list clip in smaller windows.

## 13. Settings.fxml → SettingsController.java

The settings BorderPane contains a header, category ToggleButtons, and a scrolling card column. Search filters the cards using their userData keywords. FXML theme preview buttons and Light/Auto/Dark toggles update the shared appearance model; other categories expose existing tools and storage actions. Cards use CSS backgrounds and borders.

`magentaButton` and `cyanButton` call `#magenta` and `#cyan`. The controller asks BrowserController to add/remove the `cyan-theme` class on the scene root. Selected accent buttons receive a `:selected` pseudo-class. These controls now update the persistent appearance object in the existing session file. The Easy Setup panel exposes the complete appearance controls.

`zoomSlider` ranges from 75 to 150 percent. Its listener updates `zoomLabel` and the current tab's zoom. On a blank tab, the value is stored without allocating a WebView and applied on first navigation. When the panel opens, `show(zoom)` copies the tab's existing zoom while an `updating` flag prevents that synchronization from writing back unnecessarily. `#resetZoom` restores 100 percent.

The storage card has `connectionLabel`, `connectionDetail`, and `reconnectButton`. `connection(...)` displays connecting/connected/offline state and disables repeated reconnects while one is pending. The UI shows where connection settings live without displaying the configured password. `#reconnect` requests asynchronous schema/connection initialization again.

**Safe live edit:** change headings, card padding, border radius, or the version-line text. Keep the slider bounds consistent with the 75–150% clamp in WebTabController if changing the zoom range.

## 14. EntryDialog.fxml → EntryDialogController.java

This owned modal-window content is a VBox with an eyebrow, heading, title/address fields, an optional folder section, inline error text, and Cancel/Save buttons. The hosting Stage is created by `Dialogs`, with standard native window decoration. The application-authored contents remain FXML.

`configure` supplies the initial record values and an asynchronous save callback. A null category means a Speed Dial editor, so `categoryBox` is hidden and unmanaged. New bookmarks start in **Unsorted**. `#save` validates a nonempty title, a real HTTP(S) address, and folder length. Unlike omnibox input, a URL field in this editor never silently turns invalid text into a search.

While saving, inputs and cancel/save controls are disabled and the window-close request is consumed. On success, the dialog closes and the caller refreshes the appropriate library or tiles. On failure, the form stays open, controls re-enable, and `errorLabel` explains the failure. A duplicate address on an edit gets a specific message. Pressing Enter saves; Escape cancels when the editor is not busy.

**Safe live edit:** change field spacing, prompt text, heading font, or the dialog width. `heading` and the initial field contents are supplied at runtime. Save-button text also changes to “Saving…” during I/O.

## 15. MessageDialog.fxml → MessageDialogController.java

The general message dialog contains a heading, wrapped message, optional `inputField`, and Cancel/Continue buttons. The same controller supports clear-history confirmation, JavaScript `alert`, JavaScript `confirm`, and JavaScript `prompt`.

`configure` sets the title/message, displays the input only for a prompt, and hides Cancel for an informational alert. `#accept` records acceptance and closes the owned Stage; `#cancel` closes without acceptance. Closing the native window also leaves acceptance false. The `Dialogs` helper maps these values back to the required Boolean or String result.

Webpage message text is displayed as plain text and length-limited so a site cannot create an enormous text layout. No webpage-supplied FXML is loaded. Standard dialogs use `showAndWait`'s nested JavaFX event loop; database operations still run on the bounded reader/writer executors.

**Safe live edit:** change the dialog typography, spacing, or button labels. Keep the optional input and acceptance/cancel handlers wired. Test both confirmation and alert modes after structural changes.

## 16. CSS map and a live theme edit

| Selector | What it controls |
| --- | --- |
| `.root` | Base palette, font, JavaFX accent/focus colors, custom `-flux-accent` and `-flux-soft` looked-up colors |
| `.cyan-theme` | Overrides the accent values for the entire scene |
| `.sidebar`, `.sidebar-button.selected` | Rail background, padding, active destination |
| `.tab-strip`, `.tab-chip:selected`, `.tab-title` | Tab region, active underline, tab text |
| `.navigation-strip`, `.address-box:focused` | Address/navigation strip and keyboard-focus outline |
| `.page-progress`, `.page-progress > .bar` | Thin real loading indicator |
| `.home-content`, `.hero-title`, `.hero-mark`, `.hero-outline` | Home spacing, typography, and vector artwork |
| `.dial-tile`, `.dial-monogram`, `.tile-action` | Shortcut cards and their actions |
| `.library-row`, `.row-title`, `.category-label` | Saved-item rows |
| `.settings-card`, `.accent-choice:selected` | Settings cards and active accent choice |
| `.dialog-root`, `.dialog-heading`, `.error-label` | Entry/message dialog appearance |
| `.status-bar`, `.storage-label:online` | Footer and database state |

For a short CSS demonstration, change `.hero-title`'s font size or `.dial-tile`'s background radius, save, and relaunch. For a palette demonstration, use the in-app Magenta/Cyan buttons: the root style class immediately changes looked-up colors without recreating the scene. CSS selectors style the JavaFX shell; they do not inject CSS into remote webpages.

## 17. Five checks after a live edit

1. Open the edited FXML in Scene Builder and confirm the controller and event names are still present.
2. Run `mvn test` after Java changes; run `mvn javafx:run` after FXML/CSS changes to load the edited resources.
3. Inspect the home layout at both a presentation-size window and a smaller window. Scroll when content exceeds the viewport.
4. Open a website, create/switch/close a tab, and verify the corresponding control still responds.
5. If you changed a reusable component, inspect more than one instance: multiple tab chips, saved rows, or shortcuts. A successful FXML parse alone does not prove the layout fits long runtime content.

The automated `ui-check` Maven profile loads the actual FXML, drives real WebKit and UI actions, and writes screenshots for visual inspection. See [README.md](../README.md) for the disposable database setup and [VERIFICATION.md](VERIFICATION.md) for recorded results.

## Browser tools and reader components

`Features.fxml` is included as `features` in BrowserWindow; its controller is injected as `featuresController`. The Tools button invokes `BrowserController.features()`, hides the native page, and refreshes settings into five standard TabPane sections. Workspace selection has a separate **Switch workspace** button, so **Move current tab here** uses the chosen destination without switching first. `restore`, `focus`, `blocker`, `https`, `phishing`, and `fullText` bind through explicit action handlers.

`language` in Search selects the preferred translation language. On macOS, Reader, Translate, selected-text Search and Save Page live in the native webpage menu. `compatibilityPageTools` retains Reader and Translate FXML buttons for the future JavaFX engine; it is hidden and unmanaged on macOS. `passwordProvider`, `loginUser`, and `loginPassword` supply explicit password actions; the password field is cleared when the panel opens and immediately after saving. Selecting Downloads routes to the dedicated Downloads page; its earlier controls are retained in Features.fxml for compatibility. Editing Tab captions, spacing, and preferred list heights is safe in Scene Builder; keep action names and IDs intact.

`Reader.fxml` contains heading/byline labels, a scrolling article label, font-size buttons and **Open original**. Its controller renders Readability's text content, avoiding active HTML inside the reader. Adjust the label padding or font size in FXML and preserve `title`, `byline`, and `article`.

BrowserWindow's `documentBar` holds FXML PDF page and zoom buttons. It is managed only while a PDF is selected and no shell panel covers it. The actual document is rendered by native PDFKit; its embedded viewport cannot render inside Scene Builder. The same applies to WKWebView.

For a standalone Scene Builder preview of `BrowserWindow.fxml`, import the built Flux JAR as a custom component library so it can resolve the first-party `WallpaperCanvas` class. The remaining controls use standard JavaFX classes. The wallpaper itself is configured at runtime.

## EasySetup.fxml → EasySetupController.java

All customization controls are declared in FXML. The controller applies changes to `FeatureStore.State.appearance`, normalizes bounded values and asks BrowserController to coalesce the visual update into one pulse. Persistence uses the existing debounced session writer. Named presets are snapshots; import/export accepts the versioned Flux data format. File selection uses native choosers and file IO runs on a bounded worker.

`WallpaperCanvas`, `SystemTheme` and `Soundscape` own background rendering, public JavaFX system-theme observation and optional audio. Their resources are released when the browser closes. Speed Dial applies visibility, size, positioning and effect preferences independently of web-page zoom.

Easy Setup overlays Home; when a WKWebView is present, it occupies `contentLayout.right` so native content resizes beside it. Full browser panels keep their existing lifecycle. Appearance remains independent of tab/session restoration. See [CUSTOMIZATION.md](CUSTOMIZATION.md) for supported controls, defaults and reference differences.

### Foreground accent and menus

`BrowserWindow.fxml` declares a mouse-transparent `ChromeContour` above the shell. Like `WallpaperCanvas`, it needs the compiled Flux JAR on Scene Builder’s library path. The controller supplies the selected tab node; geometry and accent changes request one paint pulse. Disposal removes listeners and stops the timer.

`TabHeader.fxml`, `SpeedDial.fxml` and `DialTile.fxml` define their context menus inside `fx:define`, with explicit `items` collections and controller handlers. `Menus` propagates the owning shell’s stylesheet and palette into popup windows. Workspaces remains in `Features.fxml`, reached by `workspacesButton` directly below the window controls, Browser tools and the Settings category.

The native webpage menu is an exception to the JavaFX/FXML shell: `FluxContextWebView` extends the menu through AppKit’s public `willOpenMenu:withEvent:` hook. Native items keep their original targets. A small isolated-world script captures selections only on trusted context-menu gestures, including child frames; it does not prevent the website’s own context-menu behavior. No polling or background selection indexing runs. Password fields are excluded. The JNI event identifies the source page, and Java drops stale actions after a tab switch or navigation. See [Apple’s contextual-menu hook](https://developer.apple.com/documentation/appkit/nsview/willopenmenu(_:with:)).

## Manager page components

- `ManagerHeader.fxml` / `ManagerHeaderController`: shared History, Downloads, Settings and Bookmarks navigation. The active destination receives `manager-current` styling.
- `Downloads.fxml` / `DownloadsController`: left search/date filters, file-type toggles, a virtualized live list, clear-finished and Open PDF actions. `DownloadRow.fxml` / `DownloadRowController` renders progress and the actions appropriate to each transfer state.
- `Library.fxml` / `LibraryController`: shared full-page History/Bookmarks layout. History date filters query bounded results asynchronously; bookmarks keep add/edit/category/delete behavior. `LibraryRow.fxml` / `LibraryRowController` now has an optional date heading above the row.
- `HistoryPanel.fxml` / `HistoryPanelController`: a compact searchable History list and an expand button. The panel moves into `contentLayout.right` while open so WKWebView reflows. It returns hidden to the content host when closed. Easy Setup and History share this dock exclusively.

All static controls remain authored in FXML. Keep IDs, `userData` filter values, toggle groups and controller action names when editing these views. Color, row, header and filter styles are in the `manager-*` rules in `gx.css`.
