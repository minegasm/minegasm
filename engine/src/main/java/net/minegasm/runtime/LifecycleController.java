package net.minegasm.runtime;

import net.minegasm.backend.BackendCoordinator;
import net.minegasm.config.RuntimeConfig;

import java.util.function.Supplier;

/**
 * Maps client lifecycle signals to stop actions (brief §7.11, §9.10). Called from the client thread;
 * delegates the actual stop to the backend coordinator, which fans it across every enabled backend
 * (brief 0003 §3.2). Panic and error paths always stop regardless of config; pause/world-unload honour
 * their config toggles.
 */
public final class LifecycleController {

    private final BackendCoordinator backends;
    private final Supplier<RuntimeConfig> config;

    public LifecycleController(BackendCoordinator backends, Supplier<RuntimeConfig> config) {
        this.backends = backends;
        this.config = config;
    }

    public void onPause() {
        switch (config.get().pauseBehavior()) {
            case STOP:
                backends.stopAll(StopReason.PAUSE);
                break;
            case PAUSE:
                backends.pauseAll();
                break;
            case CONTINUE:
            default:
                break;
        }
    }

    public void onResume() {
        backends.resumeAll();
    }

    public void onWorldUnload() {
        if (config.get().stopOnWorldUnload()) {
            backends.stopAll(StopReason.WORLD_UNLOAD);
        } else {
            backends.discardPauseAll();
        }
    }

    public void onDisconnect() {
        backends.stopAll(StopReason.DISCONNECT);
    }

    public void onGameInactive() {
        backends.stopAll(StopReason.GAME_INACTIVE);
    }

    /** Panic action: the highest-priority stop, always honoured (brief §12.1). */
    public void panic() {
        backends.setOutputEnabled(false);
        backends.stopAll(StopReason.PANIC);
    }

    /** Re-enable output after a panic once the user explicitly resumes. */
    public void clearPanic() {
        backends.setOutputEnabled(true);
    }

    public void onTransportError() {
        backends.stopAll(StopReason.TRANSPORT_ERROR);
    }

    public void onConfigReset() {
        backends.stopAll(StopReason.CONFIG_RESET);
    }
}
