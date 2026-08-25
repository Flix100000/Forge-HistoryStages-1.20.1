package net.bananemdnsa.historystages.data.dependency;

import net.bananemdnsa.historystages.api.dependency.RequirementStorage;

import net.bananemdnsa.historystages.api.dependency.RequirementDisplay;

import net.bananemdnsa.historystages.api.dependency.RequirementOutcome;

import net.bananemdnsa.historystages.api.dependency.AddonRequirement;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.stage.StageScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonRequirementTest {

    public record RelicDep(String id, int count) {}

    private static AddonRequirement.Builder<RelicDep> minimal(String id) {
        return AddonRequirement.<RelicDep>builder(id)
                .tabLangKey("tab." + id)
                .tooltipLangKey("tip." + id)
                .sectionLangKey("section." + id)
                .storage(RequirementStorage.gson(RelicDep.class))
                .evaluator((relic, ctx) -> new RequirementOutcome(
                        relic.id(), relic.count() + "x relic", true, relic.count(), relic.count()));
    }

    @Test
    void entriesRoundTripThroughTheGroupsAddonBlock() {
        AddonRequirement<RelicDep> requirement = minimal("mymod:relic").build();
        DependencyGroup group = new DependencyGroup();

        requirement.write(group, List.of(new RelicDep("mymod:shard", 3)));

        assertEquals(List.of(new RelicDep("mymod:shard", 3)), requirement.read(group));
    }

    @Test
    void anEmptyWriteLeavesNoStubInTheFile() {
        AddonRequirement<RelicDep> requirement = minimal("mymod:relic").build();
        DependencyGroup group = new DependencyGroup();
        requirement.write(group, List.of(new RelicDep("mymod:shard", 3)));

        requirement.write(group, List.of());

        assertNull(group.addonEntries("mymod:relic"));
    }

    @Test
    void aMalformedEntryCostsThatEntryAndNotTheGroup() {
        AddonRequirement<RelicDep> requirement = minimal("mymod:relic").build();
        DependencyGroup group = new DependencyGroup();
        JsonElement mixed = JsonParser.parseString(
                "[{\"id\":\"mymod:shard\",\"count\":3},\"not an object\"]");
        group.setAddonEntries("mymod:relic", mixed);

        assertEquals(1, requirement.read(group).size());
    }

    @Test
    void bothScopesUnlessTheAddonNarrowsThem() {
        assertEquals(EnumSet.allOf(StageScope.class), minimal("mymod:relic").build().supportedScopes());
        assertEquals(Set.of(StageScope.INDIVIDUAL),
                minimal("mymod:relic").supportedScopes(StageScope.INDIVIDUAL).build().supportedScopes());
    }

    @Test
    void binaryUnlessTheAddonSaysOtherwise() {
        assertEquals(RequirementDisplay.Kind.BINARY, minimal("mymod:relic").build().displayKind());
        assertEquals(RequirementDisplay.Kind.COUNTED,
                minimal("mymod:relic").displayKind(RequirementDisplay.Kind.COUNTED).build().displayKind());
    }

    @Test
    void theReservedNamespaceIsRefused() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> minimal("historystages:relic").build());
        assertTrue(thrown.getMessage().contains("reserved"));
    }

    @Test
    void anIdWithoutANamespaceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> minimal("relic").build());
    }

    @Test
    void aMissingPieceIsRefusedAtBuildTime() {
        assertThrows(IllegalStateException.class, () -> AddonRequirement.<RelicDep>builder("mymod:relic")
                .tabLangKey("tab").tooltipLangKey("tip").sectionLangKey("section")
                .evaluator((relic, ctx) -> null)
                .build());
        assertThrows(IllegalStateException.class, () -> AddonRequirement.<RelicDep>builder("mymod:relic")
                .tabLangKey("tab").tooltipLangKey("tip").sectionLangKey("section")
                .storage(RequirementStorage.gson(RelicDep.class))
                .build());
    }

    @Test
    void aRequirementThatSupportsNoScopeIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> minimal("mymod:relic").supportedScopes().build());
    }

    @Test
    void declaredInSeesOnlyItsOwnSlot() {
        AddonRequirement<RelicDep> requirement = minimal("mymod:relic").build();
        DependencyGroup group = new DependencyGroup();

        assertTrue(!requirement.declaredIn(group));

        group.setAddonEntries("othermod:thing", JsonParser.parseString("[]"));
        assertTrue(!requirement.declaredIn(group));

        requirement.write(group, List.of(new RelicDep("mymod:shard", 1)));
        assertTrue(requirement.declaredIn(group));
    }
}
