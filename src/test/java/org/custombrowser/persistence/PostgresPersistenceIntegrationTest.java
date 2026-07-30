package org.custombrowser.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.custombrowser.persistence.PersistenceModels.BrowserSession;
import org.custombrowser.persistence.PersistenceModels.StoredTab;
import org.custombrowser.persistence.PersistenceModels.WindowState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.BindMode;

@EnabledIfEnvironmentVariable(
        named = "FLUX_RUN_DB_TESTS",
        matches = "(?i)true")
class PostgresPersistenceIntegrationTest {

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("flux_browser")
                .withUsername("postgres")
                .withPassword("1234")
                .withFileSystemBind(
                        Path.of("docker/postgres/init")
                                .toAbsolutePath()
                                .toString(),
                        "/docker-entrypoint-initdb.d",
                        BindMode.READ_ONLY)
                .withStartupTimeout(Duration.ofMinutes(2));
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void initializesAndRoundTripsCoreBrowserData() {
        DatabaseConfig config = new DatabaseConfig(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                "flux_browser");

        UUID tabId = UUID.randomUUID();
        try (PersistenceService persistence = PersistenceService.open(config)) {
            assertEquals(6, persistence.startupState().speedDials().size());
            assertTrue(persistence.startupState().settings().containsKey("accent"));
            assertFalse(persistence.startupState().windowState().maximized());

            persistence.addBookmark(
                            "Example",
                            "https://example.com")
                    .join();
            assertEquals(1, persistence.bookmarks("example").join().size());

            persistence.clearBookmarks().join();
            assertTrue(persistence.bookmarks("").join().isEmpty());

            persistence.recordVisit("Example", "https://example.com");
            assertEquals(1, persistence.visits("example").join().size());

            persistence.saveSessionNow(new BrowserSession(
                    List.of(new StoredTab(
                            tabId,
                            "https://example.com",
                            "Example",
                            true,
                            true,
                            1.25,
                            false)),
                    List.of()));
            persistence.saveWindowStateNow(new WindowState(
                    40.0,
                    60.0,
                    1200,
                    760,
                    false,
                    false));
        }

        try (PersistenceService restored = PersistenceService.open(config)) {
            assertEquals(
                    tabId,
                    restored.startupState().session()
                            .openTabs().getFirst().id());
            assertEquals(
                    1.25,
                    restored.startupState().session()
                            .openTabs().getFirst().zoom());
            assertEquals(1200, restored.startupState().windowState().width());
        }
    }
}
