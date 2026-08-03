package org.custombrowser.gx;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Samples process-wide resource measurements without blocking the FX thread. */
public final class ResourceMonitor implements AutoCloseable {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ResourceMonitor.class);

    private final SystemInfo systemInfo;
    private final OperatingSystem operatingSystem;
    private final MemoryMXBean memoryBean;
    private final ScheduledExecutorService executor;
    private final List<Consumer<ResourceSample>> listeners =
            new CopyOnWriteArrayList<>();

    private volatile IntSupplier tabCountSupplier = () -> 0;
    private volatile OSProcess previousProcess;
    private volatile boolean started;
    private ScheduledFuture<?> samplingTask;

    public ResourceMonitor() {
        systemInfo = new SystemInfo();
        operatingSystem = systemInfo.getOperatingSystem();
        memoryBean = ManagementFactory.getMemoryMXBean();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "flux-resource-monitor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void setTabCountSupplier(IntSupplier supplier) {
        tabCountSupplier = supplier == null ? () -> 0 : supplier;
    }

    public void addListener(Consumer<ResourceSample> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<ResourceSample> listener) {
        listeners.remove(listener);
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        samplingTask = executor.scheduleAtFixedRate(
                this::sampleSafely,
                0,
                1,
                TimeUnit.SECONDS);
    }

    public synchronized void pause() {
        started = false;
        if (samplingTask != null) {
            samplingTask.cancel(false);
            samplingTask = null;
        }
        previousProcess = null;
    }

    ResourceSample sample() {
        OSProcess process = operatingSystem.getCurrentProcess();
        double cpuLoad = previousProcess == null
                ? process.getProcessCpuLoadCumulative()
                : process.getProcessCpuLoadBetweenTicks(previousProcess);
        previousProcess = process;
        long totalMemory = systemInfo.getHardware().getMemory().getTotal();
        return new ResourceSample(
                Instant.now(),
                clamp(cpuLoad),
                process.getResidentSetSize(),
                process.getVirtualSize(),
                Math.max(0L, memoryBean.getHeapMemoryUsage().getUsed()),
                Math.max(0L, memoryBean.getHeapMemoryUsage().getCommitted()),
                Math.max(0L, memoryBean.getNonHeapMemoryUsage().getUsed()),
                Math.max(0L, totalMemory),
                Math.max(0, tabCountSupplier.getAsInt()));
    }

    @Override
    public synchronized void close() {
        pause();
        executor.shutdownNow();
        listeners.clear();
    }

    private void sampleSafely() {
        try {
            ResourceSample sample = sample();
            listeners.forEach(listener -> listener.accept(sample));
        } catch (RuntimeException error) {
            LOGGER.warn("Process metrics sample failed: {}", error.getMessage());
        }
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record ResourceSample(
            Instant sampledAt,
            double processCpuLoad,
            long residentBytes,
            long virtualBytes,
            long heapUsedBytes,
            long heapCommittedBytes,
            long nonHeapUsedBytes,
            long physicalMemoryBytes,
            int activeTabCount) {
    }
}
