package com.flux.browser.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** All configuration/schema/connection/query I/O runs on one ordered background queue. */
public final class DatabaseManager implements AutoCloseable {
    @FunctionalInterface
    public interface SqlWork<T> { T execute(Connection connection) throws SQLException; }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("flux-jdbc").factory());
    private final String explicitUrl;
    private final Properties credentials = new Properties();
    private String url;
    private volatile boolean available;

    public DatabaseManager() { explicitUrl = null; }

    /** Explicit configuration is useful for isolated integration checks. */
    public DatabaseManager(String url, String user, String password) {
        explicitUrl = url;
        credentials.setProperty("user", user);
        credentials.setProperty("password", password);
    }

    public boolean isAvailable() { return available; }

    public CompletableFuture<Void> initialize() {
        return enqueue(() -> {
            available = false;
            try {
                loadConfiguration();
                try (var stream = DatabaseManager.class.getResourceAsStream("/db/schema.sql")) {
                    if (stream == null) throw new IOException("Missing db/schema.sql resource");
                    String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    try (Connection connection = openConnection()) {
                        connection.setAutoCommit(false);
                        try (var statement = connection.createStatement()) {
                            statement.setQueryTimeout(15);
                            // pgJDBC accepts the complete script, including the DO block.
                            statement.execute(schema);
                            connection.commit();
                        } catch (SQLException error) {
                            connection.rollback();
                            throw error;
                        }
                    }
                }
                available = true;
                return null;
            } catch (IOException | SQLException error) {
                throw new CompletionException(error);
            }
        });
    }

    public <T> CompletableFuture<T> query(SqlWork<T> work) {
        return enqueue(() -> {
            if (!available) throw new CompletionException(new SQLException("Storage is offline. Reconnect in Settings."));
            try (Connection connection = openConnection()) {
                return work.execute(connection);
            } catch (SQLException error) {
                String state = error.getSQLState();
                if (state == null || state.startsWith("08") || state.startsWith("57P")) available = false;
                throw new CompletionException(error);
            }
        });
    }

    private <T> CompletableFuture<T> enqueue(Supplier<T> work) {
        if (worker.isShutdown()) return CompletableFuture.failedFuture(new IllegalStateException("Storage is closed"));
        return CompletableFuture.supplyAsync(work, worker);
    }

    private void loadConfiguration() throws IOException {
        if (explicitUrl != null) {
            url = explicitUrl;
        } else {
            Properties file = new Properties();
            Path path = Path.of("config", "database.properties");
            if (Files.exists(path)) {
                try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { file.load(reader); }
            }
            url = setting("FLUX_DB_URL", file, "url", "jdbc:postgresql://localhost:5432/flux");
            credentials.setProperty("user", setting("FLUX_DB_USER", file, "user", "flux"));
            credentials.setProperty("password", setting("FLUX_DB_PASSWORD", file, "password", ""));
        }
        if (!url.startsWith("jdbc:postgresql:")) throw new IOException("FLUX_DB_URL must be a PostgreSQL JDBC URL");
        credentials.setProperty("connectTimeout", "3");
        credentials.setProperty("loginTimeout", "5");
        credentials.setProperty("socketTimeout", "10");
        credentials.setProperty("ApplicationName", "Flux Browser");
        credentials.setProperty("options", "-c statement_timeout=10000");
    }

    private static String setting(String env, Properties file, String key, String fallback) {
        String value = System.getenv(env);
        return value == null ? file.getProperty(key, fallback) : value;
    }

    private Connection openConnection() throws SQLException {
        try { return DriverManager.getConnection(url, credentials); }
        catch (SQLException error) { available = false; throw error; }
    }

    /** Accepted writes drain without waiting on the JavaFX Application Thread. */
    @Override public void close() { worker.shutdown(); }
}
