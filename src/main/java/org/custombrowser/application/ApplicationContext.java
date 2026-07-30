package org.custombrowser.application;

import org.custombrowser.navigation.NavigationResolver;
import org.custombrowser.ui.BrowserController;
import org.custombrowser.ui.component.EasySetupController;
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
 * <p>This is intentionally small in Phase 0. Later phases can add services here
 * without introducing global mutable controller state.</p>
 */
public final class ApplicationContext implements AutoCloseable {

    private final NavigationResolver navigationResolver;
    private final BrowserUiState browserUiState;

    private ApplicationContext(
            NavigationResolver navigationResolver,
            BrowserUiState browserUiState) {
        this.navigationResolver = navigationResolver;
        this.browserUiState = browserUiState;
    }

    public static ApplicationContext createDefault() {
        return new ApplicationContext(
                NavigationResolver.duckDuckGo(),
                new BrowserUiState());
    }

    /**
     * Controller factory used by {@link javafx.fxml.FXMLLoader}.
     *
     * @param controllerType controller class declared by an FXML document
     * @return controller with its dependencies supplied
     */
    public Object createController(Class<?> controllerType) {
        if (controllerType == BrowserController.class) {
            return new BrowserController(navigationResolver, browserUiState);
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
            return new SidebarPanelController(browserUiState);
        }
        if (controllerType == StartPageController.class) {
            return new StartPageController(browserUiState);
        }
        if (controllerType == EasySetupController.class) {
            return new EasySetupController(browserUiState);
        }

        throw new IllegalArgumentException(
                "No controller registration for " + controllerType.getName());
    }

    @Override
    public void close() {
        // Phase 0 services do not own closeable resources.
    }
}
