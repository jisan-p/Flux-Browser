package org.custombrowser.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.custombrowser.diagnostics.PerformanceTracker.MetricSnapshot;
import org.junit.jupiter.api.Test;

class PerformanceTrackerTest {

    @Test
    void aggregatesCountsTotalAndMaximumWithoutRetainingInputs() {
        PerformanceTracker tracker = new PerformanceTracker();

        tracker.recordNanos("tab.create", 2_000_000);
        tracker.recordNanos("tab.create", 5_000_000);

        MetricSnapshot metric = tracker.snapshot().get("tab.create");
        assertEquals(2, metric.count());
        assertEquals(7_000_000, metric.totalNanos());
        assertEquals(5_000_000, metric.maxNanos());
        assertEquals(3.5, metric.averageMillis());
    }

    @Test
    void measuresSuccessfulAndFailedOperations() {
        PerformanceTracker tracker = new PerformanceTracker();

        assertEquals("done", tracker.measure("work", () -> "done"));
        try {
            tracker.measure("work", () -> {
                throw new IllegalStateException("expected");
            });
        } catch (IllegalStateException expected) {
            // Failed operations still contribute timing data.
        }

        Map<String, MetricSnapshot> snapshot = tracker.snapshot();
        assertEquals(2, snapshot.get("work").count());
        assertTrue(snapshot.get("work").totalNanos() >= 0);
        assertTrue(tracker.memorySnapshot().usedBytes() >= 0);
    }
}
