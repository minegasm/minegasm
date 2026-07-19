package net.minegasm.core;

/**
 * How a layer combines with other active layers on the same endpoint (brief Â§5.2).
 */
public enum CouplingMode {
    /** Highest level wins on the endpoint. */
    MAX,
    /** Replaces any lower-or-equal-priority layer on the endpoint. */
    REPLACE,
    /** Adds on top, clamped to the safety cap. */
    ADD,
    /** Suppresses weaker layers on the same endpoint while active. */
    EXCLUSIVE
}

