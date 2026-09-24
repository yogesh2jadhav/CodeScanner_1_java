package com.java2okf.config;

/**
 * Raised when configuration cannot be loaded or is invalid. The message is
 * intended to be shown to the user as-is.
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
