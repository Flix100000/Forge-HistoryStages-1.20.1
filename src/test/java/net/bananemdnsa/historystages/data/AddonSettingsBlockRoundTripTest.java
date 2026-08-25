package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonSettingsBlockRoundTripTest {

    private static final Gson GSON = new Gson();

    private static final String WITH_SETTINGS = """
            {
              "display_name": "Bronze Age",
              "items": ["minecraft:stone"],
              "addon_settings": {
                "mymod:trades": {"hide_trades": true, "max_price": 12}
              }
            }
            """;

    private static final String WITHOUT_SETTINGS = """
            {
              "display_name": "Bronze Age"
            }
            """;

    @Test
    void settingsSurviveLoadAndSave() {
        StageEntry entry = GSON.fromJson(WITH_SETTINGS, StageEntry.class);
        String written = entry.toJson();

        StageEntry reloaded = GSON.fromJson(written, StageEntry.class);
        var settings = reloaded.addonSettings("mymod:trades").getAsJsonObject();

        assertTrue(settings.get("hide_trades").getAsBoolean());
        assertEquals(12, settings.get("max_price").getAsInt());
    }

    /**
     * The scenario this whole block exists for: someone edits a stage while the addon that owns
     * the settings is not installed. Nothing in the running instance understands the entry, and it
     * must still come out the other side untouched.
     */
    @Test
    void settingsSurviveEvenWhenNothingUnderstandsThem() {
        StageEntry entry = GSON.fromJson(WITH_SETTINGS, StageEntry.class);

        entry.setDisplayName("Bronze Age II");
        String written = entry.toJson();

        assertTrue(written.contains("mymod:trades"),
                "addon settings were dropped by an edit that had nothing to do with them");
    }

    @Test
    void aStageWithoutSettingsWritesNoBlock() {
        StageEntry entry = GSON.fromJson(WITHOUT_SETTINGS, StageEntry.class);

        assertTrue(entry.addonSettingsGroupIds().isEmpty());
        assertNull(entry.addonSettings("mymod:trades"));
        assertFalse(entry.toJson().contains("addon_settings"),
                "an empty addon_settings block should not be written into every stage file");
    }

    @Test
    void clearingTheLastGroupRemovesTheBlock() {
        StageEntry entry = GSON.fromJson(WITH_SETTINGS, StageEntry.class);
        entry.setAddonSettings("mymod:trades", null);

        assertNull(entry.addonSettings("mymod:trades"));
        assertTrue(entry.addonSettingsGroupIds().isEmpty());
        assertFalse(entry.toJson().contains("addon_settings"),
                "removing the last settings group should drop the block entirely");
    }

    @Test
    void copyCarriesTheBlockAndDoesNotShareIt() {
        StageEntry entry = GSON.fromJson(WITH_SETTINGS, StageEntry.class);
        StageEntry copy = entry.copy();

        assertEquals(entry.addonSettings("mymod:trades"), copy.addonSettings("mymod:trades"));

        copy.setAddonSettings("mymod:trades", null);

        assertNotNull(entry.addonSettings("mymod:trades"),
                "copy() handed out a shared addon_settings map");
    }

    @Test
    void compactJsonAlsoCarriesTheBlock() {
        StageEntry entry = GSON.fromJson(WITH_SETTINGS, StageEntry.class);
        assertTrue(entry.toCompactJson().contains("mymod:trades"),
                "stages are synced to clients as compact JSON — addon settings must ride along");
    }

    @Test
    void theBlockIsSeparateFromTheLockCategoryBlock() {
        StageEntry entry = new StageEntry();

        com.google.gson.JsonObject settings = new com.google.gson.JsonObject();
        settings.addProperty("hide_trades", true);
        entry.setAddonSettings("mymod:trades", settings);

        assertNull(entry.addonEntries("mymod:trades"),
                "addon_settings must not be readable through the addons (lock category) accessor");
    }

    /**
     * Walks the whole real path a stage takes when the editor saves it, hop by hop, because a
     * report that an uninstalled group's block "disappeared on save" has to be answerable with
     * evidence rather than with reasoning about which hop looks safe.
     *
     * <p>Every hop below uses a plain {@code new Gson()} on the whole {@link StageEntry}, exactly
     * as the real code does: {@code StageManager} reads the file, {@code SyncStageDefinitionsPacket}
     * writes the stage map, {@code StageSaver} sends {@link StageEntry#toCompactJson()},
     * {@code SaveStagePacket} parses it back, and {@code StageManager.saveStage} writes
     * {@link StageEntry#toJson()} over the file.
     *
     * <p>If this passes, no hop drops the block, and a block that vanishes in game was never in
     * the server's memory to begin with — stages load once at setup, so a file hand-edited while
     * the game runs is invisible until {@code /history reload}.
     */
    @Test
    void anUninstalledGroupsBlockSurvivesTheWholeEditorSaveRoundTrip() {
        // Hop 1 — StageManager reads the file into memory.
        StageEntry onServer = GSON.fromJson("""
                {
                  "display_name": "Bronze Age",
                  "addon_settings": {"notinstalled:group": {"x": 1}}
                }
                """, StageEntry.class);

        // Hop 2 — the whole stage map goes to the client as one JSON string.
        String wire = GSON.toJson(java.util.Map.of("bronze", onServer));
        StageEntry onClient = GSON.<java.util.Map<String, StageEntry>>fromJson(
                wire, new com.google.gson.reflect.TypeToken<java.util.Map<String, StageEntry>>() {}.getType())
                .get("bronze");

        // Hop 3 — the editor snapshots from the stage as it was and overwrites what it owns.
        StageEntry snapshot = onClient.copy();
        snapshot.setDisplayName("Bronze Age II");

        // Hop 4 — StageSaver sends compact JSON, SaveStagePacket parses it back.
        StageEntry backOnServer = GSON.fromJson(snapshot.toCompactJson(), StageEntry.class);

        // Hop 5 — saveStage writes this over the file.
        String written = backOnServer.toJson();

        assertTrue(written.contains("notinstalled:group"),
                "an uninstalled group's block was dropped somewhere between the file and the file");
        assertEquals(1, GSON.fromJson(written, StageEntry.class)
                .addonSettings("notinstalled:group").getAsJsonObject().get("x").getAsInt());
    }
}
