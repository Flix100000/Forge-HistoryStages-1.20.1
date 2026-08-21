package net.bananemdnsa.historystages.screen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScrollPaginationTest {

    // --- uniform entries (the icon grid) ---

    @Test
    void anEmptyChapterIsOneSheetWithoutAFooter() {
        OpenScrollPagination.Sheets sheets = OpenScrollPagination.ofUniform(0, 42, 35);
        assertEquals(1, sheets.count());
        assertFalse(sheets.footerShown());
    }

    @Test
    void whatFitsWithoutAFooterGetsNoFooter() {
        OpenScrollPagination.Sheets sheets = OpenScrollPagination.ofUniform(42, 42, 35);
        assertEquals(1, sheets.count());
        assertFalse(sheets.footerShown());
        assertEquals(42, sheets.capacity());
    }

    @Test
    void oneEntryTooManyFallsBackToTheSmallerCapacity() {
        OpenScrollPagination.Sheets sheets = OpenScrollPagination.ofUniform(43, 42, 35);
        assertTrue(sheets.footerShown());
        assertEquals(35, sheets.capacity());
        assertEquals(2, sheets.count());
    }

    @Test
    void theSheetCountRoundsUp() {
        assertEquals(3, OpenScrollPagination.ofUniform(71, 42, 35).count());
        assertEquals(2, OpenScrollPagination.ofUniform(70, 42, 35).count());
    }

    @Test
    void aCapacityOfZeroCannotDivideByZero() {
        OpenScrollPagination.Sheets sheets = OpenScrollPagination.ofUniform(5, 0, 0);
        assertEquals(5, sheets.count());
        assertEquals(1, sheets.capacity());
    }

    // --- variable rows (the text chapters) ---

    private static List<OpenScrollPagination.Row> rows(int count) {
        List<OpenScrollPagination.Row> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(new OpenScrollPagination.Row(10, false));
        return out;
    }

    @Test
    void rowsThatFitWithoutAFooterStayOnOneSheet() {
        OpenScrollPagination.Result result = OpenScrollPagination.ofRows(rows(12), 121, 100);
        assertEquals(List.of(0), result.starts());
        assertFalse(result.footerShown());
    }

    @Test
    void rowsThatOverflowArePackedAgainstTheSmallerHeight() {
        OpenScrollPagination.Result result = OpenScrollPagination.ofRows(rows(25), 121, 100);
        assertTrue(result.footerShown());
        assertEquals(List.of(0, 10, 20), result.starts());
    }

    @Test
    void aHeadingNeverEndsASheet() {
        List<OpenScrollPagination.Row> rows = new ArrayList<>(rows(9));
        rows.add(new OpenScrollPagination.Row(9, true));   // index 9 — would be last on sheet 1
        rows.addAll(rows(3));
        OpenScrollPagination.Result result = OpenScrollPagination.ofRows(rows, 100, 100);
        // The heading is pushed to sheet 2 rather than standing over nothing.
        assertEquals(List.of(0, 9), result.starts());
    }

    @Test
    void aHeadingThatIsAlreadyFirstOnItsSheetIsNotPushedAgain() {
        List<OpenScrollPagination.Row> rows = new ArrayList<>(rows(10));
        rows.add(new OpenScrollPagination.Row(9, true));
        rows.add(new OpenScrollPagination.Row(95, false));  // never fits beside the heading
        OpenScrollPagination.Result result = OpenScrollPagination.ofRows(rows, 100, 100);
        assertEquals(List.of(0, 10, 11), result.starts());
    }

    @Test
    void aTrailingHeadingIsLeftAloneBecauseThereIsNothingToOrphan() {
        List<OpenScrollPagination.Row> rows = new ArrayList<>(rows(9));
        rows.add(new OpenScrollPagination.Row(9, true));
        // 99 total against 98 forces packing; the heading breaks by height, not by the orphan rule.
        OpenScrollPagination.Result result = OpenScrollPagination.ofRows(rows, 98, 95);
        assertEquals(List.of(0, 9), result.starts());
    }
}
