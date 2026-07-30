package org.custombrowser.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

class FaviconServiceTest {

    private static final URI PAGE =
            URI.create("https://example.com/games/index.html");

    @Test
    void resolvesAbsoluteIconAddress() {
        assertEquals(
                URI.create("https://cdn.example.net/icon.png"),
                FaviconService.resolveIconUri(
                        PAGE,
                        "https://cdn.example.net/icon.png"));
    }

    @Test
    void resolvesRootRelativeIconAddress() {
        assertEquals(
                URI.create("https://example.com/assets/icon.png"),
                FaviconService.resolveIconUri(PAGE, "/assets/icon.png"));
    }

    @Test
    void resolvesProtocolRelativeIconAddress() {
        assertEquals(
                URI.create("https://cdn.example.net/icon.png"),
                FaviconService.resolveIconUri(
                        PAGE,
                        "//cdn.example.net/icon.png"));
    }

    @Test
    void fallsBackToOriginFavicon() {
        assertEquals(
                URI.create("https://example.com/favicon.ico"),
                FaviconService.resolveIconUri(PAGE, null));
    }

    @Test
    void fallsBackForMalformedOrUnsupportedIconAddress() {
        assertEquals(
                URI.create("https://example.com/favicon.ico"),
                FaviconService.resolveIconUri(PAGE, "http://[broken"));
        assertEquals(
                URI.create("https://example.com/favicon.ico"),
                FaviconService.resolveIconUri(PAGE, "data:image/png;base64,AA=="));
    }
}
