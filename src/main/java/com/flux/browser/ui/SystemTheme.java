package com.flux.browser.ui;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ObservableValue;

/** Public JavaFX preferences when present; JavaFX 21 remains compatible and defaults to dark. */
public final class SystemTheme implements AutoCloseable {
    private ObservableValue<?> scheme;
    private final InvalidationListener listener;
    public SystemTheme(Runnable changed) {
        listener = o -> changed.run();
        try {
            Object preferences = Platform.class.getMethod("getPreferences").invoke(null);
            Class<?> api = Class.forName("javafx.application.Platform$Preferences");
            scheme = (ObservableValue<?>)api.getMethod("colorSchemeProperty").invoke(preferences);
            scheme.addListener(listener);
        } catch (ReflectiveOperationException ignored) { }
    }
    public boolean light() { return scheme != null && "LIGHT".equals(String.valueOf(scheme.getValue())); }
    public void close() { if(scheme!=null)scheme.removeListener(listener); }
}
