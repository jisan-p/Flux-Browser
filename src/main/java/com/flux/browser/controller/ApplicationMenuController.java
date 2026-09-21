package com.flux.browser.controller;

import com.flux.browser.ui.WindowGeometry;
import com.flux.browser.util.Dialogs;
import com.flux.browser.web.NativeWebPage;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** FXML menus exported through JavaFX's system menu bar on macOS. */
public final class ApplicationMenuController {
    @FXML private MenuBar root;
    @FXML private Menu application, workspaceList;
    @FXML private MenuItem aboutItem, settingsItem, quitItem, newTabItem, openPdfItem, saveItem, closeTabItem,
        stopItem, zoomInItem, zoomOutItem, zoomResetItem, inspectItem, consoleItem, backItem, forwardItem,
        reopenItem, bookmarkItem, duplicateItem, nextItem, previousItem, closeOthersItem, closeRightItem, maximizeItem;
    @FXML private CheckMenuItem fullScreenItem, focusItem;
    private BrowserController browser;

    public void configure(BrowserController browser) {
        this.browser = browser;
        // The compatibility engine remains available without changing its window layout.
        root.setVisible(NativeWebPage.enabled());
        if (!NativeWebPage.enabled()) return;
        browser.window().showingProperty().addListener((o, before, showing) -> { if (showing) installApplicationMenu(); });
        if (browser.window().isShowing()) installApplicationMenu();
        browser.window().fullScreenProperty().addListener(o -> refresh());
        browser.window().maximizedProperty().addListener(o -> refresh());
    }
    private void installApplicationMenu() {
        Platform.runLater(() -> {
            if (browser.isClosed()) return;
            NativeWebPage.installApplicationMenu(application.getText(), aboutItem.getText(), settingsItem.getText(), quitItem.getText(), action -> {
                if (browser.isClosed()) return;
                switch (action) { case "about" -> about(); case "settings" -> settings(); case "quit" -> quit(); default -> { } }
            });
        });
    }
    @FXML public void refresh() {
        if (browser == null || browser.isClosed()) return;
        var state = browser.menuState();
        boolean locked = browser.focusMode();
        newTabItem.setDisable(locked || state.totalTabs() >= 200); openPdfItem.setDisable(newTabItem.isDisable());
        closeTabItem.setDisable(locked); duplicateItem.setDisable(newTabItem.isDisable());
        nextItem.setDisable(locked || state.visibleTabs() < 2); previousItem.setDisable(nextItem.isDisable());
        closeOthersItem.setDisable(!state.otherTabs()); closeRightItem.setDisable(!state.rightTabs());
        backItem.setDisable(!state.back()); forwardItem.setDisable(!state.forward()); stopItem.setDisable(!state.loading());
        inspectItem.setDisable(!state.web()); consoleItem.setDisable(!state.web()); saveItem.setDisable(!state.web());
        zoomInItem.setDisable(!state.web() || state.zoom() >= 1.5); zoomOutItem.setDisable(!state.web() || state.zoom() <= .75);
        zoomResetItem.setDisable(!state.web() || Math.abs(state.zoom()-1) < .001);
        bookmarkItem.setDisable(!state.bookmarkEnabled()); bookmarkItem.setText(state.bookmarked() ? "Remove Bookmark" : "Bookmark This Page");
        reopenItem.setDisable(!browser.canReopenTab()); focusItem.setSelected(locked); workspaceList.setDisable(locked);
        fullScreenItem.setSelected(browser.window().isFullScreen());
        fullScreenItem.setText(fullScreenItem.isSelected() ? "Exit Full Screen" : "Enter Full Screen");
        maximizeItem.setDisable(browser.window().isFullScreen());
        maximizeItem.setText(browser.window().isMaximized() ? "Restore Window" : "Zoom");
    }
    @FXML private void refreshWorkspaces() {
        refresh(); workspaceList.getItems().clear(); var group = new ToggleGroup();
        for (String name : browser.preferences().workspaces) {
            var item = new RadioMenuItem(name); item.setMnemonicParsing(false); item.setToggleGroup(group);
            item.setSelected(name.equals(browser.preferences().workspace));
            item.setOnAction(e -> browser.switchWorkspace(name)); workspaceList.getItems().add(item);
        }
    }
    @FXML private void newTab() { browser.newTab(); }
    @FXML private void location() { browser.focusAddress(); }
    @FXML private void openPdf() {
        var chooser = new FileChooser(); chooser.setTitle("Open PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF documents", "*.pdf"));
        var file = chooser.showOpenDialog(browser.window()); if (file != null) browser.openPdf(file.toPath());
    }
    @FXML private void savePage() { browser.saveCurrentPage(); }
    @FXML private void closeTab() { browser.nativeShortcut("w"); }
    @FXML private void edit(ActionEvent event) {
        String action = String.valueOf(((MenuItem)event.getSource()).getUserData());
        // Cocoa knows whether a webpage (or Inspector) owns keyboard focus. The JavaFX
        // focus owner can still point to an address field after a native view is clicked.
        if (NativeWebPage.enabled()) NativeWebPage.editFocused(action).whenComplete((handled, error) -> Platform.runLater(() -> {
            if (!browser.isClosed() && error == null && !handled) editText(action);
        }));
        else editText(action);
    }
    private void editText(String action) {
        var window = Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(browser.window());
        if (window.getScene() == null || !(window.getScene().getFocusOwner() instanceof TextInputControl input)) return;
        switch (action) {
            case "undo" -> input.undo(); case "redo" -> input.redo(); case "cut" -> input.cut();
            case "copy" -> input.copy(); case "paste" -> input.paste(); case "selectAll" -> input.selectAll(); default -> { }
        }
    }
    @FXML private void home() { browser.home(); }
    @FXML private void reload() { browser.nativeShortcut("r"); }
    @FXML private void stop() { browser.nativeShortcut("stop"); }
    @FXML private void back() { browser.nativeShortcut("back"); }
    @FXML private void forward() { browser.nativeShortcut("forward"); }
    @FXML private void zoomIn() { browser.setZoom(browser.menuState().zoom()+.1); refresh(); }
    @FXML private void zoomOut() { browser.setZoom(browser.menuState().zoom()-.1); refresh(); }
    @FXML private void zoomReset() { browser.setZoom(1); refresh(); }
    @FXML private void fullScreen() { browser.window().setFullScreen(!browser.window().isFullScreen()); }
    @FXML private void easySetup() { browser.easySetup(); }
    @FXML private void tools() { browser.features(); }
    @FXML private void inspect() { browser.showDeveloperTools(); }
    @FXML private void console() { browser.developerConsole(); }
    @FXML private void history() { browser.showLibrary(false); }
    @FXML private void historySidebar() { browser.openHistoryPanel(); }
    @FXML private void bookmarks() { browser.showLibrary(true); }
    @FXML private void bookmark() { browser.nativeShortcut("d"); }
    @FXML private void downloads() { browser.showDownloads(); }
    @FXML private void reopen() { browser.reopenClosedTab(); }
    @FXML private void workspaces() { browser.workspaces(); }
    @FXML private void focus() { browser.setFocusMode(!browser.focusMode()); refresh(); }
    @FXML private void duplicate() { browser.openNewUrl(browser.currentUrl()); }
    @FXML private void next() { browser.nativeShortcut("nextTab"); }
    @FXML private void previous() { browser.nativeShortcut("previousTab"); }
    @FXML private void closeOthers() { browser.closeCurrentOtherTabs(false); }
    @FXML private void closeRight() { browser.closeCurrentOtherTabs(true); }
    @FXML private void minimize() { browser.window().setIconified(true); }
    @FXML private void maximize() { WindowGeometry.minimum(browser.window(), WindowGeometry.screen(browser.window())); browser.window().setMaximized(!browser.window().isMaximized()); }
    @FXML private void bringToFront() { browser.window().setIconified(false); browser.window().toFront(); browser.window().requestFocus(); }
    @FXML private void settings() { bringToFront(); browser.settings(); }
    @FXML private void about() { bringToFront(); browser.about(); }
    @FXML private void help() {
        Dialogs.alert(browser.window(), "Flux Help", "Enter a website or search in the address bar.\n\n⌘T  New tab\n⌘L  Focus address bar\n⌘W  Close tab\n⇧⌘T  Reopen closed tab\n⌘Y  History\n⇧⌘B  Bookmarks\n⌘,  Settings\n⌥⌘I  Web Inspector\n\nUse Workspaces to group tabs and Easy Setup to customize Flux. Resize from any window edge or corner.");
    }
    @FXML private void quit() { browser.close(); browser.window().close(); Platform.exit(); }
    public void dispose() { if (NativeWebPage.enabled()) NativeWebPage.clearApplicationMenu(); }
}
