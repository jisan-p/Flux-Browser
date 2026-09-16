package com.flux.browser.db;

import com.flux.browser.model.HistoryEntry;
import com.flux.browser.util.UrlResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class HistoryDAO {
    public static final int DISPLAY_LIMIT = 500;
    private final DatabaseManager database;
    public HistoryDAO(DatabaseManager database) { this.database = database; }

    public CompletableFuture<Long> saveVisit(String title, String url) {
        String address = UrlResolver.webAddress(url);
        String name = UrlResolver.pageTitle(title, address);
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("INSERT INTO history (title, url) VALUES (?, ?) RETURNING id")) {
                statement.setString(1, name);
                statement.setString(2, address);
                try (var result = statement.executeQuery()) { result.next(); return result.getLong(1); }
            }
        });
    }

    public CompletableFuture<List<HistoryEntry>> getHistory(String filter) {
        return database.read(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT id, title, url, visit_timestamp FROM history
                    WHERE position(lower(?) in lower(title || ' ' || url)) > 0
                    ORDER BY visit_timestamp DESC, id DESC LIMIT ?
                    """)) {
                statement.setString(1, filter.trim());
                statement.setInt(2, DISPLAY_LIMIT);
                try (var result = statement.executeQuery()) {
                    List<HistoryEntry> entries = new ArrayList<>();
                    while (result.next()) entries.add(new HistoryEntry(result.getLong("id"), result.getString("title"),
                            result.getString("url"), result.getTimestamp("visit_timestamp").toInstant()));
                    return List.copyOf(entries);
                }
            }
        });
    }

    public CompletableFuture<Void> deleteHistory(long id) {
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM history WHERE id = ?")) {
                statement.setLong(1, id); statement.executeUpdate(); return null;
            }
        });
    }

    public CompletableFuture<Void> deleteHistory() {
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM history")) {
                statement.executeUpdate(); return null;
            }
        });
    }
}
