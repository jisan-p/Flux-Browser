package com.flux.browser.util;

import com.flux.browser.controller.EntryDialogController;
import com.flux.browser.controller.MessageDialogController;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class Dialogs {
    private Dialogs() {}

    public static boolean confirm(Window owner, String title, String message) {
        var view = Views.<MessageDialogController>load("MessageDialog");
        Stage stage = stage(owner, view.root(), title);
        view.controller().configure(title, message, null, true, stage);
        stage.showAndWait();
        return view.controller().accepted();
    }

    public static void alert(Window owner, String title, String message) {
        var view = Views.<MessageDialogController>load("MessageDialog");
        Stage stage = stage(owner, view.root(), title);
        view.controller().configure(title, message, null, false, stage);
        stage.showAndWait();
    }

    public static String prompt(Window owner, String title, String message, String initial) {
        var view = Views.<MessageDialogController>load("MessageDialog");
        Stage stage = stage(owner, view.root(), title);
        view.controller().configure(title, message, initial == null ? "" : initial, true, stage);
        stage.showAndWait();
        return view.controller().accepted() ? view.controller().value() : null;
    }

    public static void edit(Window owner, String heading, String title, String url, String category,
                            Function<EntryDialogController.Draft, CompletableFuture<?>> save, Runnable saved) {
        var view = Views.<EntryDialogController>load("EntryDialog");
        Stage stage = stage(owner, view.root(), heading);
        view.controller().configure(stage, heading, title, url, category, save, saved);
        stage.show();
    }

    private static Stage stage(Window owner, Parent root, String title) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title + " · Flux");
        stage.setResizable(false);
        if (owner instanceof Stage parent && parent.getScene().getRoot().getStyleClass().contains("cyan-theme")) {
            root.getStyleClass().add("cyan-theme");
        }
        stage.setScene(new Scene(root));
        return stage;
    }
}
