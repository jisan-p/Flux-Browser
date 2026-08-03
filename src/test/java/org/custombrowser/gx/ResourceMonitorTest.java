package org.custombrowser.gx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResourceMonitorTest {

    @Test
    void samplesBoundedProcessAndJvmMeasurements() {
        try (ResourceMonitor monitor = new ResourceMonitor()) {
            monitor.setTabCountSupplier(() -> 4);
            var sample = monitor.sample();

            assertTrue(sample.processCpuLoad() >= 0.0);
            assertTrue(sample.processCpuLoad() <= 1.0);
            assertTrue(sample.residentBytes() > 0);
            assertTrue(sample.heapUsedBytes() >= 0);
            assertTrue(sample.heapCommittedBytes() >= sample.heapUsedBytes());
            assertTrue(sample.physicalMemoryBytes() > 0);
            assertEquals(4, sample.activeTabCount());
        }
    }
}
