package net.minegasm.classic;

/**
 * Tracks which page of a fixed-height row list is visible in a screen's scroll viewport. Minecraft-free
 * so every Classic version (and the modern build, which keeps its own identical copy since it is a
 * separate Gradle project) can share the same paging arithmetic. Deliberately page-at-a-time via
 * explicit up/down steps rather than pixel-precise mouse-wheel scrolling: the old versions' input APIs
 * differ enough (1.7.10/1.8.9's LWJGL2 {@code Mouse} polling vs. 1.16.5's {@code mouseScrolled}) that a
 * single wheel-scroll implementation could not be shared across them, while up/down buttons need nothing
 * version-specific.
 *
 * <p>{@link #resize} is called from a screen's {@code init()} on every layout (including a window
 * resize, which changes how many rows fit) rather than replacing the instance, so the current scroll
 * position survives a resize or a scroll-button click.
 */
public final class RowScroller {
    private int visibleRows;
    private int totalRows;
    private int first;

    public RowScroller(int visibleRows, int totalRows) {
        resize(visibleRows, totalRows);
    }

    public void resize(int visibleRows, int totalRows) {
        this.visibleRows = Math.max(1, visibleRows);
        this.totalRows = Math.max(0, totalRows);
        first = Math.max(0, Math.min(first, Math.max(0, this.totalRows - this.visibleRows)));
    }

    public int first() {
        return first;
    }

    public int visibleRows() {
        return visibleRows;
    }

    public boolean canScrollUp() {
        return first > 0;
    }

    public boolean canScrollDown() {
        return first + visibleRows < totalRows;
    }

    public void up() {
        if (canScrollUp()) {
            first--;
        }
    }

    public void down() {
        if (canScrollDown()) {
            first++;
        }
    }

    public boolean isVisible(int rowIndex) {
        return rowIndex >= first && rowIndex < first + visibleRows;
    }
}
