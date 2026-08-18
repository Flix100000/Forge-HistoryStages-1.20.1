package net.bananemdnsa.historystages.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Lays out the open scroll's chapter words along the top of the parchment.
 *
 * <p>The row must stay one 9px band whatever a translation costs, so instead of wrapping or
 * falling back to icons, the inactive words are shortened with an ellipsis until the row fits. The
 * active word is never touched: it is the page's heading, and a heading you cannot read is worse
 * than a neighbour you cannot.
 *
 * <p>Width arrives as a function so the arithmetic is testable without a font.
 */
public final class OpenScrollTabs {

    private OpenScrollTabs() {}

    private static final String ELLIPSIS = "…";

    /** One chapter word: what to draw, and where, panel-relative to the parchment's left edge. */
    public record Tab(String label, int x, int width) {}

    public static List<Tab> layout(List<String> labels, int activeIndex, int available,
                                   ToIntFunction<String> width) {
        List<String> shown = new ArrayList<>(labels);
        while (totalWidth(shown, width) > available && shorten(shown, activeIndex, width)) {
            // shorten() reports whether anything could still give.
        }

        List<Tab> out = new ArrayList<>();
        int x = 0;
        for (String label : shown) {
            int w = width.applyAsInt(label);
            out.add(new Tab(label, x, w));
            x += w + OpenScrollGeometry.TAB_GAP;
        }
        return out;
    }

    private static int totalWidth(List<String> labels, ToIntFunction<String> width) {
        if (labels.isEmpty()) return 0;
        int total = OpenScrollGeometry.TAB_GAP * (labels.size() - 1);
        for (String label : labels) total += width.applyAsInt(label);
        return total;
    }

    /**
     * Trims one character off the widest inactive word.
     *
     * @return false when no word can give any more, which is the caller's signal to accept the
     *         overflow rather than loop forever.
     */
    private static boolean shorten(List<String> labels, int activeIndex, ToIntFunction<String> width) {
        int widest = -1;
        int widestWidth = -1;
        for (int i = 0; i < labels.size(); i++) {
            if (i == activeIndex) continue;
            String label = labels.get(i);
            if (label.isEmpty() || label.equals(ELLIPSIS)) continue;
            int w = width.applyAsInt(label);
            if (w > widestWidth) {
                widest = i;
                widestWidth = w;
            }
        }
        if (widest < 0) return false;

        String label = labels.get(widest);
        String body = label.endsWith(ELLIPSIS) ? label.substring(0, label.length() - 1) : label;
        if (body.isEmpty()) return false;
        labels.set(widest, body.substring(0, body.length() - 1) + ELLIPSIS);
        return true;
    }
}
