package org.custombrowser.download;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative URL-based download detection for JavaFX WebView, which does
 * not expose response headers or a native download callback.
 */
public final class DownloadDetector {

    private static final Set<String> DOWNLOAD_EXTENSIONS = Set.of(
            "7z", "apk", "bin", "bz2", "deb", "dmg", "doc", "docx",
            "exe", "gz", "iso", "jar", "msi", "odp", "ods", "odt",
            "pdf", "ppt", "pptx", "rar", "rpm", "tar", "tgz", "xz",
            "xls", "xlsx", "zip");
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4",
            "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2",
            "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private DownloadDetector() {
    }

    public static boolean isLikelyDownload(URI uri) {
        if (uri == null || uri.getScheme() == null
                || (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme()))) {
            return false;
        }
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return false;
        }
        int slash = path.lastIndexOf('/');
        String name = path.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return DOWNLOAD_EXTENSIONS.contains(
                name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static String suggestedFileName(URI uri) {
        String path = uri == null ? null : uri.getPath();
        String candidate = path == null || path.isBlank()
                ? "download.bin"
                : path.substring(path.lastIndexOf('/') + 1);
        return sanitizeFileName(candidate);
    }

    public static String sanitizeFileName(String rawName) {
        String candidate = rawName == null ? "" : rawName;
        candidate = candidate
                .replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_")
                .replaceAll("[. ]+$", "")
                .trim();
        if (candidate.isBlank()) {
            candidate = "download.bin";
        }
        String baseName = candidate.contains(".")
                ? candidate.substring(0, candidate.indexOf('.'))
                : candidate;
        if (WINDOWS_RESERVED_NAMES.contains(
                baseName.toUpperCase(Locale.ROOT))) {
            candidate = "_" + candidate;
        }
        if (candidate.length() > 180) {
            int extensionIndex = candidate.lastIndexOf('.');
            String extension = extensionIndex > 0
                    ? candidate.substring(extensionIndex)
                    : "";
            int baseLength = Math.max(1, 180 - extension.length());
            candidate = candidate.substring(0, baseLength) + extension;
        }
        return candidate;
    }

    public static Path uniqueDestination(Path requested) {
        Path absolute = requested.toAbsolutePath().normalize();
        if (!Files.exists(absolute) && !Files.exists(partPath(absolute))) {
            return absolute;
        }
        String fileName = absolute.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        Path parent = absolute.getParent();
        for (int number = 1; number < 10_000; number++) {
            Path candidate = parent.resolve(
                    base + " (" + number + ")" + extension);
            if (!Files.exists(candidate) && !Files.exists(partPath(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Unable to choose a non-conflicting download filename");
    }

    static Path partPath(Path destination) {
        return destination.resolveSibling(
                destination.getFileName().toString() + ".part");
    }
}
