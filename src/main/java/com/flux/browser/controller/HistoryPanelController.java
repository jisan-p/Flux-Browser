package com.flux.browser.controller;

import com.flux.browser.model.HistoryEntry;
import com.flux.browser.util.Views;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class HistoryPanelController {
    @FXML private VBox root;
    @FXML private TextField search;
    @FXML private Label status;
    @FXML private ListView<HistoryEntry> recentEntries;
    private BrowserController browser;
    private int request;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(200));
    public void configure(BrowserController browser) {
        this.browser=browser; debounce.setOnFinished(e->refresh());
        search.textProperty().addListener((o,b,a)->debounce.playFromStart());
        recentEntries.setCellFactory(list->new ListCell<>() {
            private Views.View<LibraryRowController> row;
            @Override protected void updateItem(HistoryEntry item,boolean empty) {
                super.updateItem(item,empty);setText(null);
                if(empty || item==null){setGraphic(null);return;}
                if(row==null){row=Views.load("LibraryRow");((javafx.scene.layout.Region)row.root()).prefWidthProperty().bind(widthProperty().subtract(14));setPrefWidth(0);}
                row.controller().configure(browser,null,item);row.controller().compact();
                var zone=java.time.ZoneId.systemDefault();
                row.controller().dateHeading(getIndex()<=0 || !list.getItems().get(getIndex()-1).visitedAt().atZone(zone).toLocalDate().equals(item.visitedAt().atZone(zone).toLocalDate()));
                setGraphic(row.root());
            }
        });
    }
    public void refresh() {
        if(browser==null || !root.isVisible())return;
        int version=++request;
        if(!browser.storageAvailable()){recentEntries.getItems().clear();status.setText("Storage offline. Reconnect in Settings.");return;}
        status.setText("Loading…");
        browser.history().getHistory(search.getText()).whenComplete((rows,error)->Platform.runLater(()->{
            if(version!=request || browser.isClosed() || !root.isVisible())return;
            if(error!=null){recentEntries.getItems().clear();status.setText("Could not load history.");}
            else {recentEntries.getItems().setAll(rows);status.setText(rows.size()+" visits · newest first");}
        }));
    }
    @FXML private void expand(){browser.showLibrary(false);}
    @FXML private void close(){browser.closeHistoryPanel();}
    public void dispose(){request++;debounce.stop();}
}
