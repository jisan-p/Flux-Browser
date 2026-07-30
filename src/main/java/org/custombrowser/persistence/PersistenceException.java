package org.custombrowser.persistence;

/**
 * Actionable persistence failure that is safe to show without credentials.
 */
public final class PersistenceException extends RuntimeException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
