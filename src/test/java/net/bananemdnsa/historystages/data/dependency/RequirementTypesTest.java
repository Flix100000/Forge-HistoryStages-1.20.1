package net.bananemdnsa.historystages.data.dependency;

import net.bananemdnsa.historystages.api.dependency.RequirementResult;

import net.bananemdnsa.historystages.api.dependency.RequirementContext;

import net.bananemdnsa.historystages.api.dependency.Requirement;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.stage.StageScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementTypesTest {

    /** A stand-in that never evaluates — nothing here calls evaluate, which needs Minecraft. */
    private static Requirement stub(String id, StageScope... scopes) {
        return new Requirement() {
            @Override public String id() { return id; }
            @Override public String tabLangKey() { return "tab." + id; }
            @Override public String tooltipLangKey() { return "tip." + id; }
            @Override public String sectionLangKey() { return "section." + id; }
            @Override public Set<StageScope> supportedScopes() {
                return scopes.length == 0 ? EnumSet.allOf(StageScope.class) : Set.of(scopes);
            }
            @Override public boolean declaredIn(DependencyGroup group) { return false; }
            @Override public List<RequirementResult.EntryResult> evaluate(
                    DependencyGroup group, RequirementContext ctx) {
                throw new UnsupportedOperationException("not needed for this test");
            }
        };
    }

    @AfterEach
    void reset() {
        RequirementTypes.resetForTesting();
    }

    @Test
    void theEightBuiltInsAreThereFromTheStart() {
        assertEquals(List.of("item", "stage", "individual_stage", "advancement",
                        "xp_level", "entity_kill", "stat", "scoreboard"),
                RequirementTypes.ids());
    }

    @Test
    void anAddonTypeIsFoundByItsId() {
        RequirementTypes.register(stub("mymod:relic"));

        assertNotNull(RequirementTypes.byId("mymod:relic"));
        assertEquals(List.of("mymod:relic"), RequirementTypes.addonIds());
    }

    @Test
    void addonsAreAppendedAfterTheBuiltInsRatherThanInterleaved() {
        RequirementTypes.register(stub("mymod:relic"));

        assertEquals("scoreboard", RequirementTypes.ids().get(7));
        assertEquals("mymod:relic", RequirementTypes.ids().get(8));
    }

    @Test
    void anIdAlreadyTakenIsRejected() {
        RequirementTypes.register(stub("mymod:relic"));

        assertThrows(IllegalArgumentException.class, () -> RequirementTypes.register(stub("mymod:relic")));
        assertThrows(IllegalArgumentException.class, () -> RequirementTypes.register(stub("item")));
    }

    @Test
    void registeringAfterTheFreezeIsRejected() {
        RequirementTypes.freeze();

        assertThrows(IllegalStateException.class, () -> RequirementTypes.register(stub("mymod:relic")));
    }

    @Test
    void builtInsAreReportedApartFromAddons() {
        RequirementTypes.register(stub("mymod:relic"));

        assertEquals(8, RequirementTypes.builtIns().size());
        assertEquals(9, RequirementTypes.all().size());
    }

    @Test
    void anUnknownIdIsNullRatherThanAnError() {
        assertNull(RequirementTypes.byId("nobody:home"));
    }

    @Test
    void forScopeDropsWhatTheScopeCannotAnswer() {
        List<String> global = RequirementTypes.forScope(StageScope.GLOBAL).stream()
                .map(Requirement::id).toList();

        assertEquals(List.of("item", "stage", "individual_stage", "scoreboard"), global);
        assertEquals(8, RequirementTypes.forScope(StageScope.INDIVIDUAL).size());
    }

    @Test
    void anAddonTypeIsFilteredByItsDeclaredScopeToo() {
        RequirementTypes.register(stub("mymod:relic", StageScope.INDIVIDUAL));

        assertTrue(RequirementTypes.forScope(StageScope.GLOBAL).stream()
                .noneMatch(r -> r.id().equals("mymod:relic")));
        assertTrue(RequirementTypes.forScope(StageScope.INDIVIDUAL).stream()
                .anyMatch(r -> r.id().equals("mymod:relic")));
    }

    @Test
    void resetRestoresTheBuiltInsAndReopensTheWindow() {
        RequirementTypes.register(stub("mymod:relic"));
        RequirementTypes.freeze();

        RequirementTypes.resetForTesting();

        assertEquals(8, RequirementTypes.all().size());
        assertTrue(RequirementTypes.addonIds().isEmpty());
        RequirementTypes.register(stub("mymod:relic"));
    }
}
