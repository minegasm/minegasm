package net.minegasm.config;

/**
 * Which recipe pack resolves intents into scenes (brief §3.4). Kept separate so compatibility
 * changes never constrain the modern engine (ADR-009).
 */
public enum RecipePackId {
    /** Reproduces legacy Minegasm event enablement, intensities, and durations. */
    CLASSIC,
    /** Modern, shaped, priority/ducking recipe pack, the preferred default for new users. */
    BALANCED;

    public static RecipePackId fromString(String s, RecipePackId fallback) {
        if (s == null) {
            return fallback;
        }
        return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "classic" -> CLASSIC;
            case "balanced", "modern" -> BALANCED;
            default -> fallback;
        };
    }
}
