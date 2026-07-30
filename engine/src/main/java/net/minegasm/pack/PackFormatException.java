package net.minegasm.pack;

/**
 * Thrown when a scene pack file is malformed or asks for something the format does not allow. Import
 * fails closed on this: a broken or hostile pack never produces a partially applied or silently
 * degraded result (brief 0003 §2.6).
 */
public final class PackFormatException extends RuntimeException {

    public PackFormatException(String message) {
        super(message);
    }

    public PackFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
