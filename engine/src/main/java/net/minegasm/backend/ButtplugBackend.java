package net.minegasm.backend;

import net.minegasm.runtime.HapticWorker;
import net.minegasm.runtime.StopReason;

/**
 * The Buttplug backend: a thin lifecycle adapter over the {@link HapticWorker}, which owns the scheduler
 * and Buttplug provider and pulls governed scenes from the central {@link net.minegasm.runtime.SceneGovernor}
 * (ADR-018). Scenes no longer arrive through this backend; it only starts, stops, pauses, and closes the
 * worker, so a Buttplug-only user observes no behavioral difference (the §3.2 regression guard).
 */
public final class ButtplugBackend implements HapticBackend {

    private final HapticWorker worker;

    public ButtplugBackend(HapticWorker worker) {
        this.worker = worker;
    }

    @Override
    public String id() {
        return "buttplug";
    }

    @Override
    public void start() {
        worker.start();
    }

    @Override
    public void stop(StopReason reason) {
        worker.requestStop(reason);
    }

    @Override
    public void pause() {
        worker.pause();
    }

    @Override
    public void resume() {
        worker.resume();
    }

    @Override
    public void discardPause() {
        worker.discardPause();
    }

    @Override
    public void setOutputEnabled(boolean enabled) {
        worker.setOutputEnabled(enabled);
    }

    @Override
    public long lastHealthyCycleNs() {
        return worker.lastHealthyCycleNs();
    }

    @Override
    public void close() {
        worker.shutdown();
    }
}
