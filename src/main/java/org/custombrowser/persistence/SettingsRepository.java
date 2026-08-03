package org.custombrowser.persistence;

import java.util.Map;

public interface SettingsRepository {

    Map<String, String> load();

    void save(Map<String, String> settings);

    void deleteByPrefix(String prefix);
}
