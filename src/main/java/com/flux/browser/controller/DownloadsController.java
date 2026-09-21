package com.flux.browser.controller;

import com.flux.browser.feature.*;
import com.flux.browser.util.Views;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import java.util.Locale;

/** Downloads remain owned by the browser; this view only filters their live state. */
public final class DownloadsController {
    @FXML private BorderPane root;
    @FXML private Parent managerHeader;
    @FXML private ManagerHeaderController managerHeaderController;
    @FXML private ToggleGroup periods, types;
    @FXML private ToggleButton allPeriod, allTypes;
    @FXML private TextField downloadSearch;
    @FXML private Label heading, resultLabel, emptyLabel;
    @FXML private Button clearDownloadsButton;
    @FXML private ListView<Downloads.Item> downloadEntries;
    private BrowserController browser;
    private final ListChangeListener<Downloads.Item> changes = c -> refresh();
    public void configure(BrowserController browser) {
        this.browser=browser; managerHeaderController.configure(browser);
        browser.downloads().items.addListener(changes);
        downloadSearch.textProperty().addListener((o,before,after)->refresh());
        downloadEntries.setCellFactory(list -> new ListCell<>() {
            private Views.View<DownloadRowController> row;
            @Override protected void updateItem(Downloads.Item item, boolean empty) {
                super.updateItem(item,empty); setText(null);
                if (empty || item==null) { setGraphic(null); return; }
                if (row==null) {
                    row=Views.load("DownloadRow");
                    ((javafx.scene.layout.Region)row.root()).prefWidthProperty().bind(widthProperty().subtract(20)); setPrefWidth(0);
                }
                row.controller().configure(browser,item); setGraphic(row.root());
            }
        });
    }
    public void show() { managerHeaderController.select(managerHeader,"Downloads"); refresh(); }
    @FXML private void filter() {
        if(periods.getSelectedToggle()==null)periods.selectToggle(allPeriod);
        if(types.getSelectedToggle()==null)types.selectToggle(allTypes);
        refresh();
    }
    public void refresh() {
        if(browser==null || !root.isVisible())return;
        var range=BrowsingPeriod.valueOf(periods.getSelectedToggle().getUserData().toString()).range();
        String type=types.getSelectedToggle().getUserData().toString(), query=downloadSearch.getText().trim().toLowerCase(Locale.ROOT);
        var matches=browser.downloads().items.stream().filter(d->range.contains(d.startedAt()))
                .filter(d->type.equals("ALL") || type.equals(d.kind()))
                .filter(d->(d.name()+" "+d.path()).toLowerCase(Locale.ROOT).contains(query)).toList();
        if(!matches.equals(downloadEntries.getItems()))downloadEntries.getItems().setAll(matches);
        heading.setText(((ToggleButton)periods.getSelectedToggle()).getText());
        resultLabel.setText(matches.size()+(matches.size()==1?" download · this session":" downloads · this session"));
        emptyLabel.setText(query.isEmpty()?"No downloads in this period.":"No downloads match your search.");
        clearDownloadsButton.setDisable(browser.downloads().items.stream().noneMatch(d->!d.active()));
    }
    @FXML private void clear() { browser.downloads().clearFinished(); }
    @FXML private void openPdf() {
        FileChooser chooser=new FileChooser(); chooser.setTitle("Open PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF documents","*.pdf"));
        var file=chooser.showOpenDialog(browser.window()); if(file!=null)browser.openPdf(file.toPath());
    }
    public void dispose() { if(browser!=null)browser.downloads().items.removeListener(changes); }
}
