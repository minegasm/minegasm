package net.minegasm.device;

import net.minegasm.core.InputKind;

/** An advertised input/sensor capability. Represented but unused for output in the MVP. */
public record InputCapability(InputKind kind, IntRange valueRange) {

    public InputCapability {
        if (kind == null || valueRange == null) {
            throw new IllegalArgumentException("input kind and range required");
        }
    }
}

