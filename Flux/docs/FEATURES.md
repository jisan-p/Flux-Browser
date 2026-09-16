# Browser tools

Open **Tools** beside the address bar. The expanded features use the existing JavaFX shell and native macOS services. JavaFX fallback retains sessions, workspaces, reader, translation, search tools and PostgreSQL indexing; native blocking, Keychain, downloads and PDF viewing require macOS.

| Feature | How it works / defaults |
| --- | --- |
| Session restore | Enabled by default. Saves tab URL/title/zoom, workspace membership and selection. Only the selected restored tab loads; background tabs load when selected. Forms, navigation stacks and scroll positions are not saved. |
| Workspaces | Create, switch, move the current tab or delete a workspace. Deleting moves its tabs to Default. At most 50 workspaces and 200 tabs. |
| Focus mode | Keeps the selected tab visible and prevents opening, closing or switching tabs/workspaces until disabled. Existing background tabs remain open. |
| Reopen tab | Restores the last closed tab's URL/zoom, up to 20 recent closures. Tools button or Cmd/Ctrl+Shift+T. |
| Domain blocking | Enabled by default with 11 starter domains. WebKit compiles rules, blocking matching third-party requests. Allow/block the current site, then reload. Optional Update blocklist imports up to 30,000 supported domain rules from AdGuard DNS Filter. No cosmetic rules or first-party ad filtering. |
| HTTPS upgrades | Enabled by default for hosts known by system WebKit, on newly opened tabs. This is not an HTTPS-only mode for every host. |
| Phishing warnings | Enables WebKit's system fraudulent-site warning service on newly opened tabs. Detection depends on macOS/service coverage. |
| Passwords | macOS Keychain default; explicit save/update, fill and delete by exact HTTPS origin and username. Flux-owned entries only. Optional installed Bitwarden/1Password CLI providers retrieve only the item ID you select. Never submits the form. |
| Full-text search | Off by default. Opt-in text indexing stores up to 200,000 characters per page and 1,000 pages in PostgreSQL. Password forms are excluded; other authenticated content may be stored. Search terms use PostgreSQL web-search syntax. Clearing history or the text index removes stored page text. |
| Instant answers | Local arithmetic such as `= (12 + 8) / 4`; result appears in the status bar. Supports decimal numbers, parentheses and + − * /. |
| Search plugins | Editable HTTPS URL templates containing `{query}`. Built-ins: `!gh`, `!w`, `!yt`. For example `!yt JavaFX tutorial`. Up to 50 templates; no downloaded extension code. |
| Reader | Readability extracts article text into a separate FXML window, with font size controls. Complex or non-article pages may not yield an article. |
| Translation | Select a language, then **Translate page ↗**. Opens Google Translate with the current HTTP(S) page URL in a **new tab**. Requires internet; Google receives the page URL. Signed-in/dynamic pages may not translate fully through its proxy. |
| Downloads | WKDownload uses the current WebKit session. Choose a destination through macOS's save sheet; inspect progress, cancel or reveal in Finder. Downloads survive closing their source tab, but are cancelled on app exit and are not resumed across launches. No file is opened automatically. |
| PDF viewer | **Open PDF…** or **Downloads → Open downloaded PDF** opens a local PDF in a new tab. PDFKit supplies scrolling; FXML buttons navigate pages and zoom. HTTP(S) links inside PDFs open Flux tabs. Online PDFs may also display through WebKit; download them to use the dedicated local viewer. |

![Browser tools](images/flux-tools.png)

![Native PDF viewer](images/flux-pdf.png)

## Password providers

In Tools → Passwords, the displayed page identifies where a login will be filled. For Keychain, supply a username and password, click **Save in Keychain**, then use **Fill selected login** on the matching HTTPS login page. A second save updates that origin/username entry. macOS may ask for Keychain access. Flux's service is `com.flux.browser.passwords`; values are not stored in PostgreSQL or the session file.

For Bitwarden or 1Password, install and unlock their official CLI separately. Flux looks for `bw` or `op` in `/opt/homebrew/bin` or `/usr/local/bin`. Supply the item ID rather than the username. Bitwarden requires an unlocked CLI session (`BW_SESSION` inherited by the launched browser). 1Password uses its configured CLI/desktop authorization. Save or edit external vault items in their own application. Fetches time out; output is bounded; passwords do not appear in command arguments or error messages. The selected item must contain a URL with the same HTTPS origin, including any non-default port.

## Storage and resources

Sessions/preferences live in `~/Library/Application Support/Flux/session.json` on macOS, written atomically with owner-only file permissions. The optional downloaded domain list is stored alongside it. Direct Java launches can override `flux.profileDir`; automated feature tests always select a temporary profile. Clearing browsing history preserves workspaces, bookmarks and Keychain entries. Disable **Restore tabs and workspaces** to start with a fresh tab while keeping preferences.

Session writes are debounced. Feature tasks use two workers with a bounded queue; JDBC retains two readers and one ordered writer. WebKit handles its own processes and video acceleration. Restored inactive tabs do not allocate a rendering engine until selected. Downloads and PDF buffers consume native memory outside the 1 GiB Java heap limit.

## Source references

- [WebKit content blockers](https://webkit.org/blog/3476/content-blockers-first-look/) and [domain targeting](https://webkit.org/blog/4062/targeting-domains-with-content-blockers/).
- [AdGuard DNS Filter source and licenses](https://github.com/AdguardTeam/AdGuardSDNSFilter); updating fetches its public `Filters/filter.txt` endpoint and imports supported plain domain rules.
- [Bitwarden CLI](https://bitwarden.com/help/cli/) and [1Password item commands](https://www.1password.dev/cli/reference/management-commands/item).
- [PDFKit link handling](https://developer.apple.com/documentation/pdfkit/pdfviewdelegate/pdfviewwillclick(onlink:with:)).

The expanded feature matrix is in [ARCHITECTURE.md](ARCHITECTURE.md); runtime evidence and untested cases are in [VERIFICATION.md](VERIFICATION.md).
