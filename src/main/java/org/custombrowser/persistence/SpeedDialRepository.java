package org.custombrowser.persistence;

import java.util.List;

import org.custombrowser.ui.model.SpeedDialEntry;

public interface SpeedDialRepository {

    List<SpeedDialEntry> load();

    void replaceAll(List<SpeedDialEntry> entries);
}
