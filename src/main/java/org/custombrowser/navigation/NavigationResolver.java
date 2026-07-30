package org.custombrowser.navigation;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts omnibox input into either a direct navigation or a search request.
 */
public final class NavigationResolver {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https", "file");
    private static final Set<String> EXTERNAL_SCHEMES =
            Set.of("mailto", "tel", "magnet");
    private static final Pattern IPV4_WITH_OPTIONAL_PORT = Pattern.compile(
            "^(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?:/.*)?$");
    private static final Pattern LOCALHOST_WITH_OPTIONAL_PORT = Pattern.compile(
            "^localhost(?::\\d{1,5})?(?:/.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern IPV6_WITH_OPTIONAL_PORT = Pattern.compile(
            "^\\[[0-9a-f:]+](?::\\d{1,5})?(?:/.*)?$",
            Pattern.CASE_INSENSITIVE);

    private final String searchUrlPrefix;

    public NavigationResolver(String searchUrlPrefix) {
        this.searchUrlPrefix = requireHttpUrl(searchUrlPrefix);
    }

    public static NavigationResolver duckDuckGo() {
        return new NavigationResolver("https://duckduckgo.com/?q=");
    }

    public NavigationTarget resolve(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new IllegalArgumentException("Navigation input must not be blank");
        }

        String input = rawInput.trim();
        if (isLocalAddress(input)) {
            return directTargetWithDefaultScheme(input);
        }

        URI explicitUri = parseUri(input);
        if (explicitUri != null && explicitUri.getScheme() != null) {
            String scheme = explicitUri.getScheme().toLowerCase(Locale.ROOT);
            if (SUPPORTED_SCHEMES.contains(scheme)) {
                return new NavigationTarget(explicitUri, NavigationType.DIRECT);
            }
            if (EXTERNAL_SCHEMES.contains(scheme)) {
                return new NavigationTarget(explicitUri, NavigationType.EXTERNAL);
            }
            throw new IllegalArgumentException("Unsupported URL scheme: " + scheme);
        }

        if (looksLikeHost(input)) {
            return directTargetWithDefaultScheme(input);
        }

        String encodedQuery = URLEncoder.encode(input, StandardCharsets.UTF_8);
        return new NavigationTarget(
                URI.create(searchUrlPrefix + encodedQuery),
                NavigationType.SEARCH);
    }

    private static boolean looksLikeHost(String input) {
        if (input.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        return input.contains(".") || isLocalAddress(input);
    }

    private static boolean isLocalAddress(String input) {
        return LOCALHOST_WITH_OPTIONAL_PORT.matcher(input).matches()
                || IPV4_WITH_OPTIONAL_PORT.matcher(input).matches()
                || IPV6_WITH_OPTIONAL_PORT.matcher(input).matches();
    }

    private static NavigationTarget directTargetWithDefaultScheme(String input) {
        URI uri = parseUri("https://" + input);
        if (uri == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid web address: " + input);
        }
        return new NavigationTarget(uri, NavigationType.DIRECT);
    }

    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String requireHttpUrl(String value) {
        Objects.requireNonNull(value, "searchUrlPrefix");
        URI uri = URI.create(value);
        if (uri.getScheme() == null
                || (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Search URL must use HTTP or HTTPS");
        }
        return value;
    }

    public enum NavigationType {
        DIRECT,
        SEARCH,
        EXTERNAL
    }

    public record NavigationTarget(URI uri, NavigationType type) {
        public NavigationTarget {
            Objects.requireNonNull(uri, "uri");
            Objects.requireNonNull(type, "type");
        }
    }
}
