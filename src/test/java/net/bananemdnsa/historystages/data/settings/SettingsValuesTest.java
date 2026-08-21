package net.bananemdnsa.historystages.data.settings;

import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsValuesTest {

    private static final Setting<Boolean> HIDE =
            Setting.bool("hide").defaultValue(false).langKey("l.hide").build();
    private static final Setting<Integer> PRICE =
            Setting.integer("price").range(0, 64).defaultValue(12).langKey("l.price").build();
    private static final Setting<String> NOTE =
            Setting.text("note").defaultValue("").langKey("l.note").build();
    private static final Setting<String> MODE = Setting.choice("mode")
            .option("a", "l.a").option("b", "l.b").defaultValue("a").langKey("l.mode").build();

    private static final List<Setting<?>> FIELDS = List.of(HIDE, PRICE, NOTE, MODE);

    private static SettingsValues read(String json) {
        return SettingsValues.read(FIELDS, JsonParser.parseString(json));
    }

    @Test
    void anAbsentValueIsTheDefault() {
        SettingsValues v = read("{}");

        assertEquals(false, v.get(HIDE));
        assertEquals(12, v.get(PRICE));
        assertEquals("", v.get(NOTE));
        assertEquals("a", v.get(MODE));
    }

    @Test
    void nullJsonReadsAsAllDefaults() {
        SettingsValues v = SettingsValues.read(FIELDS, null);
        assertEquals(12, v.get(PRICE));
    }

    @Test
    void storedValuesAreReadBack() {
        SettingsValues v = read("""
                {"hide": true, "price": 30, "note": "hi", "mode": "b"}
                """);

        assertEquals(true, v.get(HIDE));
        assertEquals(30, v.get(PRICE));
        assertEquals("hi", v.get(NOTE));
        assertEquals("b", v.get(MODE));
    }

    @Test
    void aNumberOutsideItsRangeIsClamped() {
        assertEquals(64, read("{\"price\": 999}").get(PRICE));
        assertEquals(0, read("{\"price\": -5}").get(PRICE));
    }

    @Test
    void aTypeMismatchFallsBackToTheDefault() {
        assertEquals(12, read("{\"price\": \"lots\"}").get(PRICE));
        assertEquals(false, read("{\"hide\": 7}").get(HIDE));
    }

    @Test
    void anUndeclaredChoiceValueFallsBackToTheDefault() {
        assertEquals("a", read("{\"mode\": \"zzz\"}").get(MODE));
    }

    @Test
    void aMalformedObjectYieldsAllDefaults() {
        SettingsValues v = SettingsValues.read(FIELDS, JsonParser.parseString("\"not an object\""));
        assertEquals(12, v.get(PRICE));
    }

    @Test
    void defaultsAreNotWritten() {
        SettingsValues v = read("{}");
        assertNull(v.write(), "a group at its defaults must not leave an empty object behind");
    }

    @Test
    void onlyNonDefaultsAreWritten() {
        SettingsValues v = read("{\"hide\": true, \"price\": 12}");
        JsonObject out = v.write().getAsJsonObject();

        assertTrue(out.has("hide"));
        assertFalse(out.has("price"), "price equals its default and must not be written");
    }

    @Test
    void unknownKeysArePreserved() {
        SettingsValues v = read("{\"from_a_newer_version\": 5}");
        JsonObject out = v.write().getAsJsonObject();

        assertEquals(5, out.get("from_a_newer_version").getAsInt(),
                "an older addon version must not strip a newer one's data");
    }

    @Test
    void aGroupWithOnlyUnknownKeysKeepsItsBlock() {
        SettingsValues v = read("{\"from_a_newer_version\": 5}");
        assertTrue(v.write() != null && v.write().getAsJsonObject().has("from_a_newer_version"));
    }

    @Test
    void settingAValueMakesItReadableAndWritable() {
        SettingsValues v = read("{}").copy();
        v.set(PRICE, 40);

        assertEquals(40, v.get(PRICE));
        assertTrue(v.write().getAsJsonObject().has("price"));
    }

    @Test
    void settingAValueBackToItsDefaultDropsItFromTheOutput() {
        SettingsValues v = read("{\"price\": 40}").copy();
        v.set(PRICE, 12);

        assertNull(v.write());
    }

    @Test
    void copyDoesNotShareStateWithTheOriginal() {
        SettingsValues original = read("{}");
        SettingsValues copy = original.copy();
        copy.set(PRICE, 40);

        assertEquals(12, original.get(PRICE), "copy() handed out shared state");
    }

    @Test
    void anIntegerSetOutsideItsRangeIsClamped() {
        SettingsValues v = read("{}").copy();
        v.set(PRICE, 999);

        assertEquals(64, v.get(PRICE));
    }

    @Test
    void aFieldTheGroupDoesNotDeclareYieldsItsOwnDefault() {
        Setting<Integer> foreign =
                Setting.integer("foreign").range(0, 9).defaultValue(7).langKey("l.foreign").build();

        assertEquals(7, read("{}").get(foreign),
                "get() is documented never to return null; a handle from another group must "
                        + "fall back to its own default rather than hand out a null that "
                        + "explodes somewhere else");
    }

    @Test
    void longTextValuesRoundTripLikeTextIncludingMultiLine() {
        Setting<String> story = Setting.longText("story")
                .defaultValue("").langKey("l.story").build();

        String multiLine = "line one\nline two\nline three";
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("story", multiLine);
        SettingsValues v = SettingsValues.read(List.of(story), json);

        assertEquals(multiLine, v.get(story));
        assertEquals(multiLine, v.write().getAsJsonObject().get("story").getAsString());

        SettingsValues copy = v.copy();
        copy.set(story, "changed");
        assertEquals("changed", copy.get(story));
        assertEquals(multiLine, v.get(story), "copy() must not share state with the original");
    }

    @Test
    void itemValuesRoundTripAsIdStrings() {
        Setting<String> icon = Setting.item("icon")
                .defaultValue("minecraft:stone").langKey("l.icon").build();

        SettingsValues v = SettingsValues.read(List.of(icon),
                JsonParser.parseString("{\"icon\": \"minecraft:emerald\"}"));

        assertEquals("minecraft:emerald", v.get(icon));
        assertEquals("minecraft:emerald",
                v.write().getAsJsonObject().get("icon").getAsString());
    }
}
