package org.custombrowser.gx;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.custombrowser.browser.BrowserTab;
import org.custombrowser.browser.TabManager;

/** Coordinates suspension and heuristic Hot Tabs ordering on the FX thread. */
public final class TabSuspensionService {

    private final TabManager tabManager;
    private final TabSuspensionPolicy policy;

    public TabSuspensionService(TabManager tabManager) {
        this(tabManager, new TabSuspensionPolicy());
    }

    TabSuspensionService(
            TabManager tabManager,
            TabSuspensionPolicy policy) {
        this.tabManager = Objects.requireNonNull(tabManager, "tabManager");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public boolean suspend(BrowserTab tab) {
        return canSuspend(tab)
                && tab.suspend();
    }

    public boolean canSuspend(BrowserTab tab) {
        return tab != null && policy.canSuspend(tab, tabManager.activeTab());
    }

    public int suspendInactive(Duration inactivity) {
        Instant now = Instant.now();
        int suspended = 0;
        for (BrowserTab tab : List.copyOf(tabManager.tabs())) {
            if (policy.canAutoSuspend(
                    tab,
                    tabManager.activeTab(),
                    now,
                    inactivity)
                    && tab.suspend()) {
                suspended++;
            }
        }
        return suspended;
    }

    public List<BrowserTab> hotTabs() {
        long now = System.currentTimeMillis();
        return tabManager.tabs().stream()
                .filter(tab -> !tab.startPageProperty().get())
                .sorted(Comparator.comparingDouble(
                        (BrowserTab tab) -> activityScore(tab, now)).reversed())
                .toList();
    }

    public double activityScore(BrowserTab tab) {
        return activityScore(tab, System.currentTimeMillis());
    }

    static double activityScore(BrowserTab tab, long nowEpochMillis) {
        long ageMillis = Math.max(
                0L,
                nowEpochMillis - tab.lastActivityEpochMillis());
        double recency = 100.0 / (1.0 + ageMillis / 60_000.0);
        double loadActivity = Math.min(100.0, tab.activityEvents() * 4.0);
        double loading = tab.loadingProperty().get() ? 150.0 : 0.0;
        double suspended = tab.suspendedProperty().get() ? -200.0 : 0.0;
        return recency + loadActivity + loading + suspended;
    }
}
