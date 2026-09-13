package com.flux.browser.model;

import java.time.Instant;
import java.util.List;

public record SpeedDial(long id, String title, String url, Instant createdAt, int position) {
    /** Read-only launch shortcuts when PostgreSQL is unavailable. Negative IDs are never persisted. */
    public static List<SpeedDial> starters() {
        return List.of(
                new SpeedDial(-1, "GitHub", "https://github.com/", Instant.EPOCH, 0),
                new SpeedDial(-2, "Wikipedia", "https://www.wikipedia.org/", Instant.EPOCH, 1),
                new SpeedDial(-3, "YouTube", "https://www.youtube.com/", Instant.EPOCH, 2),
                new SpeedDial(-4, "MDN Web Docs", "https://developer.mozilla.org/", Instant.EPOCH, 3),
                new SpeedDial(-5, "OpenJFX", "https://openjfx.io/", Instant.EPOCH, 4),
                new SpeedDial(-6, "DuckDuckGo", "https://duckduckgo.com/", Instant.EPOCH, 5));
    }
}
