package net.bananemdnsa.historystages.screen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScrollTabsTest {

    /** Six pixels per character — close enough to Minecraft's font for arithmetic. */
    private static final ToIntFunction<String> WIDTH = s -> s.length() * 6;

    @Test
    void wordsThatFitAreLaidOutLeftToRightWithTheGap() {
        List<OpenScrollTabs.Tab> tabs =
                OpenScrollTabs.layout(List.of("Ab", "Cd"), 0, 140, WIDTH);
        assertEquals("Ab", tabs.get(0).label());
        assertEquals(0, tabs.get(0).x());
        assertEquals(12 + OpenScrollGeometry.TAB_GAP, tabs.get(1).x());
    }

    @Test
    void nothingIsShortenedWhileThereIsRoom() {
        // 168 is exactly what the four words plus their three gaps cost (48+36+36+30 + 18). An
        // exact fit must not shrink: the loop fires on strictly greater, not on equal.
        List<OpenScrollTabs.Tab> tabs = OpenScrollTabs.layout(
                List.of("Overview", "Things", "Beings", "World"), 0, 168, WIDTH);
        assertEquals(List.of("Overview", "Things", "Beings", "World"),
                tabs.stream().map(OpenScrollTabs.Tab::label).toList());
    }

    @Test
    void theActiveWordIsNeverShortened() {
        List<OpenScrollTabs.Tab> tabs = OpenScrollTabs.layout(
                List.of("Uebersichtsseite", "Gegenstaende", "Kreaturen", "Weltinhalte"),
                1, 120, WIDTH);
        assertEquals("Gegenstaende", tabs.get(1).label());
    }

    @Test
    void inactiveWordsShrinkUntilTheRowFits() {
        // 120 is reachable: the untouchable active word costs 72, three ellipses 18, gaps 18.
        List<OpenScrollTabs.Tab> tabs = OpenScrollTabs.layout(
                List.of("Uebersichtsseite", "Gegenstaende", "Kreaturen", "Weltinhalte"),
                1, 120, WIDTH);
        int end = tabs.get(3).x() + tabs.get(3).width();
        assertTrue(end <= 120, "row ends at " + end);
        assertTrue(tabs.get(0).label().endsWith("…"));
    }

    @Test
    void anImpossiblyNarrowRowStopsInsteadOfLooping() {
        // Below the reachable minimum: every inactive word is already an ellipsis and nothing can
        // give, so layout must accept the overflow rather than shrink forever.
        List<OpenScrollTabs.Tab> tabs = OpenScrollTabs.layout(
                List.of("Overview", "Things", "Beings", "World"), 0, 10, WIDTH);
        assertEquals(4, tabs.size());
    }

    @Test
    void anEmptyListIsAnEmptyRow() {
        assertTrue(OpenScrollTabs.layout(List.of(), 0, 140, WIDTH).isEmpty());
    }
}
