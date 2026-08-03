package org.custombrowser.diagnostics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import org.slf4j.Logger;

/**
 * Low-overhead, application-scoped timings for the performance-sensitive
 * browser lifecycle. Only aggregate timings are retained; URLs and user data
 * are never recorded.
 */
public final class PerformanceTracker {

    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;

    private final ConcurrentMap<String, Accumulator> metrics =
            new ConcurrentHashMap<>();

    public <T> T measure(String operation, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        long started = System.nanoTime();
        try {
            return action.get();
        } finally {
            recordNanos(operation, System.nanoTime() - started);
        }
    }

    public void measure(String operation, Runnable action) {
        Objects.requireNonNull(action, "action");
        measure(operation, () -> {
            action.run();
            return null;
        });
    }

    public void recordNanos(String operation, long elapsedNanos) {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        metrics.computeIfAbsent(operation, ignored -> new Accumulator())
                .add(Math.max(0, elapsedNanos));
    }

    public Map<String, MetricSnapshot> snapshot() {
        Map<String, MetricSnapshot> result = new LinkedHashMap<>();
        metrics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(
                        entry.getKey(), entry.getValue().snapshot()));
        return Map.copyOf(result);
    }

    public MemorySnapshot memorySnapshot() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage();
        return new MemorySnapshot(
                heap.getUsed(), heap.getCommitted(), heap.getMax());
    }

    public void logSummary(Logger logger, String checkpoint) {
        Objects.requireNonNull(logger, "logger");
        MemorySnapshot memory = memorySnapshot();
        logger.info(
                "Flux performance [{}]: heap={} MiB committed={} MiB max={} MiB",
                checkpoint,
                memory.usedMebibytes(),
                memory.committedMebibytes(),
                memory.maxMebibytes());
        snapshot().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> logger.info(
                        "Flux performance [{}]: {} count={} total={} ms avg={} ms max={} ms",
                        checkpoint,
                        entry.getKey(),
                        entry.getValue().count(),
                        entry.getValue().totalMillis(),
                        entry.getValue().averageMillis(),
                        entry.getValue().maxMillis()));
    }

    public record MetricSnapshot(long count, long totalNanos, long maxNanos) {

        public double totalMillis() {
            return totalNanos / 1_000_000.0;
        }

        public double averageMillis() {
            return count == 0 ? 0 : totalMillis() / count;
        }

        public double maxMillis() {
            return maxNanos / 1_000_000.0;
        }
    }

    public record MemorySnapshot(long usedBytes, long committedBytes, long maxBytes) {

        public long usedMebibytes() {
            return usedBytes / BYTES_PER_MEBIBYTE;
        }

        public long committedMebibytes() {
            return committedBytes / BYTES_PER_MEBIBYTE;
        }

        public long maxMebibytes() {
            return maxBytes < 0 ? -1 : maxBytes / BYTES_PER_MEBIBYTE;
        }
    }

    private static final class Accumulator {

        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        private void add(long elapsedNanos) {
            count.increment();
            totalNanos.add(elapsedNanos);
            maxNanos.accumulateAndGet(elapsedNanos, Math::max);
        }

        private MetricSnapshot snapshot() {
            return new MetricSnapshot(
                    count.sum(), totalNanos.sum(), maxNanos.get());
        }
    }
}
