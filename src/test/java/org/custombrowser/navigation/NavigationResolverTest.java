package org.custombrowser.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.custombrowser.navigation.NavigationResolver.NavigationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NavigationResolverTest {

    private NavigationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = NavigationResolver.duckDuckGo();
    }

    @Test
    void preservesSupportedAbsoluteUrl() {
        var target = resolver.resolve("https://example.com/docs?q=java");

        assertEquals("https://example.com/docs?q=java", target.uri().toString());
        assertEquals(NavigationType.DIRECT, target.type());
    }

    @Test
    void addsHttpsToDomain() {
        var target = resolver.resolve("example.com/docs");

        assertEquals("https://example.com/docs", target.uri().toString());
        assertEquals(NavigationType.DIRECT, target.type());
    }

    @Test
    void recognizesLocalhostAddress() {
        var target = resolver.resolve("localhost:8080/status");

        assertEquals("https://localhost:8080/status", target.uri().toString());
        assertEquals(NavigationType.DIRECT, target.type());
    }

    @Test
    void recognizesIpv4AddressWithPort() {
        var target = resolver.resolve("127.0.0.1:9090/health");

        assertEquals("https://127.0.0.1:9090/health", target.uri().toString());
        assertEquals(NavigationType.DIRECT, target.type());
    }

    @Test
    void recognizesIpv6AddressWithPort() {
        var target = resolver.resolve("[::1]:8080/health");

        assertEquals("https://[::1]:8080/health", target.uri().toString());
        assertEquals(NavigationType.DIRECT, target.type());
    }

    @Test
    void convertsWordsToEncodedSearch() {
        var target = resolver.resolve("javafx browser");

        assertEquals(
                "https://duckduckgo.com/?q=javafx+browser",
                target.uri().toString());
        assertEquals(NavigationType.SEARCH, target.type());
    }

    @Test
    void preservesFileUrl() {
        var target = resolver.resolve("file:///tmp/index.html");

        assertEquals("file:///tmp/index.html", target.uri().toString());
        assertEquals(NavigationType.DIRECT, target.type());
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("javascript:alert('unsafe')"));
    }

    @Test
    void routesSupportedExternalScheme() {
        var target = resolver.resolve("mailto:test@example.com");

        assertEquals("mailto:test@example.com", target.uri().toString());
        assertEquals(NavigationType.EXTERNAL, target.type());
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("  "));
    }
}
