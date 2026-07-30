package org.custombrowser.persistence;

import org.custombrowser.persistence.PersistenceModels.BrowserSession;
import org.custombrowser.persistence.PersistenceModels.WindowState;

public interface SessionRepository {

    BrowserSession load();

    void save(BrowserSession session);

    WindowState loadWindowState();

    void saveWindowState(WindowState state);
}
