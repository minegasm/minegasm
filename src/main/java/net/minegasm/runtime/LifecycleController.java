package net.minegasm.runtime;

import net.minegasm.config.RuntimeConfig;

import java.util.function.Supplier;

/**
 * Maps client lifecycle signals to stop actions (brief §7.11, §9.10). Called from the client thread;
 * delegates the actual stop to the worker, which owns engine state. Panic and error paths always
 * stop regardless of config; pause/world-unload honour their config toggles.
 */
public final class LifecycleController {

    private final HapticWorker worker;
    private final Supplier<RuntimeConfig> config;

    public LifecycleController(HapticWorker worker, Supplier<RuntimeConfig> config) {
        this.worker = worker;
        this.config = config;
    }

    public void onPause() {
        switch (config.get().pauseBehavior()) {
            case STOP -> worker.requestStop(StopReason.PAUSE);
            case PAUSE -> worker.pause();
            case CONTINUE -> { }
        }
    }

    public void onResume() {
        worker.resume();
    }

    public void onWorldUnload() {
        if (config.get().stopOnWorldUnload()) {
            worker.requestStop(StopReason.WORLD_UNLOAD);
        } else {
            worker.discardPause();
        }
    }

    public void onDisconnect() {
        worker.requestStop(StopReason.DISCONNECT);
    }

    public void onGameInactive() {
        worker.requestStop(StopReason.GAME_INACTIVE);
    }

    /** Panic action: the highest-priority stop, always honoured (brief §12.1). */
    public void panic() {
        worker.setOutputEnabled(false);
        worker.requestStop(StopReason.PANIC);
    }

    /** Re-enable output after a panic once the user explicitly resumes. */
    public void clearPanic() {
        worker.setOutputEnabled(true);
    }

    public void onTransportError() {
        worker.requestStop(StopReason.TRANSPORT_ERROR);
    }

    public void onConfigReset() {
        worker.requestStop(StopReason.CONFIG_RESET);
    }
}
