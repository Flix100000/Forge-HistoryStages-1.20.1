package net.bananemdnsa.historystages.compat.script;

import net.bananemdnsa.historystages.api.stage.StageScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScriptCallResolutionTest {

    private static final Set<String> GLOBAL = Set.of("bronze", "iron");
    private static final Set<String> INDIVIDUAL = Set.of("tutorial");
    private static final List<String> CATEGORIES =
            List.of("historystages:items", "historystages:recipes", "hsdemo:relics");

    @BeforeEach
    void reset() {
        ScriptCallResolution.resetWarnings();
    }

    @Test
    void aKnownGlobalStageResolves() {
        var check = ScriptCallResolution.stage("bronze", StageScope.GLOBAL, GLOBAL, INDIVIDUAL);
        assertTrue(check.ok());
        assertNull(check.message());
    }

    @Test
    void anUnknownStageIsRejectedAndNamesTheKnownOnes() {
        var check = ScriptCallResolution.stage("bronce", StageScope.GLOBAL, GLOBAL, INDIVIDUAL);
        assertFalse(check.ok());
        assertTrue(check.message().contains("bronce"), "the typo belongs in the message");
        assertTrue(check.message().contains("bronze"), "so do the valid ids");
    }

    @Test
    void usingAnIndividualStageGloballyNamesTheScope() {
        var check = ScriptCallResolution.stage("tutorial", StageScope.GLOBAL, GLOBAL, INDIVIDUAL);
        assertFalse(check.ok());
        assertTrue(check.message().contains("individual"),
                "the whole point is telling the author it is the other scope: " + check.message());
    }

    @Test
    void usingAGlobalStageIndividuallyNamesTheScope() {
        var check = ScriptCallResolution.stage("bronze", StageScope.INDIVIDUAL, GLOBAL, INDIVIDUAL);
        assertFalse(check.ok());
        assertTrue(check.message().contains("global"), check.message());
    }

    @Test
    void anUnknownCategoryIsRejectedAndNamesTheKnownOnes() {
        var check = ScriptCallResolution.category("itemz", CATEGORIES);
        assertFalse(check.ok());
        assertTrue(check.message().contains("historystages:items"), check.message());
    }

    @Test
    void aKnownCategoryResolves() {
        assertTrue(ScriptCallResolution.category("historystages:items", CATEGORIES).ok());
    }

    @Test
    void aBuiltInCategoryMayBeNamedWithoutItsNamespace() {
        // Category ids are namespaced (historystages:items). Making a script author type that
        // for the built-ins would be noise, so a bare name resolves the way minecraft: does.
        assertTrue(ScriptCallResolution.category("items", CATEGORIES).ok());
        assertEquals("historystages:items", ScriptCallResolution.canonicalCategoryId("items", CATEGORIES));
    }

    @Test
    void anAddonCategoryStillNeedsItsOwnNamespace() {
        assertTrue(ScriptCallResolution.category("hsdemo:relics", CATEGORIES).ok());
        assertEquals("hsdemo:relics", ScriptCallResolution.canonicalCategoryId("hsdemo:relics", CATEGORIES));
        // A bare "relics" must not silently resolve to somebody else's category.
        assertFalse(ScriptCallResolution.category("relics", CATEGORIES).ok());
    }

    @Test
    void anUnresolvableCategoryKeepsWhatTheAuthorTyped() {
        assertEquals("itemz", ScriptCallResolution.canonicalCategoryId("itemz", CATEGORIES),
                "the failure message has to name what they wrote, not a guess");
    }

    @Test
    void theSameProblemWarnsOnlyOnce() {
        assertTrue(ScriptCallResolution.shouldWarn("stage:bronce"));
        assertFalse(ScriptCallResolution.shouldWarn("stage:bronce"));
        assertFalse(ScriptCallResolution.shouldWarn("stage:bronce"));
    }

    @Test
    void differentProblemsWarnSeparately() {
        assertTrue(ScriptCallResolution.shouldWarn("stage:bronce"));
        assertTrue(ScriptCallResolution.shouldWarn("stage:irn"));
    }

    @Test
    void aReloadLetsACorrectedScriptWarnAgain() {
        assertTrue(ScriptCallResolution.shouldWarn("stage:bronce"));
        ScriptCallResolution.resetWarnings();
        assertTrue(ScriptCallResolution.shouldWarn("stage:bronce"));
    }
}
