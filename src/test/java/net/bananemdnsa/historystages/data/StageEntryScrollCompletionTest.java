package net.bananemdnsa.historystages.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StageEntryScrollCompletionTest {

    @Test
    void anUnsetCompletionStaysOutOfTheJson() {
        StageEntry entry = new StageEntry();
        entry.setDisplayName("Bronze Age");
        assertFalse(entry.toJson().contains("scroll_completion"));
    }

    @Test
    void aSetValueSurvivesTheJsonRoundTrip() {
        StageEntry entry = new StageEntry();
        entry.setDisplayName("Bronze Age");
        entry.setScrollCompletion("open");
        JsonObject json = JsonParser.parseString(entry.toJson()).getAsJsonObject();
        assertEquals("open", json.get("scroll_completion").getAsString());
    }

    @Test
    void clearingTheOverrideRemovesItAgain() {
        // The editor writes an empty string when the user picks the config value back,
        // and that has to leave the file as if no override had ever been set.
        StageEntry entry = new StageEntry();
        entry.setDisplayName("Bronze Age");
        entry.setScrollCompletion("replace");
        entry.setScrollCompletion("");
        assertEquals("", entry.getScrollCompletion());
        assertFalse(entry.toJson().contains("scroll_completion"));
    }

    // "scroll_completion" is also added to StageManager.KNOWN_KEYS, so the loader does not
    // report it as a typo. That is not asserted here: StageManager pulls in
    // net.minecraft.world.level.Level through its static fields, which is not on the
    // plain-unit-test classpath, so the class cannot even be loaded from a test. It is
    // covered by loading a stage that uses the key in game.
}
