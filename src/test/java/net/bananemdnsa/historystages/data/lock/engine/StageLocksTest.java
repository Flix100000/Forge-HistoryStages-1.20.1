package net.bananemdnsa.historystages.data.lock.engine;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StageLocksTest {

    /** Answers one subject type; everything else falls through to the interface defaults. */
    private static final class FakeEngine implements StageLockEngine {
        @Override
        public List<String> gatingStagesForDimension(String dimensionId, StageScope scope) {
            if (!"minecraft:the_nether".equals(dimensionId)) return List.of();
            return scope == StageScope.GLOBAL ? List.of("bronze") : List.of("quest");
        }
    }

    @AfterEach
    void restoreDefaultEngine() {
        StageLocks.resetEngine();
    }

    @Test
    void defaultEngineIsInstalled() {
        assertNotNull(StageLocks.engine());
    }

    @Test
    void engineCanBeSwapped() {
        StageLocks.setEngine(new FakeEngine());
        assertEquals(List.of("bronze"),
                StageLocks.engine().gatingStagesForDimension("minecraft:the_nether", StageScope.GLOBAL));
        assertEquals(List.of("quest"),
                StageLocks.engine().gatingStagesForDimension("minecraft:the_nether", StageScope.INDIVIDUAL));
    }

    @Test
    void unimplementedSubjectsFallBackToEmpty() {
        StageLocks.setEngine(new FakeEngine());
        assertEquals(List.of(),
                StageLocks.engine().gatingStagesForRecipe("minecraft:iron_sword", StageScope.GLOBAL));
        assertEquals(List.of(),
                StageLocks.engine().gatingStagesForStructure("minecraft:village_plains", StageScope.GLOBAL));
    }

    @Test
    void resetRestoresTheStringEngine() {
        StageLocks.setEngine(new FakeEngine());
        StageLocks.resetEngine();
        assertEquals(StringStageLockEngine.class, StageLocks.engine().getClass());
    }
}
