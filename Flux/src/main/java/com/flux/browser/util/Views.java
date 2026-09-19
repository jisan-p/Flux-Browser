package com.flux.browser.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public final class Views {
    public record View<T>(Parent root, T controller) {}
    private Views() {}

    public static <T> View<T> load(String name) {
        var resource = Objects.requireNonNull(Views.class.getResource("/com/flux/browser/view/" + name + ".fxml"), name);
        FXMLLoader loader = new FXMLLoader(resource);
        try { return new View<>(loader.load(), loader.getController()); }
        catch (IOException error) { throw new UncheckedIOException("Unable to load " + name + ".fxml", error); }
    }
}
