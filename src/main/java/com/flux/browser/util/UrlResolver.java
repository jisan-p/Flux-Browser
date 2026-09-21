package com.flux.browser.util;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class UrlResolver {
    public static final String HOME = "flux://start";
    private UrlResolver() {}

    public static String resolve(String input) {
        String text = input == null ? "" : input.strip();
        if (text.isEmpty() || text.equalsIgnoreCase(HOME) || text.equalsIgnoreCase("about:blank")) return HOME;
        if (text.matches("(?is)^https?://.*") || text.startsWith("//")) return webAddress(text);
        if (looksLikeAddress(text)) return webAddress(text);
        if (text.matches("(?is)^[a-z][a-z0-9+.-]*:.*")) {
            throw new IllegalArgumentException("Enter an http:// or https:// address, or a search query.");
        }
        String result = "https://duckduckgo.com/?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        if (result.length() > 2048) throw new IllegalArgumentException("That search is too long. Use fewer than 2,000 characters.");
        return result;
    }

    /** Resolve a URL field without ever turning invalid input into a search. */
    public static String webAddress(String input) {
        String text = input == null ? "" : input.strip();
        if (text.isEmpty() || text.chars().anyMatch(Character::isISOControl)) throw invalidAddress();
        if (text.matches("(?is)^(javascript|data|file|mailto|ftp|about|flux):.*")) throw invalidAddress();
        if (text.startsWith("//")) text = "https:" + text;
        if (!text.matches("(?is)^https?://.*")) {
            if (!looksLikeAddress(text)) throw invalidAddress();
            String host = authority(text).toLowerCase(Locale.ROOT);
            String hostname = host.replaceFirst(":[0-9]+$", "");
            boolean local = hostname.equals("localhost") || hostname.endsWith(".localhost") || host.startsWith("[")
                    || host.matches("[0-9.]+(?::[0-9]+)?") || !host.contains(".");
            text = (local ? "http://" : "https://") + text;
        }
        try {
            URI uri = new URI(text.replace(" ", "%20"));
            String authority = uri.getRawAuthority();
            if (authority == null || authority.contains("@")) throw invalidAddress();
            String host;
            int port = -1;
            if (authority.startsWith("[")) {
                host = uri.getHost(); // URI validates IPv6 literals and their brackets.
                if (host == null) throw invalidAddress();
                port = uri.getPort();
                if (authority.endsWith(":")) throw invalidAddress();
            } else {
                int colon = authority.lastIndexOf(':');
                host = colon < 0 ? authority : authority.substring(0, colon);
                if (colon >= 0) {
                    String value = authority.substring(colon + 1);
                    if (!value.matches("[0-9]{1,5}")) throw invalidAddress();
                    port = Integer.parseInt(value);
                }
                host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
                if (host.isEmpty() || host.length() > 253) throw invalidAddress();
                if (host.matches("[0-9.]+")) {
                    String[] octets = host.split("\\.", -1);
                    if (octets.length != 4) throw invalidAddress();
                    for (String octet : octets) {
                        if (!octet.matches("[0-9]{1,3}") || Integer.parseInt(octet) > 255) throw invalidAddress();
                    }
                }
            }
            if (port < -1 || port == 0 || port > 65535) throw invalidAddress();
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) throw invalidAddress();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) port = -1;
            String path = uri.getRawPath();
            String normalized = scheme + "://" + host + (port == -1 ? "" : ":" + port)
                    + (path == null || path.isEmpty() ? "/" : path)
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
            String result = new URI(normalized).toASCIIString();
            if (result.length() > 2048) throw new IllegalArgumentException("URLs must be 2,048 characters or fewer.");
            return result;
        } catch (URISyntaxException | NumberFormatException error) {
            throw invalidAddress();
        }
    }

    // ponytail: hostname heuristic, not a public-suffix database; unknown single words remain searches.
    private static boolean looksLikeAddress(String text) {
        if (text.chars().anyMatch(Character::isWhitespace) || text.contains("@")) return false;
        String host = authority(text);
        if (host.startsWith("[")) return host.contains("]");
        return host.equalsIgnoreCase("localhost") || host.matches("(?i)^[^:]+:[0-9]+$")
                || (host.contains(".") && !host.contains(":") && !host.startsWith("."));
    }

    private static String authority(String text) { return text.split("[/?#]", 2)[0]; }
    private static IllegalArgumentException invalidAddress() {
        return new IllegalArgumentException("Enter a valid HTTP(S) address, for example https://example.com.");
    }

    public static boolean isWeb(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    public static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host.replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException error) { return url; }
    }

    public static String pageTitle(String title, String url) {
        String value = title == null || title.isBlank() ? host(url) : title.strip();
        return value.substring(0, Math.min(value.length(), 512));
    }

    public static String requiredText(String input, String name, int maximum) {
        String value = input == null ? "" : input.strip();
        if (value.isEmpty() || value.length() > maximum) {
            throw new IllegalArgumentException(name + " must contain 1–" + maximum + " characters.");
        }
        return value;
    }
}
