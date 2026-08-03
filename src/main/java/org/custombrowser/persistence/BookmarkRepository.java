package org.custombrowser.persistence;

import java.util.List;

import org.custombrowser.persistence.PersistenceModels.Bookmark;

public interface BookmarkRepository {

    List<Bookmark> search(String query, int limit);

    Bookmark add(String title, String url);

    void delete(long id);

    void clear();
}
