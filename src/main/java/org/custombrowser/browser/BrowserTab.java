package org.custombrowser.browser;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.print.PrinterJob;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;

/**
 * Owns the WebView and observable browser state for one tab.
 */
public final class BrowserTab {

    private static final String DEFAULT_TITLE = "New Tab";
    private static final Set<String> EXTERNAL_SCHEMES =
            Set.of("mailto", "tel", "magnet");

    private final UUID id = UUID.randomUUID();
    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    private final WebHistory history = engine.getHistory();
    private final FaviconService faviconService;
    private final Consumer<URI> externalNavigationHandler;

    private final StringProperty title = new SimpleStringProperty(DEFAULT_TITLE);
    private final StringProperty location = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final DoubleProperty progress = new SimpleDoubleProperty(-1.0);
    private final ObjectProperty<Image> favicon = new SimpleObjectProperty<>();
    private final BooleanProperty pinned = new SimpleBooleanProperty(false);
    private final BooleanProperty startPage = new SimpleBooleanProperty(true);
    private final StringProperty failureMessage = new SimpleStringProperty();
    private final ReadOnlyBooleanWrapper canGoBack =
            new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canGoForward =
            new ReadOnlyBooleanWrapper(false);

    private URI lastRequestedUri;
    private Supplier<WebEngine> popupEngineSupplier;
    private BiConsumer<String, String> visitHandler;
    private boolean handlingExternalLocation;
    private boolean restoredPagePending;

    public BrowserTab(
            FaviconService faviconService,
            Consumer<URI> externalNavigationHandler,
            boolean startsOnStartPage) {
        this.faviconService = Objects.requireNonNull(
                faviconService, "faviconService");
        this.externalNavigationHandler = Objects.requireNonNull(
                externalNavigationHandler, "externalNavigationHandler");
        startPage.set(startsOnStartPage);
        configureEngine();
    }

    private void configureEngine() {
        engine.titleProperty().addListener((observable, oldTitle, newTitle) ->
                title.set(newTitle == null || newTitle.isBlank()
                        ? DEFAULT_TITLE
                        : newTitle));
        engine.locationProperty().addListener((observable, oldLocation, newLocation) -> {
            location.set(newLocation == null ? "" : newLocation);
            routeExternalLocation(newLocation);
        });
        engine.getLoadWorker().runningProperty().addListener(
                (observable, wasLoading, isLoading) -> loading.set(isLoading));
        engine.getLoadWorker().progressProperty().addListener(
                (observable, oldProgress, newProgress) ->
                        progress.set(newProgress.doubleValue()));
        engine.getLoadWorker().exceptionProperty().addListener(
                (observable, oldError, error) -> {
                    if (error != null) {
                        failureMessage.set(messageFor(error));
                    }
                });
        engine.getLoadWorker().stateProperty().addListener(
                (observable, oldState, state) -> {
                    if (state == Worker.State.SUCCEEDED) {
                        failureMessage.set(null);
                        loadFavicon();
                        if (visitHandler != null
                                && !location.get().isBlank()) {
                            visitHandler.accept(title.get(), location.get());
                        }
                    } else if (state == Worker.State.FAILED) {
                        Throwable error = engine.getLoadWorker().getException();
                        failureMessage.set(error == null
                                ? "The page could not be loaded."
                                : messageFor(error));
                    }
                });
        engine.setCreatePopupHandler(features ->
                popupEngineSupplier == null ? null : popupEngineSupplier.get());

        history.currentIndexProperty().addListener(
                (observable, oldIndex, newIndex) -> updateHistoryState());
        history.getEntries().addListener(
                (ListChangeListener<WebHistory.Entry>) change ->
                        updateHistoryState());
        updateHistoryState();
    }

    public UUID id() {
        return id;
    }

    public WebView webView() {
        return webView;
    }

    public WebEngine engine() {
        return engine;
    }

    public WebHistory history() {
        return history;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty locationProperty() {
        return location;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    public ObjectProperty<Image> faviconProperty() {
        return favicon;
    }

    public BooleanProperty pinnedProperty() {
        return pinned;
    }

    public BooleanProperty startPageProperty() {
        return startPage;
    }

    public StringProperty failureMessageProperty() {
        return failureMessage;
    }

    public ReadOnlyBooleanProperty canGoBackProperty() {
        return canGoBack.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty canGoForwardProperty() {
        return canGoForward.getReadOnlyProperty();
    }

    public void setPopupEngineSupplier(Supplier<WebEngine> popupEngineSupplier) {
        this.popupEngineSupplier = popupEngineSupplier;
    }

    public void setVisitHandler(BiConsumer<String, String> visitHandler) {
        this.visitHandler = visitHandler;
    }

    public void navigate(URI uri) {
        Objects.requireNonNull(uri, "uri");
        restoredPagePending = false;
        lastRequestedUri = uri;
        startPage.set(false);
        failureMessage.set(null);
        engine.load(uri.toString());
    }

    public void showStartPage() {
        restoredPagePending = false;
        startPage.set(true);
        failureMessage.set(null);
    }

    public void restore(
            URI uri,
            String restoredTitle,
            boolean restoredPinned,
            double restoredZoom,
            boolean restoredStartPage) {
        pinned.set(restoredPinned);
        setZoom(Math.max(0.5, Math.min(2.0, restoredZoom)));
        title.set(restoredTitle == null || restoredTitle.isBlank()
                ? DEFAULT_TITLE
                : restoredTitle);
        if (restoredStartPage || uri == null) {
            showStartPage();
            return;
        }
        lastRequestedUri = uri;
        location.set(uri.toString());
        startPage.set(false);
        failureMessage.set(null);
        restoredPagePending = true;
    }

    public void activate() {
        if (restoredPagePending && lastRequestedUri != null) {
            URI address = lastRequestedUri;
            restoredPagePending = false;
            navigate(address);
        }
    }

    public void retry() {
        if (lastRequestedUri != null) {
            navigate(lastRequestedUri);
        } else if (!location.get().isBlank()) {
            navigate(URI.create(location.get()));
        }
    }

    public void goBack() {
        if (canGoBack.get()) {
            history.go(-1);
        }
    }

    public void goForward() {
        if (canGoForward.get()) {
            history.go(1);
        }
    }

    public void reloadOrStop() {
        if (loading.get()) {
            engine.getLoadWorker().cancel();
        } else if (!location.get().isBlank()) {
            engine.reload();
        }
    }

    public void setZoom(double zoom) {
        webView.setZoom(zoom);
    }

    public double zoom() {
        return webView.getZoom();
    }

    public boolean find(String query, boolean backwards, boolean matchCase) {
        if (query == null || query.isBlank() || startPage.get()) {
            return false;
        }
        Object result = engine.executeScript(
                "window.find("
                        + quoteForJavaScript(query)
                        + ", "
                        + matchCase
                        + ", "
                        + backwards
                        + ", true, false, false, false)");
        return Boolean.TRUE.equals(result);
    }

    public boolean print() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null || !job.showPrintDialog(webView.getScene().getWindow())) {
            return false;
        }
        engine.print(job);
        return job.endJob();
    }

    public void dispose() {
        popupEngineSupplier = null;
        visitHandler = null;
        engine.setCreatePopupHandler(null);
        engine.getLoadWorker().cancel();
        engine.load(null);
    }

    private void updateHistoryState() {
        int index = history.getCurrentIndex();
        canGoBack.set(index > 0);
        canGoForward.set(index >= 0 && index < history.getEntries().size() - 1);
    }

    private void loadFavicon() {
        URI pageUri;
        try {
            pageUri = URI.create(location.get());
        } catch (IllegalArgumentException error) {
            return;
        }
        if (!"http".equalsIgnoreCase(pageUri.getScheme())
                && !"https".equalsIgnoreCase(pageUri.getScheme())) {
            return;
        }

        String discovered = null;
        try {
            Object value = engine.executeScript("""
                    (() => {
                      const icon = document.querySelector(
                        "link[rel~='icon'], link[rel='shortcut icon']");
                      return icon ? icon.href : null;
                    })()
                    """);
            if (value instanceof String address) {
                discovered = address;
            }
        } catch (RuntimeException ignored) {
            // Fall back to /favicon.ico.
        }

        String expectedLocation = location.get();
        faviconService.load(pageUri, discovered).thenAccept(optionalImage ->
                optionalImage.ifPresent(image -> Platform.runLater(() -> {
                    if (expectedLocation.equals(location.get())) {
                        favicon.set(image);
                    }
                })));
    }

    private void routeExternalLocation(String rawLocation) {
        if (handlingExternalLocation || rawLocation == null || rawLocation.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(rawLocation);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || "http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)
                || "file".equalsIgnoreCase(scheme)
                || "about".equalsIgnoreCase(scheme)) {
            return;
        }
        if (!EXTERNAL_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            engine.getLoadWorker().cancel();
            failureMessage.set("Blocked unsupported URL scheme: " + scheme);
            return;
        }

        handlingExternalLocation = true;
        try {
            engine.getLoadWorker().cancel();
            externalNavigationHandler.accept(uri);
        } finally {
            handlingExternalLocation = false;
        }
    }

    private static String messageFor(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private static String quoteForJavaScript(String value) {
        return "'"
                + value.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n")
                + "'";
    }
}
