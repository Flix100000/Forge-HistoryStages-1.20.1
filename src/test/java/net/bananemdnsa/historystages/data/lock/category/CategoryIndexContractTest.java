package net.bananemdnsa.historystages.data.lock.category;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.engine.LockSubjects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one property the reverse index must never break: <em>if a stage gates a subject, the index
 * has to name that stage.</em>
 *
 * <p>Naming one too many costs a comparison — the exact check runs afterwards and rejects it.
 * Naming one too few means the stage is never asked at all, and whatever it gated is silently
 * available. That failure does not throw, does not log, and looks exactly like a stage the pack
 * author forgot to configure.
 *
 * <p>Interaction locks are not covered here. Their subject carries an {@code ItemStack}, which no
 * unit test can construct; the GameTests exercise that path with real stacks instead.
 */
class CategoryIndexContractTest {

    private static LockCategory<?> category(String id) {
        LockCategory<?> found = LockCategories.byId(id);
        assertNotNull(found, "no built-in category registered under " + id);
        return found;
    }

    /** The contract, asserted directly: gates ⇒ indexed. */
    private static void assertIndexed(String categoryId, StageEntry stage, Object subject) {
        LockCategory<?> category = category(categoryId);
        if (!category.gates(stage, subject)) return; // nothing promised about non-gating stages

        String key = category.lookupKey(subject);
        assertNotNull(key, categoryId + " gates this subject but produces no lookup key, "
                + "so the index can never find it");
        assertTrue(category.indexKeys(stage).contains(key),
                categoryId + " gates " + subject + " but does not file the stage under '" + key
                        + "' — the index would skip it and the subject would come out unlocked");
    }

    // ---- the four plain id lists ---------------------------------------------------

    @Test
    void aGatedDimensionIsIndexed() {
        StageEntry stage = new StageEntry();
        stage.setDimensions(List.of("minecraft:the_nether", "minecraft:the_end"));
        assertIndexed("historystages:dimensions", stage, "minecraft:the_nether");
        assertIndexed("historystages:dimensions", stage, "minecraft:the_end");
    }

    @Test
    void aGatedStructureIsIndexed() {
        StageEntry stage = new StageEntry();
        stage.setStructures(List.of("minecraft:village_plains"));
        assertIndexed("historystages:structures", stage, "minecraft:village_plains");
    }

    @Test
    void aGatedBiomeIsIndexed() {
        StageEntry stage = new StageEntry();
        stage.setBiomes(List.of("minecraft:desert"));
        assertIndexed("historystages:biomes", stage, "minecraft:desert");
    }

    @Test
    void aGatedRecipeIsIndexed() {
        StageEntry stage = new StageEntry();
        stage.setRecipes(List.of("minecraft:diamond_sword"));
        assertIndexed("historystages:recipes", stage, "minecraft:diamond_sword");
    }

    // ---- attack locks, the dangerous one -------------------------------------------

    @Test
    void anAttackLockFromItsOwnListIsIndexed() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setAttacklock(List.of("minecraft:zombie"));
        assertIndexed("historystages:attacklock", stage, "minecraft:zombie");
    }

    @Test
    void anAttackLockImpliedByASpawnLockIsIndexed() {
        // The case the contract on indexKeys is written for. This stage has an empty attacklock
        // list; it gates attacking the zombie only because a source-less spawn lock implies it.
        // An index built from the attacklock list alone would never name this stage, and the
        // zombie would become attackable with nothing reporting a fault.
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));
        assertIndexed("historystages:attacklock", stage, "minecraft:zombie");
    }

    @Test
    void bothSourcesOfAnAttackLockAreIndexedTogether() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setAttacklock(List.of("minecraft:creeper"));
        stage.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));
        assertIndexed("historystages:attacklock", stage, "minecraft:creeper");
        assertIndexed("historystages:attacklock", stage, "minecraft:zombie");
    }

    @Test
    void anIndexMayNameAStageThatTurnsOutNotToGate() {
        // Over-approximation is allowed and expected: a source-restricted spawn lock does not
        // imply an attack lock, but the entity is still filed. The exact check settles it.
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(
                List.of(new EntitySpawnLockEntry("minecraft:zombie", List.of("spawner"))));
        assertTrue(category("historystages:attacklock").indexKeys(stage).contains("minecraft:zombie"));
    }

    // ---- spawn locks ---------------------------------------------------------------

    @Test
    void aGatedSpawnIsIndexed() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));
        assertIndexed("historystages:spawnlock", stage,
                new LockSubjects.SpawnSubject("minecraft:zombie", "natural", "minecraft:overworld"));
    }

    @Test
    void aSpawnLockNarrowedByItsSourceIsStillIndexed() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(
                List.of(new EntitySpawnLockEntry("minecraft:zombie", List.of("spawner"))));
        assertIndexed("historystages:spawnlock", stage,
                new LockSubjects.SpawnSubject("minecraft:zombie", "spawner", "minecraft:overworld"));
    }

    // ---- interaction locks: the keys, without a subject ----------------------------

    @Test
    void everyInteractionLockEntityIsIndexed() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setInteractionlock(List.of(
                new net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry("minecraft:cow"),
                new net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry("minecraft:pig")));
        List<String> keys = category("historystages:interactionlock").indexKeys(stage);
        assertTrue(keys.contains("minecraft:cow") && keys.contains("minecraft:pig"),
                "every entity with an interaction lock has to be filed, got " + keys);
    }

    // ---- and the categories that deliberately stay unindexed -----------------------

    @Test
    void itemsModsAndTagsOptOutRatherThanIndexBadly() {
        // They are narrowed by LockRelevanceIndex instead, which serves an item id, a namespace
        // and tag membership at once. A single-key index cannot express that, and a half-built
        // one here would be the under-approximation this whole test exists to prevent.
        StageEntry stage = new StageEntry();
        stage.setItems(new ArrayList<>(List.of("minecraft:diamond")));
        stage.setMods(new ArrayList<>(List.of("minecraft")));

        for (String id : List.of("historystages:items", "historystages:mods", "historystages:tags")) {
            assertTrue(category(id).indexKeys(stage).isEmpty(),
                    id + " must not index itself while LockRelevanceIndex is what narrows it");
        }
    }
}
