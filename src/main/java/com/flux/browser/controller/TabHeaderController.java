package com.flux.browser.controller;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

public final class TabHeaderController {
    @FXML private HBox root;
    @FXML private Label titleLabel, tabIcon;
    @FXML private ProgressIndicator spinner;
    @FXML private Tooltip titleTooltip;
    @FXML private Button closeButton;
    @FXML private javafx.scene.control.ContextMenu tabMenu;
    @FXML private javafx.scene.control.MenuItem newItem, duplicateItem, closeItem, closeOthersItem, closeRightItem, reopenItem;
    @FXML private javafx.scene.control.CheckMenuItem focusModeItem;
    private BrowserController browser;
    private WebTabController page;
    private Runnable selectAction, closeAction;

    public void configure(BrowserController browser, WebTabController tab, Runnable select, Runnable close) {
        this.browser = browser; this.page = tab;
        selectAction = select;
        closeAction = close;
        titleLabel.textProperty().bind(tab.titleProperty());
        titleTooltip.textProperty().bind(Bindings.concat(tab.titleProperty(), "\n", tab.locationProperty()));
        spinner.visibleProperty().bind(tab.loadingProperty());
        tabIcon.visibleProperty().bind(tab.loadingProperty().not());
        closeButton.accessibleTextProperty().bind(Bindings.concat("Close ", tab.titleProperty()));
    }

    public void selected(boolean selected) { root.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), selected); }
    @FXML private void clicked(MouseEvent event) {
        if (event.getButton() == MouseButton.MIDDLE) close();
        else if (event.getButton() == MouseButton.PRIMARY && selectAction != null) selectAction.run();
    }
    @FXML private void close() { if (closeAction != null) closeAction.run(); }
    @FXML private void contextMenu(javafx.scene.input.ContextMenuEvent event) {
        newItem.setDisable(browser.focusMode()); duplicateItem.setDisable(browser.focusMode());
        closeItem.setDisable(browser.focusMode());
        closeOthersItem.setDisable(!browser.hasOtherTabs(page, false));
        closeRightItem.setDisable(!browser.hasOtherTabs(page, true));
        reopenItem.setDisable(!browser.canReopenTab());
        focusModeItem.setSelected(browser.focusMode());
        com.flux.browser.ui.Menus.show(tabMenu, root, event);
    }
    @FXML private void newTab() { browser.newTab(); }
    @FXML private void reload() { page.reload(); }
    @FXML private void copy() { browser.copyAddress(page.locationProperty().get()); }
    @FXML private void duplicate() { browser.openNewUrl(page.locationProperty().get()); }
    @FXML private void closeOthers() { browser.closeOtherTabs(page, false); }
    @FXML private void closeRight() { browser.closeOtherTabs(page, true); }
    @FXML private void reopen() { browser.reopenClosedTab(); }
    @FXML private void toggleFocusMode() { browser.setFocusMode(!browser.focusMode()); }

    public void dispose() {
        tabMenu.hide();
        titleLabel.textProperty().unbind(); titleTooltip.textProperty().unbind();
        spinner.visibleProperty().unbind(); tabIcon.visibleProperty().unbind();
        closeButton.accessibleTextProperty().unbind();
        browser = null; page = null; selectAction = null; closeAction = null;
    }
}
