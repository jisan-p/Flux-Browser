package com.flux.browser;

import com.flux.browser.db.*;
import java.util.concurrent.TimeUnit;

/** Uses only an explicitly selected disposable database named flux_test. */
public final class DatabaseChecks {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("FLUX_TEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/flux_test(?:\\?.*)?")) {
            throw new IllegalArgumentException("Set FLUX_TEST_DB_URL to a disposable PostgreSQL database named flux_test.");
        }
        String user = System.getenv().getOrDefault("FLUX_DB_USER", "flux");
        String password = System.getenv().getOrDefault("FLUX_DB_PASSWORD", "");
        try (DatabaseManager db = new DatabaseManager(url, user, password)) {
            db.initialize().get(20, TimeUnit.SECONDS);
            BrowserChecks.check(db.isAvailable(), "schema initialization");
            String thread = db.query(connection -> Thread.currentThread().getName()).get();
            BrowserChecks.equal(thread, "flux-jdbc");
            HistoryDAO history = new HistoryDAO(db);
            BookmarkDAO bookmarks = new BookmarkDAO(db);
            SpeedDialDAO dials = new SpeedDialDAO(db);
            history.deleteHistory().get();
            long first = history.saveVisit("First", "https://example.com/first").get();
            long second = history.saveVisit("Second ' quote বাংলা", "https://example.com/second").get();
            var visits = history.getHistory("").get();
            BrowserChecks.equal(visits.get(0).id(), second);
            BrowserChecks.equal(visits.get(1).id(), first);
            BrowserChecks.equal(history.getHistory("' quote").get().size(), 1);
            BrowserChecks.equal(history.getHistory("% OR 1=1").get().size(), 0);
            history.deleteHistory(first).get();
            BrowserChecks.equal(history.getHistory("").get().size(), 1);

            var bookmark = bookmarks.save(0, "A ' bookmark", "https://example.com/flux-test-bookmark", "Study").get();
            var same = bookmarks.save(0, "Updated", bookmark.url(), "Learning").get();
            BrowserChecks.equal(same.id(), bookmark.id());
            BrowserChecks.equal(same.createdAt(), bookmark.createdAt());
            var edited = bookmarks.save(same.id(), "Final title", "https://example.com/flux-test-edited", "Learning").get();
            BrowserChecks.check(bookmarks.contains(edited.url()).get(), "bookmark contains");
            BrowserChecks.equal(bookmarks.getBookmarks("Learning").get().get(0).title(), "Final title");
            history.deleteHistory().get();
            BrowserChecks.check(bookmarks.contains(edited.url()).get(), "history clear preserves bookmarks");

            var dial = dials.save(0, "Test dial", "https://example.com/flux-test-dial").get();
            var dialEdited = dials.save(dial.id(), "Edited dial", "https://example.com/flux-test-dial-edited").get();
            BrowserChecks.equal(dialEdited.position(), dial.position());
            BrowserChecks.equal(dialEdited.createdAt(), dial.createdAt());
            dials.delete(dial.id()).get();
            int count = dials.getSpeedDials().get().size();
            var existing = dials.getSpeedDials().get();
            if (!existing.isEmpty()) {
                var seed = existing.get(0);
                dials.delete(seed.id()).get();
                db.initialize().get();
                BrowserChecks.equal(dials.getSpeedDials().get().size(), count - 1);
                dials.save(0, seed.title(), seed.url()).get();
            }
            // Re-open through a separate manager to verify committed persistence.
            try (DatabaseManager reopened = new DatabaseManager(url, user, password)) {
                reopened.initialize().get();
                BrowserChecks.check(new BookmarkDAO(reopened).contains(edited.url()).get(), "bookmark persists across connections/reinitialization");
            }
            bookmarks.deleteByUrl(edited.url()).get();
            BrowserChecks.check(!bookmarks.contains(edited.url()).get(), "bookmark deletion");
            BrowserChecks.equal(history.getHistory("").get().size(), 0);
        }
        try (DatabaseManager offline = new DatabaseManager("jdbc:postgresql://127.0.0.1:1/flux_test", user, password)) {
            try { offline.initialize().get(12, TimeUnit.SECONDS); throw new AssertionError("Closed port must fail"); }
            catch (java.util.concurrent.ExecutionException expected) { BrowserChecks.check(!offline.isAvailable(), "offline status"); }
        }
        System.out.println("DatabaseChecks passed: async JDBC, CRUD, ordering, literal search, persistence, idempotent schema, and offline failure.");
    }
}
