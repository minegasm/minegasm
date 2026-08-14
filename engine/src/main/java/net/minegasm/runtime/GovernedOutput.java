package net.minegasm.runtime;

import net.minegasm.core.HapticScene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One central governance result shared by all backends for a cycle. */
public final class GovernedOutput {

    private final List<HapticScene> scenes;
    private final ResolvedDestinationSnapshot destinations;

    public GovernedOutput(List<HapticScene> scenes, ResolvedDestinationSnapshot destinations) {
        this.scenes = Collections.unmodifiableList(new ArrayList<>(scenes));
        this.destinations = destinations;
    }

    /** Active, conflict-resolved layers retained for physical routing refinements. */
    public List<HapticScene> scenes() {
        return scenes;
    }

    /** The authoritative backend-neutral levels sampled at this cycle's time. */
    public ResolvedDestinationSnapshot destinations() {
        return destinations;
    }
}
