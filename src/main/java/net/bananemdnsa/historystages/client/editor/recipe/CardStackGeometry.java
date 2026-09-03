package net.bananemdnsa.historystages.client.editor.recipe;

import java.util.List;

/**
 * A vertical stack of cards whose heights differ, and the scroll window over it.
 *
 * <p>Recipe cards are as tall as the recipe they show — one row for a furnace, three for a
 * crafting grid — so a card's position is the sum of everything above it rather than its index
 * times a constant. Every list widget in this editor before this one multiplied, and every one of
 * them would be off by a card here.
 *
 * <p>Scroll is measured in pixels, not in cards: a window that starts mid-card is the normal
 * case, and rounding it to a card boundary would make the wheel jump by different amounts
 * depending on what happened to be under it.
 *
 * <p>No Minecraft type appears here, so it can be unit tested.
 */
public final class CardStackGeometry {

    private CardStackGeometry() {
    }

    /** Distance from the top of the stack to the top of card {@code index}. */
    public static int offsetOf(List<Integer> heights, int index) {
        int offset = 0;
        for (int i = 0; i < index && i < heights.size(); i++) {
            offset += heights.get(i);
        }
        return offset;
    }

    /** Height of the whole stack. */
    public static int totalHeight(List<Integer> heights) {
        int total = 0;
        for (int height : heights) {
            total += height;
        }
        return total;
    }

    /**
     * Largest legal scroll, in pixels: enough to bring the bottom of the last card flush with the
     * bottom of the window, and zero when everything already fits.
     *
     * <p>Derived from the total rather than from a card count, which is what makes a single card
     * taller than the window still fully reachable.
     */
    public static int maxScroll(List<Integer> heights, int windowHeight) {
        return Math.max(0, totalHeight(heights) - windowHeight);
    }

    /** Keeps a scroll position inside {@code [0, maxScroll]}. */
    public static int clampScroll(int scroll, List<Integer> heights, int windowHeight) {
        return Math.max(0, Math.min(maxScroll(heights, windowHeight), scroll));
    }

    /**
     * Index of the card at {@code yInWindow} pixels below the top of the window, or {@code -1}
     * when that lands outside the stack. A pixel exactly on a seam belongs to the card below it,
     * which keeps the ranges free of gaps and overlaps.
     */
    public static int indexAt(List<Integer> heights, int scroll, double yInWindow) {
        double y = yInWindow + scroll;
        if (y < 0) return -1;
        double cursor = 0;
        for (int i = 0; i < heights.size(); i++) {
            cursor += heights.get(i);
            if (y < cursor) return i;
        }
        return -1;
    }

    /** First card index that has any pixel inside the window. */
    public static int firstVisible(List<Integer> heights, int scroll) {
        int cursor = 0;
        for (int i = 0; i < heights.size(); i++) {
            cursor += heights.get(i);
            if (cursor > scroll) return i;
        }
        return 0;
    }

    /**
     * One past the last card with any pixel inside the window, so callers can write
     * {@code for (int i = firstVisible(...); i < endVisible(...); i++)}. A card poking only
     * partly into the window is included — clipping it out would leave a gap at the edge.
     */
    public static int endVisible(List<Integer> heights, int scroll, int windowHeight) {
        int bottom = scroll + windowHeight;
        int cursor = 0;
        for (int i = 0; i < heights.size(); i++) {
            cursor += heights.get(i);
            if (cursor >= bottom) return i + 1;
        }
        return heights.size();
    }
}
