package org.custombrowser.ui.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
