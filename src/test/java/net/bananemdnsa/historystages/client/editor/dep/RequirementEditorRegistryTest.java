package net.bananemdnsa.historystages.client.editor.dep;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry half of addon requirement tabs. The tabs themselves cannot be unit-tested — they
 * render through Minecraft widgets, and the test runtime has no Minecraft on it — so what is
 * checked here is the part that decides whether a tab appears at all.
 */
class RequirementEditorRegistryTest {

    @AfterEach
    void reset() {
        RequirementEditors.resetForTesting();
    }

    @Test
    void nothingIsRegisteredToStartWith() {
        assertTrue(RequirementEditors.all().isEmpty());
        assertNull(RequirementEditors.byRequirement("mymod:relic"));
    }

    @Test
    void aRegisteredEditorIsFoundByItsRequirement() {
        RequirementEditors.register(RequirementEditor.ofIdList(
                "mymod:relic", "search.relics", Set::of));

        assertNotNull(RequirementEditors.byRequirement("mymod:relic"));
        assertEquals(1, RequirementEditors.all().size());
    }

    @Test
    void registrationOrderIsKept() {
        RequirementEditors.register(RequirementEditor.ofIdList("a:one", "search", Set::of));
        RequirementEditors.register(RequirementEditor.ofIdList("b:two", "search", Set::of));

        assertEquals(List.of("a:one", "b:two"),
                RequirementEditors.all().stream().map(RequirementEditor::requirementId).toList());
    }

    @Test
    void twoEditorsForOneRequirementAreRejected() {
        RequirementEditors.register(RequirementEditor.ofIdList("mymod:relic", "search", Set::of));

        assertThrows(IllegalArgumentException.class, () -> RequirementEditors.register(
                RequirementEditor.ofIdList("mymod:relic", "search", Set::of)));
    }

    @Test
    void registeringAfterTheFreezeIsRejected() {
        RequirementEditors.freeze();

        assertThrows(IllegalStateException.class, () -> RequirementEditors.register(
                RequirementEditor.ofIdList("mymod:relic", "search", Set::of)));
    }

    @Test
    void resetClearsTheRegistryAndReopensIt() {
        RequirementEditors.register(RequirementEditor.ofIdList("mymod:relic", "search", Set::of));
        RequirementEditors.freeze();

        RequirementEditors.resetForTesting();

        assertTrue(RequirementEditors.all().isEmpty());
        assertTrue(!RequirementEditors.isFrozen());
        RequirementEditors.register(RequirementEditor.ofIdList("mymod:relic", "search", Set::of));
    }

    @Test
    void theAmountLangKeySeparatesTheTwoFreeTierShapes() {
        RequirementEditor bare = RequirementEditor.ofIdList("mymod:relic", "search", Set::of);
        RequirementEditor counted = RequirementEditor.ofIdCount(
                "mymod:shard", "search", "dialog.count", Set::of);

        assertNull(bare.amountLangKey());
        assertEquals("dialog.count", counted.amountLangKey());
    }

    @Test
    void candidatesAreQueriedFreshRatherThanCaptured() {
        java.util.List<String> live = new java.util.ArrayList<>(List.of("a"));
        RequirementEditor editor = RequirementEditor.ofIdList("mymod:relic", "search", () -> live);

        live.add("b");

        assertEquals(List.of("a", "b"), List.copyOf(editor.candidates()));
    }
}
