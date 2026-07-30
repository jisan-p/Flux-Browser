# Flux Browser

Flux Browser is a lightweight desktop browser built with Java 21, JavaFX, FXML, and CSS. The project is being developed phase-by-phase toward an Opera GX-inspired interface while retaining JavaFX `WebView` as its browser engine.

## Implemented through Phase 1

- Standard Maven project targeting Java 21.
- JavaFX controls, FXML, and WebView integration.
- URL and DuckDuckGo search resolution.
- Lightweight application composition root for controller dependencies.
- Unit tests plus display-aware JavaFX/FXML and local-page smoke tests.
- Docker Compose PostgreSQL development service.
- Reproducible Maven build/test container.
- Undecorated Flux GX window shell with draggable title bar and native window actions.
- GX-inspired tab strip, navigation bar, vertical sidebar, overlay/docked panels, and start page.
- Editable and reorderable in-memory Speed Dial.
- Easy Setup controls for accent, wallpaper, UI scale, sidebar visibility, panel docking, and reduced motion.

## Prerequisites

For normal desktop development:

- JDK 21
- Maven 3.9 or newer
- Docker with Docker Compose

The JavaFX application runs on the host so native desktop windows work consistently across Windows, Linux, and macOS. Docker supplies PostgreSQL and an optional reproducible headless Maven build.

## Build and run

```bash
mvn clean verify
mvn javafx:run
```

On a Linux machine without `DISPLAY` or `WAYLAND_DISPLAY`, the JavaFX UI smoke tests are skipped. Run the same `mvn clean verify` command from a graphical session to execute them.

## PostgreSQL development service

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Development connection values:

```text
Database: flux_browser
Host:     localhost
Port:     5432
User:     postgres
Password: 1234
Schema:   flux_browser
```

The official PostgreSQL image executes numbered SQL files in `docker/postgres/init/` when it initializes a new data volume. All database DDL and seed logic must remain in those SQL files. Java code may connect through JDBC but must not create or alter database objects.

PostgreSQL only runs initialization scripts when its data directory is empty. During development, applying changed initialization scripts to a fresh database requires removing the existing Compose volume first. That operation destroys local browser data and should only be run intentionally.

Inspect service health:

```bash
docker compose ps
docker compose logs postgres
```

## Reproducible container build

The optional `flux-build` profile compiles the project and runs non-graphical tests in a Java 21/Maven container:

```bash
docker compose --profile build run --rm flux-build
```

The PostgreSQL service is started automatically and must become healthy before the build service runs.

## Configuration contract

Persistence code introduced in a later phase will read these environment variables:

```text
FLUX_DB_URL=jdbc:postgresql://localhost:5432/flux_browser
FLUX_DB_USER=postgres
FLUX_DB_PASSWORD=1234
FLUX_DB_SCHEMA=flux_browser
```

Do not commit production credentials. The fixed password in Compose is for local development only.

## Current browser controls

- Enter a domain, full URL, local address, or search query.
- Navigate backward and forward.
- Reload or stop the current page.
- Return to the Flux start page.
- Use `Ctrl`/`Command` + `L` to focus the address bar.
- Use `Ctrl`/`Command` + `+`, `-`, or `0` to control page zoom.
- Use `F11` to enter or leave full screen.
- Open GX Control, bookmarks, history, downloads, and settings placeholders from the sidebar.
- Add, edit, remove, and reorder Speed Dial entries from the start page.
- Change Phase 1 appearance settings from Easy Setup.

Phase 1 intentionally presents one visual tab. The multi-WebView tab engine is implemented in Phase 2. Speed Dial and appearance changes remain in memory until PostgreSQL persistence is connected in Phase 3.
