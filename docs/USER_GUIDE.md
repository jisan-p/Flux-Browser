# Flux Browser User Guide

Flux Browser is a Java 21 and JavaFX desktop browser with an Opera GX-inspired
interface. JavaFX WebView is the rendering engine; PostgreSQL stores browser
settings and metadata.

## Setup

Install JDK 21, Maven 3.9 or newer, and Docker with Docker Compose. From the
project directory, start PostgreSQL and verify its health:

```bash
docker compose up -d postgres
docker compose ps
```

Expected: the `postgres` service is `healthy`.

Run Flux from a graphical desktop terminal:

```bash
mvn javafx:run
```

The local development database is `flux_browser` on `localhost:5432`, with
user `postgres` and password `1234`. That fixed password is only for local
development.

PostgreSQL runs the standalone files under `docker/postgres/init/` only for an
empty database volume. Java code performs JDBC operations but does not create
or alter database objects. Do not delete the database volume unless you
intentionally want to erase all locally stored Flux data.

## Configuration

Override local database defaults with environment variables:

```text
FLUX_DB_URL=jdbc:postgresql://localhost:5432/flux_browser
FLUX_DB_USER=postgres
FLUX_DB_PASSWORD=1234
FLUX_DB_SCHEMA=flux_browser
```

Flux requires PostgreSQL. If the service is unavailable or the schema version
is wrong, startup stops with an actionable error instead of silently switching
to a different database.

## Navigation and protocols

The address field accepts full HTTP/HTTPS URLs, domains, local addresses, and
search terms. Searches use DuckDuckGo. Flux handles `http`, `https`, `file`, and
internal `about` navigation in WebView. It asks before handing `mailto`, `tel`,
or `magnet` URLs to the operating system. Other schemes are blocked.

Popups use per-site ask/allow/block decisions. A persistent allow decision can
be reset in Settings. Common download extensions are intercepted and streamed
outside the JavaFX thread; server-generated downloads with no recognizable
extension may not be detected because WebView exposes no general download
callback or response headers.

## Tabs and GX Control

New Start Page tabs are lightweight and do not allocate a WebView until they
navigate. Restored background tabs also remain unloaded until selected.

GX Control reports CPU and resident memory for the complete Flux process. Hot
Tabs is an activity heuristic, not per-tab hardware usage. Suspending an
eligible background tab releases its WebView while retaining its URL, title,
favicon, and zoom. Resuming reloads the URL and therefore loses the old DOM,
forms, scripts, media, and in-page navigation state.

Automatic suspension never targets the active, pinned, loading, Start Page, or
Keep Active tabs. GX Cleaner removes only selected metadata categories and
never deletes downloaded files.

## Keyboard shortcuts

On macOS, use Command where this guide says Ctrl.

| Shortcut | Action |
| --- | --- |
| `Ctrl+L` | Focus address field |
| `Ctrl+T` / `Ctrl+W` | Open / close tab |
| `Ctrl+Shift+T` | Reopen closed tab |
| `Ctrl+Tab` / `Ctrl+Shift+Tab` | Cycle tabs |
| `Ctrl+1` … `Ctrl+9` | Select numbered tab; 9 selects the last |
| `Ctrl+R` | Reload or stop |
| `Alt+Left` / `Alt+Right` | Back / forward |
| `Ctrl+F` | Find in page |
| `Ctrl+P` | Print page |
| `Ctrl++` / `Ctrl+-` / `Ctrl+0` | Zoom in / out / reset |
| `Ctrl+Shift+B` | Bookmarks |
| `Ctrl+H` / `Ctrl+J` | History / downloads |
| `Ctrl+,` | Settings |
| `F11` | Full screen |
| `Escape` | Close transient UI |

## Privacy and data removal limits

Flux stores settings, Speed Dial entries, bookmarks, visits, download metadata,
session tabs, recently closed tabs, and window geometry in PostgreSQL. Download
files remain at the path selected by the user.

Cookie clearing affects cookies held by the current Flux process. JavaFX has no
supported public API for completely clearing WebView's internal HTTP cache, so
Flux does not claim that clearing history, cookies, or favicons clears that
cache. Performance diagnostics store aggregate counts and durations only; they
never store URLs, titles, query text, or file paths.

## Known WebView constraints

JavaFX WebView is not Chromium. Some current JavaScript, codecs, adaptive media,
DRM video, authentication flows, and browser-specific APIs are unavailable.
YouTube may load but fail to play video. Use the operating-system browser for a
site that depends on unsupported media or web-platform features.

Native installers, extensions, password storage, synchronization, VPN, DRM,
and full Opera GX feature parity are outside the current project scope.
