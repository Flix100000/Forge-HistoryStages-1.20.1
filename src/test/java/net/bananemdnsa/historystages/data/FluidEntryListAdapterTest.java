package net.bananemdnsa.historystages.data;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.api.lock.LockActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidEntryListAdapterTest {

    private static final Type LIST = new TypeToken<List<FluidEntry>>() {}.getType();

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LIST, new FluidEntryListAdapter())
            .create();

    private static String write(List<FluidEntry> entries) {
        return GSON.toJson(entries, LIST);
    }

    private static List<FluidEntry> read(String json) {
        return GSON.fromJson(json, LIST);
    }

    @Test
    void aPlainEntryIsWrittenAsABareString() {
        assertEquals("[\"minecraft:lava\"]", write(List.of(new FluidEntry("minecraft:lava"))));
    }

    @Test
    void aBareStringReadsBackAsAnAllActionsEntry() {
        List<FluidEntry> entries = read("[\"minecraft:lava\"]");
        assertEquals(1, entries.size());
        assertEquals("minecraft:lava", entries.get(0).getId());
        assertNull(entries.get(0).getLockActions());
    }

    /**
     * The whole point of this class having its own adapter. The complement has to be taken
     * against the fluid vocabulary; against the item one a fluid would be written out carrying
     * four actions it never had.
     */
    @Test
    void narrowedActionsAreStoredAsTheirFluidComplement() {
        String json = write(List.of(
                new FluidEntry("minecraft:lava", List.of("use", "place"), null, null)));

        assertTrue(json.contains("\"unlock_actions\""), json);
        assertTrue(json.contains("pickup"), json);
        assertTrue(json.contains("recipe"), json);
        assertTrue(json.contains("ingredient"), json);
        assertTrue(json.contains("loot"), json);
        assertTrue(json.contains("icon"), json);

        assertFalse(json.contains("equip"), "item-only action leaked into a fluid entry: " + json);
        assertFalse(json.contains("attack"), "item-only action leaked into a fluid entry: " + json);
        assertFalse(json.contains("break"), "item-only action leaked into a fluid entry: " + json);
        assertFalse(json.contains("gui"), "item-only action leaked into a fluid entry: " + json);
    }

    @Test
    void narrowedActionsSurviveARoundTrip() {
        List<FluidEntry> before = List.of(
                new FluidEntry("minecraft:lava", List.of("use", "place"), null, null));
        List<FluidEntry> after = read(write(before));

        assertEquals(1, after.size());
        assertEquals(List.of("use", "place"), after.get(0).getLockActions());
    }

    @Test
    void everyActionLockedRoundTripsAsTheAllActionsForm() {
        List<FluidEntry> before = List.of(
                new FluidEntry("minecraft:lava", new ArrayList<>(LockActions.FLUID), null, null));
        List<FluidEntry> after = read(write(before));

        assertEquals(1, after.size());
        assertNull(after.get(0).getLockActions(),
                "locking everything is the same state as naming nothing");
    }

    @Test
    void textOverridesSurviveARoundTrip() {
        List<FluidEntry> after = read(write(List.of(
                new FluidEntry("minecraft:lava", null, "???", "Not yet"))));

        assertEquals("???", after.get(0).getNameTextOverride());
        assertEquals("Not yet", after.get(0).getTooltipTextOverride());
    }

    @Test
    void theLegacyLockActionsKeyIsStillRead() {
        List<FluidEntry> entries = read(
                "[{\"id\":\"minecraft:lava\",\"lock_actions\":[\"use\"]}]");
        assertEquals(List.of("use"), entries.get(0).getLockActions());
    }

    @Test
    void nullReadsAsAnEmptyListRatherThanNull() {
        assertEquals(List.of(), read("null"));
    }
}
