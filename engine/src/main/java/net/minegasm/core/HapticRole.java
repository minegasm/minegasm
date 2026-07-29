package net.minegasm.core;

/**
 * The functional role of a layer, used for fatigue budgets and ducking rules (brief §10.1, §10.6).
 * Kept coarse so recipes stay data-driven rather than encoding per-event logic.
 */
public enum HapticRole {
    IMPACT,
    REWARD,
    TEXTURE,
    WARNING,
    AMBIENT,
    CONTROL
}

