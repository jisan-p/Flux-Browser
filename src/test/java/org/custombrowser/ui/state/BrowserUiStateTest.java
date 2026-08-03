package org.custombrowser.ui.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.custombrowser.ui.model.SpeedDialEntry;
import org.custombrowser.ui.state.BrowserUiState.Accent;
import org.custombrowser.ui.state.BrowserUiState.SidebarPanel;
import org.custombrowser.ui.state.BrowserUiState.Wallpaper;
import org.junit.jupiter.api.Test;

class BrowserUiStateTest {

    @Test
    void startsWithGxThemeDefaults() {
        BrowserUiState state = new BrowserUiState();

        assertEquals(Accent.RED, state.accentProperty().get());
        assertEquals(Wallpaper.GRID, state.wallpaperProperty().get());
        assertTrue(state.sidebarVisibleProperty().get());
        assertTrue(state.panelDockedProperty().get());
        assertFalse(state.reducedMotionProperty().get());
        assertEquals(13.0, state.uiScaleProperty().get());
        assertFalse(state.autoSuspendEnabledProperty().get());
        assertEquals(15, state.autoSuspendMinutesProperty().get());
    }

    @Test
    void startsWithNoSidebarPanelOpen() {
        BrowserUiState state = new BrowserUiState();

        assertEquals(
                SidebarPanel.NONE,
                state.activeSidebarPanelProperty().get());
        assertFalse(state.easySetupOpenProperty().get());
    }

    @Test
    void providesSixDefaultSpeedDials() {
        BrowserUiState state = new BrowserUiState();

        assertEquals(6, state.speedDials().size());
        assertEquals("YouTube", state.speedDials().getFirst().title());
        assertEquals("https://mail.google.com", state.speedDials().getLast().address());
    }

    @Test
    void speedDialNormalizesSurroundingWhitespace() {
        SpeedDialEntry entry = new SpeedDialEntry(
                "  Flux  ",
                "  https://example.com  ");

        assertEquals("Flux", entry.title());
        assertEquals("https://example.com", entry.address());
    }

    @Test
    void speedDialRejectsBlankTitle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpeedDialEntry(" ", "https://example.com"));
    }

    @Test
    void speedDialRejectsBlankAddress() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpeedDialEntry("Example", " "));
    }

    @Test
    void appliesAndExportsPersistedPreferences() {
        BrowserUiState state = new BrowserUiState();
        state.applyPersistedState(
                Map.of(
                        "accent", "CYAN",
                        "wallpaper", "NEON",
                        "sidebar_visible", "false",
                        "panel_docked", "false",
                        "reduced_motion", "true",
                        "ui_scale", "15.5",
                        "auto_suspend_enabled", "true",
                        "auto_suspend_minutes", "30"),
                List.of(new SpeedDialEntry(
                        "Flux",
                        "https://example.com")));

        assertEquals(Accent.CYAN, state.accentProperty().get());
        assertEquals(Wallpaper.NEON, state.wallpaperProperty().get());
        assertFalse(state.sidebarVisibleProperty().get());
        assertFalse(state.panelDockedProperty().get());
        assertTrue(state.reducedMotionProperty().get());
        assertEquals(15.5, state.uiScaleProperty().get());
        assertTrue(state.autoSuspendEnabledProperty().get());
        assertEquals(30, state.autoSuspendMinutesProperty().get());
        assertEquals(1, state.speedDials().size());
        assertEquals("CYAN", state.toSettingsMap().get("accent"));
        assertEquals("30", state.toSettingsMap().get("auto_suspend_minutes"));
    }

    @Test
    void invalidPersistedValuesFallBackToSafeDefaults() {
        BrowserUiState state = new BrowserUiState();
        state.applyPersistedState(
                Map.of(
                        "accent", "UNKNOWN",
                        "ui_scale", "not-a-number"),
                List.of());

        assertEquals(Accent.RED, state.accentProperty().get());
        assertEquals(13.0, state.uiScaleProperty().get());
        assertEquals(6, state.speedDials().size());
    }
}
