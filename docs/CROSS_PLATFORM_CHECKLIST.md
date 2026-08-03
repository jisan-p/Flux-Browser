# Cross-Platform Smoke-Test Checklist

Run this checklist on Windows, Linux, and macOS from a graphical desktop. Native
packaging/installers are outside Phase 6; this validates Maven-launched runtime
behavior.

Record the OS/version, JDK output from `java -version`, Maven output from
`mvn -version`, display server where applicable, and the commit under test.

## Automated baseline

```bash
docker compose up -d postgres
mvn clean verify
mvn javafx:run
```

Expected: PostgreSQL is healthy, all tests pass, the JavaFX smoke suite runs
four tests rather than zero, and Flux opens without an uncaught exception.

## Window and layout

- [ ] Undecorated window can be dragged, resized on every edge/corner,
      minimized, maximized/restored, made full-screen, and closed.
- [ ] At 1280×800, 1440×900, and 1920×1080, controls remain reachable and text
      does not overlap.
- [ ] All accents, wallpapers, UI scales, docked/overlay sidebar modes, and
      reduced-motion mode render legibly.
- [ ] Window size, position, maximized state, and appearance persist after a
      normal restart.

## Browser behavior

- [ ] A fresh Start Page tab appears immediately and creates no visible page
      load until navigation.
- [ ] HTTP/HTTPS and a local fixture load; title, favicon fallback, back,
      forward, reload/stop, find, zoom, and print dialog work.
- [ ] Tab create, close, reopen, duplicate, pin, reorder, middle-click close,
      close others, close right, and keyboard selection work.
- [ ] Restarting with several tabs loads only the selected restored page; other
      pages load when selected.
- [ ] External `mailto`, `tel`, and `magnet` handling shows confirmation. The
      result may depend on an installed OS handler.

## Sidebar, database, and files

- [ ] No sidebar content is visible at startup. Each of GX Control, bookmarks,
      history, downloads, and settings loads on first selection.
- [ ] Bookmark/history/settings/session changes survive restart.
- [ ] A common-extension local download can be saved, cancelled, retried,
      opened, and revealed using native dialogs/file management.
- [ ] GX Control starts updating only while open; manual and automatic tab
      suspension preserve metadata and reload the address when resumed.
- [ ] GX Cleaner preview and selected cleanup leave downloaded files unchanged.

## Platform-specific notes

### Windows

- [ ] Ctrl shortcuts, Alt+Left/Right, F11, file chooser, print dialog, and
      Explorer reveal behavior are correct.
- [ ] The window remains usable at 100%, 125%, and 150% display scaling.

### Linux

- [ ] Test the available X11 or Wayland session and record which one was used.
- [ ] Ctrl shortcuts, file chooser, print dialog, and desktop file-manager open
      behavior are correct.
- [ ] No GTK/WebKit native-library error appears at startup.

### macOS

- [ ] Command shortcuts replace Ctrl shortcuts; Option/Command conventions do
      not prevent navigation commands.
- [ ] Native file chooser and print dialog work, and Finder opens for reveal.
- [ ] Full-screen entry/exit and high-DPI rendering are correct.

## Result record

Mark the platform as passed only when `mvn clean verify`, startup, the complete
behavior checklist, normal shutdown, and a 30-minute browsing session succeed.
Attach console output and update `failure_report.txt` with exact commands and
errors for any failure.
