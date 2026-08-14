package net.minegasm.recipe;

import net.minegasm.config.HapticConfig;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.BodyRegion;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticIntent;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticScene;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The built-in event-to-region mapping: reward events default to the genital region, everything else stays
 * whole-body, applied by the shipped packs through {@link Recipes#scene}. See
 * docs/design/body-region-event-mapping.md.
 */
class EventRegionsTest {

    @Test
    void rewardEventsMapToGenitalEverythingElseWholeBody() {
        assertEquals(BodyRegion.GENITAL, EventRegions.regionFor(GameEventKind.XP_GAIN));
        assertEquals(BodyRegion.GENITAL, EventRegions.regionFor(GameEventKind.ADVANCEMENT));
        assertEquals(BodyRegion.GENITAL, EventRegions.regionFor(GameEventKind.FISHING_BITE));
        for (GameEventKind kind : GameEventKind.values()) {
            if (kind != GameEventKind.XP_GAIN && kind != GameEventKind.ADVANCEMENT
                    && kind != GameEventKind.FISHING_BITE) {
                assertEquals(BodyRegion.WHOLE_BODY, EventRegions.regionFor(kind),
                        kind + " should stay whole-body");
            }
        }
    }

    @Test
    void placeIsANoOpForWholeBody() {
        HapticLayer layer = new HapticLayer("l", null, new net.minegasm.core.HapticPrimitive.Hold(
                0.5f, 200, 0, 0), null, null, 0, 0, 200_000_000L, null);
        assertTrue(EventRegions.place(Collections.singletonList(layer), BodyRegion.WHOLE_BODY)
                .get(0) == layer, "whole-body returns the same layer, not a copy");
        assertEquals(BodyRegion.GENITAL, EventRegions.place(Collections.singletonList(layer),
                BodyRegion.GENITAL).get(0).bodyRegion());
    }

    @Test
    void builtInPackTagsRewardScenesGenitalAndLeavesDamageWholeBody() {
        RecipeEngine engine = new RecipeEngine(); // built-in packs only

        HapticScene reward = engine.resolve(intent(GameEventKind.XP_GAIN), config())
                .orElseThrow(AssertionError::new);
        assertFalse(reward.layers().isEmpty(), "the reward event produces layers to place");
        assertTrue(reward.layers().stream().allMatch(l -> l.bodyRegion() == BodyRegion.GENITAL),
                "every layer of a reward scene is placed in the genital region");

        HapticScene damage = engine.resolve(intent(GameEventKind.HURT), config())
                .orElseThrow(AssertionError::new);
        assertTrue(damage.layers().stream().allMatch(l -> l.bodyRegion() == BodyRegion.WHOLE_BODY),
                "a damage scene stays whole-body");
    }

    private static HapticIntent intent(GameEventKind kind) {
        return new HapticIntent(kind, kind.key(), 1f, 1f, null, null, Collections.<String>emptySet(),
                1L, 1_000_000_000L);
    }

    private static RuntimeConfig config() {
        HapticConfig d = HapticConfig.defaults();
        HapticConfig.Profile profile = new HapticConfig.Profile("balanced", MinegasmMode.IMMERSION.name());
        HapticConfig.Global g = new HapticConfig.Global(true, 1.0, 0.0, false, "STOP", true, "",
                50, 2_000, 100, 10_000);
        HapticConfig cfg = new HapticConfig(1, profile, g, d.buttplug(), d.events(), d.outputPolicy(),
                d.devices(), d.positionCalibrations(), d.accumulation(), d.customIntensity(),
                d.bridges());
        return RuntimeConfig.of(cfg);
    }
}
