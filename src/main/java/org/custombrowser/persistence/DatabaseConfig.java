package org.custombrowser.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PostgreSQL connection settings loaded from the process environment.
 */
public record DatabaseConfig(
        String jdbcUrl,
        String username,
        String password,
        String schema) {

    private static final Pattern SQL_IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public DatabaseConfig {
        jdbcUrl = requireText(jdbcUrl, "jdbcUrl");
        username = requireText(username, "username");
        password = Objects.requireNonNull(password, "password");
        schema = requireText(schema, "schema");
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException(
                    "FLUX_DB_URL must be a PostgreSQL JDBC URL");
        }
        if (!SQL_IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException(
                    "FLUX_DB_SCHEMA must be a simple SQL identifier");
        }
    }

    public static DatabaseConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static DatabaseConfig fromEnvironment(Map<String, String> environment) {
        return new DatabaseConfig(
                valueOrDefault(
                        environment,
                        "FLUX_DB_URL",
                        "jdbc:postgresql://localhost:5432/flux_browser"),
                valueOrDefault(environment, "FLUX_DB_USER", "postgres"),
                valueOrDefault(environment, "FLUX_DB_PASSWORD", "1234"),
                valueOrDefault(environment, "FLUX_DB_SCHEMA", "flux_browser"));
    }

    public String safeDescription() {
        return jdbcUrl + " (user=" + username + ", schema=" + schema + ")";
    }

    private static String valueOrDefault(
            Map<String, String> environment,
            String key,
            String fallback) {
        String value = environment.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
