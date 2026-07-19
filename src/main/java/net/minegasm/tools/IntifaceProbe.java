package net.minegasm.tools;

import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.OutputCommand;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.buttplug.StopSelection;
import net.minegasm.buttplug.WebSocketTransport;
import net.minegasm.core.OutputKind;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.device.FeatureRef;
import net.minegasm.device.HapticDevice;
import net.minegasm.device.HapticFeature;
import net.minegasm.util.HapticMath;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Standalone connectivity + safe-test-pulse harness against a running Intiface server, with no
 * Minecraft involved (brief Epic 2 exit criteria). Use it to confirm the provider path end-to-end:
 * connect → negotiate v4 → scan → list device features → send a gentle vibration to each enabled
 * vibrate feature → stop. Runs the exact provider/command path the engine uses.
 *
 * <p>Usage:
 * <pre>
 *   IntifaceProbe [--url ws://127.0.0.1:12345] [--backend native|buttplug4j]
 *                 [--intensity 0.25] [--durationMs 400] [--scanMs 4000] [--noPulse]
 * </pre>
 *
 * The {@code native} backend needs only the JDK + Gson; {@code buttplug4j} needs the buttplug4j
 * runtime (run it via the Gradle {@code intifaceProbe} task, which puts those deps on the classpath).
 * Intiface Central's built-in device simulator lets you test with no physical hardware.
 */
public final class IntifaceProbe {

    private IntifaceProbe() {}

    public static void main(String[] args) throws Exception {
        String url = "ws://127.0.0.1:12345";
        String backend = "native";
        float intensity = 0.25f;
        int durationMs = 400;
        int scanMs = 4000;
        boolean pulse = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url" -> url = args[++i];
                case "--backend" -> backend = args[++i];
                case "--intensity" -> intensity = HapticMath.clamp01(Float.parseFloat(args[++i]));
                case "--durationMs" -> durationMs = Integer.parseInt(args[++i]);
                case "--scanMs" -> scanMs = Integer.parseInt(args[++i]);
                case "--noPulse" -> pulse = false;
                default -> System.out.println("ignoring unknown arg: " + args[i]);
            }
        }

        System.out.printf("Connecting to %s via %s backend%n", url, backend);
        HapticProvider provider = create(backend);
        provider.setStatusListener(s -> System.out.println("  status: " + describe(s)));

        try {
            ProviderStatus status = provider.connect(URI.create(url))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            System.out.println("Connected: " + describe(status));

            System.out.println("Scanning for " + scanMs + " ms ...");
            provider.startScanning().toCompletableFuture().get(5, TimeUnit.SECONDS);
            Thread.sleep(scanMs);
            provider.stopScanning();

            DeviceRegistrySnapshot snapshot = provider.devices();
            System.out.println("Devices (" + snapshot.all().size() + "), generation "
                    + snapshot.generation() + ":");
            if (snapshot.isEmpty()) {
                System.out.println("  (none — add a simulated device in Intiface, or connect hardware)");
            }
            for (HapticDevice device : snapshot.all()) {
                printDevice(device);
            }

            if (pulse) {
                pulseAll(provider, snapshot, intensity, durationMs);
            }
        } finally {
            System.out.println("Stopping all output and closing.");
            provider.stop(StopSelection.all());
            Thread.sleep(200);
            provider.close();
        }
    }

    private static void printDevice(HapticDevice device) {
        System.out.printf("  [%d] %s%s  timingGap=%dms%n", device.deviceIndex(), device.label(),
                device.displayName().map(n -> " (" + n + ")").orElse(""), device.messageTimingGapMs());
        for (HapticFeature feature : device.features().values()) {
            feature.outputs().forEach((kind, cap) -> System.out.printf(
                    "      feature %d [%s] %s range=%d..%d%n",
                    feature.featureIndex(), kind, feature.description(),
                    cap.valueRange().min(), cap.valueRange().max()));
        }
    }

    private static void pulseAll(HapticProvider provider, DeviceRegistrySnapshot snapshot,
                                 float intensity, int durationMs) throws InterruptedException {
        System.out.printf("Safe test pulse: %.0f%% for %d ms on each Vibrate feature.%n",
                intensity * 100, durationMs);
        for (HapticDevice device : snapshot.all()) {
            for (HapticFeature feature : device.features().values()) {
                var cap = feature.output(OutputKind.VIBRATE).orElse(null);
                if (cap == null) {
                    continue;
                }
                FeatureRef ref = new FeatureRef(device.deviceIndex(), feature.featureIndex(),
                        snapshot.generation());
                int value = HapticMath.scaleNormalized(intensity, cap.valueRange());
                System.out.printf("  -> device %d feature %d Vibrate=%d%n",
                        device.deviceIndex(), feature.featureIndex(), value);
                provider.send(OutputCommand.of(ref, OutputKind.VIBRATE, value, null));
                Thread.sleep(durationMs);
                provider.send(OutputCommand.of(ref, OutputKind.VIBRATE, 0, null)); // planned stop
                Thread.sleep(150);
            }
        }
    }

    private static HapticProvider create(String backend) throws Exception {
        if ("buttplug4j".equalsIgnoreCase(backend)) {
            // Constructed reflectively so the native run needs no buttplug4j on the classpath.
            Class<?> cls = Class.forName("net.minegasm.buttplug.b4j.Buttplug4jProvider");
            return (HapticProvider) cls.getConstructor(String.class).newInstance("Minegasm");
        }
        return new ButtplugProvider(new WebSocketTransport(), "Minegasm");
    }

    private static String describe(ProviderStatus s) {
        return s.state() + " v" + s.negotiatedVersion().orElse("?")
                + " devices=" + s.deviceCount()
                + s.lastError().map(e -> " error=" + e).orElse("");
    }
}
