package net.bananemdnsa.historystages.api.lock;

import java.util.List;

import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A category that says nothing about actions keeps the ten the mod has always offered, so no
 * existing category — and no addon written before this method existed — changes behaviour.
 */
class LockCategoryActionsTest {

    /** The smallest thing that is a category: enough to call the default method on. */
    private record Bare(String id) implements LockCategory<String> {
        @Override public String tabLangKey() { return ""; }
        @Override public String tooltipLangKey() { return ""; }
        @Override public List<String> read(StageEntry stage) { return List.of(); }
        @Override public void write(StageEntry stage, List<String> entries) {}
    }

    private record Narrowed(String id) implements LockCategory<String> {
        @Override public String tabLangKey() { return ""; }
        @Override public String tooltipLangKey() { return ""; }
        @Override public List<String> read(StageEntry stage) { return List.of(); }
        @Override public void write(StageEntry stage, List<String> entries) {}
        @Override public List<String> lockActions() { return LockActions.FLUID; }
    }

    @Test
    void theDefaultIsTheItemVocabulary() {
        assertEquals(LockActions.ITEM, new Bare("test:bare").lockActions());
    }

    @Test
    void aCategoryMayNarrowIt() {
        assertEquals(LockActions.FLUID, new Narrowed("test:narrowed").lockActions());
    }
}
