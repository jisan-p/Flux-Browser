package com.flux.browser.db;

import com.flux.browser.model.HistoryEntry;
import com.flux.browser.util.UrlResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class HistoryDAO {
    public static final int DISPLAY_LIMIT = 500;
    private final DatabaseManager database;
    private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();
    public long generation() { return generation.get(); }
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

    public CompletableFuture<Void> index(String title, String url, String text) {
        String address=UrlResolver.webAddress(url), name=UrlResolver.pageTitle(title,address);
        String body=text.substring(0,Math.min(200000,text.length()));
        return database.write(c -> {
            try(var s=c.prepareStatement("INSERT INTO page_text(url,title,body) VALUES(?,?,?) ON CONFLICT(url) DO UPDATE SET title=excluded.title,body=excluded.body,captured_at=CURRENT_TIMESTAMP")){
                s.setString(1,address);s.setString(2,name);s.setString(3,body);s.executeUpdate();
            }
            try(var s=c.prepareStatement("DELETE FROM page_text WHERE url IN (SELECT url FROM page_text ORDER BY captured_at DESC OFFSET 1000)")){s.executeUpdate();}
            return null;
        });
    }
    public CompletableFuture<List<HistoryEntry>> searchText(String query) {
        return database.read(c -> {
            try(var s=c.prepareStatement("SELECT 0 AS id,title,url,captured_at FROM page_text WHERE document @@ websearch_to_tsquery('simple',?) ORDER BY captured_at DESC LIMIT 100")){
                s.setString(1,query);try(var r=s.executeQuery()){
                    var rows=new ArrayList<HistoryEntry>();while(r.next())rows.add(new HistoryEntry(0,r.getString(2),r.getString(3),r.getTimestamp(4).toInstant()));return List.copyOf(rows);
                }
            }
        });
    }
    public CompletableFuture<Void> clearIndex() { generation.incrementAndGet(); return database.write(c -> {try(var s=c.prepareStatement("DELETE FROM page_text")){s.executeUpdate();return null;}}); }

    public CompletableFuture<Void> deleteHistory(long id) {
        generation.incrementAndGet();
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM history WHERE id = ?")) {
                statement.setLong(1, id);
                try(var purge=connection.prepareStatement("DELETE FROM page_text WHERE url=(SELECT url FROM history WHERE id=?)")){purge.setLong(1,id);purge.executeUpdate();}
                statement.executeUpdate(); return null;
            }
        });
    }

    public CompletableFuture<Void> deleteHistory() {
        generation.incrementAndGet();
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM history")) {
                statement.executeUpdate();
                try(var purge=connection.prepareStatement("DELETE FROM page_text")){purge.executeUpdate();}
                return null;
            }
        });
    }
}
