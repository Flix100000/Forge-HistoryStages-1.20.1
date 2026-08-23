package net.bananemdnsa.historystages.data.settings;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The new field kind stores exactly like a string, which is the whole reason it was added as a
 * kind rather than as a new storage shape: the read, the write and the sync are all paths that
 * already existed.
 */
class CustomScreenFieldTest {

    private static final Setting<String> LAYOUT =
            Setting.customScreen("layout").defaultValue("{}").langKey("settings.test.layout").build();

    @Test
    void aValueStoresAndReadsBackLikeAString() {
        SettingsValues values = SettingsValues.read(List.of(LAYOUT), null);
        values.set(LAYOUT, "{\"rows\":3}");

        SettingsValues reloaded = SettingsValues.read(List.of(LAYOUT), values.write());

        assertEquals("{\"rows\":3}", reloaded.get(LAYOUT));
    }

    @Test
    void anAbsentValueFallsBackToTheDeclaredDefault() {
        assertEquals("{}", SettingsValues.read(List.of(LAYOUT), null).get(LAYOUT));
    }

    @Test
    void aStoredValueOfTheWrongShapeFallsBackRatherThanThrowing() {
        JsonElement wrong = JsonParser.parseString("{\"layout\": 42}");

        assertEquals("{}", SettingsValues.read(List.of(LAYOUT), wrong).get(LAYOUT));
    }

    @Test
    void theKindIsWhatDistinguishesItFromPlainText() {
        assertEquals(SettingKind.CUSTOM_SCREEN, LAYOUT.kind());
    }
}
