package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.api.editor.CategoryTab;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tab with sections is the first place in this editor where something being edited is not on
 * screen, and the failure that comes with it is silent: the maintainer types six professions,
 * switches to levels, saves, and the professions are gone with nothing to say so.
 *
 * <p>So the section that is <em>not</em> showing is what these check.
 */
class CompositeCategoryTabTest {

    /** A section that records whether it was loaded and stored, and nothing else. */
    private static final class Recording implements CategoryTab {

        private final String id;
        private final boolean individualOk;
        private final List<String> rows = new ArrayList<>();
        private int loads;
        private int stores;
        private int shown;

        Recording(String id) {
            this(id, true);
        }

        Recording(String id, boolean individualOk) {
            this.id = id;
            this.individualOk = individualOk;
        }

        @Override public String categoryId() { return id; }
        @Override public boolean availableForIndividualStages() { return individualOk; }
        @Override public String tabLangKey() { return "tab." + id; }
        @Override public String tooltipLangKey() { return "tooltip." + id; }
        @Override public List<String> entries() { return rows; }
        @Override public void removeAt(int index) { rows.remove(index); }
        @Override public void onShown() { shown++; }
        @Override public void load(StageEntry stage) { loads++; }
        @Override public void store(StageEntry stage) { stores++; }
        @Override public void rebuildPicker() { }
        @Override public PickerOverlay activeOverlay() { return null; }
        @Override public void openPicker(int centerX, int centerY, int parentWidth) { }
    }

    private static CompositeCategoryTab tabOf(Recording... sections) {
        return tabOf(StageScope.GLOBAL, sections);
    }

    private static CompositeCategoryTab tabOf(StageScope scope, Recording... sections) {
        List<CompositeCategoryTab.Section> list = new ArrayList<>();
        for (Recording section : sections) {
            list.add(new CompositeCategoryTab.Section(section, "label." + section.categoryId()));
        }
        return new CompositeCategoryTab("historystages:composite", "tab.composite",
                "tooltip.composite", list, scope);
    }

    @Test
    void storingReachesTheSectionsThatAreNotShowing() {
        Recording items = new Recording("mod:items");
        Recording professions = new Recording("mod:professions");
        Recording levels = new Recording("mod:levels");
        CompositeCategoryTab tab = tabOf(items, professions, levels);

        tab.store(new StageEntry());

        assertEquals(1, items.stores);
        assertEquals(1, professions.stores,
                "the section that was not on screen must still be written, or a save drops"
                        + " everything the maintainer entered before switching sections");
        assertEquals(1, levels.stores);
    }

    @Test
    void loadingReachesTheSectionsThatAreNotShowing() {
        Recording items = new Recording("mod:items");
        Recording professions = new Recording("mod:professions");
        CompositeCategoryTab tab = tabOf(items, professions);

        tab.load(new StageEntry());

        assertEquals(1, items.loads);
        assertEquals(1, professions.loads,
                "a section loaded only when it is first opened would show an empty list for a"
                        + " stage that has entries in it");
    }

    @Test
    void everythingAboutARowGoesToTheSectionOnScreen() {
        Recording items = new Recording("mod:items");
        Recording professions = new Recording("mod:professions");
        items.rows.add("minecraft:diamond");
        professions.rows.add("minecraft:librarian");
        CompositeCategoryTab tab = tabOf(items, professions);

        assertEquals(List.of("minecraft:diamond"), tab.entries());
        assertEquals("mod:items", tab.activeSection().categoryId());

        assertTrue(tab.setActiveIndex(1));
        assertEquals(List.of("minecraft:librarian"), tab.entries());
        assertEquals("mod:professions", tab.activeSection().categoryId(),
                "the host asks this to tell an item row from a profession row; answering with the"
                        + " tab's own id would offer the NBT editor on both");

        tab.removeAt(0);
        assertTrue(professions.rows.isEmpty());
        assertEquals(List.of("minecraft:diamond"), items.rows,
                "a removal must not reach a section nobody is looking at");
    }

    @Test
    void theTabKeepsItsOwnIdentityWhicheverSectionIsOpen() {
        CompositeCategoryTab tab = tabOf(new Recording("mod:items"), new Recording("mod:levels"));
        tab.setActiveIndex(1);

        assertEquals("historystages:composite", tab.categoryId(),
                "the strip shows one tab with one name, and that must not follow the sections");
        assertEquals("tab.composite", tab.tabLangKey());
        assertEquals("tooltip.composite", tab.tooltipLangKey());
    }

    @Test
    void switchingToTheSectionAlreadyOpenChangesNothing() {
        Recording items = new Recording("mod:items");
        CompositeCategoryTab tab = tabOf(items, new Recording("mod:levels"));

        assertFalse(tab.setActiveIndex(0), "already there");
        assertFalse(tab.setActiveIndex(7), "out of range");
        assertFalse(tab.setActiveIndex(-1), "out of range");
        assertEquals(0, items.shown,
                "replaying the entrance animation for a section that never left would make the"
                        + " rows jump every time the bar is clicked");
    }

    @Test
    void aTabIsOfferedPerPlayerAsLongAsOneSectionWorksThere() {
        CompositeCategoryTab mixed = tabOf(StageScope.INDIVIDUAL,
                new Recording("mod:attack", true), new Recording("mod:spawn", false));

        assertTrue(mixed.availableForIndividualStages(),
                "refusing the whole tab because one section is global-only would take the two"
                        + " that do work per player down with it");

        CompositeCategoryTab noneFit = tabOf(StageScope.INDIVIDUAL,
                new Recording("mod:spawn", false), new Recording("mod:weather", false));

        assertFalse(noneFit.availableForIndividualStages(),
                "a tab whose every section is dead is worse than no tab");
    }

    @Test
    void aSectionThatCannotBeEditedHereIsNotEnabled() {
        CompositeCategoryTab individual = tabOf(StageScope.INDIVIDUAL,
                new Recording("mod:attack", true), new Recording("mod:spawn", false));

        assertTrue(individual.sectionEnabled(0));
        assertFalse(individual.sectionEnabled(1));

        CompositeCategoryTab global = tabOf(StageScope.GLOBAL,
                new Recording("mod:attack", true), new Recording("mod:spawn", false));

        assertTrue(global.sectionEnabled(1),
                "global-only is a statement about individual stages, not about this one");
    }

    @Test
    void clickingADisabledSectionChangesNothing() {
        Recording spawn = new Recording("mod:spawn", false);
        CompositeCategoryTab tab = tabOf(StageScope.INDIVIDUAL,
                new Recording("mod:attack", true), spawn);

        assertFalse(tab.setActiveIndex(1), "the segment is greyed; a click on it is not a switch");
        assertEquals("mod:attack", tab.activeSection().categoryId());
        assertEquals(0, spawn.shown,
                "showing a section nobody can edit would put its rows on screen anyway");
    }

    @Test
    void theTabOpensOnASectionThatCanBeEdited() {
        CompositeCategoryTab tab = tabOf(StageScope.INDIVIDUAL,
                new Recording("mod:spawn", false), new Recording("mod:attack", true));

        assertEquals("mod:attack", tab.activeSection().categoryId(),
                "opening on a greyed section would show an empty list nobody can add to");
    }

    @Test
    void theCountOnTheStripIsEverySectionTogether() {
        Recording offers = new Recording("mod:offers");
        Recording levels = new Recording("mod:levels");
        offers.rows.add("minecraft:diamond");
        offers.rows.add("minecraft:emerald");
        levels.rows.add("2");
        CompositeCategoryTab tab = tabOf(offers, levels);

        assertEquals(3, tab.totalEntryCount(),
                "counting only the section on screen makes a tab holding twenty offers claim (0)"
                        + " as soon as someone switches to levels");
    }
}
