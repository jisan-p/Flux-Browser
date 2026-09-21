package com.flux.browser.feature;

import com.flux.browser.util.UrlResolver;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Explicit page actions never interpret a selected phrase as a URL or a command. */
public final class PageActions {
    public static final List<String> LANGUAGES = List.of("en", "bn", "es", "fr", "de", "ar", "hi", "ja", "ko", "zh-CN");
    private PageActions() {}
    public static String translation(String url, String language) {
        if (!LANGUAGES.contains(language)) throw new IllegalArgumentException("Choose a supported translation language.");
        return "https://translate.google.com/translate?sl=auto&tl=" + language + "&u=" + encode(UrlResolver.webAddress(url));
    }
    public static String search(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Select some text to search.");
        String url = "https://duckduckgo.com/?q=" + encode(text.strip());
        if (url.length() > 2048) throw new IllegalArgumentException("Select a shorter phrase to search.");
        return url;
    }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
}
