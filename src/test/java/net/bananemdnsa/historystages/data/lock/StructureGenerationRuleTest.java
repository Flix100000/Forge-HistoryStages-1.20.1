package net.bananemdnsa.historystages.data.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureGenerationRuleTest {

    @Test
    void plainIdBecomesTheLegacyBlockRule() {
        StructureGenerationRule rule = StructureGenerationRule.blockEntirely("minecraft:village_plains");
        assertEquals("minecraft:village_plains", rule.id());
        assertEquals(GenerationPhase.WHILE_LOCKED, rule.phase());
        assertEquals(0, rule.max());
        assertFalse(rule.resetOnRelock());
        assertTrue(rule.isLegacyBlock(), "a while_locked/0 rule without reset must serialize as a bare string");
    }

    @Test
    void anyDeviationFromTheDefaultIsNoLongerLegacy() {
        assertFalse(new StructureGenerationRule("a", GenerationPhase.WHILE_LOCKED, 3, false).isLegacyBlock());
        assertFalse(new StructureGenerationRule("a", GenerationPhase.AFTER_UNLOCK, 0, false).isLegacyBlock());
        assertFalse(new StructureGenerationRule("a", GenerationPhase.WHILE_LOCKED, 0, true).isLegacyBlock());
    }

    @Test
    void negativeLimitsAreClampedToZero() {
        assertEquals(0, new StructureGenerationRule("a", GenerationPhase.WHILE_LOCKED, -5, false).max());
    }

    @Test
    void phaseParsesItsSerializedNameAndFallsBackToWhileLocked() {
        assertEquals(GenerationPhase.AFTER_UNLOCK, GenerationPhase.parse("after_unlock"));
        assertEquals(GenerationPhase.WHILE_LOCKED, GenerationPhase.parse("while_locked"));
        assertEquals(GenerationPhase.WHILE_LOCKED, GenerationPhase.parse("nonsense"));
        assertEquals(GenerationPhase.WHILE_LOCKED, GenerationPhase.parse(null));
        assertEquals("after_unlock", GenerationPhase.AFTER_UNLOCK.serialize());
    }

    @Test
    void nullPhaseOnTheConstructorDefaultsToWhileLocked() {
        assertEquals(GenerationPhase.WHILE_LOCKED,
                new StructureGenerationRule("a:b", null, 2, false).phase());
    }
}
