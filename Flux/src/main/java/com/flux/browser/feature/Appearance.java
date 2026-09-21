package com.flux.browser.feature;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Versioned, bounded appearance preferences. No CSS, scripts or remote asset URLs are accepted. */
public final class Appearance {
    public int version = 1;
    public String theme = "GX Classic", accent = "#fa1e4e", base = "#100d17", mode = "Dark";
    public String tileEffect = "None", dialPosition = "Auto";
    public boolean bigTiles;
    public String wallpaper = "Waves", image = "", music = "", font = "System", layout = "Comfortable";
    public double opacity = 0.85, wallpaperBrightness = 0.8, blur = 0, vignette = 0.35, fontSize = 13, volume = 0.2;
    public int columns = 4;
    public boolean sidebar = true, search = true, dials = true, clock, tileTitles, statusBar;
    public boolean wallpapers = true, elementBackgrounds = true, animations = true, sounds, typingSounds, musicEnabled;
    public static final List<String> THEMES = List.of("GX Classic", "Ultraviolet", "Sub Zero", "Frost", "Rose Quartz", "Mint", "Custom");
    public static final List<String> ACCENTS = List.of("#fa1e4e", "#965bff", "#536dfe", "#00d9ed", "#f28ac5", "#57dfb5");
    public Appearance copy() { return FeatureStore.JSON.fromJson(FeatureStore.JSON.toJson(this), Appearance.class); }
    public void theme(String name) {
        int index = THEMES.indexOf(name);
        if (index >= 0 && index < ACCENTS.size()) { theme = name; accent = ACCENTS.get(index); }
    }
    public void normalize() {
        version = 1;
        if (!THEMES.contains(theme == null ? "" : theme)) theme = "GX Classic";
        accent = color(accent, "#fa1e4e"); base = color(base, "#100d17");
        mode = choice(mode, List.of("Light", "Auto", "Dark"), "Dark");
        wallpaper = choice(wallpaper, List.of("Waves", "Aurora", "Grid", "Custom"), "Waves");
        tileEffect = choice(tileEffect, List.of("None", "Zoom", "Glow", "Lift"), "None");
        dialPosition = choice(dialPosition, List.of("Auto", "Top", "Center", "Bottom"), "Auto");
        layout = choice(layout, List.of("Compact", "Comfortable", "Spacious"), "Comfortable");
        if (font == null || !font.matches("[\\p{L}\\p{N} _-]{1,64}")) font = "System";
        image = localAsset(image); music = localAsset(music);
        opacity = bounded(opacity, 0.2, 1, 0.85); wallpaperBrightness = bounded(wallpaperBrightness, 0, 1, 0.8);
        blur = bounded(blur, 0, 20, 0); vignette = bounded(vignette, 0, 1, 0.35);
        fontSize = bounded(fontSize, 11, 18, 13); volume = bounded(volume, 0, 1, 0.2);
        columns = Math.clamp(columns, 3, 8);
    }
    private static String color(String value, String fallback) { return value != null && value.matches("#[0-9a-fA-F]{6}") ? value.toLowerCase(Locale.ROOT) : fallback; }
    private static String choice(String value, List<String> choices, String fallback) { return choices.contains(value == null ? "" : value) ? value : fallback; }
    private static double bounded(double value, double min, double max, double fallback) { return Double.isFinite(value) ? Math.clamp(value,min,max) : fallback; }
    private static String localAsset(String value) {
        try { var uri = URI.create(value); return value.length() <= 4096 && "file".equals(uri.getScheme()) && uri.getAuthority() == null && uri.getPath().startsWith("/") ? value : ""; }
        catch (Exception e) { return ""; }
    }
}
