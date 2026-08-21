package net.bananemdnsa.historystages.data.lock.engine;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockResolutionTest {

    private static final StageStateView BRONZE_ONLY = StageStateView.of(Set.of("bronze"));

    @Test
    void ungatedSubjectIsNeverLocked() {
        assertFalse(LockResolution.isLocked(List.of(), BRONZE_ONLY));
        assertFalse(LockResolution.isLockedLenient(List.of(), BRONZE_ONLY, List.of(), BRONZE_ONLY));
    }

    @Test
    void strictLocksOnASingleMissingStage() {
        assertTrue(LockResolution.isLocked(List.of("bronze", "iron"), BRONZE_ONLY));
    }

    @Test
    void strictUnlocksOnlyWhenEveryGatingStageIsUnlocked() {
        assertFalse(LockResolution.isLocked(List.of("bronze"), BRONZE_ONLY));
    }

    @Test
    void strictAcrossScopesLocksWhenEitherScopeIsMissing() {
        StageStateView noneIndividual = StageStateView.NONE_UNLOCKED;
        assertTrue(LockResolution.isLocked(List.of("bronze"), BRONZE_ONLY, List.of("quest"), noneIndividual));
        assertFalse(LockResolution.isLocked(List.of("bronze"), BRONZE_ONLY, List.of(), noneIndividual));
    }

    @Test
    void lenientUnlocksAsSoonAsOneGatingStageIsUnlocked() {
        // Gated by two stages, one of them unlocked -> LENIENT treats it as available.
        assertFalse(LockResolution.isLockedLenient(
                List.of("bronze", "iron"), BRONZE_ONLY, List.of(), StageStateView.NONE_UNLOCKED));
    }

    @Test
    void lenientLocksWhenNoGatingStageIsUnlockedInEitherScope() {
        assertTrue(LockResolution.isLockedLenient(
                List.of("iron"), BRONZE_ONLY, List.of("quest"), StageStateView.NONE_UNLOCKED));
    }

    @Test
    void lenientCountsAnUnlockedIndividualStageToo() {
        StageStateView questDone = StageStateView.of(Set.of("quest"));
        assertFalse(LockResolution.isLockedLenient(
                List.of("iron"), BRONZE_ONLY, List.of("quest"), questDone));
    }

    @Test
    void missingStagesKeepsEngineOrderAndDropsUnlockedOnes() {
        List<String> missing = LockResolution.missingStages(List.of("iron", "bronze", "steel"), BRONZE_ONLY);
        assertEquals(List.of("iron", "steel"), missing);
    }

    @Test
    void missingStagesReturnsEmptyListWhenAllUnlocked() {
        assertEquals(List.of(), LockResolution.missingStages(List.of("bronze"), BRONZE_ONLY));
    }
}
