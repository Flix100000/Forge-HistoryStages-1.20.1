package net.bananemdnsa.historystages.client.editor.tab;

import net.bananemdnsa.historystages.api.editor.CategoryEditor;

import net.bananemdnsa.historystages.api.editor.CategoryTab;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry half of addon editor tabs. The tabs themselves cannot be unit-tested — they carry
 * Minecraft widgets, and the test runtime has no Minecraft on it — so what is checked here is the
 * part that decides whether a tab appears at all.
 */
class CategoryEditorRegistryTest {

    private static CategoryEditor editorFor(String categoryId) {
        return new CategoryEditor() {
            @Override
            public String categoryId() {
                return categoryId;
            }

            @Override
            public CategoryTab createTab(Runnable onChanged) {
                throw new UnsupportedOperationException("not needed for this test");
            }
        };
    }

    @AfterEach
    void reset() {
        CategoryEditors.resetForTesting();
    }

    @Test
    void nothingIsRegisteredToStartWith() {
        assertTrue(CategoryEditors.all().isEmpty());
        assertNull(CategoryEditors.byCategory("mymod:villagertrades"));
    }

    @Test
    void aRegisteredEditorIsFoundByItsCategory() {
        CategoryEditors.register(editorFor("mymod:villagertrades"));

        assertNotNull(CategoryEditors.byCategory("mymod:villagertrades"));
        assertEquals(1, CategoryEditors.all().size());
    }

    @Test
    void registrationOrderIsKept() {
        CategoryEditors.register(editorFor("a:one"));
        CategoryEditors.register(editorFor("b:two"));

        assertEquals(List.of("a:one", "b:two"),
                CategoryEditors.all().stream().map(CategoryEditor::categoryId).toList());
    }

    @Test
    void registeringAfterTheFreezeIsRejected() {
        CategoryEditors.freeze();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> CategoryEditors.register(editorFor("mymod:toolate")));
        assertTrue(thrown.getMessage().contains("mymod:toolate"),
                "the error should name the editor that tried to register: " + thrown.getMessage());
    }

    @Test
    void twoEditorsForOneCategoryAreRejected() {
        CategoryEditors.register(editorFor("mymod:villagertrades"));

        assertThrows(IllegalArgumentException.class,
                () -> CategoryEditors.register(editorFor("mymod:villagertrades")));
    }

    /**
     * A category may register without an editor. That is not a broken state: it still gates and
     * still stores, it just cannot be edited in game — so the screen must simply skip it.
     */
    @Test
    void aCategoryWithoutAnEditorIsAbsentRatherThanBroken() {
        CategoryEditors.register(editorFor("mymod:haseditor"));
        CategoryEditors.freeze();

        assertNull(CategoryEditors.byCategory("mymod:noeditor"));
        assertNotNull(CategoryEditors.byCategory("mymod:haseditor"));
    }

    @Test
    void freezingTwiceIsHarmless() {
        CategoryEditors.freeze();
        CategoryEditors.freeze();
        assertTrue(CategoryEditors.isFrozen());
    }
}
