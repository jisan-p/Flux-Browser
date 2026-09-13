package com.flux.browser.db;

import com.flux.browser.model.Bookmark;
import com.flux.browser.util.UrlResolver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BookmarkDAO {
    private final DatabaseManager database;
    public BookmarkDAO(DatabaseManager database) { this.database = database; }

    public CompletableFuture<List<Bookmark>> getBookmarks() { return getBookmarks(""); }

    public CompletableFuture<List<Bookmark>> getBookmarks(String filter) {
        return database.query(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT * FROM bookmarks WHERE position(lower(?) in lower(title || ' ' || url || ' ' || category)) > 0
                    ORDER BY created_at DESC, id DESC LIMIT 500
                    """)) {
                statement.setString(1, filter.trim());
                try (var result = statement.executeQuery()) {
                    List<Bookmark> entries = new ArrayList<>();
                    while (result.next()) entries.add(read(result));
                    return List.copyOf(entries);
                }
            }
        });
    }

    public CompletableFuture<Boolean> contains(String url) {
        return database.query(connection -> {
            try (var statement = connection.prepareStatement("SELECT 1 FROM bookmarks WHERE url = ?")) {
                statement.setString(1, url);
                try (var result = statement.executeQuery()) { return result.next(); }
            }
        });
    }

    /** New bookmarks upsert by URL; edits keep the original identity and creation timestamp. */
    public CompletableFuture<Bookmark> save(long id, String title, String url, String category) {
        String address = UrlResolver.webAddress(url);
        String name = UrlResolver.requiredText(title, "Title", 512);
        String folder = UrlResolver.requiredText(category.isBlank() ? "Unsorted" : category, "Folder", 80);
        return database.query(connection -> {
            String sql = id == 0 ? """
                    INSERT INTO bookmarks (title, url, category) VALUES (?, ?, ?)
                    ON CONFLICT (url) DO UPDATE SET title = EXCLUDED.title, category = EXCLUDED.category RETURNING *
                    """ : "UPDATE bookmarks SET title = ?, url = ?, category = ? WHERE id = ? RETURNING *";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, name); statement.setString(2, address); statement.setString(3, folder);
                if (id != 0) statement.setLong(4, id);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("This bookmark no longer exists.");
                    return read(result);
                }
            }
        });
    }

    public CompletableFuture<Void> delete(long id) {
        return database.query(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM bookmarks WHERE id = ?")) {
                statement.setLong(1, id); statement.executeUpdate(); return null;
            }
        });
    }

    public CompletableFuture<Void> deleteByUrl(String url) {
        return database.query(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM bookmarks WHERE url = ?")) {
                statement.setString(1, url); statement.executeUpdate(); return null;
            }
        });
    }

    private static Bookmark read(ResultSet result) throws SQLException {
        return new Bookmark(result.getLong("id"), result.getString("title"), result.getString("url"),
                result.getTimestamp("created_at").toInstant(), result.getString("category"));
    }
}
