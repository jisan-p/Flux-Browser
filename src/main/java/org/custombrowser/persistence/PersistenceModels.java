package org.custombrowser.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PersistenceModels {

    private PersistenceModels() {
    }

    public record Bookmark(
            long id,
            String title,
            String url,
            String folder,
            Instant createdAt) {
        @Override
        public String toString() {
            return title + "\n" + url;
        }
    }

    public record Visit(long id, String title, String url, Instant visitedAt) {
        @Override
        public String toString() {
            return title + "\n" + url;
        }
    }

    public record Download(
            long id,
            String fileName,
            String sourceUrl,
            String status,
            Instant startedAt) {
        @Override
        public String toString() {
            return fileName + "  ·  " + status + "\n" + sourceUrl;
        }
    }

    public record StoredTab(
            UUID id,
            String url,
            String title,
            boolean pinned,
            boolean selected,
            double zoom,
            boolean startPage) {

        public StoredTab {
            id = Objects.requireNonNull(id, "id");
            title = title == null || title.isBlank() ? "New Tab" : title;
            zoom = Math.max(0.5, Math.min(2.0, zoom));
        }
    }

    public record BrowserSession(
            List<StoredTab> openTabs,
            List<StoredTab> recentlyClosed) {

        public BrowserSession {
            openTabs = List.copyOf(openTabs);
            recentlyClosed = List.copyOf(recentlyClosed);
        }

        public static BrowserSession empty() {
            return new BrowserSession(List.of(), List.of());
        }
    }

    public record WindowState(
            Double x,
            Double y,
            double width,
            double height,
            boolean maximized,
            boolean fullscreen) {

        public WindowState {
            width = Math.max(900.0, width);
            height = Math.max(640.0, height);
        }

        public static WindowState defaults() {
            return new WindowState(null, null, 1280, 800, false, false);
        }
    }
}
