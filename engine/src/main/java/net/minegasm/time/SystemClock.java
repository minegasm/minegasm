package net.minegasm.time;

/** Production {@link Clock} backed by {@link System#nanoTime()}. */
public final class SystemClock implements Clock {

    public static final SystemClock INSTANCE = new SystemClock();

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }
}

