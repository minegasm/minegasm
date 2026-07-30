package net.minegasm.neoforge;

/**
 * Tracks which page of a fixed-height row list is visible in a screen's scroll viewport. Deliberately
 * page-at-a-time via explicit up/down steps rather than pixel-precise mouse-wheel scrolling: Minecraft's
 * {@code mouseScrolled} signature changed across the Minecraft versions this mod spans (a delta added a
 * horizontal component in newer versions), and there is no existing precedent for it elsewhere in this
 * codebase to copy from. Up/down buttons are unambiguous across every supported version and need no new
 * per-era guard.
 *
 * <p>{@link #resize} is called from {@code init()} on every layout (including a window resize, which
 * changes how many rows fit) rather than replacing the instance, so the current scroll position survives
 * a resize or a scroll-button click triggering {@code rebuildWidgets()}.
 */
final class RowScroller {
    private int visibleRows;
    private int totalRows;
    private int first;

    RowScroller(int visibleRows, int totalRows) {
        resize(visibleRows, totalRows);
    }

    void resize(int visibleRows, int totalRows) {
        this.visibleRows = Math.max(1, visibleRows);
        this.totalRows = Math.max(0, totalRows);
        first = Math.max(0, Math.min(first, Math.max(0, this.totalRows - this.visibleRows)));
    }

    int first() {
        return first;
    }

    int visibleRows() {
        return visibleRows;
    }

    boolean canScrollUp() {
        return first > 0;
    }

    boolean canScrollDown() {
        return first + visibleRows < totalRows;
    }

    void up() {
        if (canScrollUp()) {
            first--;
        }
    }

    void down() {
        if (canScrollDown()) {
            first++;
        }
    }

    boolean isVisible(int rowIndex) {
        return rowIndex >= first && rowIndex < first + visibleRows;
    }
}
