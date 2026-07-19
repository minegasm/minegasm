package gg.meza.feelcraft.runtime;
import gg.meza.feelcraft.buttplug.ButtplugProvider;
import gg.meza.feelcraft.config.HapticConfig;
import gg.meza.feelcraft.core.GameHapticEvent;
import gg.meza.feelcraft.device.HapticCommand;
import gg.meza.feelcraft.engine.*;
import gg.meza.feelcraft.haptics.HapticScene;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
public final class HapticRuntime {
    private static HapticRuntime INSTANCE;
    private final HapticConfig config = HapticConfig.defaults();
    private final TickEventBuffer eventBuffer = new TickEventBuffer();
    private final HapticAggregator aggregator = new HapticAggregator();
    private final SceneResolver sceneResolver = new SceneResolver();
    private final HapticMixer mixer = new HapticMixer();
    private final ButtplugRenderer renderer = new ButtplugRenderer(config);
    private final ButtplugProvider provider = new ButtplugProvider();
    private final CommandScheduler scheduler = new CommandScheduler();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "feelcraft-haptic-worker"));
    public static synchronized void bootstrap() { if (INSTANCE == null) INSTANCE = new HapticRuntime(); }
    public static HapticRuntime instance() { return INSTANCE; }
    private HapticRuntime() { if (config.enabled()) provider.connect(config.buttplugUri()); worker.scheduleAtFixedRate(this::flushCommands, 10, 10, TimeUnit.MILLISECONDS); }
    public void record(GameHapticEvent event) { if (config.enabled()) eventBuffer.record(event); }
    public void endClientTick() { List<HapticScene> scenes = eventBuffer.drain().stream().collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), aggregator::aggregate)).stream().map(sceneResolver::resolve).toList(); for (HapticScene scene : mixer.mix(scenes)) for (HapticCommand command : renderer.render(scene, provider.devices())) { int gap = provider.devices().stream().filter(d -> d.deviceIndex() == command.deviceIndex()).findFirst().map(d -> d.timingGapMs()).orElse(50); scheduler.enqueue(command, gap); } }
    public void stopAll(String reason) { eventBuffer.clear(); scheduler.clear(); provider.stopAll(); }
    private void flushCommands() { try { long now = System.nanoTime(); for (HapticCommand command : scheduler.pollDue(now)) provider.send(command); } catch (Exception e) { System.err.println("[Feelcraft] Haptic worker error: " + e.getMessage()); } }
}
