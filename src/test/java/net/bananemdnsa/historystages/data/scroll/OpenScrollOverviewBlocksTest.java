package net.bananemdnsa.historystages.data.scroll;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScrollOverviewBlocksTest {

    @Test
    void everyBlockRoundTripsThroughItsSerializedName() {
        for (OpenScrollOverviewBlock block : OpenScrollOverviewBlock.values()) {
            assertEquals(block, OpenScrollOverviewBlock.parse(block.serialize()));
        }
    }

    @Test
    void anUnknownBlockIdIsNullSoTheParserCanSkipIt() {
        assertNull(OpenScrollOverviewBlock.parse("author"));
        assertNull(OpenScrollOverviewBlock.parse(null));
    }

    @Test
    void theDefaultsAreAllFourBlocksInReadingOrder() {
        List<OpenScrollOverviewBlockEntry> defaults = OpenScrollOverviewBlocks.defaults();
        assertEquals(List.of(OpenScrollOverviewBlock.ICON, OpenScrollOverviewBlock.TITLE,
                        OpenScrollOverviewBlock.DESCRIPTION, OpenScrollOverviewBlock.COUNTS),
                defaults.stream().map(OpenScrollOverviewBlockEntry::block).toList());
        assertTrue(defaults.stream().allMatch(OpenScrollOverviewBlockEntry::enabled));
    }

    @Test
    void theDefaultsSurviveEncodingAndParsing() {
        assertEquals(OpenScrollOverviewBlocks.defaults(),
                OpenScrollOverviewBlocks.parse(OpenScrollOverviewBlocks.defaultsEncoded()));
    }

    @Test
    void aMissingBlockIsAppendedSoAnOldConfigNeverLosesOne() {
        List<OpenScrollOverviewBlockEntry> parsed =
                OpenScrollOverviewBlocks.parse(List.of("counts|true", "icon|false"));
        assertEquals(List.of(OpenScrollOverviewBlock.COUNTS, OpenScrollOverviewBlock.ICON,
                        OpenScrollOverviewBlock.TITLE, OpenScrollOverviewBlock.DESCRIPTION),
                parsed.stream().map(OpenScrollOverviewBlockEntry::block).toList());
        assertEquals(false, parsed.get(1).enabled());
    }

    @Test
    void aBrokenFlagCountsAsEnabledBecauseLosingABlockIsWorse() {
        List<OpenScrollOverviewBlockEntry> parsed = OpenScrollOverviewBlocks.parse(List.of("title|maybe"));
        assertTrue(parsed.get(0).enabled());
    }

    @Test
    void repeatsAndUnknownIdsAreDropped() {
        List<OpenScrollOverviewBlockEntry> parsed =
                OpenScrollOverviewBlocks.parse(List.of("title|true", "title|false", "author|true"));
        assertEquals(4, parsed.size());
        assertEquals(OpenScrollOverviewBlock.TITLE, parsed.get(0).block());
        assertTrue(parsed.get(0).enabled());
    }
}
