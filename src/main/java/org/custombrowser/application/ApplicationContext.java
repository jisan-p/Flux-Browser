package org.custombrowser.application;

import org.custombrowser.navigation.NavigationResolver;
import org.custombrowser.browser.FaviconService;
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

/**
 * Owns application-scoped services and creates controllers for FXML.
 *
 * <p>Services are application-scoped and supplied without global mutable
 * controller state.</p>
 */
public final class ApplicationContext implements AutoCloseable {

    private final NavigationResolver navigationResolver;
    private final BrowserUiState browserUiState;
    private final FaviconService faviconService;
    private final PersistenceService persistenceService;
    private BrowserController browserController;

    private ApplicationContext(
            NavigationResolver navigationResolver,
            BrowserUiState browserUiState,
            FaviconService faviconService,
            PersistenceService persistenceService) {
        this.navigationResolver = navigationResolver;
        this.browserUiState = browserUiState;
        this.faviconService = faviconService;
        this.persistenceService = persistenceService;
    }

    public static ApplicationContext createDefault() {
        PersistenceService persistence =
                PersistenceService.open(DatabaseConfig.fromEnvironment());
        BrowserUiState uiState = new BrowserUiState();
        uiState.applyPersistedState(
                persistence.startupState().settings(),
                persistence.startupState().speedDials());
        persistence.bind(uiState);
        return new ApplicationContext(
                NavigationResolver.duckDuckGo(),
                uiState,
                new FaviconService(),
                persistence);
    }

    public static ApplicationContext createForTests() {
        PersistenceService persistence = PersistenceService.forTests();
        BrowserUiState uiState = new BrowserUiState();
        persistence.bind(uiState);
        return new ApplicationContext(
                NavigationResolver.duckDuckGo(),
                uiState,
                new FaviconService(),
                persistence);
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
                    persistenceService);
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
                    persistenceService);
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

    @Override
    public void close() {
        try {
            if (browserController != null) {
                browserController.close();
            }
        } finally {
            persistenceService.close();
        }
    }
}
