package net.minegasm.backend;

import net.minegasm.core.HapticScene;
import net.minegasm.runtime.HapticWorker;
import net.minegasm.runtime.StopReason;

/**
 * The Buttplug backend: a thin adapter over the existing {@link HapticWorker}, which already owns the
 * mixer, scheduler, and Buttplug provider (brief 0003 §3.2). Wrapping the worker unchanged is the
 * regression guard, a Buttplug-only user must observe no behavioral difference, so this class only
 * forwards calls and adds no logic of its own.
 *
 * <p>When central mixing lands (brief 0003 §3.3), the mixer and fatigue governor move up to the
 * coordinator and this backend shrinks to the device-rendering half; until then it holds the whole
 * worker.
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
    public void submit(HapticScene scene) {
        worker.offer(scene);
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
