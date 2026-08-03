package org.custombrowser.persistence;

import java.util.List;
import java.time.Instant;

import org.custombrowser.persistence.PersistenceModels.Download;

public interface DownloadRepository {

    List<Download> search(String query, int limit);

    Download create(String sourceUrl, String fileName, String targetPath);

    void update(
            long id,
            String status,
            long bytesDownloaded,
            Long totalBytes,
            Instant completedAt,
            String failureMessage);

    void delete(long id);

    void clear();

    int countCompletedBefore(Instant cutoff);

    int deleteCompletedBefore(Instant cutoff);
}
