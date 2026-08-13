package net.minegasm.core;

/**
 * Where on the body an effect is delivered or a device is worn. Region is the second axis of a logical
 * destination, alongside role: two effects compete only when they share a role and their regions overlap,
 * so a warning scoped to one region no longer silences an ambient on another. Everything defaults to
 * {@link #WHOLE_BODY}, which reaches every region, so a setup that tags nothing behaves exactly as before
 * region existed.
 *
 * <p>Two relations drive resolution. {@link #overlaps} is symmetric and decides whether two things share
 * any physical reach (used to route an effect to a device and to scope competition). {@link #contains} is
 * directional and decides whether one region's reach wholly includes another's (used for the coarse,
 * device-neutral suppression in {@link net.minegasm.runtime.SceneGovernor}: an exclusive layer may drop a
 * lower-priority same-role layer only when it wholly contains that layer's region, never partially). The
 * per-device final resolution then happens in the renderer, which does have a device model.
 *
 * <p>The specific regions are an educated default taxonomy, not hardware-validated placement. Two spare
 * {@link #GENERIC_A}/{@link #GENERIC_B} slots exist for anything the named set does not cover.
 */
public enum BodyRegion {
    WHOLE_BODY,
    GENITAL,
    ANAL,
    NIPPLE,
    PERINEAL,
    ORAL,
    GENERIC_A,
    GENERIC_B;

    /** Whether this region's reach wholly includes {@code other}: whole-body reaches all; a region itself. */
    public boolean contains(BodyRegion other) {
        return this == WHOLE_BODY || this == other;
    }

    /** Whether two regions share any physical reach: they are equal, or either is whole-body. */
    public boolean overlaps(BodyRegion other) {
        return this == WHOLE_BODY || other == WHOLE_BODY || this == other;
    }
}
