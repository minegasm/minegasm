package gg.meza.feelcraft.engine;
import gg.meza.feelcraft.device.HapticCommand;
import java.util.*;
public final class CommandScheduler {
    private final Map<Integer, DeviceQueue> queues = new HashMap<>();
    public synchronized void enqueue(HapticCommand command, int timingGapMs) { queues.computeIfAbsent(command.deviceIndex(), idx -> new DeviceQueue(timingGapMs)).enqueue(command); }
    public synchronized List<HapticCommand> pollDue(long nowNs) { List<HapticCommand> due = new ArrayList<>(); for (DeviceQueue q : queues.values()) { HapticCommand c = q.pollDue(nowNs); if (c != null) due.add(c); } return due; }
    public synchronized void clear() { queues.clear(); }
    private static final class DeviceQueue {
        private final int timingGapMs; private long lastSentNs = 0; private final List<HapticCommand> pending = new ArrayList<>();
        DeviceQueue(int timingGapMs) { this.timingGapMs = Math.max(0, timingGapMs); }
        void enqueue(HapticCommand command) { pending.removeIf(c -> c.expiresAtNs() <= System.nanoTime()); if (command.coalesceKey() != null) pending.removeIf(c -> command.coalesceKey().equals(c.coalesceKey()) && c.featureIndex() == command.featureIndex()); if (pending.size() >= 16) { pending.sort(Comparator.comparingInt(HapticCommand::priority)); pending.remove(0); } pending.add(command); }
        HapticCommand pollDue(long nowNs) { pending.removeIf(c -> c.expiresAtNs() <= nowNs); long gapNs = timingGapMs * 1_000_000L; if (nowNs - lastSentNs < gapNs) return null; HapticCommand best = pending.stream().filter(c -> c.earliestSendNs() <= nowNs).max(Comparator.comparingInt(HapticCommand::priority).thenComparingLong(HapticCommand::expiresAtNs)).orElse(null); if (best != null) { pending.remove(best); lastSentNs = nowNs; } return best; }
    }
}
