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

    /**
     * A canary, not a round number. 544 is the largest such stage that still fits, so this test
     * fails the moment anything makes a narrowed entry longer on disk.
     *
     * <p>It has already caught one: {@code unlock_actions} stores the <em>complement</em>, so
     * every action added to {@link net.bananemdnsa.historystages.api.lock.LockActions#ITEM} adds
     * one more word to every narrowed entry in every stage. Adding {@code trade} as the eleventh
     * took the ceiling from 581 entries down to 544 — about six percent of the headroom, paid by
     * packs that narrow actions on hundreds of items in one stage.
     *
     * <p>If this fails after another action is added, that is the test doing its job. Lower the
     * number and say in the changelog that stages got bigger; do not quietly widen the limit.
     */
    @Test
    void aRealisticLargeStageStillFits() {
        assertTrue(StageJsonLimits.fitsSavePacket(stageWith(544).toCompactJson()));
        assertFalse(StageJsonLimits.fitsSavePacket(stageWith(545).toCompactJson()),
                "544 is meant to be the ceiling - if 545 fits too, the measurement is stale");
    }

    @Test
    void anOversizedStageIsRejectedInsteadOfSent() {
        assertFalse(StageJsonLimits.fitsSavePacket(stageWith(1000).toCompactJson()));
    }
}
