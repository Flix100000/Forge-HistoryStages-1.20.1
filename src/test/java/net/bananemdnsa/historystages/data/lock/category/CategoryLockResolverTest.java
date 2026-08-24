package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.CategoryStorage;

import net.bananemdnsa.historystages.api.lock.AddonLockCategory;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.stage.StageStateView;
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

    // ---- the stage-level hook ------------------------------------------------------
    //
    // Two built-ins need an answer their own entries cannot give: a mod lock is vetoed by the
    // stage's exception list, and an attack lock can be implied by a spawn lock in a different
    // category on the same stage. VETOED stands in for that shape here.

    private static final LockCategory<Trade> VETOED = new LockCategory<>() {
        @Override public String id() { return "mymod:vetoed"; }
        @Override public String tabLangKey() { return "editor.mymod.tab.vetoed"; }
        @Override public String tooltipLangKey() { return "editor.mymod.tooltip.vetoed"; }
        @Override public List<Trade> read(StageEntry stage) { return TRADES.read(stage); }
        @Override public void write(StageEntry stage, List<Trade> entries) { TRADES.write(stage, entries); }

        @Override public boolean matches(Trade entry, Object subject) {
            return subject instanceof Offer offer && entry.soldItem().equals(offer.soldItem());
        }

        @Override public boolean gates(StageEntry stage, Object subject) {
            if ("vetoed".equals(stage.getDisplayName())) return false;
            return LockCategory.super.gates(stage, subject);
        }
    };

    private static StageEntry vetoing(String... soldItems) {
        StageEntry stage = gating(soldItems);
        stage.setDisplayName("vetoed");
        return stage;
    }

    @Test
    void theDefaultGatesHookIsTheEntryLoop() {
        assertTrue(TRADES.gates(gating("minecraft:emerald"), new Offer("minecraft:emerald")));
        assertFalse(TRADES.gates(gating("minecraft:emerald"), new Offer("minecraft:diamond")));
    }

    @Test
    void anOverriddenGatesHookWinsOverTheEntryLoop() {
        assertFalse(VETOED.gates(vetoing("minecraft:emerald"), new Offer("minecraft:emerald")));
        assertTrue(VETOED.gates(gating("minecraft:emerald"), new Offer("minecraft:emerald")),
                "without the veto the override falls back to the loop");
    }

    @Test
    void theResolverAsksTheCategoryInsteadOfLoopingTheEntriesItself() {
        assertTrue(CategoryLockResolver.missingStages(VETOED, new Offer("minecraft:emerald"),
                stages("bronze", vetoing("minecraft:emerald")), StageStateView.NONE_UNLOCKED).isEmpty(),
                "the resolver must route through gates(), or an override cannot veto anything");
    }

    // ---- the state-free query the engine asks --------------------------------------

    @Test
    void gatingStagesIgnoresWhatIsAlreadyUnlocked() {
        assertEquals(List.of("bronze", "iron"), CategoryLockResolver.gatingStages(TRADES,
                new Offer("minecraft:emerald"),
                stages("bronze", gating("minecraft:emerald"), "iron", gating("minecraft:emerald"))));
    }

    @Test
    void gatingStagesReportsNothingForAnUngatedSubject() {
        assertTrue(CategoryLockResolver.gatingStages(TRADES, new Offer("minecraft:diamond"),
                stages("bronze", gating("minecraft:emerald"))).isEmpty());
    }

    // ---- several categories, one pass ----------------------------------------------

    @Test
    void severalCategoriesAreOredPerStageAndTheStageAppearsOnce() {
        assertEquals(List.of("bronze"), CategoryLockResolver.gatingStages(
                List.of(TRADES, VETOED), new Offer("minecraft:emerald"),
                List.of("bronze"), stages("bronze", gating("minecraft:emerald"))));
    }

    @Test
    void aStageIsStillFoundWhenOnlyTheSecondCategoryGatesIt() {
        assertEquals(List.of("bronze"), CategoryLockResolver.gatingStages(
                List.of(VETOED, TRADES), new Offer("minecraft:emerald"),
                List.of("bronze"), stages("bronze", vetoing("minecraft:emerald"))),
                "the vetoing category says no, the plain one still gates it");
    }

    @Test
    void theCandidateListDecidesBothTheScopeAndTheOrder() {
        Map<String, StageEntry> all = stages(
                "bronze", gating("minecraft:emerald"),
                "iron", gating("minecraft:emerald"),
                "gold", gating("minecraft:emerald"));
        assertEquals(List.of("gold", "bronze"), CategoryLockResolver.gatingStages(
                List.of(TRADES), new Offer("minecraft:emerald"), List.of("gold", "bronze"), all),
                "only the listed stages, in the listed order — iron is not a candidate");
    }

    @Test
    void aCandidateIdTheStageMapDoesNotKnowIsSkipped() {
        // The relevance index is a deliberate over-approximation and may name a removed stage.
        assertEquals(List.of("bronze"), CategoryLockResolver.gatingStages(
                List.of(TRADES), new Offer("minecraft:emerald"),
                List.of("ghost", "bronze"), stages("bronze", gating("minecraft:emerald"))));
    }
}
