package net.minegasm.core;

/**
 * Coarse spatial hint for an intent. Used by future directional routing (e.g. left/right hand
 * devices); vibration renderers ignore it. Kept simple and device-independent (brief Â§5.2).
 */
public enum SpatialDirection {
    NONE,
    FORWARD,
    BACKWARD,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    OMNI
}

