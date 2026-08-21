package net.bananemdnsa.historystages.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StageJsonLimitsTest {

    private static String ofLength(int length) {
        return "x".repeat(length);
    }

    @Test
    void acceptsJsonExactlyAtTheLimit() {
        assertTrue(StageJsonLimits.fitsSavePacket(ofLength(StageJsonLimits.MAX_STAGE_JSON)));
    }

    @Test
    void rejectsJsonOneCharacterOver() {
        assertFalse(StageJsonLimits.fitsSavePacket(ofLength(StageJsonLimits.MAX_STAGE_JSON + 1)));
    }

    @Test
    void rejectsNull() {
        assertFalse(StageJsonLimits.fitsSavePacket(null));
    }

    /** Builds a stage with {@code count} items where every item restricts single actions. */
    private static StageEntry stageWith(int count) {
        StageEntry entry = new StageEntry();
        entry.setDisplayName("Sized Stage");
        List<ItemEntry> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new ItemEntry("somemod:some_item_" + i, null, List.of("use", "attack")));
        }
        entry.setItemEntries(items);
        return entry;
    }

    @Test
    void aRealisticLargeStageStillFits() {
        assertTrue(StageJsonLimits.fitsSavePacket(stageWith(581).toCompactJson()));
    }

    @Test
    void anOversizedStageIsRejectedInsteadOfSent() {
        assertFalse(StageJsonLimits.fitsSavePacket(stageWith(1000).toCompactJson()));
    }
}
