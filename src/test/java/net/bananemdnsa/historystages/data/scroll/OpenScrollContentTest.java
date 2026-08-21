package net.bananemdnsa.historystages.data.scroll;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.EntityLocks;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScrollContentTest {

    /** Stand-in for the item registry: no Minecraft needed to test grouping and dedupe. */
    private static OpenScrollContent.TagResolver tags(Map<String, List<String>> contents) {
        return tagId -> contents.getOrDefault(tagId, List.of());
    }

    private static StageEntry stage() {
        StageEntry entry = new StageEntry();
        entry.setDisplayName("Bronze Age");
        entry.setIcon("minecraft:copper_ingot");
        return entry;
    }

    @Test
    void directItemsKeepTheirConfigOrder() {
        StageEntry entry = stage();
        entry.setItems(List.of("minecraft:iron_sword", "minecraft:anvil"));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(List.of("minecraft:iron_sword", "minecraft:anvil"), doc.itemIds());
    }

    @Test
    void tagItemsFollowTheDirectOnes() {
        StageEntry entry = stage();
        entry.setItems(List.of("minecraft:anvil"));
        entry.setTags(List.of("c:ingots"));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry,
                tags(Map.of("c:ingots", List.of("minecraft:iron_ingot", "minecraft:gold_ingot"))));

        assertEquals(List.of("minecraft:anvil", "minecraft:iron_ingot", "minecraft:gold_ingot"),
                doc.itemIds());
    }

    @Test
    void anItemListedDirectlyAndViaATagAppearsOnce() {
        StageEntry entry = stage();
        entry.setItems(List.of("minecraft:iron_ingot"));
        entry.setTags(List.of("c:ingots"));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry,
                tags(Map.of("c:ingots", List.of("minecraft:iron_ingot", "minecraft:gold_ingot"))));

        assertEquals(List.of("minecraft:iron_ingot", "minecraft:gold_ingot"), doc.itemIds());
    }

    @Test
    void everyEntityLockKindLandsInOneChapter() {
        StageEntry entry = stage();
        EntityLocks locks = new EntityLocks();
        locks.setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));
        locks.setAttacklock(List.of("minecraft:creeper"));
        locks.setInteractionlock(List.of(new EntityInteractionLockEntry("minecraft:villager")));
        entry.setEntities(locks);

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(List.of("minecraft:zombie", "minecraft:creeper", "minecraft:villager"),
                doc.creatures().stream().map(OpenScrollEntry::id).toList());
    }

    @Test
    void anEntityInSeveralLockListsAppearsOnceWithEveryMarker() {
        StageEntry entry = stage();
        EntityLocks locks = new EntityLocks();
        locks.setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));
        locks.setAttacklock(List.of("minecraft:zombie"));
        entry.setEntities(locks);

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(1, doc.creatures().size());
        assertEquals(java.util.Set.of(OpenScrollMarker.SPAWN, OpenScrollMarker.ATTACK),
                doc.creatures().get(0).markers());
    }

    @Test
    void worldGroupsKeepTheirLabelsAndDropEmptyOnes() {
        StageEntry entry = stage();
        entry.setDimensions(List.of("minecraft:the_nether"));
        entry.setBiomes(List.of("minecraft:crimson_forest"));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(2, doc.world().size(), "an empty structures group must not take a heading");
        assertEquals(OpenScrollContent.DIMENSIONS_KEY, doc.world().get(0).labelKey());
        assertEquals(OpenScrollContent.BIOMES_KEY, doc.world().get(1).labelKey());
        assertEquals(2, doc.worldCount());
    }

    @Test
    void theOverviewCarriesIconNameAndDescription() {
        OpenScrollDocument doc = OpenScrollContent.build("bronze", true, stage(), tags(Map.of()),
                "Where copper met tin.");

        assertEquals("minecraft:copper_ingot", doc.iconId());
        assertEquals("Bronze Age", doc.displayName());
        assertEquals("Where copper met tin.", doc.description());
        assertTrue(doc.individual());
    }

    @Test
    void aStageThatLocksNothingYieldsAnEmptyButUsableDocument() {
        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, stage(), tags(Map.of()));

        assertTrue(doc.isEmpty(OpenScrollChapter.ITEMS));
        assertTrue(doc.isEmpty(OpenScrollChapter.CREATURES));
        assertTrue(doc.isEmpty(OpenScrollChapter.WORLD));
        assertFalse(doc.isEmpty(OpenScrollChapter.OVERVIEW), "the overview always has something to say");
    }

    @Test
    void aMissingStageStillProducesADocumentSoTheScreenCanExplainItself() {
        OpenScrollDocument doc = OpenScrollContent.unknown("ghost");

        assertEquals("ghost", doc.stageId());
        assertTrue(doc.itemIds().isEmpty());
        assertTrue(doc.displayName().isEmpty());
    }
}
