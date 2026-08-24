package net.bananemdnsa.historystages.data.lock.category;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageStateView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generic lock query, asked about a <em>built-in</em> category.
 *
 * <p>Before Phase 8 this answered "not locked" for every one of them. {@code LockCategory.matches}
 * defaulted to false and the built-ins were reached through their own typed paths instead, so an
 * addon calling {@code CategoryLocks.isLockedForPlayer("historystages:items", …)} got a confident
 * wrong answer with nothing to warn it. It was on the list of things to decide before Phase 9
 * froze that method as public API; moving the built-ins onto the model decided it.
 *
 * <p>These exercise {@link CategoryLockResolver} rather than {@code CategoryLocks} itself, which
 * needs a live server cache. The difference between the two is only which stage map and which
 * viewer get passed in — everything worth getting wrong is here.
 */
class CategoryLocksBuiltInTest {

    private static LockCategory<?> category(String id) {
        LockCategory<?> found = LockCategories.byId(id);
        assertNotNull(found, "no built-in category registered under " + id);
        return found;
    }

    private static Map<String, StageEntry> stages(String id, StageEntry entry) {
        Map<String, StageEntry> map = new LinkedHashMap<>();
        map.put(id, entry);
        return map;
    }

    private static StageEntry gatingDimension(String dimensionId) {
        StageEntry stage = new StageEntry();
        stage.setDimensions(List.of(dimensionId));
        return stage;
    }

    @Test
    void aBuiltInCategoryAnswersTheGenericQuery() {
        assertTrue(CategoryLockResolver.isLocked(category("historystages:dimensions"),
                "minecraft:the_nether",
                stages("bronze", gatingDimension("minecraft:the_nether")),
                StageStateView.NONE_UNLOCKED),
                "a built-in category must answer for itself instead of defaulting to false");
    }

    @Test
    void aBuiltInCategoryReportsUnlockedOnceTheStageIsUnlocked() {
        assertFalse(CategoryLockResolver.isLocked(category("historystages:dimensions"),
                "minecraft:the_nether",
                stages("bronze", gatingDimension("minecraft:the_nether")),
                StageStateView.of(Set.of("bronze"))));
    }

    @Test
    void aBuiltInCategoryNamesTheStagesTheViewerIsStillMissing() {
        assertEquals(List.of("bronze"), CategoryLockResolver.missingStages(
                category("historystages:dimensions"), "minecraft:the_nether",
                stages("bronze", gatingDimension("minecraft:the_nether")),
                StageStateView.NONE_UNLOCKED));
    }

    @Test
    void anUngatedSubjectIsNotLockedByABuiltInCategory() {
        // The control: without it, a category that answers "locked" to everything passes the
        // first test and gates the entire game.
        assertFalse(CategoryLockResolver.isLocked(category("historystages:dimensions"),
                "minecraft:the_end",
                stages("bronze", gatingDimension("minecraft:the_nether")),
                StageStateView.NONE_UNLOCKED));
    }

    @Test
    void everyBuiltInCategoryIsReachableByIdAndSupportsAtLeastOneScope() {
        for (LockCategory<?> category : LockCategories.builtIns()) {
            assertNotNull(LockCategories.byId(category.id()),
                    category.id() + " is not reachable by its own id");
            assertFalse(category.supportedScopes().isEmpty(),
                    category.id() + " supports no scope at all, so nothing can ever ask it");
        }
    }
}
