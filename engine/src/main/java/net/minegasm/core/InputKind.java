package net.minegasm.core;

/**
 * Buttplug v4 input verbs (sensors). Not used for gameplay output in the MVP but represented so the
 * registry can surface battery/RSSI later without a model change (brief §12 deferred work).
 */
public enum InputKind {
    BATTERY("Battery"),
    RSSI("Rssi"),
    PRESSURE("Pressure"),
    BUTTON("Button"),
    UNKNOWN("Unknown");

    private final String wireName;

    InputKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static InputKind fromWire(String token) {
        if (token == null) {
            return UNKNOWN;
        }
        for (InputKind k : values()) {
            if (k.wireName.equalsIgnoreCase(token)) {
                return k;
            }
        }
        return UNKNOWN;
    }
}

