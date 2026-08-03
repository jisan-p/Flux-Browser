package org.custombrowser.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.custombrowser.persistence.PersistenceModels.Bookmark;
import org.custombrowser.diagnostics.PerformanceTracker;
import org.custombrowser.persistence.PersistenceModels.BrowserSession;
import org.custombrowser.persistence.PersistenceModels.Download;
import org.custombrowser.persistence.PersistenceModels.Visit;
import org.custombrowser.persistence.PersistenceModels.WindowState;
import org.custombrowser.persistence.PostgresRepositories.RepositorySet;
import org.custombrowser.ui.model.SpeedDialEntry;
import org.custombrowser.ui.state.BrowserUiState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;

/**
 * Application persistence facade. Controllers use this service and never
 * issue SQL or block the JavaFX Application Thread.
 */
public final class PersistenceService implements AutoCloseable {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PersistenceService.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final HikariDataSource dataSource;
    private final RepositorySet repositories;
    private final ScheduledExecutorService executor;
    private final StartupState startupState;
    private final boolean enabled;
    private final PerformanceTracker performanceTracker;

    private BrowserUiState boundUiState;
    private ScheduledFuture<?> pendingSettingsSave;
    private ScheduledFuture<?> pendingSpeedDialSave;
    private ScheduledFuture<?> pendingSessionSave;
    private boolean sessionPersistenceSuppressed;

    private PersistenceService(
            HikariDataSource dataSource,
            RepositorySet repositories,
            ScheduledExecutorService executor,
            StartupState startupState,
            boolean enabled,
            PerformanceTracker performanceTracker) {
        this.dataSource = dataSource;
        this.repositories = repositories;
        this.executor = executor;
        this.startupState = startupState;
        this.enabled = enabled;
        this.performanceTracker = Objects.requireNonNull(
                performanceTracker, "performanceTracker");
    }

    public static PersistenceService open(DatabaseConfig config) {
        return open(config, new PerformanceTracker());
    }

    public static PersistenceService open(
            DatabaseConfig config,
            PerformanceTracker performanceTracker) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(performanceTracker, "performanceTracker");
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setPoolName("flux-postgres");
        hikari.setMaximumPoolSize(4);
        hikari.setMinimumIdle(0);
        hikari.setConnectionTimeout(5_000);
        hikari.setValidationTimeout(3_000);
        hikari.setInitializationFailTimeout(5_000);

        HikariDataSource dataSource = null;
        try {
            dataSource = new HikariDataSource(hikari);
            validateSchema(dataSource, config);
            RepositorySet repositories =
                    PostgresRepositories.create(dataSource, config.schema());
            StartupState startup = new StartupState(
                    repositories.settings().load(),
                    repositories.speedDials().load(),
                    repositories.sessions().load(),
                    repositories.sessions().loadWindowState());
            return new PersistenceService(
                    dataSource,
                    repositories,
                    newExecutor(),
                    startup,
                    true,
                    performanceTracker);
        } catch (RuntimeException error) {
            if (dataSource != null) {
                dataSource.close();
            }
            if (error instanceof PersistenceException persistenceError) {
                throw persistenceError;
            }
            throw new PersistenceException(
                    "Flux could not connect to PostgreSQL at "
                            + config.safeDescription()
                            + ". Start it with `docker compose up -d postgres` "
                            + "and verify that the Phase 3 SQL scripts were "
                            + "initialized on a fresh volume.",
                    error);
        }
    }

    public static PersistenceService forTests() {
        return forTests(new PerformanceTracker());
    }

    public static PersistenceService forTests(
            PerformanceTracker performanceTracker) {
        RepositorySet repositories = new RepositorySet(
                new NoOpSettingsRepository(),
                new NoOpSpeedDialRepository(),
                new NoOpBookmarkRepository(),
                new NoOpVisitRepository(),
                new NoOpDownloadRepository(),
                new NoOpSessionRepository());
        return new PersistenceService(
                null,
                repositories,
                newExecutor(),
                StartupState.empty(),
                false,
                performanceTracker);
    }

    public StartupState startupState() {
        return startupState;
    }

    public boolean enabled() {
        return enabled;
    }

    public void bind(BrowserUiState uiState) {
        boundUiState = Objects.requireNonNull(uiState, "uiState");
        if (!enabled) {
            return;
        }
        InvalidationListener settingsListener = observable ->
                scheduleSettingsSave();
        uiState.accentProperty().addListener(settingsListener);
        uiState.wallpaperProperty().addListener(settingsListener);
        uiState.sidebarVisibleProperty().addListener(settingsListener);
        uiState.panelDockedProperty().addListener(settingsListener);
        uiState.reducedMotionProperty().addListener(settingsListener);
        uiState.uiScaleProperty().addListener(settingsListener);
        uiState.autoSuspendEnabledProperty().addListener(settingsListener);
        uiState.autoSuspendMinutesProperty().addListener(settingsListener);
        uiState.speedDials().addListener(
                (ListChangeListener<SpeedDialEntry>) change ->
                        scheduleSpeedDialSave());
    }

    public CompletableFuture<List<Bookmark>> bookmarks(String query) {
        return supply(() -> repositories.bookmarks().search(query, 250));
    }

    public CompletableFuture<Bookmark> addBookmark(String title, String url) {
        return supply(() -> repositories.bookmarks().add(title, url));
    }

    public CompletableFuture<Void> deleteBookmark(long id) {
        return run(() -> repositories.bookmarks().delete(id));
    }

    public CompletableFuture<Void> clearBookmarks() {
        return run(() -> repositories.bookmarks().clear());
    }

    public CompletableFuture<List<Visit>> visits(String query) {
        return supply(() -> repositories.visits().search(query, 500));
    }

    public void recordVisit(String title, String url) {
        if (enabled && url != null && !url.isBlank()) {
            run(() -> repositories.visits().record(title, url));
        }
    }

    public CompletableFuture<Void> deleteVisit(long id) {
        return run(() -> repositories.visits().delete(id));
    }

    public CompletableFuture<Void> clearVisits() {
        return run(() -> repositories.visits().clear());
    }

    public CompletableFuture<Integer> countVisitsBefore(Instant cutoff) {
        return supply(() -> repositories.visits().countBefore(cutoff));
    }

    public CompletableFuture<Integer> deleteVisitsBefore(Instant cutoff) {
        return supply(() -> repositories.visits().deleteBefore(cutoff));
    }

    public CompletableFuture<List<Download>> downloads(String query) {
        return supply(() -> repositories.downloads().search(query, 250));
    }

    public CompletableFuture<Download> createDownload(
            String sourceUrl,
            String fileName,
            String targetPath) {
        return supply(() -> repositories.downloads().create(
                sourceUrl, fileName, targetPath));
    }

    public CompletableFuture<Void> updateDownload(
            long id,
            String status,
            long bytesDownloaded,
            Long totalBytes,
            Instant completedAt,
            String failureMessage) {
        return run(() -> repositories.downloads().update(
                id,
                status,
                bytesDownloaded,
                totalBytes,
                completedAt,
                failureMessage));
    }

    public CompletableFuture<Void> saveSetting(String key, String value) {
        return run(() -> repositories.settings().save(Map.of(key, value)));
    }

    public CompletableFuture<Void> deleteSettingsByPrefix(String prefix) {
        return run(() -> repositories.settings().deleteByPrefix(prefix));
    }

    public CompletableFuture<Void> deleteDownload(long id) {
        return run(() -> repositories.downloads().delete(id));
    }

    public CompletableFuture<Void> clearDownloads() {
        return run(() -> repositories.downloads().clear());
    }

    public CompletableFuture<Integer> countCompletedDownloadsBefore(
            Instant cutoff) {
        return supply(() -> repositories.downloads()
                .countCompletedBefore(cutoff));
    }

    public CompletableFuture<Integer> deleteCompletedDownloadsBefore(
            Instant cutoff) {
        return supply(() -> repositories.downloads()
                .deleteCompletedBefore(cutoff));
    }

    public CompletableFuture<Integer> countOldSessionRecordsBefore(
            Instant cutoff) {
        return supply(() -> repositories.sessions()
                .countOldSessionRecordsBefore(cutoff));
    }

    public CompletableFuture<Integer> deleteOldSessionRecordsBefore(
            Instant cutoff) {
        return supply(() -> repositories.sessions()
                .deleteOldSessionRecordsBefore(cutoff));
    }

    public synchronized CompletableFuture<Void> clearSessionData() {
        sessionPersistenceSuppressed = true;
        if (pendingSessionSave != null) {
            pendingSessionSave.cancel(false);
            pendingSessionSave = null;
        }
        return run(() -> repositories.sessions().clearSessionData());
    }

    public synchronized void saveSession(BrowserSession session) {
        if (enabled && !sessionPersistenceSuppressed) {
            if (pendingSessionSave != null) {
                pendingSessionSave.cancel(false);
            }
            pendingSessionSave = executor.schedule(
                    () -> safely(() -> repositories.sessions().save(session)),
                    400,
                    TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void saveSessionNow(BrowserSession session) {
        if (enabled && !sessionPersistenceSuppressed) {
            if (pendingSessionSave != null) {
                pendingSessionSave.cancel(false);
                pendingSessionSave = null;
            }
            repositories.sessions().save(session);
        }
    }

    public void saveWindowStateNow(WindowState state) {
        if (enabled) {
            repositories.sessions().saveWindowState(state);
        }
    }

    @Override
    public void close() {
        try {
            if (enabled && boundUiState != null) {
                saveUiStateNow();
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(
                        SHUTDOWN_TIMEOUT.toMillis(),
                        TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException error) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            } finally {
                if (dataSource != null) {
                    dataSource.close();
                }
            }
        }
    }

    private synchronized void scheduleSettingsSave() {
        if (pendingSettingsSave != null) {
            pendingSettingsSave.cancel(false);
        }
        pendingSettingsSave = executor.schedule(
                () -> safely(() -> repositories.settings().save(
                        boundUiState.toSettingsMap())),
                250,
                TimeUnit.MILLISECONDS);
    }

    private synchronized void scheduleSpeedDialSave() {
        if (pendingSpeedDialSave != null) {
            pendingSpeedDialSave.cancel(false);
        }
        pendingSpeedDialSave = executor.schedule(
                () -> safely(() -> repositories.speedDials().replaceAll(
                        List.copyOf(boundUiState.speedDials()))),
                250,
                TimeUnit.MILLISECONDS);
    }

    private void saveUiStateNow() {
        repositories.settings().save(boundUiState.toSettingsMap());
        repositories.speedDials().replaceAll(
                List.copyOf(boundUiState.speedDials()));
    }

    private CompletableFuture<Void> run(Runnable action) {
        if (!enabled) {
            performanceTracker.measure("database.operation", action);
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(
                        () -> performanceTracker.measure(
                                "database.operation", action),
                        executor)
                .whenComplete((unused, error) -> logAsyncFailure(error));
    }

    private <T> CompletableFuture<T> supply(Supplier<T> action) {
        if (!enabled) {
            return CompletableFuture.completedFuture(performanceTracker.measure(
                    "database.operation", action));
        }
        return CompletableFuture.supplyAsync(
                        () -> performanceTracker.measure(
                                "database.operation", action),
                        executor)
                .whenComplete((unused, error) -> logAsyncFailure(error));
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            LOGGER.error("Asynchronous persistence operation failed: {}",
                    error.getMessage());
        }
    }

    private static void logAsyncFailure(Throwable error) {
        if (error != null) {
            LOGGER.error("Asynchronous persistence operation failed: {}",
                    error.getMessage());
        }
    }

    private static ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "flux-persistence");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void validateSchema(
            HikariDataSource dataSource,
            DatabaseConfig config) {
        String sql = "SELECT version FROM " + config.schema()
                + ".schema_version WHERE version = 3";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new PersistenceException(
                        "PostgreSQL is reachable, but the Flux Phase 3 schema "
                                + "is not initialized. Recreate the development "
                                + "volume so docker/postgres/init scripts run.");
            }
        } catch (SQLException error) {
            throw new PersistenceException(
                    "PostgreSQL is reachable, but the Flux Phase 3 schema "
                            + "could not be validated. Recreate the development "
                            + "volume so docker/postgres/init scripts run.",
                    error);
        }
    }

    public record StartupState(
            Map<String, String> settings,
            List<SpeedDialEntry> speedDials,
            BrowserSession session,
            WindowState windowState) {

        public StartupState {
            settings = Map.copyOf(settings);
            speedDials = List.copyOf(speedDials);
            session = Objects.requireNonNull(session, "session");
            windowState = Objects.requireNonNull(windowState, "windowState");
        }

        public static StartupState empty() {
            return new StartupState(
                    Map.of(),
                    List.of(),
                    BrowserSession.empty(),
                    WindowState.defaults());
        }
    }

    private static final class NoOpSettingsRepository
            implements SettingsRepository {
        @Override
        public Map<String, String> load() {
            return Map.of();
        }

        @Override
        public void save(Map<String, String> settings) {
        }

        @Override
        public void deleteByPrefix(String prefix) {
        }
    }

    private static final class NoOpSpeedDialRepository
            implements SpeedDialRepository {
        @Override
        public List<SpeedDialEntry> load() {
            return List.of();
        }

        @Override
        public void replaceAll(List<SpeedDialEntry> entries) {
        }
    }

    private static final class NoOpBookmarkRepository
            implements BookmarkRepository {
        @Override
        public List<Bookmark> search(String query, int limit) {
            return List.of();
        }

        @Override
        public Bookmark add(String title, String url) {
            return new Bookmark(0, title, url, "Bookmarks",
                    java.time.Instant.now());
        }

        @Override
        public void delete(long id) {
        }

        @Override
        public void clear() {
        }
    }

    private static final class NoOpVisitRepository implements VisitRepository {
        @Override
        public List<Visit> search(String query, int limit) {
            return List.of();
        }

        @Override
        public void record(String title, String url) {
        }

        @Override
        public void delete(long id) {
        }

        @Override
        public void clear() {
        }

        @Override
        public int countBefore(Instant cutoff) {
            return 0;
        }

        @Override
        public int deleteBefore(Instant cutoff) {
            return 0;
        }
    }

    private static final class NoOpDownloadRepository
            implements DownloadRepository {
        @Override
        public List<Download> search(String query, int limit) {
            return List.of();
        }

        @Override
        public Download create(
                String sourceUrl,
                String fileName,
                String targetPath) {
            return new Download(
                    0,
                    fileName,
                    sourceUrl,
                    targetPath,
                    "QUEUED",
                    0,
                    null,
                    null,
                    Instant.now());
        }

        @Override
        public void update(
                long id,
                String status,
                long bytesDownloaded,
                Long totalBytes,
                Instant completedAt,
                String failureMessage) {
        }

        @Override
        public void delete(long id) {
        }

        @Override
        public void clear() {
        }

        @Override
        public int countCompletedBefore(Instant cutoff) {
            return 0;
        }

        @Override
        public int deleteCompletedBefore(Instant cutoff) {
            return 0;
        }
    }

    private static final class NoOpSessionRepository
            implements SessionRepository {
        @Override
        public BrowserSession load() {
            return BrowserSession.empty();
        }

        @Override
        public void save(BrowserSession session) {
        }

        @Override
        public WindowState loadWindowState() {
            return WindowState.defaults();
        }

        @Override
        public void saveWindowState(WindowState state) {
        }

        @Override
        public void clearSessionData() {
        }

        @Override
        public int countOldSessionRecordsBefore(Instant cutoff) {
            return 0;
        }

        @Override
        public int deleteOldSessionRecordsBefore(Instant cutoff) {
            return 0;
        }
    }
}
