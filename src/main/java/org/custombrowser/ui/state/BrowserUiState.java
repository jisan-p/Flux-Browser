package org.custombrowser.ui.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.custombrowser.ui.model.SpeedDialEntry;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Shared, in-memory browser UI preferences.
 *
 * <p>Persistent fields are stored through the settings repository; transient
 * panel visibility remains process-local.</p>
 */
public final class BrowserUiState {

    private final ObjectProperty<Accent> accent =
            new SimpleObjectProperty<>(Accent.RED);
    private final ObjectProperty<Wallpaper> wallpaper =
            new SimpleObjectProperty<>(Wallpaper.GRID);
    private final ObjectProperty<SidebarPanel> activeSidebarPanel =
            new SimpleObjectProperty<>(SidebarPanel.NONE);
    private final BooleanProperty sidebarVisible =
            new SimpleBooleanProperty(true);
    private final BooleanProperty panelDocked =
            new SimpleBooleanProperty(true);
    private final BooleanProperty easySetupOpen =
            new SimpleBooleanProperty(false);
    private final BooleanProperty reducedMotion =
            new SimpleBooleanProperty(false);
    private final DoubleProperty uiScale =
            new SimpleDoubleProperty(13.0);
    private final BooleanProperty autoSuspendEnabled =
            new SimpleBooleanProperty(false);
    private final IntegerProperty autoSuspendMinutes =
            new SimpleIntegerProperty(15);
    private final ObservableList<SpeedDialEntry> speedDials =
            FXCollections.observableArrayList(
                    new SpeedDialEntry("YouTube", "https://youtube.com"),
                    new SpeedDialEntry("Twitch", "https://twitch.tv"),
                    new SpeedDialEntry("Discord", "https://discord.com"),
                    new SpeedDialEntry("Reddit", "https://reddit.com"),
                    new SpeedDialEntry("GitHub", "https://github.com"),
                    new SpeedDialEntry("Gmail", "https://mail.google.com"));

    public ObjectProperty<Accent> accentProperty() {
        return accent;
    }

    public ObjectProperty<Wallpaper> wallpaperProperty() {
        return wallpaper;
    }

    public ObjectProperty<SidebarPanel> activeSidebarPanelProperty() {
        return activeSidebarPanel;
    }

    public BooleanProperty sidebarVisibleProperty() {
        return sidebarVisible;
    }

    public BooleanProperty panelDockedProperty() {
        return panelDocked;
    }

    public BooleanProperty easySetupOpenProperty() {
        return easySetupOpen;
    }

    public BooleanProperty reducedMotionProperty() {
        return reducedMotion;
    }

    public DoubleProperty uiScaleProperty() {
        return uiScale;
    }

    public BooleanProperty autoSuspendEnabledProperty() {
        return autoSuspendEnabled;
    }

    public IntegerProperty autoSuspendMinutesProperty() {
        return autoSuspendMinutes;
    }

    public ObservableList<SpeedDialEntry> speedDials() {
        return speedDials;
    }

    public void applyPersistedState(
            Map<String, String> settings,
            List<SpeedDialEntry> persistedSpeedDials) {
        accent.set(enumValue(
                Accent.class,
                settings.get("accent"),
                Accent.RED));
        wallpaper.set(enumValue(
                Wallpaper.class,
                settings.get("wallpaper"),
                Wallpaper.GRID));
        sidebarVisible.set(booleanValue(
                settings.get("sidebar_visible"),
                true));
        panelDocked.set(booleanValue(
                settings.get("panel_docked"),
                true));
        reducedMotion.set(booleanValue(
                settings.get("reduced_motion"),
                false));
        uiScale.set(doubleValue(
                settings.get("ui_scale"),
                13.0,
                11.0,
                16.0));
        autoSuspendEnabled.set(booleanValue(
                settings.get("auto_suspend_enabled"),
                false));
        autoSuspendMinutes.set(integerValue(
                settings.get("auto_suspend_minutes"),
                15,
                1,
                120));
        if (persistedSpeedDials != null && !persistedSpeedDials.isEmpty()) {
            speedDials.setAll(persistedSpeedDials);
        }
    }

    public Map<String, String> toSettingsMap() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("accent", accent.get().name());
        values.put("wallpaper", wallpaper.get().name());
        values.put("sidebar_visible", Boolean.toString(sidebarVisible.get()));
        values.put("panel_docked", Boolean.toString(panelDocked.get()));
        values.put("reduced_motion", Boolean.toString(reducedMotion.get()));
        values.put("ui_scale", Double.toString(uiScale.get()));
        values.put("auto_suspend_enabled",
                Boolean.toString(autoSuspendEnabled.get()));
        values.put("auto_suspend_minutes",
                Integer.toString(autoSuspendMinutes.get()));
        return Map.copyOf(values);
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type,
            String rawValue,
            T fallback) {
        if (rawValue == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, rawValue);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static boolean booleanValue(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    private static double doubleValue(
            String value,
            double fallback,
            double minimum,
            double maximum) {
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(minimum, Math.min(maximum, Double.parseDouble(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int integerValue(
            String value,
            int fallback,
            int minimum,
            int maximum) {
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public enum Accent {
        RED("accent-red"),
        CYAN("accent-cyan"),
        PURPLE("accent-purple"),
        GREEN("accent-green"),
        ORANGE("accent-orange"),
        BLUE("accent-blue");

        private final String styleClass;

        Accent(String styleClass) {
            this.styleClass = styleClass;
        }

        public String styleClass() {
            return styleClass;
        }
    }

    public enum Wallpaper {
        GRID("wallpaper-grid"),
        VOID("wallpaper-void"),
        NEON("wallpaper-neon"),
        CIRCUIT("wallpaper-circuit"),
        SUNSET("wallpaper-sunset");

        private final String styleClass;

        Wallpaper(String styleClass) {
            this.styleClass = styleClass;
        }

        public String styleClass() {
            return styleClass;
        }
    }

    public enum SidebarPanel {
        NONE,
        GX_CONTROL,
        BOOKMARKS,
        HISTORY,
        DOWNLOADS,
        SETTINGS
    }
}
