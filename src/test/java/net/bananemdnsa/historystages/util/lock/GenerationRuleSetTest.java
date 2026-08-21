package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.StructureGenerationRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GenerationRuleSetTest {

    private static final List<String> NO_TAGS = List.of();

    private static GenerationRuleSet build(Map<String, List<StructureGenerationRule>> rules, Set<String> unlocked) {
        return GenerationRuleSet.build(rules, unlocked);
    }

    @Test
    void whileLockedRuleWithZeroMaxBlocksWhileLocked() {
        GenerationRuleSet set = build(
                Map.of("bronze", List.of(StructureGenerationRule.blockEntirely("a:hut"))), Set.of());

        assertTrue(set.isHardBlocked("a:hut", NO_TAGS));
        assertTrue(set.countingLimits("a:hut", NO_TAGS).isEmpty());
    }

    @Test
    void whileLockedRuleStopsMatteringOnceUnlocked() {
        GenerationRuleSet set = build(
                Map.of("bronze", List.of(StructureGenerationRule.blockEntirely("a:hut"))), Set.of("bronze"));

        assertFalse(set.isHardBlocked("a:hut", NO_TAGS));
        assertTrue(set.countingLimits("a:hut", NO_TAGS).isEmpty());
        assertFalse(set.isActive(), "no active rule means the gate can short-circuit");
    }

    @Test
    void whileLockedRuleWithLimitCountsWhileLocked() {
        GenerationRuleSet set = build(
                Map.of("bronze", List.of(
                        new StructureGenerationRule("a:hut", GenerationPhase.WHILE_LOCKED, 3, false))),
                Set.of());

        assertFalse(set.isHardBlocked("a:hut", NO_TAGS));
        assertEquals(Map.of("bronze|a:hut", 3), set.countingLimits("a:hut", NO_TAGS));
    }

    @Test
    void afterUnlockRuleBlocksHardWhileLockedAndCountsAfterwards() {
        StructureGenerationRule rule =
                new StructureGenerationRule("a:hut", GenerationPhase.AFTER_UNLOCK, 2, false);

        assertTrue(build(Map.of("bronze", List.of(rule)), Set.of()).isHardBlocked("a:hut", NO_TAGS));
        assertEquals(Map.of("bronze|a:hut", 2),
                build(Map.of("bronze", List.of(rule)), Set.of("bronze")).countingLimits("a:hut", NO_TAGS));
    }

    @Test
    void tagRulesMatchTheirMembersUnderOneSharedKey() {
        GenerationRuleSet set = build(
                Map.of("bronze", List.of(
                        new StructureGenerationRule("#minecraft:village", GenerationPhase.WHILE_LOCKED, 3, false))),
                Set.of());

        assertEquals(Map.of("bronze|#minecraft:village", 3),
                set.countingLimits("minecraft:village_plains", List.of("minecraft:village")));
        assertEquals(Map.of("bronze|#minecraft:village", 3),
                set.countingLimits("minecraft:village_taiga", List.of("minecraft:village")));
        assertTrue(set.countingLimits("minecraft:igloo", NO_TAGS).isEmpty());
    }

    @Test
    void twoStagesLimitingTheSameStructureKeepSeparateKeys() {
        GenerationRuleSet set = build(Map.of(
                        "bronze", List.of(new StructureGenerationRule("a:hut", GenerationPhase.WHILE_LOCKED, 3, false)),
                        "iron", List.of(new StructureGenerationRule("a:hut", GenerationPhase.WHILE_LOCKED, 1, false))),
                Set.of());

        assertEquals(Map.of("bronze|a:hut", 3, "iron|a:hut", 1), set.countingLimits("a:hut", NO_TAGS));
    }

    @Test
    void hardBlockWinsOverACountingRuleOnTheSameStructure() {
        GenerationRuleSet set = build(Map.of(
                        "bronze", List.of(new StructureGenerationRule("a:hut", GenerationPhase.AFTER_UNLOCK, 2, false)),
                        "iron", List.of(new StructureGenerationRule("a:hut", GenerationPhase.WHILE_LOCKED, 5, false))),
                Set.of());

        assertTrue(set.isHardBlocked("a:hut", NO_TAGS));
    }

    @Test
    void keysThatResetWhenAPhaseRestartsAreReported() {
        GenerationRuleSet set = build(Map.of("bronze", List.of(
                        new StructureGenerationRule("a:hut", GenerationPhase.WHILE_LOCKED, 3, true),
                        new StructureGenerationRule("a:tent", GenerationPhase.AFTER_UNLOCK, 3, true),
                        new StructureGenerationRule("a:barn", GenerationPhase.WHILE_LOCKED, 3, false))),
                Set.of());

        // Re-locking restarts the while_locked phase.
        assertEquals(Set.of("bronze|a:hut"), set.resetKeysFor("bronze", false));
        // Unlocking restarts the after_unlock phase.
        assertEquals(Set.of("bronze|a:tent"), set.resetKeysFor("bronze", true));
        assertTrue(set.resetKeysFor("other", false).isEmpty());
    }

    @Test
    void theSnapshotHandsOutNothingACallerCouldMutate() {
        // Worldgen worker threads share one snapshot, so a leaked mutable collection would be a
        // cross-thread hazard, not just a style problem.
        GenerationRuleSet set = build(Map.of("bronze", List.of(
                        new StructureGenerationRule("a:hut", GenerationPhase.WHILE_LOCKED, 3, true))),
                Set.of());

        assertThrows(UnsupportedOperationException.class,
                () -> set.resetKeysFor("bronze", false).add("injected"));
    }
}
