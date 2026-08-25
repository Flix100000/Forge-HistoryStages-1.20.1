package net.bananemdnsa.historystages.data.dependency;

import net.bananemdnsa.historystages.api.dependency.IdCountEntry;

import net.bananemdnsa.historystages.api.dependency.RequirementStorage;

import net.bananemdnsa.historystages.api.dependency.RequirementDisplay;

import net.bananemdnsa.historystages.api.dependency.AddonRequirement;

import net.bananemdnsa.historystages.api.dependency.RequirementDisplay.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementDisplayTest {

    @Test
    void itemsAreDepositedRegardlessOfTheDepositFlag() {
        // Items always come from deposited NBT, and DependencyChecker never sets canDeposit on
        // them — so the type alone has to decide.
        assertEquals(Kind.DEPOSITED, RequirementDisplay.kindOf("item", false));
        assertEquals(Kind.DEPOSITED, RequirementDisplay.kindOf("item", true));
    }

    @Test
    void consumingXpIsDepositedButPlainXpIsCounted() {
        assertEquals(Kind.DEPOSITED, RequirementDisplay.kindOf("xp_level", true));
        assertEquals(Kind.COUNTED, RequirementDisplay.kindOf("xp_level", false));
    }

    @Test
    void livePlayerMinimumsAreCounted() {
        assertEquals(Kind.COUNTED, RequirementDisplay.kindOf("entity_kill", false));
        assertEquals(Kind.COUNTED, RequirementDisplay.kindOf("stat", false));
    }

    @Test
    void comparisonAndYesNoRequirementsAreBinary() {
        // Scoreboard is live but carries an operator, so a fraction would misread it.
        assertEquals(Kind.BINARY, RequirementDisplay.kindOf("scoreboard", false));
        assertEquals(Kind.BINARY, RequirementDisplay.kindOf("stage", false));
        assertEquals(Kind.BINARY, RequirementDisplay.kindOf("individual_stage", false));
        assertEquals(Kind.BINARY, RequirementDisplay.kindOf("advancement", false));
    }

    @Test
    void unknownTypesFallBackToTheSafestPresentation() {
        // A type this view has never heard of gets the glyph and no invented figures.
        assertEquals(Kind.BINARY, RequirementDisplay.kindOf("something_new", false));
        assertEquals(Kind.BINARY, RequirementDisplay.kindOf(null, false));
    }

    @Test
    void anAddonTypeIsPresentedTheWayItsAuthorDeclared() {
        RequirementTypes.register(AddonRequirement.<IdCountEntry>builder("mymod:relic")
                .tabLangKey("tab").tooltipLangKey("tip").sectionLangKey("section")
                .storage(RequirementStorage.gson(IdCountEntry.class))
                .displayKind(Kind.COUNTED)
                .evaluator((entry, ctx) -> null)
                .build());

        assertEquals(Kind.COUNTED, RequirementDisplay.kindOf("mymod:relic", false));
    }

    @Test
    void anAddonThatDeclaresNothingGetsTheSafeDefault() {
        RequirementTypes.register(AddonRequirement.<IdCountEntry>builder("mymod:quiet")
                .tabLangKey("tab").tooltipLangKey("tip").sectionLangKey("section")
                .storage(RequirementStorage.gson(IdCountEntry.class))
                .evaluator((entry, ctx) -> null)
                .build());

        assertEquals(Kind.BINARY, RequirementDisplay.kindOf("mymod:quiet", false));
    }

    @org.junit.jupiter.api.AfterEach
    void reset() {
        // Without this a type registered here would leak into whichever test runs next, and the
        // failure would depend on execution order.
        RequirementTypes.resetForTesting();
    }

    @Test
    void onlyDepositedRequirementsHideTheirStatus() {
        assertFalse(RequirementDisplay.showsStatus(Kind.DEPOSITED));
        assertTrue(RequirementDisplay.showsStatus(Kind.COUNTED));
        assertTrue(RequirementDisplay.showsStatus(Kind.BINARY));
    }

    @Test
    void onlyCountedRequirementsShowAnAmount() {
        assertTrue(RequirementDisplay.showsAmount(Kind.COUNTED));
        assertFalse(RequirementDisplay.showsAmount(Kind.DEPOSITED));
        assertFalse(RequirementDisplay.showsAmount(Kind.BINARY));
    }
}
