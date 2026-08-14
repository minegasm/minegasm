package net.minegasm.recipe;

import net.minegasm.core.BodyRegion;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in body-region defaults for the shipped recipe packs (see
 * {@code docs/design/body-region-event-mapping.md} for the rationale). The reward-family events default to
 * the genital region; everything else stays whole-body. Applied once, centrally, in {@link Recipes#scene},
 * so both built-in packs inherit it without each restating the mapping.
 *
 * <p>This is a soft default that only changes behavior for a user who assigns a device to a specific
 * region: a whole-body device (the default, and any untagged device) overlaps every region, so it still
 * receives every effect and nothing is ever muted. A genital-tagged device gets the reward pulses plus the
 * broad effects; a nipple-tagged device gets the broad effects but not the genital-scoped reward. Minecraft
 * events have no real anatomy, so this maps by sensation intent, not body part, and is expected to be
 * confirmed by a hardware feel pass. Custom packs author their own region per layer and never pass through
 * here.
 */
final class EventRegions {

    private EventRegions() {
    }

    /** The built-in region for an event kind; {@link BodyRegion#WHOLE_BODY} unless it is a reward event. */
    static BodyRegion regionFor(GameEventKind kind) {
        switch (kind) {
            case XP_GAIN:
            case ADVANCEMENT:
            case FISHING_BITE:
                return BodyRegion.GENITAL;
            default:
                return BodyRegion.WHOLE_BODY;
        }
    }

    /** The layers placed in the given region; the same list unchanged when it is whole-body (a no-op). */
    static List<HapticLayer> place(List<HapticLayer> layers, BodyRegion region) {
        if (region == BodyRegion.WHOLE_BODY) {
            return layers;
        }
        List<HapticLayer> placed = new ArrayList<>(layers.size());
        for (HapticLayer layer : layers) {
            placed.add(layer.withRegion(region));
        }
        return placed;
    }
}
