package net.minegasm.classic;

import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.b4j.Buttplug4jProvider;
import net.minegasm.config.ConfigStore;
import net.minegasm.config.RuntimeConfig;

import java.nio.file.Path;

/**
 * Selects the Buttplug client backend for the classic loaders from config (mirrors the modern
 * {@code ProviderFactory}). Default is {@code buttplug4j}; {@code native} uses the bundled
 * Java-WebSocket transport (ADR-019). No Minecraft types are used here.
 */
public final class ClassicProviderFactory {

    private static final String CLIENT_NAME = "Minegasm";

    private ClassicProviderFactory() {
    }

    public static HapticProvider create(Path configFile) {
        RuntimeConfig cfg = RuntimeConfig.of(new ConfigStore(configFile).load().config());
        return create(cfg.providerBackend());
    }

    public static HapticProvider create(String backend) {
        if ("native".equalsIgnoreCase(backend)) {
            return new ButtplugProvider(new ClassicWebSocketTransport(), CLIENT_NAME);
        }
        return new Buttplug4jProvider(CLIENT_NAME);
    }
}
