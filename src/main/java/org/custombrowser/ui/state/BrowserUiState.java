package org.custombrowser.ui.state;

import org.custombrowser.ui.model.SpeedDialEntry;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Shared, in-memory Phase 1 UI preferences.
 *
 * <p>PostgreSQL persistence is introduced in Phase 3. Until then these values
 * intentionally reset when Flux restarts.</p>
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

    public ObservableList<SpeedDialEntry> speedDials() {
        return speedDials;
    }

    public enum Accent {
        RED("accent-red"),
        CYAN("accent-cyan"),
        PURPLE("accent-purple"),
        GREEN("accent-green");

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
        NEON("wallpaper-neon");

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
