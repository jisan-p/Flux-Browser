package org.custombrowser.ui.model;

import java.util.Objects;

/**
 * A user-visible shortcut on the Flux start page.
 */
public record SpeedDialEntry(String title, String address) {

    public SpeedDialEntry {
        title = requireText(title, "title");
        address = requireText(address, "address");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
