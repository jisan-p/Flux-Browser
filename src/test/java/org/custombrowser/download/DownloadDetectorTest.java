package org.custombrowser.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadDetectorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsConservativeHttpDownloadExtensions() {
        assertTrue(DownloadDetector.isLikelyDownload(
                URI.create("https://example.com/releases/flux.ZIP?build=4")));
        assertTrue(DownloadDetector.isLikelyDownload(
                URI.create("https://example.com/manual.pdf")));
        assertFalse(DownloadDetector.isLikelyDownload(
                URI.create("https://example.com/index.html")));
        assertFalse(DownloadDetector.isLikelyDownload(
                URI.create("file:///tmp/archive.zip")));
    }

    @Test
    void createsSafeCrossPlatformNames() {
        assertEquals(
                "flux_bad_name_.zip",
                DownloadDetector.sanitizeFileName("flux<bad:name?.zip"));
        assertEquals(
                "_CON.txt",
                DownloadDetector.sanitizeFileName("CON.txt"));
        assertEquals(
                "download.bin",
                DownloadDetector.sanitizeFileName("..."));
    }

    @Test
    void extractsAndSanitizesSuggestedName() {
        assertEquals(
                "Flux Browser.zip",
                DownloadDetector.suggestedFileName(
                        URI.create("https://example.com/Flux%20Browser.zip")));
    }

    @Test
    void avoidsExistingFilesAndPartialTransfers() throws Exception {
        Path requested = temporaryDirectory.resolve("flux.zip");
        Files.createFile(requested);
        Files.createFile(temporaryDirectory.resolve("flux (1).zip.part"));

        assertEquals(
                temporaryDirectory.resolve("flux (2).zip").toAbsolutePath(),
                DownloadDetector.uniqueDestination(requested));
    }
}
