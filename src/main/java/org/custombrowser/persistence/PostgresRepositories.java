package org.custombrowser.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.custombrowser.persistence.PersistenceModels.Bookmark;
import org.custombrowser.persistence.PersistenceModels.BrowserSession;
import org.custombrowser.persistence.PersistenceModels.Download;
import org.custombrowser.persistence.PersistenceModels.StoredTab;
import org.custombrowser.persistence.PersistenceModels.Visit;
import org.custombrowser.persistence.PersistenceModels.WindowState;
import org.custombrowser.ui.model.SpeedDialEntry;

/**
 * JDBC repository implementations. Database objects are defined exclusively by
 * the numbered SQL initialization scripts.
 */
final class PostgresRepositories {

    private PostgresRepositories() {
    }

    static RepositorySet create(DataSource dataSource, String schema) {
        String prefix = schema + ".";
        return new RepositorySet(
                new JdbcSettingsRepository(dataSource, prefix),
                new JdbcSpeedDialRepository(dataSource, prefix),
                new JdbcBookmarkRepository(dataSource, prefix),
                new JdbcVisitRepository(dataSource, prefix),
                new JdbcDownloadRepository(dataSource, prefix),
                new JdbcSessionRepository(dataSource, prefix));
    }

    record RepositorySet(
            SettingsRepository settings,
            SpeedDialRepository speedDials,
            BookmarkRepository bookmarks,
            VisitRepository visits,
            DownloadRepository downloads,
            SessionRepository sessions) {
    }

    private abstract static class JdbcRepository {

        final DataSource dataSource;
        final String prefix;

        JdbcRepository(DataSource dataSource, String prefix) {
            this.dataSource = dataSource;
            this.prefix = prefix;
        }

        PersistenceException failure(String operation, SQLException error) {
            return new PersistenceException(
                    "PostgreSQL " + operation + " failed: " + error.getMessage(),
                    error);
        }

        void deleteId(
                String table,
                String idColumn,
                long id,
                String operation) {
            String sql = "DELETE FROM " + prefix + table
                    + " WHERE " + idColumn + " = ?";
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            } catch (SQLException error) {
                throw failure(operation, error);
            }
        }

        void deleteAll(String table, String operation) {
            String sql = "DELETE FROM " + prefix + table;
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            } catch (SQLException error) {
                throw failure(operation, error);
            }
        }
    }

    private static final class JdbcSettingsRepository
            extends JdbcRepository implements SettingsRepository {

        JdbcSettingsRepository(DataSource dataSource, String prefix) {
            super(dataSource, prefix);
        }

        @Override
        public Map<String, String> load() {
            String sql = "SELECT setting_key, setting_value FROM "
                    + prefix + "settings";
            Map<String, String> values = new LinkedHashMap<>();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql);
                    ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    values.put(results.getString(1), results.getString(2));
                }
                return values;
            } catch (SQLException error) {
                throw failure("settings load", error);
            }
        }

        @Override
        public void save(Map<String, String> settings) {
            String sql = """
                    INSERT INTO %ssettings (
                        setting_key,
                        setting_value,
                        updated_at
                    )
                    VALUES (?, ?, now())
                    ON CONFLICT (setting_key) DO UPDATE
                    SET setting_value = excluded.setting_value,
                        updated_at = now()
                    """.formatted(prefix);
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Map.Entry<String, String> entry : settings.entrySet()) {
                    statement.setString(1, entry.getKey());
                    statement.setString(2, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException error) {
                throw failure("settings save", error);
            }
        }
    }

    private static final class JdbcSpeedDialRepository
            extends JdbcRepository implements SpeedDialRepository {

        JdbcSpeedDialRepository(DataSource dataSource, String prefix) {
            super(dataSource, prefix);
        }

        @Override
        public List<SpeedDialEntry> load() {
            String sql = "SELECT title, url FROM "
                    + prefix + "speed_dial_entries ORDER BY position";
            List<SpeedDialEntry> entries = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql);
                    ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    entries.add(new SpeedDialEntry(
                            results.getString("title"),
                            results.getString("url")));
                }
                return entries;
            } catch (SQLException error) {
                throw failure("Speed Dial load", error);
            }
        }

        @Override
        public void replaceAll(List<SpeedDialEntry> entries) {
            String deleteSql = "DELETE FROM " + prefix + "speed_dial_entries";
            String insertSql = """
                    INSERT INTO %sspeed_dial_entries (
                        speed_dial_id,
                        title,
                        url,
                        position,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, now())
                    """.formatted(prefix);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (Statement delete = connection.createStatement();
                        PreparedStatement insert =
                                connection.prepareStatement(insertSql)) {
                    delete.executeUpdate(deleteSql);
                    for (int index = 0; index < entries.size(); index++) {
                        SpeedDialEntry entry = entries.get(index);
                        insert.setObject(1, UUID.randomUUID());
                        insert.setString(2, truncate(entry.title(), 160));
                        insert.setString(3, entry.address());
                        insert.setInt(4, index);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                    connection.commit();
                } catch (SQLException error) {
                    connection.rollback();
                    throw error;
                }
            } catch (SQLException error) {
                throw failure("Speed Dial save", error);
            }
        }
    }

    private static final class JdbcBookmarkRepository
            extends JdbcRepository implements BookmarkRepository {

        JdbcBookmarkRepository(DataSource dataSource, String prefix) {
            super(dataSource, prefix);
        }

        @Override
        public List<Bookmark> search(String query, int limit) {
            String sql = """
                    SELECT b.bookmark_id,
                           b.title,
                           b.url,
                           COALESCE(f.name, 'Bookmarks') AS folder_name,
                           b.created_at
                    FROM %sbookmarks b
                    LEFT JOIN %sbookmark_folders f
                      ON f.folder_id = b.folder_id
                    WHERE lower(b.title) LIKE ?
                       OR lower(b.url) LIKE ?
                    ORDER BY b.created_at DESC
                    LIMIT ?
                    """.formatted(prefix, prefix);
            String pattern = pattern(query);
            List<Bookmark> bookmarks = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, pattern);
                statement.setString(2, pattern);
                statement.setInt(3, boundedLimit(limit));
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        bookmarks.add(new Bookmark(
                                results.getLong("bookmark_id"),
                                results.getString("title"),
                                results.getString("url"),
                                results.getString("folder_name"),
                                results.getTimestamp("created_at").toInstant()));
                    }
                }
                return bookmarks;
            } catch (SQLException error) {
                throw failure("bookmark search", error);
            }
        }

        @Override
        public Bookmark add(String title, String url) {
            String sql = """
                    INSERT INTO %sbookmarks (
                        folder_id,
                        title,
                        url,
                        position
                    )
                    VALUES (
                        (SELECT folder_id
                           FROM %sbookmark_folders
                          ORDER BY position
                          LIMIT 1),
                        ?,
                        ?,
                        COALESCE((SELECT max(position) + 1
                                    FROM %sbookmarks), 0)
                    )
                    RETURNING bookmark_id, title, url, created_at
                    """.formatted(prefix, prefix, prefix);
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, truncate(normalizedTitle(title, url), 500));
                statement.setString(2, url);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return new Bookmark(
                            result.getLong("bookmark_id"),
                            result.getString("title"),
                            result.getString("url"),
                            "Bookmarks",
                            result.getTimestamp("created_at").toInstant());
                }
            } catch (SQLException error) {
                throw failure("bookmark creation", error);
            }
        }

        @Override
        public void delete(long id) {
            updateById("bookmarks", "bookmark_id", id, "bookmark deletion");
        }

        @Override
        public void clear() {
            clearTable("bookmarks", "bookmark clear");
        }

        private void updateById(
                String table,
                String column,
                long id,
                String operation) {
            String sql = "DELETE FROM " + prefix + table + " WHERE " + column + " = ?";
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            } catch (SQLException error) {
                throw failure(operation, error);
            }
        }

        private void clearTable(String table, String operation) {
            String sql = "DELETE FROM " + prefix + table;
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            } catch (SQLException error) {
                throw failure(operation, error);
            }
        }
    }

    private static final class JdbcVisitRepository
            extends JdbcRepository implements VisitRepository {

        JdbcVisitRepository(DataSource dataSource, String prefix) {
            super(dataSource, prefix);
        }

        @Override
        public List<Visit> search(String query, int limit) {
            String sql = """
                    SELECT visit_id, title, url, visited_at
                    FROM %svisits
                    WHERE lower(title) LIKE ? OR lower(url) LIKE ?
                    ORDER BY visited_at DESC
                    LIMIT ?
                    """.formatted(prefix);
            String pattern = pattern(query);
            List<Visit> visits = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, pattern);
                statement.setString(2, pattern);
                statement.setInt(3, boundedLimit(limit));
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        visits.add(new Visit(
                                results.getLong("visit_id"),
                                results.getString("title"),
                                results.getString("url"),
                                results.getTimestamp("visited_at").toInstant()));
                    }
                }
                return visits;
            } catch (SQLException error) {
                throw failure("history search", error);
            }
        }

        @Override
        public void record(String title, String url) {
            String sql = "INSERT INTO " + prefix
                    + "visits (title, url) VALUES (?, ?)";
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, truncate(normalizedTitle(title, url), 500));
                statement.setString(2, url);
                statement.executeUpdate();
            } catch (SQLException error) {
                throw failure("history write", error);
            }
        }

        @Override
        public void delete(long id) {
            deleteId("visits", "visit_id", id, "history deletion");
        }

        @Override
        public void clear() {
            deleteAll("visits", "history clear");
        }
    }

    private static final class JdbcDownloadRepository
            extends JdbcRepository implements DownloadRepository {

        JdbcDownloadRepository(DataSource dataSource, String prefix) {
            super(dataSource, prefix);
        }

        @Override
        public List<Download> search(String query, int limit) {
            String sql = """
                    SELECT download_id,
                           file_name,
                           source_url,
                           status,
                           started_at
                    FROM %sdownloads
                    WHERE lower(file_name) LIKE ?
                       OR lower(source_url) LIKE ?
                       OR lower(status) LIKE ?
                    ORDER BY started_at DESC
                    LIMIT ?
                    """.formatted(prefix);
            String pattern = pattern(query);
            List<Download> downloads = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, pattern);
                statement.setString(2, pattern);
                statement.setString(3, pattern);
                statement.setInt(4, boundedLimit(limit));
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        downloads.add(new Download(
                                results.getLong("download_id"),
                                results.getString("file_name"),
                                results.getString("source_url"),
                                results.getString("status"),
                                results.getTimestamp("started_at").toInstant()));
                    }
                }
                return downloads;
            } catch (SQLException error) {
                throw failure("download search", error);
            }
        }

        @Override
        public void delete(long id) {
            deleteId("downloads", "download_id", id, "download deletion");
        }

        @Override
        public void clear() {
            deleteAll("downloads", "download clear");
        }
    }

    private static final class JdbcSessionRepository
            extends JdbcRepository implements SessionRepository {

        JdbcSessionRepository(DataSource dataSource, String prefix) {
            super(dataSource, prefix);
        }

        @Override
        public BrowserSession load() {
            String sessionSql = "SELECT session_id FROM " + prefix
                    + "browser_sessions ORDER BY saved_at DESC LIMIT 1";
            String tabsSql = """
                    SELECT tab_id,
                           url,
                           title,
                           pinned,
                           selected,
                           zoom,
                           start_page
                    FROM %ssession_tabs
                    WHERE session_id = ?
                    ORDER BY position
                    """.formatted(prefix);
            String closedSql = """
                    SELECT url, title, pinned, zoom, start_page
                    FROM %srecently_closed_tabs
                    ORDER BY closed_at DESC
                    LIMIT 20
                    """.formatted(prefix);
            try (Connection connection = dataSource.getConnection()) {
                List<StoredTab> openTabs = new ArrayList<>();
                try (PreparedStatement sessionStatement =
                                connection.prepareStatement(sessionSql);
                        ResultSet sessionResult = sessionStatement.executeQuery()) {
                    if (sessionResult.next()) {
                        UUID sessionId = sessionResult.getObject(1, UUID.class);
                        try (PreparedStatement tabsStatement =
                                connection.prepareStatement(tabsSql)) {
                            tabsStatement.setObject(1, sessionId);
                            try (ResultSet results = tabsStatement.executeQuery()) {
                                while (results.next()) {
                                    openTabs.add(storedTab(results, true));
                                }
                            }
                        }
                    }
                }

                List<StoredTab> closedTabs = new ArrayList<>();
                try (PreparedStatement statement =
                                connection.prepareStatement(closedSql);
                        ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        closedTabs.add(storedTab(results, false));
                    }
                }
                return new BrowserSession(openTabs, closedTabs);
            } catch (SQLException error) {
                throw failure("session load", error);
            }
        }

        @Override
        public void save(BrowserSession session) {
            String deleteSessions = "DELETE FROM " + prefix + "browser_sessions";
            String insertSession = "INSERT INTO " + prefix
                    + "browser_sessions (session_id) VALUES (?)";
            String insertTab = """
                    INSERT INTO %ssession_tabs (
                        tab_id,
                        session_id,
                        position,
                        url,
                        title,
                        pinned,
                        selected,
                        zoom,
                        start_page
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.formatted(prefix);
            String deleteClosed = "DELETE FROM " + prefix + "recently_closed_tabs";
            String insertClosed = """
                    INSERT INTO %srecently_closed_tabs (
                        url,
                        title,
                        pinned,
                        zoom,
                        start_page
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """.formatted(prefix);
            UUID sessionId = UUID.randomUUID();
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (Statement delete = connection.createStatement();
                        PreparedStatement sessionStatement =
                                connection.prepareStatement(insertSession);
                        PreparedStatement tabStatement =
                                connection.prepareStatement(insertTab);
                        PreparedStatement closedStatement =
                                connection.prepareStatement(insertClosed)) {
                    delete.executeUpdate(deleteSessions);
                    sessionStatement.setObject(1, sessionId);
                    sessionStatement.executeUpdate();

                    for (int index = 0;
                            index < session.openTabs().size();
                            index++) {
                        StoredTab tab = session.openTabs().get(index);
                        tabStatement.setObject(1, tab.id());
                        tabStatement.setObject(2, sessionId);
                        tabStatement.setInt(3, index);
                        tabStatement.setString(4, tab.url());
                        tabStatement.setString(5, truncate(tab.title(), 500));
                        tabStatement.setBoolean(6, tab.pinned());
                        tabStatement.setBoolean(7, tab.selected());
                        tabStatement.setDouble(8, tab.zoom());
                        tabStatement.setBoolean(9, tab.startPage());
                        tabStatement.addBatch();
                    }
                    tabStatement.executeBatch();

                    delete.executeUpdate(deleteClosed);
                    for (StoredTab tab : session.recentlyClosed()) {
                        closedStatement.setString(1, tab.url());
                        closedStatement.setString(2, truncate(tab.title(), 500));
                        closedStatement.setBoolean(3, tab.pinned());
                        closedStatement.setDouble(4, tab.zoom());
                        closedStatement.setBoolean(5, tab.startPage());
                        closedStatement.addBatch();
                    }
                    closedStatement.executeBatch();
                    connection.commit();
                } catch (SQLException error) {
                    connection.rollback();
                    throw error;
                }
            } catch (SQLException error) {
                throw failure("session save", error);
            }
        }

        @Override
        public WindowState loadWindowState() {
            String sql = """
                    SELECT x, y, width, height, maximized, fullscreen
                    FROM %swindow_state
                    WHERE singleton
                    """.formatted(prefix);
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql);
                    ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return WindowState.defaults();
                }
                return new WindowState(
                        nullableDouble(result, "x"),
                        nullableDouble(result, "y"),
                        result.getDouble("width"),
                        result.getDouble("height"),
                        result.getBoolean("maximized"),
                        result.getBoolean("fullscreen"));
            } catch (SQLException error) {
                throw failure("window state load", error);
            }
        }

        @Override
        public void saveWindowState(WindowState state) {
            String sql = """
                    INSERT INTO %swindow_state (
                        singleton,
                        x,
                        y,
                        width,
                        height,
                        maximized,
                        fullscreen,
                        updated_at
                    )
                    VALUES (true, ?, ?, ?, ?, ?, ?, now())
                    ON CONFLICT (singleton) DO UPDATE
                    SET x = excluded.x,
                        y = excluded.y,
                        width = excluded.width,
                        height = excluded.height,
                        maximized = excluded.maximized,
                        fullscreen = excluded.fullscreen,
                        updated_at = now()
                    """.formatted(prefix);
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                setNullableDouble(statement, 1, state.x());
                setNullableDouble(statement, 2, state.y());
                statement.setDouble(3, state.width());
                statement.setDouble(4, state.height());
                statement.setBoolean(5, state.maximized());
                statement.setBoolean(6, state.fullscreen());
                statement.executeUpdate();
            } catch (SQLException error) {
                throw failure("window state save", error);
            }
        }

        private static StoredTab storedTab(
                ResultSet results,
                boolean hasId) throws SQLException {
            return new StoredTab(
                    hasId
                            ? results.getObject("tab_id", UUID.class)
                            : UUID.randomUUID(),
                    results.getString("url"),
                    results.getString("title"),
                    results.getBoolean("pinned"),
                    hasId && results.getBoolean("selected"),
                    results.getDouble("zoom"),
                    results.getBoolean("start_page"));
        }
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private static String pattern(String query) {
        String normalized = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);
        return "%" + normalized + "%";
    }

    private static String normalizedTitle(String title, String url) {
        return title == null || title.isBlank() ? url : title.trim();
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }

    private static Double nullableDouble(
            ResultSet result,
            String column) throws SQLException {
        double value = result.getDouble(column);
        return result.wasNull() ? null : value;
    }

    private static void setNullableDouble(
            PreparedStatement statement,
            int index,
            Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }
    }
}
