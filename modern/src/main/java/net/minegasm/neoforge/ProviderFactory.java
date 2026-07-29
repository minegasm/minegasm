package net.minegasm.neoforge;

import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.WebSocketTransport;
import net.minegasm.buttplug.b4j.Buttplug4jProvider;
import net.minegasm.config.ConfigStore;
import net.minegasm.config.RuntimeConfig;

import java.nio.file.Path;

/**
 * Selects the Buttplug client backend from config (brief §9.2). Default is {@code buttplug4j} (the
 * maintained v4 client library); {@code native} selects the dependency-free JDK-WebSocket provider.
 * Lives in the Gradle-only source set because it references {@link Buttplug4jProvider} (which pulls in
 * buttplug4j/Jetty). No Minecraft types are used here.
 */
public final class ProviderFactory {

    private static final String CLIENT_NAME = "Minegasm";

    private ProviderFactory() {}

    public static HapticProvider create(Path configFile) {
        RuntimeConfig cfg = RuntimeConfig.of(new ConfigStore(configFile).load().config());
        return create(cfg.providerBackend());
    }

    public static HapticProvider create(String backend) {
        if ("native".equalsIgnoreCase(backend)) {
            return new ButtplugProvider(new WebSocketTransport(), CLIENT_NAME);
        }
        return new Buttplug4jProvider(CLIENT_NAME);
    }
}
