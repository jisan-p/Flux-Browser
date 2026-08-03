package org.custombrowser.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class SqlInitializationContractTest {

    private static final Path INIT_DIRECTORY =
            Path.of("docker", "postgres", "init");

    @Test
    void numberedScriptsDefineEveryPhaseThreeTable() throws IOException {
        List<Path> scripts;
        try (var files = Files.list(INIT_DIRECTORY)) {
            scripts = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }

        assertTrue(scripts.size() >= 4);
        assertTrue(scripts.getFirst().getFileName().toString().startsWith("001_"));
        String sql = readAll(scripts).toLowerCase(Locale.ROOT);
        for (String table : List.of(
                "settings",
                "bookmark_folders",
                "bookmarks",
                "visits",
                "downloads",
                "speed_dial_entries",
                "browser_sessions",
                "session_tabs",
                "recently_closed_tabs",
                "window_state")) {
            assertTrue(
                    sql.contains("flux_browser." + table),
                    () -> "Missing SQL definition for " + table);
        }
        assertTrue(sql.contains("values (3)"));
    }

    @Test
    void javaSourcesContainNoSchemaDdl() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        List<Path> javaFiles;
        try (var files = Files.walk(sourceRoot)) {
            javaFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        }
        String source = readAll(javaFiles).toUpperCase(Locale.ROOT);

        assertFalse(source.contains("CREATE TABLE"));
        assertFalse(source.contains("ALTER TABLE"));
        assertFalse(source.contains("DROP TABLE"));
    }

    private static String readAll(List<Path> files) throws IOException {
        StringBuilder combined = new StringBuilder();
        for (Path file : files) {
            combined.append(Files.readString(file)).append('\n');
        }
        return combined.toString();
    }
}
