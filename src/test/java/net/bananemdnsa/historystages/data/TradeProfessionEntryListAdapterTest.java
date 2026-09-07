package net.bananemdnsa.historystages.data;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A profession was a bare string before it could carry levels, and every stage file that exists
 * still writes it that way. The shape has to survive in both directions, or the first save by a
 * newer version rewrites files that an older version can no longer read.
 */
class TradeProfessionEntryListAdapterTest {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(new TypeToken<List<TradeProfessionEntry>>() {}.getType(),
                    new TradeProfessionEntryListAdapter())
            .create();

    private static List<TradeProfessionEntry> read(String json) {
        return GSON.fromJson(json, new TypeToken<List<TradeProfessionEntry>>() {}.getType());
    }

    private static String write(List<TradeProfessionEntry> entries) {
        return GSON.toJson(entries, new TypeToken<List<TradeProfessionEntry>>() {}.getType());
    }

    @Test
    void aBareStringIsAProfessionGatedAtEveryLevel() {
        List<TradeProfessionEntry> entries = read("[\"minecraft:librarian\"]");

        assertEquals(1, entries.size());
        assertEquals("minecraft:librarian", entries.get(0).getId());
        assertNull(entries.get(0).getLevels());
        assertTrue(entries.get(0).gates("minecraft:librarian", 1));
    }

    @Test
    void aProfessionWithoutLevelsIsWrittenBackAsABareString() {
        String json = write(List.of(new TradeProfessionEntry("minecraft:cleric")));

        assertEquals("[\"minecraft:cleric\"]", json,
                "a stage that never used the narrowing must not change shape when it is saved,"
                        + " or every existing file is rewritten into something older versions"
                        + " read differently");
    }

    @Test
    void levelsAreWrittenAsNumbers() {
        String json = write(List.of(
                new TradeProfessionEntry("minecraft:librarian", List.of("4", "5"))));

        assertEquals("[{\"id\":\"minecraft:librarian\",\"levels\":[4,5]}]", json,
                "the block's own level list writes numbers; two lists in one file saying the same"
                        + " kind of thing must not look different");
    }

    @Test
    void levelsAreReadFromNumbersAndFromStrings() {
        assertEquals(List.of("4", "5"),
                read("[{\"id\":\"a:b\",\"levels\":[4,5]}]").get(0).getLevels());
        assertEquals(List.of("4", "5"),
                read("[{\"id\":\"a:b\",\"levels\":[\"4\",\"5\"]}]").get(0).getLevels(),
                "hand-edited files quote things; refusing them would be a papercut with no upside");
    }

    @Test
    void theTwoShapesLiveSideBySide() {
        List<TradeProfessionEntry> entries = read(
                "[\"minecraft:cleric\",{\"id\":\"minecraft:librarian\",\"levels\":[4]}]");

        assertEquals(2, entries.size());
        assertFalse(entries.get(0).hasLevels());
        assertTrue(entries.get(1).hasLevels());
        assertEquals("[\"minecraft:cleric\",{\"id\":\"minecraft:librarian\",\"levels\":[4]}]",
                write(entries));
    }

    @Test
    void anEmptyLevelListMeansEveryLevel() {
        List<TradeProfessionEntry> entries = read("[{\"id\":\"a:b\",\"levels\":[]}]");

        assertNull(entries.get(0).getLevels(),
                "an empty list would otherwise gate nothing, and an entry that gates nothing is a"
                        + " row the maintainer sees and cannot explain");
        assertTrue(entries.get(0).gates("a:b", 3));
    }

    @Test
    void rubbishIsSkippedRatherThanFatal() {
        assertEquals(List.of(), read("[42]").stream().map(TradeProfessionEntry::getId).toList());
        assertEquals(List.of(), read("[{\"levels\":[1]}]"),
                "an object with no id names no profession");
        assertEquals(List.of("a:b"),
                read("[{\"id\":\"a:b\"},null,7]").stream().map(TradeProfessionEntry::getId).toList());
    }

    @Test
    void nullIsAnEmptyList() {
        assertEquals(List.of(), read("null"));
    }
}
