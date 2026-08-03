package org.custombrowser.browser;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.custombrowser.persistence.PersistenceService;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * Maintains persisted per-site popup decisions. Unknown sites ask first.
 */
public final class PopupPolicyService {

    private static final String SETTING_PREFIX = "popup_policy.";

    private final PersistenceService persistenceService;
    private final Map<String, Decision> decisions = new LinkedHashMap<>();

    public PopupPolicyService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
        persistenceService.startupState().settings().forEach((key, value) -> {
            if (key.startsWith(SETTING_PREFIX)) {
                try {
                    decisions.put(
                            key.substring(SETTING_PREFIX.length()),
                            Decision.valueOf(value));
                } catch (IllegalArgumentException ignored) {
                    // Ignore an invalid value instead of weakening the policy.
                }
            }
        });
    }

    public boolean allowPopup(URI origin, Window owner) {
        String site = siteKey(origin);
        String policyKey = storageKey(site);
        Decision saved = decisions.getOrDefault(policyKey, Decision.ASK);
        if (saved == Decision.ALLOW) {
            return true;
        }
        if (saved == Decision.BLOCK) {
            return false;
        }

        ButtonType once = new ButtonType(
                "Open once", ButtonBar.ButtonData.YES);
        ButtonType always = new ButtonType(
                "Always allow", ButtonBar.ButtonData.OK_DONE);
        ButtonType block = new ButtonType(
                "Block", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Allow a popup requested by " + site + "?",
                once,
                always,
                block);
        alert.setTitle("Popup request");
        alert.setHeaderText("This page wants to open a new tab");
        if (owner != null) {
            alert.initOwner(owner);
        }
        ButtonType selected = alert.showAndWait().orElse(block);
        if (selected == always) {
            remember(policyKey, Decision.ALLOW);
            return true;
        }
        if (selected == block) {
            remember(policyKey, Decision.BLOCK);
            return false;
        }
        return selected == once;
    }

    public void reset() {
        decisions.clear();
        persistenceService.deleteSettingsByPrefix(SETTING_PREFIX);
    }

    private void remember(String site, Decision decision) {
        decisions.put(site, decision);
        persistenceService.saveSetting(
                SETTING_PREFIX + site,
                decision.name());
    }

    private static String siteKey(URI origin) {
        if (origin == null || origin.getScheme() == null) {
            return "unknown";
        }
        if (origin.getHost() != null) {
            return origin.getHost().toLowerCase(Locale.ROOT);
        }
        return origin.getScheme().toLowerCase(Locale.ROOT);
    }

    private static String storageKey(String site) {
        if (site.length() <= 60) {
            return site;
        }
        return site.substring(0, 50)
                + "-"
                + Integer.toUnsignedString(site.hashCode(), 16);
    }

    private enum Decision {
        ASK,
        ALLOW,
        BLOCK
    }
}
