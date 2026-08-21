package net.bananemdnsa.historystages.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StageEntryJsonTest {

    private static StageEntry sampleStage() {
        StageEntry entry = new StageEntry();
        entry.setDisplayName("Bronze Age");
        List<ItemEntry> items = new ArrayList<>();
        items.add(new ItemEntry("minecraft:iron_ingot"));
        items.add(new ItemEntry("minecraft:copper_ingot", null, List.of("use", "attack")));
        entry.setItemEntries(items);
        return entry;
    }

    @Test
    void compactJsonHasNoLineBreaks() {
        assertFalse(sampleStage().toCompactJson().contains("\n"));
    }

    @Test
    void fileJsonStaysPrettyPrinted() {
        assertTrue(sampleStage().toJson().contains("\n"));
    }

    @Test
    void bothFormsCarryTheSameData() {
        StageEntry entry = sampleStage();
        assertEquals(
                JsonParser.parseString(entry.toJson()),
                JsonParser.parseString(entry.toCompactJson()));
    }

    /**
     * A stage the size the editor actually chokes on: the pretty-printed form of this one is
     * past the 65536 character limit of the save packet, the compact form has to fit.
     */
    private static StageEntry hugeStage() {
        StageEntry entry = new StageEntry();
        entry.setDisplayName("Huge Pack Stage");
        List<ItemEntry> items = new ArrayList<>();
        for (int i = 0; i < 581; i++) {
            // Every other item restricts single actions, which is what blows the line count up:
            // those turn into objects with one line per remaining unlock action.
            List<String> locked = (i % 2 == 0) ? List.of("use", "attack") : null;
            items.add(new ItemEntry("somemod:some_item_" + i, null, locked));
        }
        entry.setItemEntries(items);
        return entry;
    }

    @Test
    void compactJsonFitsThePacketLimitWherePrettyDoesNot() {
        StageEntry entry = hugeStage();
        assertTrue(entry.toJson().length() > 65536,
                "sample is meant to exceed the old limit when pretty-printed");
        assertTrue(entry.toCompactJson().length() < 65536,
                "compact form must fit into the save packet");
    }
}
