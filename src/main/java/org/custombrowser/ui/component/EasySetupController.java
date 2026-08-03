package org.custombrowser.ui.component;

import org.custombrowser.ui.state.BrowserUiState;
import org.custombrowser.ui.state.BrowserUiState.Accent;
import org.custombrowser.ui.state.BrowserUiState.Wallpaper;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;

public final class EasySetupController {

    private final BrowserUiState uiState;

    @FXML
    private ToggleButton redAccent;

    @FXML
    private ToggleButton cyanAccent;

    @FXML
    private ToggleButton purpleAccent;

    @FXML
    private ToggleButton greenAccent;

    @FXML
    private ToggleButton orangeAccent;

    @FXML
    private ToggleButton blueAccent;

    @FXML
    private ToggleButton gridWallpaper;

    @FXML
    private ToggleButton voidWallpaper;

    @FXML
    private ToggleButton neonWallpaper;

    @FXML
    private ToggleButton circuitWallpaper;

    @FXML
    private ToggleButton sunsetWallpaper;

    @FXML
    private CheckBox showSidebar;

    @FXML
    private CheckBox dockPanel;

    @FXML
    private CheckBox reduceMotion;

    @FXML
    private Slider uiScale;

    public EasySetupController(BrowserUiState uiState) {
        this.uiState = uiState;
    }

    @FXML
    private void initialize() {
        showSidebar.selectedProperty().bindBidirectional(
                uiState.sidebarVisibleProperty());
        dockPanel.selectedProperty().bindBidirectional(
                uiState.panelDockedProperty());
        reduceMotion.selectedProperty().bindBidirectional(
                uiState.reducedMotionProperty());
        uiScale.valueProperty().bindBidirectional(uiState.uiScaleProperty());

        uiState.accentProperty().addListener(
                (observable, oldAccent, newAccent) -> selectAccent(newAccent));
        uiState.wallpaperProperty().addListener(
                (observable, oldWallpaper, newWallpaper) ->
                        selectWallpaper(newWallpaper));
        selectAccent(uiState.accentProperty().get());
        selectWallpaper(uiState.wallpaperProperty().get());
    }

    @FXML
    private void close() {
        uiState.easySetupOpenProperty().set(false);
    }

    @FXML
    private void useRedAccent() {
        uiState.accentProperty().set(Accent.RED);
        selectAccent(Accent.RED);
    }

    @FXML
    private void useCyanAccent() {
        uiState.accentProperty().set(Accent.CYAN);
        selectAccent(Accent.CYAN);
    }

    @FXML
    private void usePurpleAccent() {
        uiState.accentProperty().set(Accent.PURPLE);
        selectAccent(Accent.PURPLE);
    }

    @FXML
    private void useGreenAccent() {
        uiState.accentProperty().set(Accent.GREEN);
        selectAccent(Accent.GREEN);
    }

    @FXML
    private void useOrangeAccent() {
        uiState.accentProperty().set(Accent.ORANGE);
        selectAccent(Accent.ORANGE);
    }

    @FXML
    private void useBlueAccent() {
        uiState.accentProperty().set(Accent.BLUE);
        selectAccent(Accent.BLUE);
    }

    @FXML
    private void useGridWallpaper() {
        uiState.wallpaperProperty().set(Wallpaper.GRID);
        selectWallpaper(Wallpaper.GRID);
    }

    @FXML
    private void useVoidWallpaper() {
        uiState.wallpaperProperty().set(Wallpaper.VOID);
        selectWallpaper(Wallpaper.VOID);
    }

    @FXML
    private void useNeonWallpaper() {
        uiState.wallpaperProperty().set(Wallpaper.NEON);
        selectWallpaper(Wallpaper.NEON);
    }

    @FXML
    private void useCircuitWallpaper() {
        uiState.wallpaperProperty().set(Wallpaper.CIRCUIT);
        selectWallpaper(Wallpaper.CIRCUIT);
    }

    @FXML
    private void useSunsetWallpaper() {
        uiState.wallpaperProperty().set(Wallpaper.SUNSET);
        selectWallpaper(Wallpaper.SUNSET);
    }

    private void selectAccent(Accent accent) {
        redAccent.setSelected(accent == Accent.RED);
        cyanAccent.setSelected(accent == Accent.CYAN);
        purpleAccent.setSelected(accent == Accent.PURPLE);
        greenAccent.setSelected(accent == Accent.GREEN);
        orangeAccent.setSelected(accent == Accent.ORANGE);
        blueAccent.setSelected(accent == Accent.BLUE);
    }

    private void selectWallpaper(Wallpaper wallpaper) {
        gridWallpaper.setSelected(wallpaper == Wallpaper.GRID);
        voidWallpaper.setSelected(wallpaper == Wallpaper.VOID);
        neonWallpaper.setSelected(wallpaper == Wallpaper.NEON);
        circuitWallpaper.setSelected(wallpaper == Wallpaper.CIRCUIT);
        sunsetWallpaper.setSelected(wallpaper == Wallpaper.SUNSET);
    }
}
