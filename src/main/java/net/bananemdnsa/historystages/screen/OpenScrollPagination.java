package net.bananemdnsa.historystages.screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits an open scroll chapter into sheets.
 *
 * <p>The hard part is a circular dependency: how much fits on a sheet depends on whether the sheet
 * counter is drawn, and whether it is drawn depends on how many sheets there are. Both methods
 * here break it the same way — measure once against the larger, footer-free capacity, and only if
 * that overflows switch to the smaller one and never look back. The check happens exactly once, so
 * it terminates by construction.
 *
 * <p>Free of Minecraft types: callers hand in plain heights and get back plain indices.
 */
public final class OpenScrollPagination {

    private OpenScrollPagination() {}

    /** One row of a text chapter. {@code heading} drives the orphan rule. */
    public record Row(int height, boolean heading) {}

    /** @param capacity entries per sheet — the number the caller paginates by. */
    public record Sheets(int count, boolean footerShown, int capacity) {}

    /** @param starts index of the first row on each sheet; its size is the sheet count. */
    public record Result(List<Integer> starts, boolean footerShown) {}

    /** Equal-sized entries, i.e. the icon grid. */
    public static Sheets ofUniform(int total, int capacityWithoutFooter, int capacityWithFooter) {
        if (total <= capacityWithoutFooter) {
            return new Sheets(1, false, Math.max(1, capacityWithoutFooter));
        }
        // Never zero: a parchment too small for one entry would divide by zero, and one clipped
        // entry per sheet is still a readable document.
        int capacity = Math.max(1, capacityWithFooter);
        int count = (total + capacity - 1) / capacity;
        return new Sheets(count, true, capacity);
    }

    /** Rows of differing height, i.e. the text chapters with their group headings. */
    public static Result ofRows(List<Row> rows, int heightWithoutFooter, int heightWithFooter) {
        if (rows == null || rows.isEmpty()) return new Result(List.of(0), false);

        int total = 0;
        for (Row row : rows) total += row.height();
        if (total <= heightWithoutFooter) return new Result(List.of(0), false);

        return new Result(pack(rows, Math.max(1, heightWithFooter)), true);
    }

    private static List<Integer> pack(List<Row> rows, int height) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        int used = 0;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (used > 0 && used + row.height() > height) {
                starts.add(i);
                used = 0;
            }
            used += row.height();

            // A group heading must not be the last row on a sheet. Only move it when it is not
            // already the first row there — otherwise the same heading would be pushed forever.
            boolean hasNext = i + 1 < rows.size();
            if (row.heading() && hasNext && used > row.height()
                    && used + rows.get(i + 1).height() > height) {
                starts.add(i);
                used = row.height();
            }
        }
        return starts;
    }
}
