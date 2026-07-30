package net.minegasm.pack;

import net.minegasm.core.GameEventKind;

import java.util.Objects;

/**
 * Maps one game event to the scene it should feel like (brief 0003 §2.3). Tier 1 is one template per
 * event; context predicates (ore vs stone, hardness bands) are a Tier 2 addition.
 */
public final class PackTrigger {

    private final GameEventKind event;
    private final SceneTemplate scene;

    public PackTrigger(GameEventKind event, SceneTemplate scene) {
        if (event == null) {
            throw new IllegalArgumentException("event required");
        }
        if (scene == null) {
            throw new IllegalArgumentException("scene required");
        }
        this.event = event;
        this.scene = scene;
    }

    public GameEventKind event() {
        return event;
    }

    public SceneTemplate scene() {
        return scene;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PackTrigger)) {
            return false;
        }
        PackTrigger other = (PackTrigger) o;
        return event == other.event && Objects.equals(scene, other.scene);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event, scene);
    }

    @Override
    public String toString() {
        return "PackTrigger[event=" + event + ", scene=" + scene + "]";
    }
}
