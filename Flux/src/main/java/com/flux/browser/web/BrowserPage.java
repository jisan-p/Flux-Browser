package com.flux.browser.web;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.beans.property.*;
import javafx.concurrent.Worker;
import javafx.scene.Node;

/** Engine-neutral, FX-thread-owned tab state. Script evaluation is always asynchronous. */
public abstract class BrowserPage implements AutoCloseable {
    public final ReadOnlyStringWrapper location = new ReadOnlyStringWrapper("");
    public final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper("");
    public final ReadOnlyDoubleWrapper zoomLevel = new ReadOnlyDoubleWrapper(1);
    public final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(0);
    public final ReadOnlyObjectWrapper<Worker.State> state = new ReadOnlyObjectWrapper<>(Worker.State.READY);
    public final ReadOnlyBooleanWrapper back = new ReadOnlyBooleanWrapper(false);
    public final ReadOnlyBooleanWrapper forward = new ReadOnlyBooleanWrapper(false);
    public Runnable closeRequested = () -> {};
    public Consumer<BrowserPage> popup = page -> page.close();
    public Consumer<String> shortcut = key -> {};
    public abstract Node view();
    public abstract void load(String url);
    public abstract void back();
    public abstract void forward();
    public abstract void reload();
    public abstract void stop();
    public abstract void zoom(double value);
    public abstract void focus();
    public abstract void visible(boolean value);
    public abstract CompletableFuture<String> evaluate(String script);
    @Override public abstract void close();
}
