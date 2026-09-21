package com.flux.browser;

import com.flux.browser.util.UrlResolver;
import java.util.Objects;

/** Framework-free checks run automatically by `mvn test` and `mvn verify`. */
public final class BrowserChecks {
    public static void main(String[] args) throws Exception {
        WindowGeometryChecks.run();
        FeatureChecks.run();
        equal(UrlResolver.resolve("  "), UrlResolver.HOME);
        equal(UrlResolver.resolve("about:blank"), UrlResolver.HOME);
        equal(UrlResolver.resolve(" example.com "), "https://example.com/");
        equal(UrlResolver.resolve("HTTP://Example.COM:80/a?q=Java#top"), "http://example.com/a?q=Java#top");
        equal(UrlResolver.resolve("https://example.com:443"), "https://example.com/");
        equal(UrlResolver.resolve("//example.com/path"), "https://example.com/path");
        equal(UrlResolver.resolve("localhost:8080/path?q=1"), "http://localhost:8080/path?q=1");
        equal(UrlResolver.resolve("app.localhost"), "http://app.localhost/");
        equal(UrlResolver.resolve("localhost.example.com"), "https://localhost.example.com/");
        equal(UrlResolver.resolve("127.0.0.1:8080"), "http://127.0.0.1:8080/");
        equal(UrlResolver.resolve("[::1]:8080/a"), "http://[::1]:8080/a");
        equal(UrlResolver.resolve("bücher.de/lesen"), "https://xn--bcher-kva.de/lesen");
        equal(UrlResolver.resolve("https://example.com/a b"), "https://example.com/a%20b");
        equal(UrlResolver.resolve("JavaFX & PostgreSQL"), "https://www.google.com/search?q=JavaFX+%26+PostgreSQL");
        equal(UrlResolver.resolve("a+b #java"), "https://www.google.com/search?q=a%2Bb+%23java");
        equal(UrlResolver.resolve("hello"), "https://www.google.com/search?q=hello");
        equal(UrlResolver.pageTitle("", "https://www.example.com/"), "example.com");
        equal(UrlResolver.pageTitle("a".repeat(600), "https://example.com/").length(), 512);
        for (String bad : new String[]{"https://", "https://user:secret@example.com", "http://999.0.0.1", "http://[::1]:65536",
                "https://example.com:0", "https://example.com:abc", "http://[::1]:", "https://example.com/%oops",
                "javascript:1", "javascript:alert(1)", "file:///tmp/test.html", "data:text/html,hi", "some words", "https://a\nb.com"}) {
            rejects(() -> UrlResolver.webAddress(bad), bad);
        }
        rejects(() -> UrlResolver.resolve("javascript:alert(1)"), "omnibox javascript");
        rejects(() -> UrlResolver.resolve("javascript:1"), "numeric javascript scheme");
        rejects(() -> UrlResolver.resolve("x".repeat(3000)), "oversized search");
        rejects(() -> UrlResolver.webAddress("https://example.com/" + "x".repeat(2048)), "oversized URL");
        rejects(() -> UrlResolver.requiredText(" ", "Title", 512), "blank title");
        rejects(() -> UrlResolver.requiredText("x".repeat(81), "Folder", 80), "long folder");
        System.out.println("BrowserChecks passed: URL/search resolution, normalization, validation, and title fallback.");
    }

    static void equal(Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    static void check(boolean value, String description) { if (!value) throw new AssertionError(description); }
    private static void rejects(Runnable action, String description) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Should reject " + description);
    }
}
