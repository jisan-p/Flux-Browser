# Weird Browser

A custom web browser project built in Java that fetches web pages and parses their HTML DOM asynchronously.

## Prerequisites

- **Java Development Kit (JDK) 26** or higher
- **Apache Maven** installed

## How to Build and Run

1. Open your terminal and navigate to the root directory of the project (where the `pom.xml` is located).

2. Compile the project and download dependencies using Maven:
   ```bash
   mvn clean compile
   ```

3. Run the application using the Maven Exec plugin:
   ```bash
   mvn exec:java -Dexec.mainClass="org.custombrowser.Main"
   ```

## Usage

Once the program starts, a browser window will open.
You can enter a domain like `example.com` or a full URL like `https://example.com` in the address bar.

The application uses JavaFX's `WebEngine` (based on WebKit) to fetch, parse, and render the webpages, providing a fully functional modern web browsing experience out of the box.

## Current Capabilities
- **Web Rendering**: Full HTML5, CSS3, and modern JavaScript support powered by WebKit.
- **UI**: A lightweight JavaFX-based toolbar with Back, Forward, Refresh, Zoom, and History controls.
- **Navigation**: Automatic protocol appending (`https://`) for ease of use.

## TODO / Development Roadmap
To evolve into a fully-featured desktop browser wrapper, the following features need to be implemented:

### User Interface (Browser Shell)
- [x] **Navigation History**: Implement Back, Forward, and Refresh functionalities.
- [ ] **Tabbed Browsing**: Allow multiple webpages to be open simultaneously in different tabs.
- [ ] **Bookmarks & History**: Save browsing history and bookmarks to local SQLite database or JSON.
 - [x] **Search Engine Integration**: Allow users to type queries in the address bar that redirect to a default search engine (e.g., Google or DuckDuckGo).
 - [ ] **Downloads Manager**: Intercept file downloads and provide a UI to track progress.