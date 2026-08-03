package org.custombrowser.application;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

import org.custombrowser.navigation.NavigationResolver;
import org.custombrowser.browser.FaviconService;
import org.custombrowser.browser.PopupPolicyService;
import org.custombrowser.diagnostics.PerformanceTracker;
import org.custombrowser.download.DownloadManager;
import org.custombrowser.gx.ResourceMonitor;
import org.custombrowser.persistence.DatabaseConfig;
import org.custombrowser.persistence.PersistenceModels.WindowState;
import org.custombrowser.persistence.PersistenceService;
import org.custombrowser.ui.BrowserController;
import org.custombrowser.ui.component.EasySetupController;
import org.custombrowser.ui.component.ErrorPageController;
import org.custombrowser.ui.component.FindBarController;
import org.custombrowser.ui.component.NavigationBarController;
import org.custombrowser.ui.component.SidebarController;
import org.custombrowser.ui.component.SidebarPanelController;
import org.custombrowser.ui.component.StartPageController;
import org.custombrowser.ui.component.TabStripController;
import org.custombrowser.ui.component.TitleBarController;
import org.custombrowser.ui.state.BrowserUiState;
import org.custombrowser.settings.BrowsingDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns application-scoped services and creates controllers for FXML.
 *
 * <p>Services are application-scoped and supplied without global mutable
 * controller state.</p>
 */
public final class ApplicationContext implements AutoCloseable {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ApplicationContext.class);

    private final NavigationResolver navigationResolver;
    private final BrowserUiState browserUiState;
    private final FaviconService faviconService;
    private final PersistenceService persistenceService;
    private final DownloadManager downloadManager;
    private final PopupPolicyService popupPolicyService;
    private final BrowsingDataService browsingDataService;
    private final ResourceMonitor resourceMonitor;
    private final PerformanceTracker performanceTracker;
    private BrowserController browserController;

    private ApplicationContext(
            NavigationResolver navigationResolver,
            BrowserUiState browserUiState,
            FaviconService faviconService,
            PersistenceService persistenceService,
            DownloadManager downloadManager,
            PopupPolicyService popupPolicyService,
            BrowsingDataService browsingDataService,
            ResourceMonitor resourceMonitor,
            PerformanceTracker performanceTracker) {
        this.navigationResolver = navigationResolver;
        this.browserUiState = browserUiState;
        this.faviconService = faviconService;
        this.persistenceService = persistenceService;
        this.downloadManager = downloadManager;
        this.popupPolicyService = popupPolicyService;
        this.browsingDataService = browsingDataService;
        this.resourceMonitor = resourceMonitor;
        this.performanceTracker = performanceTracker;
    }

    public static ApplicationContext createDefault() {
        PerformanceTracker performance = new PerformanceTracker();
        PersistenceService persistence = performance.measure(
                "startup.persistence",
                () -> PersistenceService.open(
                        DatabaseConfig.fromEnvironment(), performance));
        CookieManager cookieManager = installCookieManager();
        FaviconService faviconService = new FaviconService();
        PopupPolicyService popupPolicyService =
                new PopupPolicyService(persistence);
        DownloadManager downloadManager = new DownloadManager(persistence);
        BrowsingDataService browsingDataService = new BrowsingDataService(
                cookieManager,
                faviconService,
                popupPolicyService,
                persistence);
        BrowserUiState uiState = new BrowserUiState();
        ResourceMonitor resourceMonitor = new ResourceMonitor();
        uiState.applyPersistedState(
                persistence.startupState().settings(),
                persistence.startupState().speedDials());
        persistence.bind(uiState);
        return new ApplicationContext(
                NavigationResolver.duckDuckGo(),
                uiState,
                faviconService,
                persistence,
                downloadManager,
                popupPolicyService,
                browsingDataService,
                resourceMonitor,
                performance);
    }

    public static ApplicationContext createForTests() {
        PerformanceTracker performance = new PerformanceTracker();
        PersistenceService persistence = PersistenceService.forTests(performance);
        CookieManager cookieManager = installCookieManager();
        FaviconService faviconService = new FaviconService();
        PopupPolicyService popupPolicyService =
                new PopupPolicyService(persistence);
        DownloadManager downloadManager = new DownloadManager(persistence);
        BrowsingDataService browsingDataService = new BrowsingDataService(
                cookieManager,
                faviconService,
                popupPolicyService,
                persistence);
        BrowserUiState uiState = new BrowserUiState();
        ResourceMonitor resourceMonitor = new ResourceMonitor();
        persistence.bind(uiState);
        return new ApplicationContext(
                NavigationResolver.duckDuckGo(),
                uiState,
                faviconService,
                persistence,
                downloadManager,
                popupPolicyService,
                browsingDataService,
                resourceMonitor,
                performance);
    }

    /**
     * Controller factory used by {@link javafx.fxml.FXMLLoader}.
     *
     * @param controllerType controller class declared by an FXML document
     * @return controller with its dependencies supplied
     */
    public Object createController(Class<?> controllerType) {
        if (controllerType == BrowserController.class) {
            browserController = new BrowserController(
                    navigationResolver,
                    browserUiState,
                    faviconService,
                    persistenceService,
                    downloadManager,
                    popupPolicyService,
                    performanceTracker);
            return browserController;
        }
        if (controllerType == TitleBarController.class) {
            return new TitleBarController();
        }
        if (controllerType == NavigationBarController.class) {
            return new NavigationBarController();
        }
        if (controllerType == TabStripController.class) {
            return new TabStripController();
        }
        if (controllerType == SidebarController.class) {
            return new SidebarController(browserUiState);
        }
        if (controllerType == SidebarPanelController.class) {
            return new SidebarPanelController(
                    browserUiState,
                    persistenceService,
                    downloadManager,
                    browsingDataService,
                    resourceMonitor,
                    faviconService);
        }
        if (controllerType == StartPageController.class) {
            return new StartPageController(browserUiState);
        }
        if (controllerType == EasySetupController.class) {
            return new EasySetupController(browserUiState);
        }
        if (controllerType == FindBarController.class) {
            return new FindBarController();
        }
        if (controllerType == ErrorPageController.class) {
            return new ErrorPageController();
        }

        throw new IllegalArgumentException(
                "No controller registration for " + controllerType.getName());
    }

    public WindowState initialWindowState() {
        return persistenceService.startupState().windowState();
    }

    public void saveWindowState(WindowState state) {
        persistenceService.saveWindowStateNow(state);
    }

    public PerformanceTracker performanceTracker() {
        return performanceTracker;
    }

    private static CookieManager installCookieManager() {
        CookieManager manager = new CookieManager(
                null,
                CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        CookieHandler.setDefault(manager);
        return manager;
    }

    @Override
    public void close() {
        try {
            if (browserController != null) {
                browserController.close();
            }
        } finally {
            try {
                downloadManager.close();
            } finally {
                try {
                    resourceMonitor.close();
                } finally {
                    try {
                        persistenceService.close();
                    } finally {
                        performanceTracker.logSummary(LOGGER, "shutdown");
                    }
                }
            }
        }
    }
}
