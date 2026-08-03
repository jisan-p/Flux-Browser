package org.custombrowser.gx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.custombrowser.gx.TabSuspensionPolicy.SuspensionState;
import org.junit.jupiter.api.Test;

class TabSuspensionPolicyTest {

    private final TabSuspensionPolicy policy = new TabSuspensionPolicy();

    @Test
    void allowsIdleUnprotectedBackgroundPage() {
        SuspensionState tab = state(
                false, false, false, false, false, false, 20);

        assertTrue(policy.canSuspend(tab));
        assertTrue(policy.canAutoSuspend(
                tab,
                Instant.now(),
                Duration.ofMinutes(15)));
    }

    @Test
    void rejectsActivePinnedLoadingExcludedSuspendedAndStartTabs() {
        assertFalse(policy.canSuspend(
                state(true, false, false, false, false, false, 20)));
        assertFalse(policy.canSuspend(
                state(false, true, false, false, false, false, 20)));
        assertFalse(policy.canSuspend(
                state(false, false, true, false, false, false, 20)));
        assertFalse(policy.canSuspend(
                state(false, false, false, true, false, false, 20)));
        assertFalse(policy.canSuspend(
                state(false, false, false, false, true, false, 20)));
        assertFalse(policy.canSuspend(
                state(false, false, false, false, false, true, 20)));
    }

    @Test
    void requiresPositiveAutoSuspendDuration() {
        SuspensionState tab = state(
                false, false, false, false, false, false, 20);
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.canAutoSuspend(
                        tab,
                        Instant.now(),
                        Duration.ZERO));
    }

    private static SuspensionState state(
            boolean active,
            boolean pinned,
            boolean loading,
            boolean excluded,
            boolean suspended,
            boolean startPage,
            int idleMinutes) {
        return new SuspensionState(
                active,
                pinned,
                loading,
                excluded,
                suspended,
                startPage,
                Instant.now().minus(Duration.ofMinutes(idleMinutes))
                        .toEpochMilli());
    }
}
