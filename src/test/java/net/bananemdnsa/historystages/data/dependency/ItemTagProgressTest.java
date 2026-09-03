package net.bananemdnsa.historystages.data.dependency;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.bananemdnsa.historystages.data.DependencyGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tag half of a dependency group: where its progress is filed, and whether the group carries
 * it everywhere it carries its items.
 *
 * <p>Item tags reached the dependency side late, and every one of these tests stands for a place
 * that would have failed silently: a group counted as empty and dropped on save, an OR group read
 * as blocked in the graph, a copy that arrives without its tags. None of it throws — it just
 * quietly demands nothing.
 */
class ItemTagProgressTest {

    private static final Gson GSON = new Gson();

    private static DependencyGroup groupWithTag(String tagId, int count) {
        DependencyGroup group = new DependencyGroup();
        group.setId("g1");
        group.getItemTags().add(new DependencyItem(tagId, count));
        return group;
    }

    @Test
    void aTagsCountAndItsChoiceAreFiledSideBySide() {
        String groupKey = DependencyProgress.groupKey(groupWithTag("#c:ingots", 3), 0);

        assertEquals("Group_g1_ItemTag_#c:ingots",
                DependencyProgress.key(groupKey, DependencyProgress.itemTagSuffix("#c:ingots")));
        assertEquals("Group_g1_ItemTagChoice_#c:ingots",
                DependencyProgress.key(groupKey, DependencyProgress.itemTagChoiceSuffix("#c:ingots")));
    }

    @Test
    void aTagCanNeverShareACounterWithAnItem() {
        // The two suffixes take the same shape of argument, so the only thing keeping their keys
        // apart is that "#" cannot appear in an item id. If a tag ever loses its prefix on the
        // way in, "#c:ingots" and "c:ingots" would land in different keys — but an item and a tag
        // named alike would land in the same one, and share a count no one asked them to share.
        assertNotEquals(DependencyProgress.itemSuffix("c:ingots"),
                DependencyProgress.itemTagSuffix("#c:ingots"));
        assertNotEquals(DependencyProgress.itemTagSuffix("#c:ingots"),
                DependencyProgress.itemTagChoiceSuffix("#c:ingots"));
        assertTrue(DependencyProgress.itemTagSuffix("#c:ingots").contains("#"),
                "the prefix is what separates the two namespaces — it has to survive into the key");
    }

    @Test
    void aGroupOfNothingButTagsIsNotEmpty() {
        assertFalse(groupWithTag("#c:ingots", 1).isEmpty(),
                "a group that counts as empty is dropped when the stage is saved");
    }

    @Test
    void aTagCountsAsANonStageRequirement() {
        // The graph asks this to decide whether an OR group has an escape hatch it cannot
        // evaluate client-side. Answer no, and a locked stage reference makes the whole group
        // read as blocked even though handing in three ingots would open it.
        assertTrue(groupWithTag("#c:ingots", 1).hasNonStageRequirements());
    }

    @Test
    void copyCarriesTheTagsAndTheirNbt() {
        DependencyGroup original = groupWithTag("#c:ingots", 3);
        JsonObject nbt = new JsonObject();
        nbt.addProperty("Damage", 0);
        original.getItemTags().get(0).setNbt(nbt);

        DependencyGroup copy = original.copy();

        assertEquals(1, copy.getItemTags().size());
        assertEquals("#c:ingots", copy.getItemTags().get(0).getId());
        assertEquals(3, copy.getItemTags().get(0).getCount());
        assertTrue(copy.getItemTags().get(0).hasNbt());
        assertNotSame(original.getItemTags().get(0), copy.getItemTags().get(0));

        copy.getItemTags().get(0).setCount(9);
        assertEquals(3, original.getItemTags().get(0).getCount(),
                "the editor works on copies; a shared entry would write edits back before saving");
    }

    @Test
    void tagsRoundTripThroughJsonUnderItemTags() {
        String json = GSON.toJson(groupWithTag("#c:ingots", 3));
        assertTrue(json.contains("\"item_tags\""), json);

        DependencyGroup back = GSON.fromJson(json, DependencyGroup.class);

        assertEquals(List.of("#c:ingots"),
                back.getItemTags().stream().map(DependencyItem::getId).toList());
        assertEquals(3, back.getItemTags().get(0).getCount());
    }

    @Test
    void aGroupFromAFileWithoutTagsStillAnswers() {
        // Gson leaves an absent field null, and every getter on this class lazily fills in.
        DependencyGroup back = GSON.fromJson("{\"logic\":\"AND\"}", DependencyGroup.class);

        assertTrue(back.getItemTags().isEmpty());
        assertTrue(back.isEmpty());
    }
}
