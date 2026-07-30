package org.custombrowser.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class DatabaseConfigTest {

    @Test
    void usesDocumentedDevelopmentDefaults() {
        DatabaseConfig config = DatabaseConfig.fromEnvironment(Map.of());

        assertEquals(
                "jdbc:postgresql://localhost:5432/flux_browser",
                config.jdbcUrl());
        assertEquals("postgres", config.username());
        assertEquals("1234", config.password());
        assertEquals("flux_browser", config.schema());
    }

    @Test
    void environmentOverridesEveryValue() {
        DatabaseConfig config = DatabaseConfig.fromEnvironment(Map.of(
                "FLUX_DB_URL", "jdbc:postgresql://db:5432/custom",
                "FLUX_DB_USER", "flux",
                "FLUX_DB_PASSWORD", "secret",
                "FLUX_DB_SCHEMA", "custom_schema"));

        assertEquals("jdbc:postgresql://db:5432/custom", config.jdbcUrl());
        assertEquals("flux", config.username());
        assertEquals("secret", config.password());
        assertEquals("custom_schema", config.schema());
    }

    @Test
    void rejectsUnsafeSchemaIdentifiers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseConfig(
                        "jdbc:postgresql://localhost:5432/flux_browser",
                        "postgres",
                        "1234",
                        "flux_browser; DROP SCHEMA public"));
    }

    @Test
    void safeDescriptionNeverIncludesPassword() {
        DatabaseConfig config = new DatabaseConfig(
                "jdbc:postgresql://localhost:5432/flux_browser",
                "postgres",
                "top-secret",
                "flux_browser");

        assertFalse(config.safeDescription().contains("top-secret"));
    }
}
