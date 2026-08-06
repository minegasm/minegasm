package net.minegasm.runtime;

import net.minegasm.config.RuntimeConfig;

import java.util.function.Supplier;

/**
 * Maps client lifecycle signals to stop actions (brief §7.11, §9.10). Called from the client thread;
 * delegates to the governance driver, which freezes or resets the central governor and fans the action
 * across every backend (brief 0003 §3.2, ADR-018). Panic and error paths always stop regardless of
 * config; pause/world-unload honour their config toggles.
 */
public final class LifecycleController {

    private final HapticWorker driver;
    private final Supplier<RuntimeConfig> config;

    public LifecycleController(HapticWorker driver, Supplier<RuntimeConfig> config) {
        this.driver = driver;
        this.config = config;
    }

    public void onPause() {
        switch (config.get().pauseBehavior()) {
            case STOP:
                driver.stopAll(StopReason.PAUSE);
                break;
            case PAUSE:
                driver.pauseAll();
                break;
            case CONTINUE:
            default:
                break;
        }
    }

    public void onResume() {
        driver.resumeAll();
    }

    public void onWorldUnload() {
        if (config.get().stopOnWorldUnload()) {
            driver.stopAll(StopReason.WORLD_UNLOAD);
        } else {
            driver.discardPauseAll();
        }
    }

    public void onDisconnect() {
        driver.stopAll(StopReason.DISCONNECT);
    }

    public void onGameInactive() {
        driver.stopAll(StopReason.GAME_INACTIVE);
    }

    /** Panic action: the highest-priority stop, always honoured (brief §12.1). */
    public void panic() {
        driver.setOutputEnabled(false);
        driver.stopAll(StopReason.PANIC);
    }

    /** Re-enable output after a panic once the user explicitly resumes. */
    public void clearPanic() {
        driver.setOutputEnabled(true);
    }

    public void onTransportError() {
        driver.stopAll(StopReason.TRANSPORT_ERROR);
    }

    public void onConfigReset() {
        driver.stopAll(StopReason.CONFIG_RESET);
    }
}
