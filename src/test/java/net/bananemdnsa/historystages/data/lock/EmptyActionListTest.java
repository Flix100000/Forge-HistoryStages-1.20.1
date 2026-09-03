package net.bananemdnsa.historystages.data.lock;

import java.lang.reflect.Type;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.api.lock.LockActions;
import net.bananemdnsa.historystages.data.FluidEntry;
import net.bananemdnsa.historystages.data.FluidEntryListAdapter;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.ItemEntryListAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An entry the maintainer narrowed down to no action at all locks nothing.
 *
 * <p>The editor has always offered that state — the action popup has a "None" button and a
 * "0 of 10 blocked" line — but the data model used to fold an empty list into {@code null}, and
 * {@code null} is the marker for "every action". Ticking nothing therefore produced the exact
 * file ticking everything produces, which is how Issue #117 was found: with all ten actions
 * cleared the mod stayed fully locked.
 *
 * <p>So the three states are distinct and stay distinct across a save: {@code null} locks
 * everything, a filled list locks what it names, and an empty list locks nothing.
 */
class EmptyActionListTest {

    private static final Type ITEM_LIST = new TypeToken<List<ItemEntry>>() {}.getType();
    private static final Type FLUID_LIST = new TypeToken<List<FluidEntry>>() {}.getType();
    private static final Type NAMED_LIST = new TypeToken<List<NamedLockEntry>>() {}.getType();

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ITEM_LIST, new ItemEntryListAdapter())
            .registerTypeAdapter(FLUID_LIST, new FluidEntryListAdapter())
            .registerTypeAdapter(NAMED_LIST, new NamedLockEntryListAdapter())
            .create();

    // ---------------------------------------------------------------- entry classes

    @Test
    void anEmptyListSurvivesTheModEntryConstructor() {
        NamedLockEntry entry = new NamedLockEntry("create", List.of());

        assertNotNull(entry.getLockActions(), "an empty list is not the same as no list");
        assertEquals(List.of(), entry.getLockActions());
        assertTrue(entry.hasLockActions(), "the entry is narrowed — to nothing");
    }

    @Test
    void anEmptyListSurvivesTheItemAndFluidConstructors() {
        assertEquals(List.of(), new ItemEntry("create:cogwheel", null, List.of()).getLockActions());
        assertEquals(List.of(), new FluidEntry("create:honey", List.of(), null, null).getLockActions());
        assertEquals(List.of(),
                new EntityInteractionLockEntry("minecraft:cow", List.of()).getLockActions());
    }

    @Test
    void aMissingListStillMeansEveryAction() {
        assertEquals(null, new NamedLockEntry("create").getLockActions());
        assertFalse(new NamedLockEntry("create").hasLockActions());
    }

    // ---------------------------------------------------------------- persistence

    /**
     * The complement is what goes on disk, so "locks nothing" is written as "every action is
     * unlocked". Without this the entry falls back to the bare-string form, which reads back as
     * the opposite of what was saved.
     */
    @Test
    void aModEntryLockingNothingRoundTrips() {
        String json = GSON.toJson(List.of(new NamedLockEntry("create", List.of())), NAMED_LIST);

        assertTrue(json.contains("\"unlock_actions\""), json);
        for (String action : LockActions.ITEM) {
            assertTrue(json.contains("\"" + action + "\""), action + " missing from " + json);
        }

        List<NamedLockEntry> back = GSON.fromJson(json, NAMED_LIST);
        assertEquals(1, back.size());
        assertEquals(List.of(), back.get(0).getLockActions());
    }

    @Test
    void anItemEntryLockingNothingRoundTrips() {
        String json = GSON.toJson(List.of(new ItemEntry("create:cogwheel", null, List.of())), ITEM_LIST);
        List<ItemEntry> back = GSON.fromJson(json, ITEM_LIST);

        assertEquals(List.of(), back.get(0).getLockActions());
    }

    @Test
    void aFluidEntryLockingNothingRoundTrips() {
        String json = GSON.toJson(
                List.of(new FluidEntry("create:honey", List.of(), null, null)), FLUID_LIST);
        List<FluidEntry> back = GSON.fromJson(json, FLUID_LIST);

        assertEquals(List.of(), back.get(0).getLockActions());
    }

    @Test
    void anEntryLockingEverythingIsStillWrittenAsABareString() {
        String json = GSON.toJson(
                List.of(new NamedLockEntry("create", LockActions.ITEM)), NAMED_LIST);

        assertEquals("[\"create\"]", json);
    }
}
