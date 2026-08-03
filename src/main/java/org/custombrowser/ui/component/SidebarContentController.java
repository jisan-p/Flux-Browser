package org.custombrowser.ui.component;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import org.custombrowser.browser.BrowserTab;
import org.custombrowser.browser.FaviconService;
import org.custombrowser.browser.TabManager;
import org.custombrowser.download.DownloadManager;
import org.custombrowser.download.DownloadTask;
import org.custombrowser.download.DownloadTask.Status;
import org.custombrowser.gx.GxCleanerService;
import org.custombrowser.gx.GxCleanerService.CleanerSelection;
import org.custombrowser.gx.ResourceMonitor;
import org.custombrowser.gx.ResourceMonitor.ResourceSample;
import org.custombrowser.gx.TabSuspensionService;
import org.custombrowser.persistence.PersistenceModels.Bookmark;
import org.custombrowser.persistence.PersistenceModels.Download;
import org.custombrowser.persistence.PersistenceModels.Visit;
import org.custombrowser.persistence.PersistenceService;
import org.custombrowser.settings.BrowsingDataService;
import org.custombrowser.ui.BrowserActions;
import org.custombrowser.ui.state.BrowserUiState;
import org.custombrowser.ui.state.BrowserUiState.SidebarPanel;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

/** Controller shared by the five independently loaded sidebar documents. */
final class SidebarContentController implements AutoCloseable {

    private final SidebarPanel panel;
    private final BrowserUiState uiState;
    private final PersistenceService persistenceService;
    private final DownloadManager downloadManager;
    private final BrowsingDataService browsingDataService;
    private final ResourceMonitor resourceMonitor;
    private final GxCleanerService cleanerService;
    private final XYChart.Series<Number, Number> cpuSeries =
            new XYChart.Series<>();
    private final XYChart.Series<Number, Number> memorySeries =
            new XYChart.Series<>();
    private final Consumer<ResourceSample> resourceListener =
            sample -> Platform.runLater(() -> updateResourceMetrics(sample));

    @FXML
    private VBox panelRoot;
    @FXML
    private Label cpuValue;
    @FXML
    private ProgressBar cpuProgress;
    @FXML
    private Label memoryValue;
    @FXML
    private ProgressBar memoryProgress;
    @FXML
    private Label jvmValue;
    @FXML
    private Label tabCountValue;
    @FXML
    private LineChart<Number, Number> cpuChart;
    @FXML
    private LineChart<Number, Number> memoryChart;
    @FXML
    private CheckBox autoSuspendEnabled;
    @FXML
    private Slider autoSuspendMinutes;
    @FXML
    private Label autoSuspendValue;
    @FXML
    private ListView<BrowserTab> hotTabList;
    @FXML
    private Label suspensionStatus;
    @FXML
    private CheckBox cleanExpiredHistory;
    @FXML
    private CheckBox cleanCompletedDownloads;
    @FXML
    private CheckBox cleanFavicons;
    @FXML
    private CheckBox cleanOldSessions;
    @FXML
    private Label cleanerPreview;
    @FXML
    private TextField bookmarkSearch;
    @FXML
    private ListView<Bookmark> bookmarkList;
    @FXML
    private Label bookmarkStatus;
    @FXML
    private TextField historySearch;
    @FXML
    private ListView<Visit> historyList;
    @FXML
    private Label historyStatus;
    @FXML
    private TextField downloadSearch;
    @FXML
    private ListView<Download> downloadList;
    @FXML
    private ListView<DownloadTask> activeDownloadList;
    @FXML
    private Label downloadStatus;
    @FXML
    private TextField settingsSearch;
    @FXML
    private VBox appearanceSettings;
    @FXML
    private VBox privacySettings;
    @FXML
    private Label databaseStatus;
    @FXML
    private Label browsingDataStatus;
    @FXML
    private Label cacheLimitation;

    private BrowserActions actions;
    private TabManager tabManager;
    private TabSuspensionService suspensionService;
    private long resourceSequence;
    private boolean resourceListenerInstalled;
    private ListChangeListener<BrowserTab> tabsListener;
    private ChangeListener<BrowserTab> activeTabListener;
    private ListChangeListener<DownloadTask> downloadTasksListener;

    SidebarContentController(
            SidebarPanel panel,
            BrowserUiState uiState,
            PersistenceService persistenceService,
            DownloadManager downloadManager,
            BrowsingDataService browsingDataService,
            ResourceMonitor resourceMonitor,
            FaviconService faviconService) {
        this.panel = Objects.requireNonNull(panel, "panel");
        this.uiState = Objects.requireNonNull(uiState, "uiState");
        this.persistenceService = Objects.requireNonNull(
                persistenceService, "persistenceService");
        this.downloadManager = Objects.requireNonNull(
                downloadManager, "downloadManager");
        this.browsingDataService = Objects.requireNonNull(
                browsingDataService, "browsingDataService");
        this.resourceMonitor = Objects.requireNonNull(
                resourceMonitor, "resourceMonitor");
        cleanerService = new GxCleanerService(
                persistenceService,
                Objects.requireNonNull(faviconService, "faviconService"));
    }

    @FXML
    private void initialize() {
        switch (panel) {
            case GX_CONTROL -> initializeGx();
            case BOOKMARKS -> initializeBookmarks();
            case HISTORY -> initializeHistory();
            case DOWNLOADS -> initializeDownloads();
            case SETTINGS -> initializeSettings();
            case NONE -> throw new IllegalStateException(
                    "NONE cannot have sidebar content");
        }
    }

    void setActions(BrowserActions actions) {
        this.actions = actions;
    }

    void setTabManager(TabManager nextManager) {
        if (panel != SidebarPanel.GX_CONTROL || tabManager == nextManager) {
            tabManager = nextManager;
            return;
        }
        detachTabManagerListeners();
        tabManager = nextManager;
        if (tabManager == null) {
            suspensionService = null;
            return;
        }
        suspensionService = new TabSuspensionService(tabManager);
        resourceMonitor.setTabCountSupplier(() -> tabManager.tabs().size());
        tabsListener = change -> refreshHotTabs();
        activeTabListener = (observable, oldTab, newTab) -> refreshHotTabs();
        tabManager.tabs().addListener(tabsListener);
        tabManager.activeTabProperty().addListener(activeTabListener);
        refreshHotTabs();
    }

    void onShown() {
        switch (panel) {
            case GX_CONTROL -> {
                resourceMonitor.start();
                refreshHotTabs();
                previewCleaner();
            }
            case BOOKMARKS -> refreshBookmarks();
            case HISTORY -> refreshHistory();
            case DOWNLOADS -> refreshDownloads();
            case SETTINGS, NONE -> {
            }
        }
    }

    void onHidden() {
        if (panel == SidebarPanel.GX_CONTROL) {
            resourceMonitor.pause();
        }
    }

    @Override
    public void close() {
        onHidden();
        if (resourceListenerInstalled) {
            resourceMonitor.removeListener(resourceListener);
            resourceListenerInstalled = false;
        }
        detachTabManagerListeners();
        if (downloadTasksListener != null) {
            downloadManager.tasks().removeListener(downloadTasksListener);
            downloadTasksListener = null;
        }
        if (activeDownloadList != null) {
            activeDownloadList.setItems(null);
        }
    }

    void refreshBookmarks() {
        if (bookmarkSearch == null) {
            return;
        }
        persistenceService.bookmarks(bookmarkSearch.getText())
                .whenComplete((items, error) -> Platform.runLater(() -> {
                    if (bookmarkList == null) {
                        return;
                    }
                    if (error != null) {
                        bookmarkStatus.setText("DATABASE ERROR");
                    } else {
                        bookmarkList.getItems().setAll(items);
                        bookmarkStatus.setText(items.size() + " BOOKMARKS");
                    }
                }));
    }

    private void initializeGx() {
        cpuSeries.setName("PROCESS CPU");
        memorySeries.setName("RESIDENT MEMORY");
        cpuChart.getData().add(cpuSeries);
        memoryChart.getData().add(memorySeries);
        autoSuspendEnabled.selectedProperty().bindBidirectional(
                uiState.autoSuspendEnabledProperty());
        autoSuspendMinutes.setValue(uiState.autoSuspendMinutesProperty().get());
        autoSuspendMinutes.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    int minutes = Math.max(1, newValue.intValue());
                    uiState.autoSuspendMinutesProperty().set(minutes);
                    autoSuspendValue.setText(minutes + " MINUTES");
                });
        autoSuspendValue.setText(
                uiState.autoSuspendMinutesProperty().get() + " MINUTES");
        hotTabList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(BrowserTab tab, boolean empty) {
                super.updateItem(tab, empty);
                if (empty || tab == null) {
                    setText(null);
                    return;
                }
                String state = tab.suspendedProperty().get()
                        ? "SUSPENDED"
                        : tab.loadingProperty().get() ? "LOADING" : "READY";
                String protectedState = tab.suspensionExcludedProperty().get()
                        ? " · KEEP ACTIVE"
                        : "";
                double score = suspensionService == null
                        ? 0.0
                        : suspensionService.activityScore(tab);
                setText("%s\n%s · ACTIVITY %.0f%s".formatted(
                        tab.titleProperty().get(), state, score, protectedState));
            }
        });
        resourceMonitor.addListener(resourceListener);
        resourceListenerInstalled = true;
    }

    private void initializeBookmarks() {
        bookmarkSearch.textProperty().addListener(
                (observable, oldText, newText) -> refreshBookmarks());
        bookmarkList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2) {
                openSelectedBookmark();
            }
        });
    }

    private void initializeHistory() {
        historySearch.textProperty().addListener(
                (observable, oldText, newText) -> refreshHistory());
        historyList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2) {
                openSelectedVisit();
            }
        });
    }

    private void initializeDownloads() {
        downloadSearch.textProperty().addListener(
                (observable, oldText, newText) -> refreshDownloads());
        activeDownloadList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(DownloadTask task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label details = new Label(task.toString());
                details.setWrapText(true);
                details.getStyleClass().add("download-details");
                ProgressBar progress = new ProgressBar(
                        task.progressProperty().get());
                progress.setMaxWidth(Double.MAX_VALUE);
                progress.getStyleClass().add("download-progress");
                VBox cell = new VBox(5, details, progress);
                if (task.failureMessageProperty().get() != null) {
                    Label failure = new Label(task.failureMessageProperty().get());
                    failure.setWrapText(true);
                    failure.getStyleClass().add("warning-copy");
                    cell.getChildren().add(failure);
                }
                setText(null);
                setGraphic(cell);
            }
        });
        activeDownloadList.setItems(downloadManager.tasks());
        downloadManager.tasks().forEach(this::observeDownloadTask);
        downloadTasksListener = change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(this::observeDownloadTask);
                }
            }
            activeDownloadList.refresh();
        };
        downloadManager.tasks().addListener(downloadTasksListener);
    }

    private void initializeSettings() {
        settingsSearch.textProperty().addListener(
                (observable, oldText, newText) -> filterSettings(newText));
        databaseStatus.setText(persistenceService.enabled()
                ? "CONNECTED · POSTGRESQL"
                : "TEST MODE · NO DATABASE");
        cacheLimitation.setText(browsingDataService.webViewCacheLimitation());
    }

    @FXML
    private void bookmarkCurrentPage() {
        if (actions != null) {
            actions.bookmarkCurrentPage();
        }
    }

    @FXML
    private void openSelectedBookmark() {
        Bookmark selected = bookmarkList.getSelectionModel().getSelectedItem();
        if (selected != null && actions != null) {
            actions.navigate(selected.url());
        }
    }

    @FXML
    private void deleteSelectedBookmark() {
        Bookmark selected = bookmarkList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            persistenceService.deleteBookmark(selected.id())
                    .thenRun(() -> Platform.runLater(this::refreshBookmarks));
        }
    }

    @FXML
    private void clearBookmarks() {
        if (confirm("Delete all bookmarks?")) {
            persistenceService.clearBookmarks()
                    .thenRun(() -> Platform.runLater(this::refreshBookmarks));
        }
    }

    @FXML
    private void openSelectedVisit() {
        Visit selected = historyList.getSelectionModel().getSelectedItem();
        if (selected != null && actions != null) {
            actions.navigate(selected.url());
        }
    }

    @FXML
    private void deleteSelectedVisit() {
        Visit selected = historyList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            persistenceService.deleteVisit(selected.id())
                    .thenRun(() -> Platform.runLater(this::refreshHistory));
        }
    }

    @FXML
    private void clearHistory() {
        if (confirm("Delete all browsing history?")) {
            persistenceService.clearVisits()
                    .thenRun(() -> Platform.runLater(this::refreshHistory));
        }
    }

    @FXML
    private void deleteSelectedDownload() {
        Download selected = downloadList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            persistenceService.deleteDownload(selected.id())
                    .thenRun(() -> Platform.runLater(this::refreshDownloads));
        }
    }

    @FXML
    private void clearDownloads() {
        boolean running = downloadManager.tasks().stream()
                .anyMatch(task -> task.status() == Status.RUNNING
                        || task.status() == Status.QUEUED);
        if (running) {
            setDownloadStatus("CANCEL ACTIVE DOWNLOADS FIRST");
            return;
        }
        if (confirm("Delete all download metadata?")) {
            persistenceService.clearDownloads()
                    .thenRun(() -> Platform.runLater(this::refreshDownloads));
        }
    }

    @FXML
    private void cancelActiveDownload() {
        downloadManager.cancel(activeDownloadList
                .getSelectionModel().getSelectedItem());
    }

    @FXML
    private void retryActiveDownload() {
        downloadManager.retry(activeDownloadList
                .getSelectionModel().getSelectedItem());
    }

    @FXML
    private void openActiveDownload() {
        downloadManager.open(activeDownloadList
                .getSelectionModel().getSelectedItem());
    }

    @FXML
    private void revealActiveDownload() {
        downloadManager.reveal(activeDownloadList
                .getSelectionModel().getSelectedItem());
    }

    @FXML
    private void suspendSelectedTab() {
        BrowserTab selected = hotTabList.getSelectionModel().getSelectedItem();
        if (selected == null || suspensionService == null) {
            suspensionStatus.setText("SELECT A BACKGROUND TAB");
            return;
        }
        if (!suspensionService.canSuspend(selected)) {
            suspensionStatus.setText(suspensionBlockReason(selected));
            return;
        }
        if (!confirmTabSuspension(selected)) {
            return;
        }
        if (suspensionService.suspend(selected)) {
            suspensionStatus.setText("TAB SUSPENDED · PAGE STATE RELEASED");
        } else {
            suspensionStatus.setText(suspensionBlockReason(selected));
        }
        refreshHotTabs();
    }

    @FXML
    private void resumeSelectedTab() {
        BrowserTab selected = hotTabList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            suspensionStatus.setText("SELECT A SUSPENDED TAB");
            return;
        }
        suspensionStatus.setText(selected.resume()
                ? "TAB RESUMED · URL RELOADED"
                : "TAB IS ALREADY LOADED");
        refreshHotTabs();
    }

    @FXML
    private void toggleSuspensionExclusion() {
        BrowserTab selected = hotTabList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            suspensionStatus.setText("SELECT A TAB");
            return;
        }
        boolean excluded = !selected.suspensionExcludedProperty().get();
        selected.suspensionExcludedProperty().set(excluded);
        suspensionStatus.setText(excluded
                ? "TAB EXCLUDED FROM SUSPENSION"
                : "TAB MAY BE SUSPENDED");
        refreshHotTabs();
    }

    @FXML
    private void previewCleaner() {
        if (cleanerPreview == null) {
            return;
        }
        cleanerPreview.setText("CALCULATING PREVIEW…");
        cleanerService.preview(java.time.Duration.ofDays(30))
                .whenComplete((preview, error) -> Platform.runLater(() -> {
                    if (cleanerPreview == null) {
                        return;
                    }
                    if (error != null) {
                        cleanerPreview.setText("CLEANER PREVIEW FAILED");
                        return;
                    }
                    cleanerPreview.setText(
                            "30+ DAYS · %d HISTORY · %d DOWNLOADS · "
                                    .formatted(
                                            preview.expiredHistory(),
                                            preview.completedDownloads())
                                    + "%d FAVICONS · %d SESSION ITEMS"
                                            .formatted(
                                                    preview.faviconCache(),
                                                    preview.oldSessionMetadata()));
                }));
    }

    @FXML
    private void runCleaner() {
        CleanerSelection selection = new CleanerSelection(
                cleanExpiredHistory.isSelected(),
                cleanCompletedDownloads.isSelected(),
                cleanFavicons.isSelected(),
                cleanOldSessions.isSelected());
        if (!confirm("Clean the selected GX Cleaner categories?")) {
            return;
        }
        cleanerPreview.setText("CLEANING SELECTED DATA…");
        cleanerService.clean(java.time.Duration.ofDays(30), selection)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        cleanerPreview.setText("GX CLEANER FAILED");
                    } else {
                        cleanerPreview.setText(
                                "REMOVED %d ITEMS · DOWNLOADED FILES UNCHANGED"
                                        .formatted(result.total()));
                    }
                }));
    }

    @FXML
    private void clearCookies() {
        if (confirm("Delete all cookies held by this Flux process?")) {
            int removed = browsingDataService.clearCookies();
            browsingDataStatus.setText("CLEARED " + removed + " COOKIES");
        }
    }

    @FXML
    private void clearFavicons() {
        browsingDataService.clearFavicons();
        browsingDataStatus.setText("FAVICON CACHE CLEARED");
    }

    @FXML
    private void clearSessionData() {
        if (confirm("Delete saved session and recently closed tabs?")) {
            browsingDataService.clearSessionData()
                    .thenRun(() -> Platform.runLater(() ->
                            browsingDataStatus.setText("SESSION DATA CLEARED")));
        }
    }

    @FXML
    private void resetPopupPermissions() {
        browsingDataService.resetPopupPermissions();
        browsingDataStatus.setText("POPUP PERMISSIONS RESET");
    }

    private void refreshHistory() {
        if (historySearch == null) {
            return;
        }
        persistenceService.visits(historySearch.getText())
                .whenComplete((items, error) -> Platform.runLater(() -> {
                    if (historyList == null) {
                        return;
                    }
                    if (error != null) {
                        historyStatus.setText("DATABASE ERROR");
                    } else {
                        historyList.getItems().setAll(items);
                        historyStatus.setText(items.size() + " VISITS");
                    }
                }));
    }

    private void refreshDownloads() {
        if (downloadSearch == null) {
            return;
        }
        persistenceService.downloads(downloadSearch.getText())
                .whenComplete((items, error) -> Platform.runLater(() -> {
                    if (downloadList == null) {
                        return;
                    }
                    if (error != null) {
                        setDownloadStatus("DATABASE ERROR");
                    } else {
                        downloadList.getItems().setAll(items);
                        setDownloadStatus(items.size() + " DOWNLOADS");
                    }
                }));
    }

    private void observeDownloadTask(DownloadTask task) {
        task.statusProperty().addListener((observable, oldStatus, newStatus) -> {
            if (activeDownloadList != null) {
                activeDownloadList.refresh();
            }
            if (newStatus == Status.COMPLETED
                    || newStatus == Status.CANCELLED
                    || newStatus == Status.FAILED) {
                refreshDownloads();
            }
        });
        task.progressProperty().addListener(observable -> {
            if (activeDownloadList != null) {
                activeDownloadList.refresh();
            }
        });
        task.bytesDownloadedProperty().addListener(observable -> {
            if (activeDownloadList != null) {
                activeDownloadList.refresh();
            }
        });
    }

    private void updateResourceMetrics(ResourceSample sample) {
        if (cpuProgress == null) {
            return;
        }
        cpuProgress.setProgress(sample.processCpuLoad());
        cpuValue.setText("%.1f%% PROCESS".formatted(
                sample.processCpuLoad() * 100.0));
        double memoryRatio = sample.physicalMemoryBytes() <= 0
                ? 0.0
                : (double) sample.residentBytes() / sample.physicalMemoryBytes();
        memoryProgress.setProgress(Math.max(0.0, Math.min(1.0, memoryRatio)));
        memoryValue.setText(formatBytes(sample.residentBytes()) + " RSS");
        jvmValue.setText("HEAP %s / %s · NON-HEAP %s".formatted(
                formatBytes(sample.heapUsedBytes()),
                formatBytes(sample.heapCommittedBytes()),
                formatBytes(sample.nonHeapUsedBytes())));
        tabCountValue.setText(sample.activeTabCount() + " OPEN");
        resourceSequence++;
        appendPoint(cpuSeries, resourceSequence, sample.processCpuLoad() * 100.0);
        appendPoint(
                memorySeries,
                resourceSequence,
                sample.residentBytes() / (1024.0 * 1024.0));
        refreshHotTabs();
    }

    private void refreshHotTabs() {
        if (hotTabList == null || suspensionService == null) {
            return;
        }
        BrowserTab selected = hotTabList.getSelectionModel().getSelectedItem();
        hotTabList.getItems().setAll(suspensionService.hotTabs());
        if (selected != null && hotTabList.getItems().contains(selected)) {
            hotTabList.getSelectionModel().select(selected);
        }
        hotTabList.refresh();
    }

    private void detachTabManagerListeners() {
        if (tabManager != null) {
            if (tabsListener != null) {
                tabManager.tabs().removeListener(tabsListener);
            }
            if (activeTabListener != null) {
                tabManager.activeTabProperty().removeListener(activeTabListener);
            }
        }
        tabsListener = null;
        activeTabListener = null;
    }

    private String suspensionBlockReason(BrowserTab tab) {
        if (tabManager != null && tab == tabManager.activeTab()) {
            return "ACTIVE TAB CANNOT BE SUSPENDED";
        }
        if (tab.pinnedProperty().get()) {
            return "PINNED TAB CANNOT BE SUSPENDED";
        }
        if (tab.loadingProperty().get()) {
            return "LOADING TAB CANNOT BE SUSPENDED";
        }
        if (tab.suspensionExcludedProperty().get()) {
            return "TAB IS MARKED KEEP ACTIVE";
        }
        return "TAB CANNOT BE SUSPENDED";
    }

    private boolean confirmTabSuspension(BrowserTab tab) {
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Suspend ‘" + tab.titleProperty().get() + "’?\n\n"
                        + "The URL, title, favicon, and zoom are retained, but "
                        + "unsaved forms, media, scripts, and page state are lost.",
                ButtonType.OK,
                ButtonType.CANCEL);
        alert.setTitle("Suspend background tab");
        alert.setHeaderText("The page will reload when resumed");
        initOwner(alert);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void filterSettings(String query) {
        String normalized = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);
        setVisibleAndManaged(
                appearanceSettings,
                normalized.isBlank()
                        || "appearance theme accent wallpaper sidebar scale motion"
                                .contains(normalized));
        setVisibleAndManaged(
                privacySettings,
                normalized.isBlank()
                        || "privacy data history downloads database cookies favicon session popup cache"
                                .contains(normalized));
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                message,
                ButtonType.OK,
                ButtonType.CANCEL);
        alert.setTitle("Confirm data removal");
        alert.setHeaderText("This action cannot be undone");
        initOwner(alert);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void initOwner(Alert alert) {
        if (panelRoot != null && panelRoot.getScene() != null) {
            alert.initOwner(panelRoot.getScene().getWindow());
        }
    }

    private void setDownloadStatus(String text) {
        if (downloadStatus != null) {
            downloadStatus.setText(text);
        }
    }

    private static void appendPoint(
            XYChart.Series<Number, Number> series,
            long sequence,
            double value) {
        series.getData().add(new XYChart.Data<>(sequence, value));
        while (series.getData().size() > 60) {
            series.getData().removeFirst();
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return "%.1f %s".formatted(value, units[unit]);
    }

    private static void setVisibleAndManaged(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
