package net.bananemdnsa.historystages.client.editor.tab;

import net.bananemdnsa.historystages.api.editor.EntryActionContext;

import net.bananemdnsa.historystages.api.editor.EntryAction;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.bananemdnsa.historystages.api.editor.RequirementEditor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The declaration half of entry actions. Showing one in a menu needs a screen and cannot be tested
 * here; what can is that an editor declares none unless it says so, and that a declared one
 * carries its handler intact.
 */
class EntryActionDeclarationTest {

    @Test
    void anEditorDeclaresNoActionsUnlessItSaysSo() {
        assertTrue(RequirementEditor.ofIdList("mymod:relic", "search", Set::of)
                .entryActions().isEmpty());
    }

    @Test
    void adeclaredActionCarriesItsLangKeyAndHandler() {
        AtomicInteger seenIndex = new AtomicInteger(-1);
        AtomicBoolean markedDirty = new AtomicBoolean(false);

        EntryAction action = EntryAction.of("editor.mymod.context.reroll",
                ctx -> { seenIndex.set(ctx.index()); ctx.markChanged(); });

        assertEquals("editor.mymod.context.reroll", action.langKey());

        // dataOnly, not the canonical constructor: naming its two sinks from test source would
        // mean naming Screen and PickerOverlay, and Minecraft is on neither test classpath.
        action.run(EntryActionContext.dataOnly(4, () -> markedDirty.set(true)));

        assertEquals(4, seenIndex.get());
        assertTrue(markedDirty.get(), "the handler must be able to mark the stage dirty");
    }

    @Test
    void anEditorMayDeclareSeveralInOrder() {
        RequirementEditor editor = new RequirementEditor() {
            @Override public String requirementId() { return "mymod:relic"; }
            @Override public String searchPlaceholderLangKey() { return "search"; }
            @Override public String amountLangKey() { return null; }
            @Override public java.util.Collection<String> candidates() { return Set.of(); }
            @Override public net.bananemdnsa.historystages.api.editor.DependencyTab
                    createTab(Runnable onChanged) {
                throw new UnsupportedOperationException("not needed for this test");
            }
            @Override public List<EntryAction> entryActions() {
                return List.of(EntryAction.of("a", ctx -> { }), EntryAction.of("b", ctx -> { }));
            }
        };

        assertEquals(List.of("a", "b"), editor.entryActions().stream().map(EntryAction::langKey).toList());
    }
}
