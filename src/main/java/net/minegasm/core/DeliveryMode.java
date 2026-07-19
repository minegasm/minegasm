package net.minegasm.core;

/**
 * How a route spreads a layer across the enabled compatible features (brief Â§5.4).
 */
public enum DeliveryMode {
    /** Render to every enabled compatible feature. Default for impact/reward vibration. */
    ALL_COMPATIBLE,
    /** Highest-scoring feature/renderer per device. */
    BEST_PER_DEVICE,
    /** A single global endpoint, to avoid duplication. */
    BEST_GLOBAL,
    /** Play only if the user enabled that layer/output class. Default for experimental motion. */
    SUPPLEMENTAL,
    /** Suppress weaker layers on the same endpoint. */
    EXCLUSIVE
}

