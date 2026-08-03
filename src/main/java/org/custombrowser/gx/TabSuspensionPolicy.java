package org.custombrowser.gx;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.custombrowser.browser.BrowserTab;

/** Honest eligibility rules shared by manual and automatic tab suspension. */
public final class TabSuspensionPolicy {

    public boolean canSuspend(BrowserTab tab, BrowserTab activeTab) {
        Objects.requireNonNull(tab, "tab");
        return canSuspend(SuspensionState.from(tab, activeTab));
    }

    public boolean canAutoSuspend(
            BrowserTab tab,
            BrowserTab activeTab,
            Instant now,
            Duration inactivity) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(inactivity, "inactivity");
        if (inactivity.isNegative() || inactivity.isZero()) {
            throw new IllegalArgumentException("Inactivity must be positive");
        }
        return canAutoSuspend(
                SuspensionState.from(tab, activeTab),
                now,
                inactivity);
    }

    boolean canSuspend(SuspensionState state) {
        Objects.requireNonNull(state, "state");
        return !state.active()
                && !state.pinned()
                && !state.loading()
                && !state.excluded()
                && !state.suspended()
                && !state.startPage();
    }

    boolean canAutoSuspend(
            SuspensionState state,
            Instant now,
            Duration inactivity) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(inactivity, "inactivity");
        if (inactivity.isNegative() || inactivity.isZero()) {
            throw new IllegalArgumentException("Inactivity must be positive");
        }
        long idleMillis = now.toEpochMilli() - state.lastActivityEpochMillis();
        return canSuspend(state) && idleMillis >= inactivity.toMillis();
    }

    record SuspensionState(
            boolean active,
            boolean pinned,
            boolean loading,
            boolean excluded,
            boolean suspended,
            boolean startPage,
            long lastActivityEpochMillis) {

        static SuspensionState from(BrowserTab tab, BrowserTab activeTab) {
            return new SuspensionState(
                    tab == activeTab,
                    tab.pinnedProperty().get(),
                    tab.loadingProperty().get(),
                    tab.suspensionExcludedProperty().get(),
                    tab.suspendedProperty().get(),
                    tab.startPageProperty().get(),
                    tab.lastActivityEpochMillis());
        }
    }
}
