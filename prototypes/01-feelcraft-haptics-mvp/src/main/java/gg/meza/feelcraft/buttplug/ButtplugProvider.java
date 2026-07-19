package gg.meza.feelcraft.buttplug;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.meza.feelcraft.device.HapticCommand;
import gg.meza.feelcraft.device.HapticDevice;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
public final class ButtplugProvider implements AutoCloseable {
    private final AtomicInteger ids = new AtomicInteger(1);
    private final ButtplugDeviceMapper mapper = new ButtplugDeviceMapper();
    private final ButtplugWebSocketClient socket = new ButtplugWebSocketClient(this::onMessage);
    private final List<Consumer<List<HapticDevice>>> deviceListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService pingLoop = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "feelcraft-buttplug-ping"));
    private volatile List<HapticDevice> devices = List.of();
    private volatile int maxPingTimeMs = 0;
    public void connect(String uri) { socket.connect(URI.create(uri)).thenRun(() -> { socket.send(ButtplugMessageFactory.requestServerInfo(nextId())); socket.send(ButtplugMessageFactory.requestDeviceList(nextId())); }); pingLoop.scheduleAtFixedRate(this::pingIfNeeded, 1, 1, TimeUnit.SECONDS); }
    public void startScanning() { socket.send(ButtplugMessageFactory.startScanning(nextId())); }
    public void requestDeviceList() { socket.send(ButtplugMessageFactory.requestDeviceList(nextId())); }
    public List<HapticDevice> devices() { return devices; }
    public void addDeviceListener(Consumer<List<HapticDevice>> listener) { deviceListeners.add(listener); }
    public void send(HapticCommand command) { socket.send(ButtplugMessageFactory.output(nextId(), command)); }
    public void stopAll() { socket.send(ButtplugMessageFactory.stopAll(nextId())); }
    private int nextId() { return ids.getAndIncrement(); }
    private void pingIfNeeded() { if (maxPingTimeMs > 0) socket.send(ButtplugMessageFactory.ping(nextId())); }
    private void onMessage(String text) { try { for (JsonElement element : JsonParser.parseString(text).getAsJsonArray()) { JsonObject obj = element.getAsJsonObject(); if (obj.has("ServerInfo")) { JsonObject server = obj.getAsJsonObject("ServerInfo"); if (server.has("MaxPingTime")) maxPingTimeMs = server.get("MaxPingTime").getAsInt(); } else if (obj.has("DeviceList")) { devices = mapper.parseDeviceList(obj.getAsJsonObject("DeviceList")); for (Consumer<List<HapticDevice>> listener : deviceListeners) listener.accept(devices); } else if (obj.has("Error")) { System.err.println("[Feelcraft] Buttplug error: " + obj.getAsJsonObject("Error")); } } } catch (Exception e) { System.err.println("[Feelcraft] Could not parse Buttplug message: " + e.getMessage()); } }
    @Override public void close() { try { stopAll(); } catch (Exception ignored) {} try { socket.send(ButtplugMessageFactory.disconnect(nextId())); } catch (Exception ignored) {} socket.close(); pingLoop.shutdownNow(); }
}
