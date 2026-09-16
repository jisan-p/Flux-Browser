package com.flux.browser;

import com.flux.browser.db.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
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
            var initialization = db.initialize();
            BrowserChecks.check(db.read(connection -> connection.isValid(1)).get(20, TimeUnit.SECONDS), "reads wait for initialization");
            initialization.get(20, TimeUnit.SECONDS);
            BrowserChecks.check(db.isAvailable(), "schema initialization");
            BrowserChecks.check(db.read(connection -> Thread.currentThread().getName()).get().startsWith("flux-jdbc-read-"), "background reader");
            BrowserChecks.check(db.write(connection -> Thread.currentThread().getName()).get().startsWith("flux-jdbc-write-"), "background writer");
            checkConcurrency(db);
            checkReconnect(db);
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
        checkShutdown(url, user, password);
        try (DatabaseManager offline = new DatabaseManager("jdbc:postgresql://127.0.0.1:1/flux_test", user, password)) {
            try { offline.initialize().get(12, TimeUnit.SECONDS); throw new AssertionError("Closed port must fail"); }
            catch (java.util.concurrent.ExecutionException expected) { BrowserChecks.check(!offline.isAvailable(), "offline status"); }
        }
        System.out.println("DatabaseChecks passed: bounded concurrent JDBC, ordered writes, shutdown drain, CRUD, literal search, persistence, idempotent schema, and offline failure.");
    }

    private static void checkConcurrency(DatabaseManager db) throws Exception {
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var firstRead = db.read(connection -> { started.countDown(); await(release); return null; });
        var secondRead = db.read(connection -> { started.countDown(); await(release); return null; });
        var queued = new ArrayList<CompletableFuture<Void>>();
        try {
            BrowserChecks.check(started.await(5, TimeUnit.SECONDS), "two reads execute concurrently");
            BrowserChecks.check(db.write(connection -> connection.isValid(1)).get(2, TimeUnit.SECONDS), "slow reads do not stall writes");
            for (int i = 0; i < 64; i++) queued.add(db.read(connection -> null));
            failsWith(db.read(connection -> null), RejectedExecutionException.class);
            BrowserChecks.check(db.isAvailable(), "overload does not mark storage offline");
        } finally {
            release.countDown();
        }
        firstRead.get(5, TimeUnit.SECONDS);
        secondRead.get(5, TimeUnit.SECONDS);
        CompletableFuture.allOf(queued.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

        var writeStarted = new CountDownLatch(1);
        var releaseWrite = new CountDownLatch(1);
        var slowWrite = db.write(connection -> { writeStarted.countDown(); await(releaseWrite); return null; });
        try {
            BrowserChecks.check(writeStarted.await(5, TimeUnit.SECONDS), "write started");
            BrowserChecks.check(db.read(connection -> connection.isValid(1)).get(2, TimeUnit.SECONDS), "slow writes do not stall reads");
        } finally { releaseWrite.countDown(); }
        slowWrite.get(5, TimeUnit.SECONDS);
    }

    private static void checkShutdown(String url, String user, String password) throws Exception {
        var db = new DatabaseManager(url, user, password);
        var started = new CountDownLatch(3);
        var release = new CountDownLatch(1);
        var order = new ArrayList<Integer>();
        try {
            db.initialize().get(20, TimeUnit.SECONDS);
            var first = db.write(connection -> { started.countDown(); await(release); order.add(1); return null; });
            var firstRead = db.read(connection -> { started.countDown(); await(release); return null; });
            var secondRead = db.read(connection -> { started.countDown(); await(release); return null; });
            BrowserChecks.check(started.await(5, TimeUnit.SECONDS), "shutdown write started");
            var second = db.write(connection -> { order.add(2); return null; });
            var queuedRead = db.read(connection -> { throw new AssertionError("Queued read must be skipped on close"); });
            db.close();
            BrowserChecks.check(!first.isDone() && !second.isDone(), "close returns without waiting for writes");
            BrowserChecks.check(!db.isAvailable(), "closed storage status");
            failsWith(db.read(connection -> null), IllegalStateException.class);
            failsWith(db.write(connection -> null), IllegalStateException.class);
            failsWith(db.initialize(), IllegalStateException.class);
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            firstRead.get(5, TimeUnit.SECONDS);
            secondRead.get(5, TimeUnit.SECONDS);
            failsWith(queuedRead, IllegalStateException.class);
            BrowserChecks.equal(order, java.util.List.of(1, 2));
        } finally { release.countDown(); db.close(); }
    }

    private static void checkReconnect(DatabaseManager db) throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var oldRead = db.read(connection -> {
            started.countDown(); await(release);
            throw new SQLException("Simulated old connection failure", "08006");
        });
        try {
            BrowserChecks.check(started.await(5, TimeUnit.SECONDS), "old read started before reconnect");
            var reconnect = db.initialize();
            BrowserChecks.check(db.read(connection -> connection.isValid(1)).get(20, TimeUnit.SECONDS), "reads wait for reconnect");
            reconnect.get(20, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        failsWith(oldRead, SQLException.class);
        BrowserChecks.check(db.isAvailable(), "failure on old configuration does not break reconnected storage");
    }

    private static void await(CountDownLatch latch) throws SQLException {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new SQLException("Concurrency check timed out", "57014");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new SQLException("Concurrency check interrupted", "57014", error);
        }
    }

    private static void failsWith(CompletableFuture<?> future, Class<? extends Throwable> type) throws Exception {
        try { future.get(2, TimeUnit.SECONDS); throw new AssertionError("Expected " + type.getSimpleName()); }
        catch (ExecutionException expected) { BrowserChecks.check(type.isInstance(expected.getCause()), "expected " + type.getSimpleName()); }
    }
}
