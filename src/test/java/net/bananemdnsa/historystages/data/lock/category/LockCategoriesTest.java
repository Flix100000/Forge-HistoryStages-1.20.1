package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockCategoriesTest {

    @Test
    void theTwelveBuiltInsAreRegisteredInEditorTabOrder() {
        List<String> ids = LockCategories.all().stream().map(LockCategory::id).toList();
        assertEquals(List.of(
                "historystages:items",
                // Next to items on purpose: it answers about the same subject, reading what the
                // stack carries rather than what it is.
                "historystages:fluids",
                "historystages:tags",
                "historystages:mods",
                "historystages:mod_exceptions",
                "historystages:recipes",
                "historystages:dimensions",
                "historystages:attacklock",
                "historystages:spawnlock",
                "historystages:interactionlock",
                "historystages:structures",
                "historystages:biomes"), ids);
    }

    @Test
    void categoriesAreLookedUpById() {
        assertNotNull(LockCategories.byId("historystages:items"));
        assertEquals("historystages:items", LockCategories.byId("historystages:items").id());
    }

    @Test
    void anUnknownIdIsNullRatherThanAnException() {
        assertNull(LockCategories.byId("mymod:villagertrades"));
    }

    @Test
    void everyCategoryDeclaresBothLangKeys() {
        for (LockCategory<?> category : LockCategories.all()) {
            assertTrue(category.tabLangKey().startsWith("editor.historystages.tab."),
                    category.id() + " has an unexpected tab lang key: " + category.tabLangKey());
            assertTrue(category.tooltipLangKey().startsWith("editor.historystages.tooltip."),
                    category.id() + " has an unexpected tooltip lang key: " + category.tooltipLangKey());
        }
    }

    @Test
    void everyCategoryIdIsNamespaced() {
        for (LockCategory<?> category : LockCategories.all()) {
            assertTrue(category.id().startsWith("historystages:"),
                    "built-in category is not namespaced: " + category.id());
        }
    }
}
