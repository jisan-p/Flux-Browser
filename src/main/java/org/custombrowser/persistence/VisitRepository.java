package org.custombrowser.persistence;

import java.time.Instant;
import java.util.List;

import org.custombrowser.persistence.PersistenceModels.Visit;

public interface VisitRepository {

    List<Visit> search(String query, int limit);

    void record(String title, String url);

    void delete(long id);

    void clear();

    int countBefore(Instant cutoff);

    int deleteBefore(Instant cutoff);
}
