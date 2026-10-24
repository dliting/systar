package com.systar.monitor.asset;

/**
 * Runtime exception for asset-related errors.
 */
public class AssetException extends RuntimeException {

    public AssetException(String message) {
        super(message);
    }

    public AssetException(String message, Throwable cause) {
        super(message, cause);
    }

    public AssetException(String format, Object... args) {
        super(format.formatted(args));
    }

    public AssetException(Throwable cause, String format, Object... args) {
        super(format.formatted(args), cause);
    }
}
