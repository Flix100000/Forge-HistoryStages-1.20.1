package net.bananemdnsa.historystages.data;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Addon data has to survive the whole editor round trip, not just a file read.
 *
 * <p>The first test covers the sync boundary: the editor works on the copy the client was sent,
 * so if that copy lost the block, preserving it on save would preserve nothing.
 *
 * <p>The second guards the bug this file was written for. {@code buildEntrySnapshot} used to
 * start from a bare {@code new StageEntry()} and copy the fields the editor knows about one by
 * one, so anything it did not enumerate was erased on every save. Starting from the stage as it
 * was makes unmodelled state survive by construction — which matters for far more than addons,
 * since every future field would otherwise walk into the same trap.
 */
class AddonBlockSurvivesEditingTest {

    private static final Gson GSON = new Gson();

    private static final String WITH_ADDON = """
            {
              "display_name": "Bronze Age",
              "addons": { "mymod:villagertrades": [{"id": "minecraft:emerald"}] }
            }
            """;

    @Test
    void addonDataSurvivesTheSyncToTheClient() {
        // Mirrors SyncStageDefinitionsPacket: the whole map goes over the wire as one JSON string.
        Map<String, StageEntry> serverSide = Map.of("bronze", GSON.fromJson(WITH_ADDON, StageEntry.class));

        String wire = GSON.toJson(serverSide);
        Map<String, StageEntry> clientSide =
                GSON.fromJson(wire, new TypeToken<Map<String, StageEntry>>() {}.getType());

        assertNotNull(clientSide.get("bronze").addonEntries("mymod:villagertrades"),
                "the client never receives the addon data, so nothing downstream could preserve it");
    }

    @Test
    void theEditorSnapshotStartsFromTheStageAsItWas() throws IOException {
        Path screen = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages",
                "client", "editor", "StageDetailScreen.java");
        assertTrue(Files.exists(screen), "expected to run from the project root");

        String source = Files.readString(screen);
        int start = source.indexOf("private StageEntry buildEntrySnapshot()");
        assertTrue(start >= 0, "could not find buildEntrySnapshot in StageDetailScreen");
        String body = source.substring(start, source.indexOf("\n    }", start));

        assertTrue(body.contains("originalEntry"),
                """
                buildEntrySnapshot builds the stage that gets written to disk. Starting it from a \
                bare new StageEntry() silently erases every part of the stage the editor does not \
                model — that is how the addons block was being destroyed on save. Start from the \
                original stage instead and overwrite the fields the editor owns.""");
    }

    /**
     * Documents which StageEntry fields the editor deliberately does not set, so the copy-based
     * base is doing real work rather than being incidental. If this list ever grows, that is a
     * signal to check the editor still owns everything it thinks it owns.
     *
     * <p>{@code addon_settings} joined {@code addons} here deliberately: it is the same kind of
     * raw, addon-owned block, and the stage editor has no UI for it either.
     */
    @Test
    void addonsAndAddonSettingsAreTheOnlyFieldsTheEditorDoesNotSetItself() throws IOException {
        Path screen = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages",
                "client", "editor", "StageDetailScreen.java");
        String source = Files.readString(screen);
        int start = source.indexOf("private StageEntry buildEntrySnapshot()");
        String body = source.substring(start, source.indexOf("\n    }", start));

        // Four fields are written through a setter that is not named after them.
        Map<String, String> setterAliases = Map.of(
                "items", "setItemEntries",
                "tags", "setTagEntries",
                "mods", "setModEntries",
                "modExceptions", "setModExceptionEntries");

        List<String> unset = new ArrayList<>();
        for (Field field : StageEntry.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            String name = field.getName();
            String setter = setterAliases.getOrDefault(name,
                    "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            if (!body.contains(setter + "(")) unset.add(name);
        }

        // Fields the editor no longer sets by name because a CategoryTab stores them through
        // LockCategory.write(). This list grows as tabs migrate onto the category-driven path.
        List<String> storedByACategoryTab = List.of("items", "tags", "mods", "modExceptions", "recipes", "dimensions", "structures", "biomes", "entities");
        assertTrue(body.contains("tab.store(newEntry)"),
                "the snapshot must still run the category tabs store loop");
        unset.removeAll(storedByACategoryTab);

        assertTrue(unset.equals(List.of("addons", "addonSettings")),
                "fields the editor neither sets nor stores through a category tab changed to "
                        + unset + " — confirm the copy-based snapshot base still covers them");
    }
}
