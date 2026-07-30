package org.custombrowser.persistence;

import java.util.List;

import org.custombrowser.persistence.PersistenceModels.Download;

public interface DownloadRepository {

    List<Download> search(String query, int limit);

    void delete(long id);

    void clear();
}
