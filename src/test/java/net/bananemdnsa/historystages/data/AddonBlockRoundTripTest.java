package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonBlockRoundTripTest {

    private static final Gson GSON = new Gson();

    private static final String WITH_ADDON = """
            {
              "display_name": "Bronze Age",
              "items": ["minecraft:stone"],
              "addons": {
                "mymod:villagertrades": [{"id": "minecraft:emerald"}]
              }
            }
            """;

    private static final String WITHOUT_ADDON = """
            {
              "display_name": "Bronze Age"
            }
            """;

    @Test
    void addonDataSurvivesLoadAndSave() {
        StageEntry entry = GSON.fromJson(WITH_ADDON, StageEntry.class);
        String written = entry.toJson();

        StageEntry reloaded = GSON.fromJson(written, StageEntry.class);
        JsonArray trades = reloaded.addonEntries("mymod:villagertrades").getAsJsonArray();

        assertEquals(1, trades.size());
        assertEquals("minecraft:emerald", trades.get(0).getAsJsonObject().get("id").getAsString());
    }

    /**
     * The scenario this whole block exists for: someone edits a stage while the addon that owns
     * the data is not installed. Nothing in the running instance understands the entry, and it
     * must still come out the other side untouched.
     */
    @Test
    void addonDataSurvivesEvenWhenNothingUnderstandsIt() {
        StageEntry entry = GSON.fromJson(WITH_ADDON, StageEntry.class);

        entry.setDisplayName("Bronze Age II");
        String written = entry.toJson();

        assertTrue(written.contains("mymod:villagertrades"),
                "addon data was dropped by an edit that had nothing to do with it");
    }

    @Test
    void aStageWithoutAddonsReadsAsEmptyAndWritesNoAddonsKey() {
        StageEntry entry = GSON.fromJson(WITHOUT_ADDON, StageEntry.class);

        assertTrue(entry.addonCategoryIds().isEmpty());
        assertNull(entry.addonEntries("mymod:villagertrades"));
        assertFalse(entry.toJson().contains("addons"),
                "an empty addons block should not be written into every stage file");
    }

    @Test
    void writingAnAddonEntryMakesItReadable() {
        StageEntry entry = new StageEntry();

        JsonObject trade = new JsonObject();
        trade.addProperty("id", "minecraft:emerald");
        JsonArray trades = new JsonArray();
        trades.add(trade);

        entry.setAddonEntries("mymod:villagertrades", trades);

        assertEquals(trades, entry.addonEntries("mymod:villagertrades"));
        assertEquals(java.util.Set.of("mymod:villagertrades"), entry.addonCategoryIds());
        assertTrue(entry.toJson().contains("mymod:villagertrades"));
    }

    @Test
    void clearingAnAddonEntryRemovesIt() {
        StageEntry entry = GSON.fromJson(WITH_ADDON, StageEntry.class);
        entry.setAddonEntries("mymod:villagertrades", null);

        assertNull(entry.addonEntries("mymod:villagertrades"));
        assertTrue(entry.addonCategoryIds().isEmpty());
        assertFalse(entry.toJson().contains("addons"),
                "removing the last addon entry should drop the block entirely");
    }

    @Test
    void copyCarriesTheAddonBlock() {
        StageEntry entry = GSON.fromJson(WITH_ADDON, StageEntry.class);
        StageEntry copy = entry.copy();

        assertEquals(entry.addonEntries("mymod:villagertrades"),
                copy.addonEntries("mymod:villagertrades"));
    }

    @Test
    void copyDoesNotShareTheAddonBlockWithTheOriginal() {
        StageEntry entry = GSON.fromJson(WITH_ADDON, StageEntry.class);
        StageEntry copy = entry.copy();

        copy.setAddonEntries("mymod:villagertrades", null);

        assertNotNull(entry.addonEntries("mymod:villagertrades"),
                "copy() handed out a shared addons map");
    }

    @Test
    void compactJsonAlsoCarriesTheAddonBlock() {
        StageEntry entry = GSON.fromJson(WITH_ADDON, StageEntry.class);
        assertTrue(entry.toCompactJson().contains("mymod:villagertrades"),
                "stages are synced to clients as compact JSON — addon data must ride along");
    }
}
