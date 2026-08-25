package net.bananemdnsa.historystages.data.scroll;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScrollChaptersTest {

    @Test
    void everyChapterRoundTripsThroughItsSerializedName() {
        for (OpenScrollChapter chapter : OpenScrollChapter.values()) {
            assertEquals(chapter, OpenScrollChapter.parse(chapter.serialize()));
        }
    }

    @Test
    void anUnknownChapterIdIsNullSoTheParserCanSkipIt() {
        assertNull(OpenScrollChapter.parse("recipes"));
        assertNull(OpenScrollChapter.parse(null));
        assertEquals(OpenScrollChapterMode.TEXT, OpenScrollChapterMode.parse("nonsense", OpenScrollChapterMode.TEXT));
        assertEquals(OpenScrollChapterMode.ICONS, OpenScrollChapterMode.parse("ICONS", OpenScrollChapterMode.TEXT));
    }

    @Test
    void theDefaultsAreAllFourChaptersInOrder() {
        List<OpenScrollChapterEntry> defaults = OpenScrollChapters.defaults();
        assertEquals(List.of(OpenScrollChapter.OVERVIEW, OpenScrollChapter.ITEMS,
                        OpenScrollChapter.CREATURES, OpenScrollChapter.WORLD),
                defaults.stream().map(OpenScrollChapterEntry::chapter).toList());
        assertTrue(defaults.stream().allMatch(OpenScrollChapterEntry::enabled));
    }

    @Test
    void theDefaultsSurviveEncodingAndParsing() {
        assertEquals(OpenScrollChapters.defaults(),
                OpenScrollChapters.parse(OpenScrollChapters.defaultsEncoded()));
    }

    @Test
    void theListOrderIsTheTabOrder() {
        List<OpenScrollChapterEntry> parsed = OpenScrollChapters.parse(
                List.of("world|true|text", "overview|true|text"));
        assertEquals(OpenScrollChapter.WORLD, parsed.get(0).chapter());
        assertEquals(OpenScrollChapter.OVERVIEW, parsed.get(1).chapter());
    }

    @Test
    void missingChaptersAreAppendedInDefaultOrderSoAHalfWrittenConfigStillWorks() {
        List<OpenScrollChapterEntry> parsed = OpenScrollChapters.parse(List.of("items|true|icons"));
        assertEquals(4, parsed.size());
        assertEquals(OpenScrollChapter.ITEMS, parsed.get(0).chapter());
        assertEquals(List.of(OpenScrollChapter.OVERVIEW, OpenScrollChapter.CREATURES, OpenScrollChapter.WORLD),
                parsed.subList(1, 4).stream().map(OpenScrollChapterEntry::chapter).toList());
    }

    @Test
    void unknownIdsAndDuplicatesAreDropped() {
        List<OpenScrollChapterEntry> parsed = OpenScrollChapters.parse(
                List.of("recipes|true|icons", "items|true|icons", "items|false|text", "", "garbage"));
        assertEquals(4, parsed.size());
        assertTrue(parsed.get(0).enabled(), "the first items line wins, the duplicate is ignored");
    }

    @Test
    void aDisabledChapterKeepsItsPlaceButIsMarkedOff() {
        List<OpenScrollChapterEntry> parsed = OpenScrollChapters.parse(List.of("creatures|false|icons"));
        assertFalse(parsed.get(0).enabled());
    }

    @Test
    void textOnlyChaptersIgnoreAnIconsMode() {
        // There is no icon for a dimension or a biome, and the overview is a page of prose.
        assertEquals(OpenScrollChapterMode.TEXT,
                OpenScrollChapters.parse(List.of("world|true|icons")).get(0).mode());
        assertEquals(OpenScrollChapterMode.TEXT,
                OpenScrollChapters.parse(List.of("overview|true|icons")).get(0).mode());
    }

    @Test
    void aBrokenModeFallsBackToTheChaptersDefault() {
        assertEquals(OpenScrollChapterMode.ICONS,
                OpenScrollChapters.parse(List.of("items|true|pictures")).get(0).mode());
    }

    @Test
    void aBrokenEnabledFlagCountsAsEnabledRatherThanHidingContent() {
        assertTrue(OpenScrollChapters.parse(List.of("items|maybe|icons")).get(0).enabled());
    }
}
