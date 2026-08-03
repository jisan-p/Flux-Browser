package org.custombrowser.browser;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javafx.scene.image.Image;

/**
 * Asynchronously downloads and caches page icons.
 */
public final class FaviconService {

    private static final int MAX_ICON_BYTES = 2 * 1024 * 1024;
    static final int MAX_CACHE_ENTRIES = 256;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<URI, Image> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<URI, Image> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });

    public void clearCache() {
        cache.clear();
    }

    public int cacheSize() {
        return cache.size();
    }

    public CompletableFuture<Optional<Image>> load(
            URI pageUri,
            String discoveredIconAddress) {
        URI iconUri = resolveIconUri(pageUri, discoveredIconAddress);
        CompletableFuture<Optional<Image>> primary = loadIcon(iconUri);
        if (discoveredIconAddress == null || discoveredIconAddress.isBlank()) {
            return primary;
        }

        URI fallback = resolveIconUri(pageUri, null);
        return primary.thenCompose(image -> image.isPresent()
                ? CompletableFuture.completedFuture(image)
                : loadIcon(fallback));
    }

    private CompletableFuture<Optional<Image>> loadIcon(URI iconUri) {
        Image cached = cache.get(iconUri);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        HttpRequest request = HttpRequest.newBuilder(iconUri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "FluxBrowser/0.2")
                .GET()
                .build();
        return httpClient.sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> toImage(iconUri, response))
                .exceptionally(error -> Optional.empty());
    }

    static URI resolveIconUri(URI pageUri, String discoveredIconAddress) {
        if (discoveredIconAddress != null && !discoveredIconAddress.isBlank()) {
            try {
                String iconAddress = discoveredIconAddress.trim();
                URI resolved = iconAddress.startsWith("//")
                        ? URI.create(pageUri.getScheme() + ":" + iconAddress)
                        : pageUri.resolve(iconAddress);
                if ("http".equalsIgnoreCase(resolved.getScheme())
                        || "https".equalsIgnoreCase(resolved.getScheme())) {
                    return resolved;
                }
            } catch (IllegalArgumentException ignored) {
                // Use the origin favicon for malformed or unsupported icon URLs.
            }
        }

        return originFavicon(pageUri);
    }

    private static URI originFavicon(URI pageUri) {
        return URI.create(
                pageUri.getScheme()
                        + "://"
                        + pageUri.getAuthority()
                        + "/favicon.ico");
    }

    private Optional<Image> toImage(
            URI iconUri,
            HttpResponse<byte[]> response) {
        byte[] body = response.body();
        if (response.statusCode() < 200
                || response.statusCode() >= 300
                || body.length == 0
                || body.length > MAX_ICON_BYTES) {
            return Optional.empty();
        }

        Image image = new Image(new ByteArrayInputStream(body));
        if (image.isError()) {
            return Optional.empty();
        }
        cache.put(iconUri, image);
        return Optional.of(image);
    }
}
