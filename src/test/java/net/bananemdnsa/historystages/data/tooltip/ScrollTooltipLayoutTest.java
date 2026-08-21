package net.bananemdnsa.historystages.data.tooltip;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScrollTooltipLayoutTest {

    @Test
    void encodesAndDecodesAPlainLine() {
        ScrollTooltipLine line = new ScrollTooltipLine("info1", true, false, "gray+italic", "");
        assertEquals("info1|true|false|gray+italic|", ScrollTooltipLayout.encodeLine(line));
        assertEquals(line, ScrollTooltipLayout.decodeLine("info1|true|false|gray+italic|"));
    }

    @Test
    void escapesSeparatorsInText() {
        ScrollTooltipLine line = new ScrollTooltipLine("info1", true, false, "", "a|b;c\\d");
        String encoded = ScrollTooltipLayout.encodeLine(line);
        assertFalse(encoded.substring("info1|true|false||".length()).contains("|"));
        assertFalse(encoded.contains(";"));
        assertEquals(line, ScrollTooltipLayout.decodeLine(encoded));
    }

    @Test
    void roundTripsTextThatLooksLikeAnEscape() {
        ScrollTooltipLine line = new ScrollTooltipLine("info1", true, false, "", "\\p\\s\\\\");
        assertEquals(line, ScrollTooltipLayout.decodeLine(ScrollTooltipLayout.encodeLine(line)));
    }

    @Test
    void roundTripsRealLineBreaks() {
        ScrollTooltipLine line = new ScrollTooltipLine("info1", true, false, "", "a\nb\nc");
        String encoded = ScrollTooltipLayout.encodeLine(line);
        assertFalse(encoded.contains("\n"), "a raw newline would not survive the toml round trip");
        assertEquals(line, ScrollTooltipLayout.decodeLine(encoded));
    }

    @Test
    void changedIconOptionsSurviveTheRoundTrip() {
        List<ScrollTooltipLine> edited = new java.util.ArrayList<>(ScrollTooltipLayout.defaults());
        edited.replaceAll(l -> l.id().equals("dep.icon_fulfilled") ? l.withText("★")
                : l.id().equals("dep.icon_open") ? l.withText("✖")
                : l.id().equals("dep.icon_unknown") ? l.withText("?") : l);

        List<String> encoded = edited.stream().map(ScrollTooltipLayout::encodeLine).toList();
        List<ScrollTooltipLine> parsed = ScrollTooltipLayout.parse(encoded);

        assertEquals("★", find(parsed, "dep.icon_fulfilled").text());
        assertEquals("✖", find(parsed, "dep.icon_open").text());
        assertEquals("?", find(parsed, "dep.icon_unknown").text());
    }

    @Test
    void optionReadsTheEditedIconRatherThanTheFallback() {
        ScrollTooltipLayout.rebuildFromConfig(List.of("dep.icon_fulfilled|true|false||★"));
        assertEquals("★", ScrollTooltipLayout.option("dep.icon_fulfilled", "✔"));
        ScrollTooltipLayout.rebuildFromConfig(List.of());
    }

    private static ScrollTooltipLine find(List<ScrollTooltipLine> lines, String id) {
        return lines.stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void decodeLineRejectsMalformedInput() {
        assertNull(ScrollTooltipLayout.decodeLine("info1|true"));
        assertNull(ScrollTooltipLayout.decodeLine(""));
        assertNull(ScrollTooltipLayout.decodeLine(null));
    }

    @Test
    void decodeLineTreatsNonTrueAsFalse() {
        ScrollTooltipLine line = ScrollTooltipLayout.decodeLine("info1|nope|yes|gray|x");
        assertNotNull(line);
        assertFalse(line.enabled());
        assertFalse(line.spacerBefore());
    }

    @Test
    void defaultsCoverEveryKnownId() {
        List<String> ids = ScrollTooltipLayout.defaults().stream()
                .map(ScrollTooltipLine::id).toList();
        assertTrue(ids.contains(ScrollTooltipLayout.NAME_ID));
        assertTrue(ids.containsAll(ScrollTooltipLayout.MOVABLE_IDS));
        assertTrue(ids.containsAll(ScrollTooltipLayout.DEP_TEMPLATE_IDS));
        assertTrue(ids.containsAll(ScrollTooltipLayout.DEP_OPTION_IDS));
        assertEquals(ids.size(), ids.stream().distinct().count());
    }

    @Test
    void emptyConfigYieldsDefaults() {
        assertEquals(ScrollTooltipLayout.defaults(), ScrollTooltipLayout.parse(List.of()));
        assertEquals(ScrollTooltipLayout.defaults(), ScrollTooltipLayout.parse(null));
    }

    @Test
    void configOrderWinsForMovableLines() {
        List<String> config = List.of(
                "dependencies|true|true||",
                "info1|true|false|gray|");
        List<String> movable = ScrollTooltipLayout.parse(config).stream()
                .map(ScrollTooltipLine::id)
                .filter(ScrollTooltipLayout.MOVABLE_IDS::contains)
                .toList();
        assertEquals("dependencies", movable.get(0));
        assertEquals("info1", movable.get(1));
    }

    @Test
    void missingIdsAreAppendedFromDefaults() {
        List<ScrollTooltipLine> parsed = ScrollTooltipLayout.parse(List.of("info1|false|false||"));
        assertEquals(ScrollTooltipLayout.defaults().size(), parsed.size());
        assertFalse(parsed.stream().filter(l -> l.id().equals("info1")).findFirst()
                .orElseThrow().enabled());
        assertTrue(parsed.stream().filter(l -> l.id().equals("tier")).findFirst()
                .orElseThrow().enabled());
    }

    @Test
    void unknownIdsAreDropped() {
        List<ScrollTooltipLine> parsed = ScrollTooltipLayout.parse(List.of("bogus|true|false||"));
        assertTrue(parsed.stream().noneMatch(l -> l.id().equals("bogus")));
        assertEquals(ScrollTooltipLayout.defaults(), parsed);
    }

    @Test
    void malformedEntryFallsBackWithoutBreakingTheRest() {
        List<ScrollTooltipLine> parsed = ScrollTooltipLayout.parse(List.of(
                "kaputt", "info1|false|false||"));
        assertEquals(ScrollTooltipLayout.defaults().size(), parsed.size());
        assertFalse(parsed.stream().filter(l -> l.id().equals("info1")).findFirst()
                .orElseThrow().enabled());
    }

    @Test
    void nameAndTemplatesKeepTheirFixedPositions() {
        List<ScrollTooltipLine> parsed = ScrollTooltipLayout.parse(List.of(
                "dep.header|true|false|red|", "name|true|false|aqua|", "tier|true|false||"));
        assertEquals(ScrollTooltipLayout.NAME_ID, parsed.get(0).id());
        int firstTemplate = -1;
        int lastMovable = -1;
        for (int i = 0; i < parsed.size(); i++) {
            String id = parsed.get(i).id();
            if (ScrollTooltipLayout.MOVABLE_IDS.contains(id)) lastMovable = i;
            if (firstTemplate < 0 && ScrollTooltipLayout.DEP_TEMPLATE_IDS.contains(id)) {
                firstTemplate = i;
            }
        }
        assertTrue(lastMovable < firstTemplate);
    }

    @Test
    void encodeAndParseRoundTripTheWholeLayout() {
        List<ScrollTooltipLine> defaults = ScrollTooltipLayout.defaults();
        List<String> encoded = defaults.stream().map(ScrollTooltipLayout::encodeLine).toList();
        assertEquals(defaults, ScrollTooltipLayout.parse(encoded));
    }

    @Test
    void styleTokensSplitOnPlus() {
        assertEquals(List.of("gray", "italic"), ScrollTooltipLayout.styleTokens("gray+italic"));
        assertEquals(List.of("gray"), ScrollTooltipLayout.styleTokens(" GRAY "));
        assertEquals(List.of(), ScrollTooltipLayout.styleTokens(""));
        assertEquals(List.of(), ScrollTooltipLayout.styleTokens(null));
    }

    @Test
    void styleTokensPassHexColoursThroughLowercased() {
        assertEquals(List.of("#ff8800"), ScrollTooltipLayout.styleTokens("#FF8800"));
        assertEquals(List.of("#ff8800", "italic"), ScrollTooltipLayout.styleTokens("#FF8800+italic"));
    }

    @Test
    void rebuildMakesTheLayoutLive() {
        ScrollTooltipLayout.rebuildFromConfig(List.of("info1|false|false||"));
        assertFalse(ScrollTooltipLayout.line("info1").enabled());
        ScrollTooltipLayout.rebuildFromConfig(List.of());
        assertTrue(ScrollTooltipLayout.line("info1").enabled());
    }

    @Test
    void movableReturnsOnlyMovableLinesInConfigOrder() {
        ScrollTooltipLayout.rebuildFromConfig(List.of(
                "tier|true|false||", "info1|true|false||"));
        List<String> ids = ScrollTooltipLayout.movable().stream()
                .map(ScrollTooltipLine::id).toList();
        assertEquals("tier", ids.get(0));
        assertEquals("info1", ids.get(1));
        assertEquals(ScrollTooltipLayout.MOVABLE_IDS.size(), ids.size());
        ScrollTooltipLayout.rebuildFromConfig(List.of());
    }

    @Test
    void lineFallsBackToTheDefaultForAnUnknownId() {
        assertNull(ScrollTooltipLayout.line("nope"));
    }

    @Test
    void fillReplacesKnownPlaceholders() {
        assertEquals("  x Eisen (2/5)", ScrollTooltipLayout.fill(
                "  %icon% %name% (%current%/%required%)",
                Map.of("icon", "x", "name", "Eisen", "current", "2", "required", "5")));
    }

    @Test
    void fillLeavesUnknownPlaceholdersVerbatim() {
        assertEquals("%bogus% x", ScrollTooltipLayout.fill("%bogus% %icon%", Map.of("icon", "x")));
    }

    @Test
    void fillHandlesNullAndEmpty() {
        assertEquals("", ScrollTooltipLayout.fill(null, Map.of()));
        assertEquals("plain", ScrollTooltipLayout.fill("plain", Map.of()));
    }
}
