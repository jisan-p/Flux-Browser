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
    private Runnable selectAction, closeAction;

    public void configure(WebTabController tab, Runnable select, Runnable close) {
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
    public void dispose() {
        titleLabel.textProperty().unbind(); titleTooltip.textProperty().unbind();
        spinner.visibleProperty().unbind(); tabIcon.visibleProperty().unbind();
        closeButton.accessibleTextProperty().unbind();
        selectAction = null; closeAction = null;
    }
}
