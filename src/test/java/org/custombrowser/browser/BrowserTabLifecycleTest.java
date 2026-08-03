package org.custombrowser.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.custombrowser.diagnostics.PerformanceTracker;
import org.junit.jupiter.api.Test;

class BrowserTabLifecycleTest {

    @Test
    void newAndRestoredTabsRemainLazyUntilDisplayed() {
        FaviconService favicons = new FaviconService();
        BrowserTab newTab = new BrowserTab(favicons, ignored -> { }, true);
        BrowserTab restored = new BrowserTab(favicons, ignored -> { }, false);

        restored.restore(
                URI.create("https://example.com/restored"),
                "Restored",
                false,
                1.25,
                false);

        assertTrue(newTab.loadedWebView().isEmpty());
        assertTrue(restored.loadedWebView().isEmpty());
        assertEquals("https://example.com/restored",
                restored.locationProperty().get());
        assertFalse(restored.startPageProperty().get());

        newTab.dispose();
        restored.dispose();
    }

    @Test
    void repeatedStartTabOpenAndCloseDoesNotAllocateWebViews() {
        PerformanceTracker performance = new PerformanceTracker();
        TabManager manager = new TabManager(
                new FaviconService(),
                ignored -> { },
                ignored -> false,
                performance);

        for (int index = 0; index < 20; index++) {
            manager.createTab();
        }
        assertTrue(manager.tabs().stream()
                .allMatch(tab -> tab.loadedWebView().isEmpty()));

        while (manager.tabs().size() > 1) {
            manager.close(manager.tabs().getLast());
        }
        manager.close(manager.activeTab());

        assertEquals(1, manager.tabs().size());
        assertTrue(manager.activeTab().loadedWebView().isEmpty());
        assertEquals(20,
                performance.snapshot().get("tab.close").count());
        manager.close();
    }
}
