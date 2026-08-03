package org.custombrowser.gx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.custombrowser.browser.FaviconService;
import org.custombrowser.gx.GxCleanerService.CleanerSelection;
import org.custombrowser.persistence.PersistenceService;
import org.junit.jupiter.api.Test;

class GxCleanerServiceTest {

    @Test
    void noOpPersistenceProducesAnExactEmptyPreviewAndResult() {
        try (PersistenceService persistence = PersistenceService.forTests()) {
            GxCleanerService cleaner = new GxCleanerService(
                    persistence,
                    new FaviconService());

            assertEquals(0, cleaner.preview(Duration.ofDays(30)).join().total());
            assertEquals(
                    0,
                    cleaner.clean(
                                    Duration.ofDays(30),
                                    new CleanerSelection(true, true, true, true))
                            .join()
                            .total());
        }
    }

    @Test
    void rejectsNonPositiveRetention() {
        try (PersistenceService persistence = PersistenceService.forTests()) {
            GxCleanerService cleaner = new GxCleanerService(
                    persistence,
                    new FaviconService());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> cleaner.preview(Duration.ZERO));
        }
    }
}
