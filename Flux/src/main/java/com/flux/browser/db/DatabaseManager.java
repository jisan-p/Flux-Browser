package com.flux.browser.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Ordered writes and two independent readers; JDBC never runs on a submitting UI thread. */
public final class DatabaseManager implements AutoCloseable {
    @FunctionalInterface
    public interface SqlWork<T> { T execute(Connection connection) throws SQLException; }

    private static final class Configuration {
        final String url;
        final Properties credentials;
        volatile boolean available;
        Configuration(String url, Properties credentials) { this.url = url; this.credentials = credentials; }
    }

    // Three connections at most, with bounded pending work for an 8 GB desktop.
    private final ThreadPoolExecutor writer = executor(1, "flux-jdbc-write-");
    private final ThreadPoolExecutor readers = executor(2, "flux-jdbc-read-");
    private final String explicitUrl, explicitUser, explicitPassword;
    private volatile CompletableFuture<Configuration> initialization = CompletableFuture.failedFuture(offline());
    private volatile boolean closed;

    public DatabaseManager() { explicitUrl = explicitUser = explicitPassword = null; }

    /** Explicit configuration is useful for isolated integration checks. */
    public DatabaseManager(String url, String user, String password) {
        explicitUrl = url; explicitUser = user; explicitPassword = password;
    }

    public boolean isAvailable() {
        var ready = initialization;
        return !closed && ready.isDone() && !ready.isCompletedExceptionally() && ready.getNow(null).available;
    }

    public synchronized CompletableFuture<Void> initialize() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Storage is closed"));
        try {
            var ready = CompletableFuture.supplyAsync(this::initializeStorage, writer);
            initialization = ready;
            return ready.thenApply(ignored -> null);
        } catch (RejectedExecutionException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private Configuration initializeStorage() {
        try {
            Configuration configuration = loadConfiguration();
            try (var stream = DatabaseManager.class.getResourceAsStream("/db/schema.sql")) {
                if (stream == null) throw new IOException("Missing db/schema.sql resource");
                String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                try (Connection connection = openConnection(configuration)) {
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
            configuration.available = true;
            return configuration;
        } catch (IOException | SQLException error) {
            throw new CompletionException(error);
        }
    }

    /** Reads may observe the last committed state while a write is still pending. */
    public synchronized <T> CompletableFuture<T> read(SqlWork<T> work) {
        var ready = initialization;
        return enqueue(readers, () -> {
            Configuration configuration = ready.join();
            if (closed) throw new IllegalStateException("Storage is closed");
            return execute(configuration, work, true);
        });
    }

    /** Writes retain submission order; refresh saved data after this future completes. */
    public synchronized <T> CompletableFuture<T> write(SqlWork<T> work) {
        var ready = initialization;
        return enqueue(writer, () -> execute(ready.join(), work, false));
    }

    private <T> T execute(Configuration configuration, SqlWork<T> work, boolean readOnly) {
        if (!configuration.available) throw new CompletionException(offline());
        try (Connection connection = openConnection(configuration)) {
            connection.setReadOnly(readOnly);
            return work.execute(connection);
        } catch (SQLException error) {
            String state = error.getSQLState();
            if (state == null || state.startsWith("08") || state.startsWith("57P")) configuration.available = false;
            throw new CompletionException(error);
        }
    }

    private <T> CompletableFuture<T> enqueue(ThreadPoolExecutor executor, Supplier<T> work) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Storage is closed"));
        try { return CompletableFuture.supplyAsync(work, executor); }
        catch (RejectedExecutionException error) { return CompletableFuture.failedFuture(error); }
    }

    private static ThreadPoolExecutor executor(int threads, String name) {
        var executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), Thread.ofPlatform().name(name, 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private Configuration loadConfiguration() throws IOException {
        Properties credentials = new Properties();
        String url;
        if (explicitUrl != null) {
            url = explicitUrl;
            credentials.setProperty("user", explicitUser);
            credentials.setProperty("password", explicitPassword);
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
        return new Configuration(url, credentials);
    }

    private static String setting(String env, Properties file, String key, String fallback) {
        String value = System.getenv(env);
        return value == null ? file.getProperty(key, fallback) : value;
    }

    private Connection openConnection(Configuration configuration) throws SQLException {
        try { return DriverManager.getConnection(configuration.url, configuration.credentials); }
        catch (SQLException error) { configuration.available = false; throw error; }
    }

    private static SQLException offline() { return new SQLException("Storage is offline. Reconnect in Settings."); }

    /** Accepted writes drain; queued reads are skipped without waiting on the UI thread. */
    @Override public synchronized void close() {
        closed = true;
        readers.shutdown();
        writer.shutdown();
    }
}
