package net.bananemdnsa.historystages.data.lock.category;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageStateView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryLockResolverTest {

    record Trade(String soldItem) {}
    record Offer(String soldItem) {}

    private static final AddonLockCategory<Trade> TRADES = AddonLockCategory.<Trade>builder("mymod:trades")
            .tabLangKey("editor.mymod.tab.trades")
            .tooltipLangKey("editor.mymod.tooltip.trades")
            .storage(CategoryStorage.gson(Trade.class))
            .matcher(Offer.class, (Trade entry, Offer offer) -> entry.soldItem().equals(offer.soldItem()))
            .build();

    private static StageEntry gating(String... soldItems) {
        StageEntry stage = new StageEntry();
        TRADES.write(stage, Arrays.stream(soldItems).map(Trade::new).toList());
        return stage;
    }

    private static Map<String, StageEntry> stages(Object... idsAndEntries) {
        Map<String, StageEntry> map = new LinkedHashMap<>();
        for (int i = 0; i < idsAndEntries.length; i += 2) {
            map.put((String) idsAndEntries[i], (StageEntry) idsAndEntries[i + 1]);
        }
        return map;
    }

    @Test
    void aSubjectGatedByALockedStageIsLocked() {
        assertEquals(List.of("bronze"), CategoryLockResolver.missingStages(TRADES,
                new Offer("minecraft:emerald"),
                stages("bronze", gating("minecraft:emerald")), StageStateView.NONE_UNLOCKED));
    }

    @Test
    void aSubjectGatedByAnUnlockedStageIsNotLocked() {
        assertTrue(CategoryLockResolver.missingStages(TRADES, new Offer("minecraft:emerald"),
                stages("bronze", gating("minecraft:emerald")),
                StageStateView.of(Set.of("bronze"))).isEmpty());
    }

    @Test
    void anUngatedSubjectIsNeverLocked() {
        assertTrue(CategoryLockResolver.missingStages(TRADES, new Offer("minecraft:diamond"),
                stages("bronze", gating("minecraft:emerald")),
                StageStateView.NONE_UNLOCKED).isEmpty());
    }

    @Test
    void everyGatingStageIsReportedInStageOrder() {
        assertEquals(List.of("bronze", "iron"), CategoryLockResolver.missingStages(TRADES,
                new Offer("minecraft:emerald"),
                stages("bronze", gating("minecraft:emerald"), "iron", gating("minecraft:emerald")),
                StageStateView.NONE_UNLOCKED));
    }

    @Test
    void onlyTheStillLockedGatingStagesAreReported() {
        assertEquals(List.of("iron"), CategoryLockResolver.missingStages(TRADES,
                new Offer("minecraft:emerald"),
                stages("bronze", gating("minecraft:emerald"), "iron", gating("minecraft:emerald")),
                StageStateView.of(Set.of("bronze"))));
    }

    @Test
    void aStageIsReportedOnceEvenWithSeveralMatchingEntries() {
        assertEquals(List.of("bronze"), CategoryLockResolver.missingStages(TRADES,
                new Offer("minecraft:emerald"),
                stages("bronze", gating("minecraft:emerald", "minecraft:emerald")),
                StageStateView.NONE_UNLOCKED));
    }

    @Test
    void isLockedAgreesWithMissingStages() {
        Map<String, StageEntry> stages = stages("bronze", gating("minecraft:emerald"));

        assertTrue(CategoryLockResolver.isLocked(TRADES, new Offer("minecraft:emerald"),
                stages, StageStateView.NONE_UNLOCKED));
        assertFalse(CategoryLockResolver.isLocked(TRADES, new Offer("minecraft:emerald"),
                stages, StageStateView.of(Set.of("bronze"))));
        assertFalse(CategoryLockResolver.isLocked(TRADES, new Offer("minecraft:diamond"),
                stages, StageStateView.NONE_UNLOCKED));
    }

    @Test
    void aCategoryWithoutAMatcherGatesNothing() {
        AddonLockCategory<Trade> plain = AddonLockCategory.<Trade>builder("mymod:plain")
                .tabLangKey("editor.mymod.tab.plain")
                .tooltipLangKey("editor.mymod.tooltip.plain")
                .storage(CategoryStorage.gson(Trade.class))
                .build();

        StageEntry stage = new StageEntry();
        plain.write(stage, List.of(new Trade("minecraft:emerald")));

        assertTrue(CategoryLockResolver.missingStages(plain, new Offer("minecraft:emerald"),
                stages("bronze", stage), StageStateView.NONE_UNLOCKED).isEmpty());
    }

    @Test
    void noStagesAtAllIsNotLocked() {
        assertFalse(CategoryLockResolver.isLocked(TRADES, new Offer("minecraft:emerald"),
                Map.of(), StageStateView.NONE_UNLOCKED));
    }
}
