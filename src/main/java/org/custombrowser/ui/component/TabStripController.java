package org.custombrowser.ui.component;

import java.util.UUID;

import org.custombrowser.browser.BrowserTab;
import org.custombrowser.browser.TabManager;
import org.custombrowser.ui.BrowserActions;

import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

public final class TabStripController {

    @FXML
    private HBox tabHeaders;

    @FXML
    private ScrollPane tabScroller;

    private BrowserActions actions;
    private TabManager tabManager;

    public void setActions(BrowserActions actions) {
        this.actions = actions;
    }

    public void setTabManager(TabManager tabManager) {
        this.tabManager = tabManager;
        tabManager.tabs().addListener(
                (ListChangeListener<BrowserTab>) change -> rebuild());
        tabManager.activeTabProperty().addListener(
                (observable, oldTab, newTab) -> rebuild());
        rebuild();
    }

    @FXML
    private void newTab() {
        if (actions != null) {
            actions.newTab();
        }
    }

    @FXML
    private void reopenClosedTab() {
        if (actions != null) {
            actions.reopenClosedTab();
        }
    }

    private void rebuild() {
        if (tabManager == null) {
            return;
        }
        tabHeaders.getChildren().setAll(
                tabManager.tabs().stream().map(this::createHeader).toList());
    }

    private HBox createHeader(BrowserTab tab) {
        ImageView favicon = new ImageView();
        favicon.setFitWidth(15);
        favicon.setFitHeight(15);
        favicon.setPreserveRatio(true);
        favicon.imageProperty().bind(tab.faviconProperty());

        Label fallback = new Label("◆");
        fallback.getStyleClass().add("tab-favicon-fallback");
        fallback.visibleProperty().bind(tab.faviconProperty().isNull());
        fallback.managedProperty().bind(fallback.visibleProperty());
        StackPane icon = new StackPane(favicon, fallback);
        icon.getStyleClass().add("tab-icon");

        Label title = new Label();
        title.getStyleClass().add("tab-title");
        title.textProperty().bind(Bindings.when(tab.startPageProperty())
                .then("Start Page")
                .otherwise(tab.titleProperty()));
        title.setMaxWidth(170);
        HBox.setHgrow(title, javafx.scene.layout.Priority.ALWAYS);

        Label pin = new Label("●");
        pin.getStyleClass().add("tab-pin");
        pin.visibleProperty().bind(tab.pinnedProperty());
        pin.managedProperty().bind(pin.visibleProperty());

        Button close = new Button("×");
        close.getStyleClass().add("tab-close");
        close.setFocusTraversable(false);
        close.setOnAction(event -> tabManager.close(tab));

        HBox header = new HBox(7, icon, title, pin, close);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setMinWidth(tab.pinnedProperty().get() ? 92 : 190);
        header.setPrefWidth(tab.pinnedProperty().get() ? 108 : 230);
        header.setMaxWidth(tab.pinnedProperty().get() ? 120 : 260);
        header.getStyleClass().add("browser-tab");
        if (tab == tabManager.activeTab()) {
            header.getStyleClass().add("active");
        }
        header.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.MIDDLE) {
                tabManager.close(tab);
            } else if (event.getButton() == MouseButton.PRIMARY) {
                tabManager.select(tab);
            }
        });
        ContextMenu contextMenu = createContextMenu(tab);
        header.setOnContextMenuRequested(event -> {
            contextMenu.show(header, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        installDragReordering(header, tab);
        return header;
    }

    private ContextMenu createContextMenu(BrowserTab tab) {
        MenuItem newTab = item("New tab", event -> actions.newTab());
        MenuItem reload = item("Reload", event -> tab.reloadOrStop());
        MenuItem duplicate = item("Duplicate", event -> tabManager.duplicate(tab));
        MenuItem pin = item(
                tab.pinnedProperty().get() ? "Unpin tab" : "Pin tab",
                event -> {
                    tab.pinnedProperty().set(!tab.pinnedProperty().get());
                    rebuild();
                });
        MenuItem moveLeft = item(
                "Move left", event -> tabManager.moveBy(tab, -1));
        MenuItem moveRight = item(
                "Move right", event -> tabManager.moveBy(tab, 1));
        MenuItem close = item("Close tab", event -> tabManager.close(tab));
        MenuItem closeOthers = item(
                "Close other tabs", event -> tabManager.closeOtherTabs(tab));
        MenuItem closeRight = item(
                "Close tabs to the right",
                event -> tabManager.closeTabsToRight(tab));
        MenuItem reopen = item(
                "Reopen closed tab", event -> tabManager.reopenClosedTab());
        return new ContextMenu(
                newTab,
                reload,
                duplicate,
                pin,
                moveLeft,
                moveRight,
                close,
                closeOthers,
                closeRight,
                reopen);
    }

    private static MenuItem item(
            String text,
            javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(action);
        return item;
    }

    private void installDragReordering(HBox header, BrowserTab tab) {
        header.setOnDragDetected(event -> {
            Dragboard dragboard = header.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(tab.id().toString());
            dragboard.setContent(content);
            event.consume();
        });
        header.setOnDragOver(event -> {
            if (event.getGestureSource() != header
                    && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        header.setOnDragDropped(event -> {
            boolean completed = false;
            try {
                UUID sourceId = UUID.fromString(event.getDragboard().getString());
                BrowserTab source = tabManager.find(sourceId).orElse(null);
                if (source != null) {
                    tabManager.move(source, tabManager.tabs().indexOf(tab));
                    completed = true;
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore drag content that did not originate from a Flux tab.
            }
            event.setDropCompleted(completed);
            event.consume();
        });
    }
}
