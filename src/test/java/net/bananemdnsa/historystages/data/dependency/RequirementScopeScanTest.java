package net.bananemdnsa.historystages.data.dependency;

import java.util.List;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minecraft-free by design, so the rule can be tested while its only caller sits inside the
 * loader. Same split the other four axes use.
 */
class RequirementScopeScanTest {

    @Test
    void aGlobalStageDeclaringAPlayerBoundKindIsReported() {
        DependencyGroup group = new DependencyGroup();
        group.getAdvancements().add("minecraft:story/mine_stone");

        assertEquals(List.of("advancement"), RequirementScopeScan.unusable(group, StageScope.GLOBAL));
    }

    @Test
    void theSameGroupOnAnIndividualStageIsFine() {
        DependencyGroup group = new DependencyGroup();
        group.getAdvancements().add("minecraft:story/mine_stone");

        assertTrue(RequirementScopeScan.unusable(group, StageScope.INDIVIDUAL).isEmpty());
    }

    @Test
    void everyOffendingKindIsNamedOnceEach() {
        DependencyGroup group = new DependencyGroup();
        group.getAdvancements().add("minecraft:story/mine_stone");
        group.getAdvancements().add("minecraft:story/smelt_iron");
        group.getStats().add(new StatDep("minecraft:jump", 10));

        assertEquals(List.of("advancement", "stat"),
                RequirementScopeScan.unusable(group, StageScope.GLOBAL));
    }

    @Test
    void aGroupTheScopeFullyAllowsReportsNothing() {
        DependencyGroup group = new DependencyGroup();
        group.getStages().add("bronze_age");
        group.getItems().add(new DependencyItem("minecraft:iron_ingot", 4));

        assertTrue(RequirementScopeScan.unusable(group, StageScope.GLOBAL).isEmpty());
    }

    @Test
    void anXpRequirementOfLevelZeroIsNotDeclaredAtAll() {
        DependencyGroup group = new DependencyGroup();
        group.setXpLevel(new XpLevelDep(0, false));

        assertTrue(RequirementScopeScan.unusable(group, StageScope.GLOBAL).isEmpty());
    }
}
