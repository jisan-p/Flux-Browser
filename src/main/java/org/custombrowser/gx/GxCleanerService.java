package org.custombrowser.gx;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.custombrowser.browser.FaviconService;
import org.custombrowser.persistence.PersistenceService;

/** Previews and executes narrowly selected cleanup categories. */
public final class GxCleanerService {

    private final PersistenceService persistence;
    private final FaviconService favicons;

    public GxCleanerService(
            PersistenceService persistence,
            FaviconService favicons) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.favicons = Objects.requireNonNull(favicons, "favicons");
    }

    public CompletableFuture<CleanerPreview> preview(Duration retention) {
        Instant cutoff = cutoff(retention);
        CompletableFuture<Integer> history =
                persistence.countVisitsBefore(cutoff);
        CompletableFuture<Integer> downloads =
                persistence.countCompletedDownloadsBefore(cutoff);
        CompletableFuture<Integer> sessions =
                persistence.countOldSessionRecordsBefore(cutoff);
        return history.thenCombine(downloads, Counts::new)
                .thenCombine(sessions, (counts, sessionCount) ->
                        new CleanerPreview(
                                cutoff,
                                counts.history(),
                                counts.downloads(),
                                favicons.cacheSize(),
                                sessionCount));
    }

    public CompletableFuture<CleanerResult> clean(
            Duration retention,
            CleanerSelection selection) {
        Objects.requireNonNull(selection, "selection");
        Instant cutoff = cutoff(retention);
        CompletableFuture<Integer> history = selection.expiredHistory()
                ? persistence.deleteVisitsBefore(cutoff)
                : CompletableFuture.completedFuture(0);
        CompletableFuture<Integer> downloads = selection.completedDownloads()
                ? persistence.deleteCompletedDownloadsBefore(cutoff)
                : CompletableFuture.completedFuture(0);
        CompletableFuture<Integer> sessions = selection.oldSessionMetadata()
                ? persistence.deleteOldSessionRecordsBefore(cutoff)
                : CompletableFuture.completedFuture(0);
        int faviconCount = selection.faviconCache() ? favicons.cacheSize() : 0;
        if (selection.faviconCache()) {
            favicons.clearCache();
        }
        return history.thenCombine(downloads, Counts::new)
                .thenCombine(sessions, (counts, sessionCount) ->
                        new CleanerResult(
                                counts.history(),
                                counts.downloads(),
                                faviconCount,
                                sessionCount));
    }

    private static Instant cutoff(Duration retention) {
        Objects.requireNonNull(retention, "retention");
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Retention must be positive");
        }
        return Instant.now().minus(retention);
    }

    private record Counts(int history, int downloads) {
    }

    public record CleanerPreview(
            Instant cutoff,
            int expiredHistory,
            int completedDownloads,
            int faviconCache,
            int oldSessionMetadata) {

        public int total() {
            return expiredHistory
                    + completedDownloads
                    + faviconCache
                    + oldSessionMetadata;
        }
    }

    public record CleanerSelection(
            boolean expiredHistory,
            boolean completedDownloads,
            boolean faviconCache,
            boolean oldSessionMetadata) {
    }

    public record CleanerResult(
            int expiredHistory,
            int completedDownloads,
            int faviconCache,
            int oldSessionMetadata) {

        public int total() {
            return expiredHistory
                    + completedDownloads
                    + faviconCache
                    + oldSessionMetadata;
        }
    }
}
