# Flux Browser

A JavaFX browser with an Opera GX-inspired dark theme, native WebKit rendering on macOS, and PostgreSQL persistence.

Built with Java 21, JavaFX, and WKWebView. Browsing works without a database — storage features activate when PostgreSQL is available.

![Flux Browser](docs/images/flux-gx-home.png)

> [!CAUTION]
> **macOS Required:** Flux relies on native WKWebView through a JNI bridge for its core functionality. While it technically has fallback support for Windows and Linux, the complete experience (especially media playback on sites like YouTube) is **only fully supported and optimized on macOS**.

[**📺 Watch the Presentation Video on YouTube**](https://youtu.be/toFne5K-vAY)

## Platform Support

Flux is currently **fully optimized for macOS**. On macOS, it uses Apple's native **WKWebView** through a JNI bridge instead of relying only on JavaFX WebView. This provides better compatibility with modern websites, JavaScript, media playback, and native browser functionality.

Flux can also run on **Windows and Linux** using JavaFX WebView. However, JavaFX WebView has limitations with some modern, media-heavy websites such as YouTube, so **macOS is currently the preferred platform for the complete Flux experience**.

## Prerequisites

- JDK 21+
- Maven 3.9+
- **macOS only:** Xcode Command Line Tools — `xcode-select --install`
- PostgreSQL *(optional, for bookmarks/history/speed dial persistence)*

> Apple Silicon JDK is recommended for M-series Macs.

## Database Setup (Optional)

```sh
# Start PostgreSQL
brew services start postgresql@18

# Create the role and database
psql -d postgres
```

```sql
CREATE ROLE flux WITH LOGIN;
\password flux
CREATE DATABASE flux OWNER flux;
\q
```

```sh
# Copy and edit the config file with your password
cp config/database.properties.example config/database.properties
```

Tables are created automatically on first launch.

## Build & Run

```sh
mvn clean verify
mvn javafx:run
```

To open a specific URL at launch:

```sh
mvn javafx:run -Djavafx.args="--url=https://github.com/"
```

## Features

- **Native WebKit** — WKWebView for full web compatibility on macOS
- **Tabbed browsing** — independent tabs, popup handling, session restore
- **Navigation** — Back / Forward / Reload / Home / Stop, URL & search bar
- **Search Plugins** — quick-search shortcuts in address bar (e.g., `!yt`, `!w`, `!gh`)
- **Bookmarks & History** — toggle, edit, search (PostgreSQL)
- **Speed Dial** — editable start-page shortcuts
- **Workspaces** — organize tabs into groups
- **Ad/Tracker blocking** — domain-level blocking via Tools
- **Downloads & PDF viewer** — built-in download manager and PDFKit viewer
- **Reader mode & Translation** — distraction-free reading, translate in new tab
- **Focus mode** — minimize distractions
- **HTTPS upgrades & Phishing warnings** — security defaults
- **Password providers** — optional macOS Keychain, Bitwarden, or 1Password CLI
- **GX-inspired theming** — accent colors, wallpapers, fonts, sound, and presets via Easy Setup
- **Developer Tools** — F12 or Option+Cmd+I
- **Zoom** — per-tab zoom level

## Keyboard Shortcuts

| Action | Shortcut |
| --- | --- |
| Focus address bar | Cmd+L |
| New / Close tab | Cmd+T / Cmd+W |
| Next / Previous tab | Ctrl+Tab / Ctrl+Shift+Tab |
| Select tab 1–8 / last | Cmd+1–8 / Cmd+9 |
| Back / Forward / Home | Alt+Left / Alt+Right / Alt+Home |
| Reload | Cmd+R or F5 |
| Toggle bookmark | Cmd+D |
| History / Bookmarks | Cmd+Y / Cmd+Shift+B |
| Developer Tools | F12 or Option+Cmd+I |

## Project Structure

```
├── src/main/java/       # Application source
├── src/main/native/     # WKWebView bridge (Objective-C)
├── src/main/resources/  # FXML views, CSS, assets
├── src/test/            # Tests and verification checks
├── config/              # Database config (gitignored)
├── database/            # SQL schema
├── docs/                # Architecture, features, verification docs
└── pom.xml              # Maven build config
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Features Guide](docs/FEATURES.md)
- [Customization](docs/CUSTOMIZATION.md)
- [Verification Results](docs/VERIFICATION.md)
- [UI & Scene Builder Guide](docs/UI_SCENEBUILDER_GUIDE.md)
- [Presentation Guide](docs/PRESENTATION.md)
- [Third-Party Notices](docs/THIRD_PARTY_NOTICES.md)

## Dependencies

| Dependency | Version | Purpose |
| --- | --- | --- |
| JavaFX Controls + FXML + Web | 21.0.12 | UI framework |
| Gson | 2.13.2 | JSON for settings and search providers |
| PostgreSQL JDBC | 42.7.13 | Database storage |

On JDK 24+, JavaFX 26.0.2 is selected automatically.

## Team Contributions

This project was developed collaboratively, with each member taking ownership of core subsystems to ensure a functional and polished final product:

| Member | ID | Key Responsibilities & Contributions |
| :--- | :--- | :--- |
| **Saad Al Abeed** | 230041142 | **Data Persistence & Storage Layer:** Architected the PostgreSQL database schema and implemented JDBC connections. Developed background DAOs (Data Access Objects) for asynchronous, thread-safe management of browser history, bookmarks, and speed dial entries. |
| **Mehedul Hasan Prodhan** | 230041116 | **Core Architecture & Feature Integration:** Engineered the browser's core logic, navigation system, and tab management. Integrated the native WKWebView bridge and built out the advanced browser features, including workspaces, download management, and instant search utilities. |
| **Mueej Al Basit** | 230041151 | **User Interface & UX Design:** Designed the Opera GX-inspired graphical interface using JavaFX and Scene Builder. Authored the comprehensive FXML layouts and custom CSS styling for all visual components, ensuring a cohesive and responsive user experience. |
