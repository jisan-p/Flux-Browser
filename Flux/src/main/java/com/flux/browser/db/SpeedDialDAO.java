package com.flux.browser.db;

import com.flux.browser.model.SpeedDial;
import com.flux.browser.util.UrlResolver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SpeedDialDAO {
    private final DatabaseManager database;
    public SpeedDialDAO(DatabaseManager database) { this.database = database; }

    public CompletableFuture<List<SpeedDial>> getSpeedDials() {
        return database.read(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM speed_dial ORDER BY position, id")) {
                try (var result = statement.executeQuery()) {
                    List<SpeedDial> entries = new ArrayList<>();
                    while (result.next()) entries.add(read(result));
                    return List.copyOf(entries);
                }
            }
        });
    }

    public CompletableFuture<SpeedDial> save(long id, String title, String url) {
        String address = UrlResolver.webAddress(url);
        String name = UrlResolver.requiredText(title, "Title", 512);
        return database.write(connection -> {
            String sql = id == 0 ? """
                    INSERT INTO speed_dial (title, url, position)
                    VALUES (?, ?, (SELECT COALESCE(MAX(position), -1) + 1 FROM speed_dial))
                    ON CONFLICT (url) DO UPDATE SET title = EXCLUDED.title RETURNING *
                    """ : "UPDATE speed_dial SET title = ?, url = ? WHERE id = ? RETURNING *";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, name); statement.setString(2, address);
                if (id != 0) statement.setLong(3, id);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("This shortcut no longer exists.");
                    return read(result);
                }
            }
        });
    }

    public CompletableFuture<Void> delete(long id) {
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM speed_dial WHERE id = ?")) {
                statement.setLong(1, id); statement.executeUpdate(); return null;
            }
        });
    }

    private static SpeedDial read(ResultSet result) throws SQLException {
        return new SpeedDial(result.getLong("id"), result.getString("title"), result.getString("url"),
                result.getTimestamp("created_at").toInstant(), result.getInt("position"));
    }
}
