package net.minegasm.core;

import java.util.Optional;

/**
 * Buttplug v4 output verbs, normalized into an internal enum. Unknown/future output strings are
 * intentionally represented as {@link #UNKNOWN} so the registry can store and display them without
 * ever routing to them (brief §9.6, ADR-008).
 *
 * <p>{@code Spray} is deliberately absent: it is permanently unsupported and must never be routed
 * (brief non-goal). A stray {@code "Spray"} wire string maps to {@link #UNKNOWN}.
 */
public enum OutputKind {
    VIBRATE("Vibrate"),
    OSCILLATE("Oscillate"),
    ROTATE("Rotate"),
    CONSTRICT("Constrict"),
    POSITION("Position"),
    HW_POSITION_WITH_DURATION("HwPositionWithDuration"),
    TEMPERATURE("Temperature"),
    LED("Led"),
    /** Any wire string we do not model. Stored/displayed, never rendered. */
    UNKNOWN("Unknown");

    private final String wireName;

    OutputKind(String wireName) {
        this.wireName = wireName;
    }

    /** The exact Buttplug protocol token (the key inside an {@code OutputCmd.Command} object). */
    public String wireName() {
        return wireName;
    }

    /** True if this kind carries a movement duration field rather than a held level. */
    public boolean carriesDuration() {
        return this == HW_POSITION_WITH_DURATION;
    }

    /** True if the output holds its value until explicitly changed (needs a planned zero/neutral). */
    public boolean isHeldLevel() {
        return switch (this) {
            case VIBRATE, OSCILLATE, ROTATE, CONSTRICT, TEMPERATURE, POSITION, LED -> true;
            case HW_POSITION_WITH_DURATION -> true; // endpoint stays at final position after the move
            case UNKNOWN -> false;
        };
    }

    /**
     * Parse a wire token. Unrecognised tokens (including {@code "Spray"}) return {@link #UNKNOWN}
     * rather than throwing, so a malformed or future device list never aborts registry ingestion.
     */
    public static OutputKind fromWire(String token) {
        if (token == null) {
            return UNKNOWN;
        }
        for (OutputKind k : values()) {
            if (k.wireName.equalsIgnoreCase(token)) {
                return k;
            }
        }
        return UNKNOWN;
    }

    /** Present only for kinds we can serialise back to the wire (i.e. not {@link #UNKNOWN}). */
    public Optional<String> renderableWireName() {
        return this == UNKNOWN ? Optional.empty() : Optional.of(wireName);
    }
}

