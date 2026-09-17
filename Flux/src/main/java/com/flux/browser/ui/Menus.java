package com.flux.browser.ui;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.ContextMenuEvent;

/** Keeps detached popup windows in the same palette as their owning browser. */
public final class Menus {
    private Menus() {}
    public static void show(ContextMenu menu, Node owner, ContextMenuEvent event) {
        var shell = owner.getScene().getRoot();
        menu.setStyle(shell.getStyle());
        menu.getStyleClass().remove("light-theme");
        if (shell.getStyleClass().contains("light-theme")) menu.getStyleClass().add("light-theme");
        menu.show(owner, event.getScreenX(), event.getScreenY());
        var popup = menu.getScene().getRoot();
        popup.getStylesheets().setAll(shell.getStylesheets());
        event.consume();
    }
}
