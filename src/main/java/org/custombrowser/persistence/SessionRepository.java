package org.custombrowser.persistence;

import java.time.Instant;

import org.custombrowser.persistence.PersistenceModels.BrowserSession;
import org.custombrowser.persistence.PersistenceModels.WindowState;

public interface SessionRepository {

    BrowserSession load();

    void save(BrowserSession session);

    WindowState loadWindowState();

    void saveWindowState(WindowState state);

    void clearSessionData();

    int countOldSessionRecordsBefore(Instant cutoff);

    int deleteOldSessionRecordsBefore(Instant cutoff);
}
