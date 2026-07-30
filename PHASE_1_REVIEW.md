# Phase 1 Review Guide

Phase 1 implements the Flux GX visual shell and its interactive appearance controls. It intentionally retains one `WebView`; the multi-tab engine begins in Phase 2.

## Delivered behavior

- Undecorated, resizable Flux window with custom drag, minimize, maximize/restore, and close behavior.
- FXML components for the title bar, tab strip, navigation bar, sidebar, sidebar panels, start page, and Easy Setup.
- Shared GX stylesheet with dark layered surfaces and red, cyan, purple, and green accents.
- Start page with search, six default Speed Dials, and an FXML-defined Speed Dial editor.
- Add, edit, remove, and left/right reorder behavior for Speed Dial entries.
- Sidebar with Start Page, GX Control, bookmarks, history, downloads, settings, and hide controls.
- Sidebar panels that can be docked or displayed as overlays.
- Easy Setup controls for accent, wallpaper, UI scale, sidebar visibility, panel docking, and reduced motion.
- Bounded page zoom and keyboard commands.

## Expected limitations

- The tab strip contains one visual tab. Real multi-tab browsing starts in Phase 2.
- GX metrics are visual placeholders. OSHI metrics and tab suspension start in Phase 5.
- Bookmarks, persistent history, downloads, and full settings pages are placeholders for later phases.
- Speed Dial and Easy Setup changes are held in memory and reset when the process exits. PostgreSQL persistence starts in Phase 3.
- The wallpaper designs are original CSS gradients; no Opera artwork is copied.

## Automated test commands

Run from the Flux project:

```bash
cd /home/mark/Projects/Java/Flux-Browser
mvn clean verify
```

Expected results in a graphical desktop session:

```text
NavigationResolverTest:  Tests run: 8, Failures: 0, Errors: 0
BrowserUiStateTest:      Tests run: 6, Failures: 0, Errors: 0
WindowResizeSupportTest: Tests run: 3, Failures: 0, Errors: 0
BrowserUiSmokeTest:      Tests run: 2, Failures: 0, Errors: 0
Total:                   Tests run: 19, Failures: 0, Errors: 0
BUILD SUCCESS
```

On headless Linux, the two `BrowserUiSmokeTest` methods do not run. The expected total is then 17 passing tests and `BUILD SUCCESS`.

To run only the FXML and local-WebView smoke tests:

```bash
mvn -Dtest=BrowserUiSmokeTest test
```

Expected in a graphical session:

```text
Tests run: 2, Failures: 0, Errors: 0
BUILD SUCCESS
```

Validate Docker Compose without starting services:

```bash
docker compose config --quiet
```

Expected: exit status `0` and no output. Phase 1 does not require a running PostgreSQL container.

## Start the application

```bash
mvn javafx:run
```

Expected initial values:

| Item | Expected value |
|---|---|
| Window title | `Flux Browser` |
| Initial size | `1280 × 800` |
| Minimum size | `900 × 640` |
| Initial page | Flux start page; no external page loaded |
| Accent | Red, `#ff1b57` |
| Wallpaper | Grid |
| Interface scale | `13` |
| Sidebar | Visible, `68 px` wide |
| Sidebar panel | Closed; `312 px` wide when opened |
| Panel mode | Docked |
| Reduced motion | Off |
| Speed Dials | 6: YouTube, Twitch, Discord, Reddit, GitHub, Gmail |

## Manual interaction checklist

### Window chrome

- [ ] Dragging the empty title-bar region moves the window.
- [ ] Double-clicking that region toggles maximize/restore.
- [ ] The `—`, `□`, and `×` buttons minimize, maximize/restore, and close.
- [ ] Dragging every window edge and corner resizes the window.
- [ ] Resizing stops at `900 × 640`.
- [ ] `F11` enters and leaves full screen.

### Navigation

- [ ] Entering `example.com` loads `https://example.com`.
- [ ] Entering `javafx browser` opens a DuckDuckGo search.
- [ ] Back and forward enable only when their corresponding history entry exists.
- [ ] Reload changes to a stop symbol while a page is loading.
- [ ] The Home button returns to the Flux start page without destroying the current WebView history.
- [ ] `Ctrl+L` or `Command+L` focuses and selects the address field.
- [ ] `Ctrl/Command` with `+`, `-`, and `0` changes, decreases, and resets page zoom.

### Sidebar and panels

- [ ] Each sidebar panel button opens the matching titled panel.
- [ ] Clicking the selected panel button again closes it.
- [ ] `▥` switches between docked and overlay behavior.
- [ ] Docked mode reserves `320 px` in the content surface; overlay mode covers the left content edge.
- [ ] The panel `×` closes the panel.
- [ ] `«` hides the sidebar and closes its panel.
- [ ] Easy Setup can restore the hidden sidebar.

### Speed Dial

1. Click `+ ADD SITE`.
2. Enter title `Example` and address `example.com`.
3. Click `SAVE`.

Expected:

- The editor closes.
- A seventh tile named `Example` appears.
- Clicking it loads `https://example.com`.

Then right-click the tile:

- [ ] `Edit` changes its title/address.
- [ ] `Move left` and `Move right` change its grid position.
- [ ] `Remove` deletes it.
- [ ] Restarting Flux restores the original six defaults, as expected before Phase 3.

### Easy Setup

- [ ] Red, cyan, purple, and green accent buttons immediately recolor borders, selections, and highlighted controls.
- [ ] Grid, Void, and Neon immediately change the start-page background.
- [ ] The scale slider ranges from `11` to `16`.
- [ ] Show sidebar controls sidebar visibility.
- [ ] Dock sidebar panels controls panel layout.
- [ ] Reduce interface motion adds reduced-motion mode without changing functional behavior.
- [ ] Closing and reopening Easy Setup retains values for the current process.
- [ ] Restarting Flux restores defaults, as expected before Phase 3.

## Failure reporting

If a command or manual check fails, replace the contents of `failure_report.txt` with:

1. The command that was run.
2. The complete error output.
3. The operating system and desktop session type.
4. For visual failures, the window size and a short description of the mismatched element.
