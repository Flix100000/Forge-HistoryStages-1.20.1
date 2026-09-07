package net.bananemdnsa.historystages.data;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a gated trade looks like in a stage file. Hand-editing one is a supported way to work, so
 * the shape has to be readable and the reader has to survive what a person types.
 */
class TradeOfferEntryListAdapterTest {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(new TypeToken<List<TradeOfferEntry>>() {}.getType(),
                    new TradeOfferEntryListAdapter())
            .create();

    private static List<TradeOfferEntry> read(String json) {
        return GSON.fromJson(json, new TypeToken<List<TradeOfferEntry>>() {}.getType());
    }

    private static String write(List<TradeOfferEntry> entries) {
        return GSON.toJson(entries, new TypeToken<List<TradeOfferEntry>>() {}.getType());
    }

    @Test
    void aTradeReadsAsWhatItIs() {
        String json = write(List.of(new TradeOfferEntry("minecraft:librarian", 2,
                "minecraft:bookshelf", "minecraft:emerald", null)));

        assertEquals("[{\"merchant\":\"minecraft:librarian\",\"level\":2,"
                        + "\"gives\":\"minecraft:bookshelf\",\"takes\":[\"minecraft:emerald\"]}]",
                json);
    }

    @Test
    void bothPricesAreOneList() {
        String json = write(List.of(new TradeOfferEntry("minecraft:librarian", 3,
                "minecraft:enchanted_book", "minecraft:emerald", "minecraft:book")));

        assertTrue(json.contains("\"takes\":[\"minecraft:emerald\",\"minecraft:book\"]"),
                "a thing costs these items - two fields would read like two different questions");
    }

    @Test
    void whatWasWrittenReadsBackTheSame() {
        List<TradeOfferEntry> entries = List.of(
                new TradeOfferEntry("minecraft:librarian", 2,
                        "minecraft:bookshelf", "minecraft:emerald", null),
                new TradeOfferEntry("minecraft:wandering_trader", 1,
                        "minecraft:blue_dye", "minecraft:emerald", null));

        assertEquals(entries, read(write(entries)));
    }

    @Test
    void aCriterionSurvivesTheRoundTrip() {
        JsonObject nbt = new JsonObject();
        nbt.addProperty("tier", "gold");
        List<TradeOfferEntry> entries = List.of(new TradeOfferEntry("minecraft:librarian", 3,
                "minecraft:enchanted_book", "minecraft:emerald", "minecraft:book", nbt));

        List<TradeOfferEntry> back = read(write(entries));
        assertTrue(back.get(0).hasNbt());
        assertEquals("gold", back.get(0).nbt().get("tier").getAsString());
    }

    @Test
    void anAbsentLevelIsTheFirstOne() {
        List<TradeOfferEntry> entries =
                read("[{\"merchant\":\"mod:trader\",\"gives\":\"mod:thing\"}]");

        assertEquals(1, entries.get(0).level(),
                "a merchant with no levels is level 1, and so is an entry that does not say");
        assertNull(entries.get(0).takesAId());
    }

    @Test
    void aThirdPriceIsIgnoredRatherThanFatal() {
        List<TradeOfferEntry> entries = read("[{\"merchant\":\"a:b\",\"level\":1,"
                + "\"gives\":\"c:d\",\"takes\":[\"e:f\",\"g:h\",\"i:j\"]}]");

        assertEquals("e:f", entries.get(0).takesAId());
        assertEquals("g:h", entries.get(0).takesBId());
    }

    @Test
    void anEntryWithNothingToNameIsSkipped() {
        assertEquals(List.of(), read("[{\"level\":2}]"), "no merchant and no goods");
        assertEquals(List.of(), read("[{\"merchant\":\"a:b\"}]"), "nothing handed over");
        assertEquals(List.of(), read("[42,\"nonsense\"]"));
    }

    @Test
    void nullIsAnEmptyList() {
        assertEquals(List.of(), read("null"));
    }
}
