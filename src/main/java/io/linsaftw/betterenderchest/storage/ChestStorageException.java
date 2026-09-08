package io.linsaftw.betterenderchest.storage;

public class ChestStorageException extends RuntimeException {
    public ChestStorageException(final String message) {
        super(message);
    }

    public ChestStorageException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
