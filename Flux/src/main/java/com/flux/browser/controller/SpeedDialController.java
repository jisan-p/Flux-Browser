package com.flux.browser.controller;

import com.flux.browser.db.SpeedDialDAO;
import com.flux.browser.model.SpeedDial;
import com.flux.browser.util.Views;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.ScrollPane;
import javafx.util.Duration;

public final class SpeedDialController {
    @FXML private ScrollPane root;
    @FXML private VBox background, homeContent, dialSection;
    @FXML private HBox searchBox, clockRow;
    @FXML private Label clockLabel, dateLabel, dialNote;
    @FXML private TextField searchField;
    @FXML private Button addDialButton;
    @FXML private TilePane tiles;
    @FXML private javafx.scene.control.ContextMenu homeMenu;
    @FXML private javafx.scene.control.MenuItem addMenuItem;
    @FXML private void contextMenu(javafx.scene.input.ContextMenuEvent event) {
        addMenuItem.setDisable(!browser.storageAvailable());
        com.flux.browser.ui.Menus.show(homeMenu, root, event);
    }
    @FXML private void reloadDials() { refresh(); }
    @FXML private void settings() { browser.settings(); }
    private BrowserController browser;
    private SpeedDialDAO dao;
    private Timeline clock;
    private int request;
    private boolean disposed, active, loading, dirty = true;
    private List<SpeedDial> renderedItems;
    private Map<SpeedDial, Node> tileViews = Map.of();

    public void configure(BrowserController browser, SpeedDialDAO dao) {
        this.browser = browser;
        this.dao = dao;
        clock = new Timeline(new KeyFrame(Duration.minutes(1), event -> updateClock()));
        clock.setCycleCount(Timeline.INDEFINITE);
        root.viewportBoundsProperty().addListener((o,before,after) -> { background.setMinHeight(after.getHeight()); layoutTiles(after.getWidth()); });
        homeContent.paddingProperty().addListener(o -> layoutTiles(root.getViewportBounds().getWidth()));
        applyAppearance();
    }

    /** Called on the FX thread when this tab's home page becomes visible or hidden. */
    public void setActive(boolean active) {
        if (disposed || this.active == active) return;
        this.active = active;
        if (active) {
            updateClock();
            if(browser.preferences().appearance.clock)clock.play();
            loadIfNeeded();
        } else clock.stop();
    }

    public void applyAppearance() {
        if(browser==null)return;
        var a=browser.preferences().appearance;
        background.setAlignment(switch(a.dialPosition){case "Center"->javafx.geometry.Pos.CENTER;case "Bottom"->javafx.geometry.Pos.BOTTOM_CENTER;default->javafx.geometry.Pos.TOP_CENTER;});
        show(searchBox,a.search);show(dialSection,a.dials);show(clockRow,a.clock);
        if(clock!=null) { if(active && a.clock)clock.play();else clock.stop(); }
        layoutTiles(root.getViewportBounds().getWidth());
    }
    private void layoutTiles(double width) {
        if(browser==null || width<=0)return;
        var a=browser.preferences().appearance;
        int columns=a.columns;
        double tile=a.bigTiles?224:184;
        tiles.setPrefTileWidth(tile);tiles.setPrefTileHeight(a.bigTiles?162:138);
        double available=Math.max(120,Math.min(homeContent.getMaxWidth(),width)-homeContent.getPadding().getLeft()-homeContent.getPadding().getRight());
        int fit=Math.max(1,Math.min(columns,(int)((available+18)/(tile+18))));
        tiles.setMaxWidth(fit*(tile+18)-18);tiles.setPrefColumns(fit);
    }
    private static void show(Node node,boolean value) {node.setVisible(value);node.setManaged(value);}
    @FXML private void customize() { if(browser!=null)browser.easySetup(); }
    private void updateClock() {
        var now = LocalDateTime.now();
        clockLabel.setText(now.format(DateTimeFormatter.ofPattern("HH:mm")));
        dateLabel.setText(now.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)).toUpperCase(Locale.ROOT));
    }

    public void refresh() {
        if (disposed || browser == null) return;
        request++;
        dirty = true;
        loadIfNeeded();
    }

    private void loadIfNeeded() {
        if (disposed || !active || !dirty || loading || browser == null) return;
        dirty = false;
        int version = request;
        addDialButton.setDisable(!browser.storageAvailable());
        if (!browser.storageAvailable()) {
            render(SpeedDial.starters());
            dialNote.setVisible(true); dialNote.setManaged(true);
            dialNote.setText("Starter shortcuts · Connect storage in Settings to customize your Speed Dial.");
            return;
        }
        loading = true;
        dao.getSpeedDials().whenComplete((items, error) -> Platform.runLater(() -> {
            loading = false;
            if (disposed) return;
            if (request != version || !active) {
                if (!active) dirty = true;
                loadIfNeeded();
                return;
            }
            if (error != null) {
                browser.storageChanged();
                render(SpeedDial.starters());
                dialNote.setVisible(true); dialNote.setManaged(true);
                dialNote.setText("Saved shortcuts could not be loaded. Showing starter shortcuts.");
                addDialButton.setDisable(true);
            } else {
                render(items);
                dialNote.setText(items.isEmpty() ? "An open canvas. Add your first favorite site." : "Your favorite places, one click away.");
                dialNote.setVisible(items.isEmpty()); dialNote.setManaged(items.isEmpty());
            }
        }));
    }

    private void render(List<SpeedDial> items) {
        if (items.equals(renderedItems)) return;
        Map<SpeedDial, Node> nextViews = new HashMap<>();
        List<Node> nodes = new ArrayList<>(items.size());
        for (SpeedDial item : items) {
            Node node = tileViews.get(item);
            if (node == null) {
                var view = Views.<DialTileController>load("DialTile");
                view.controller().configure(browser, item);
                node = view.root();
            }
            nextViews.put(item, node);
            nodes.add(node);
        }
        nodes.add(addDialButton);
        tiles.getChildren().setAll(nodes);
        tileViews = nextViews;
        renderedItems = List.copyOf(items);
    }

    @FXML private void search() { if (browser != null) browser.navigateTo(searchField.getText()); }
    @FXML private void add() { if (browser != null) browser.editDial(null); }
    public void dispose() {
        disposed = true; request++; homeMenu.hide();
        if (clock != null) clock.stop();
        tileViews = Map.of();
        renderedItems = null;
    }
}
