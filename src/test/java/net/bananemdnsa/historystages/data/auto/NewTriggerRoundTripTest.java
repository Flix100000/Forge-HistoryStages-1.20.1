package net.bananemdnsa.historystages.data.auto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.auto.conditions.DayCountTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EffectTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StatCategory;
import net.bananemdnsa.historystages.data.auto.conditions.StatTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TimeOfDayTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TimePreset;
import net.bananemdnsa.historystages.data.auto.conditions.WeatherState;
import net.bananemdnsa.historystages.data.auto.conditions.WeatherTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.XpLevelTrigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The six player-state and world-state triggers, written out and read back.
 *
 * <p>A trigger that survives this is one the editor can save without changing what it means. The
 * identity check matters as much as the field check: the signature is what player progress is
 * stored against, so a round trip that alters it would silently reset everyone's progress.
 */
class NewTriggerRoundTripTest {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(AutoTrigger.class, new AutoTriggerAdapter())
            .create();

    @AfterEach
    void reset() {
        TriggerTypes.resetForTesting();
    }

    private static TriggerCondition roundTrip(TriggerCondition original) {
        AutoTrigger wrapped = new AutoTrigger("any", List.of(original));
        AutoTrigger parsed = GSON.fromJson(GSON.toJson(wrapped, AutoTrigger.class), AutoTrigger.class);
        assertEquals(1, parsed.getTriggers().size(),
                "the trigger was dropped somewhere between writing and reading");
        return parsed.getTriggers().get(0);
    }

    private static JsonObject writtenTrigger(TriggerCondition original) {
        AutoTrigger wrapped = new AutoTrigger("any", List.of(original));
        return GSON.toJsonTree(wrapped, AutoTrigger.class).getAsJsonObject()
                .getAsJsonArray("triggers").get(0).getAsJsonObject();
    }

    @Test
    void everyNewTypeIsRegistered() {
        List<String> types = TriggerTypes.allTypes();

        assertTrue(types.contains("stat"));
        assertTrue(types.contains("xp_level"));
        assertTrue(types.contains("effect"));
        assertTrue(types.contains("weather"));
        assertTrue(types.contains("day_count"));
        assertTrue(types.contains("world_time"));
    }

    @Test
    void aStatTriggerSurvives() {
        StatTrigger original = new StatTrigger(StatCategory.USED.serialize(), "minecraft:bow", 25);
        StatTrigger read = assertInstanceOf(StatTrigger.class, roundTrip(original));

        assertEquals(StatCategory.USED, read.resolvedCategory());
        assertEquals("minecraft:bow", read.id());
        assertEquals(25, read.requiredCount());
        assertEquals(original.signature(), read.signature());
    }

    @Test
    void anXpLevelTriggerSurvives() {
        XpLevelTrigger original = new XpLevelTrigger(30);
        XpLevelTrigger read = assertInstanceOf(XpLevelTrigger.class, roundTrip(original));

        assertEquals(30, read.requiredLevel());
        assertEquals(original.signature(), read.signature());
    }

    @Test
    void anEffectTriggerSurvives() {
        EffectTrigger original = new EffectTrigger("minecraft:blindness");
        EffectTrigger read = assertInstanceOf(EffectTrigger.class, roundTrip(original));

        assertEquals("minecraft:blindness", read.id());
        assertEquals(original.signature(), read.signature());
    }

    @Test
    void aWeatherTriggerSurvives() {
        WeatherTrigger original = new WeatherTrigger(WeatherState.THUNDER.serialize());
        WeatherTrigger read = assertInstanceOf(WeatherTrigger.class, roundTrip(original));

        assertEquals(WeatherState.THUNDER, read.resolvedState());
        assertEquals(original.signature(), read.signature());
    }

    @Test
    void aDayCountTriggerSurvives() {
        DayCountTrigger original = new DayCountTrigger(7);
        DayCountTrigger read = assertInstanceOf(DayCountTrigger.class, roundTrip(original));

        assertEquals(7, read.requiredDays());
        assertEquals(original.signature(), read.signature());
    }

    @Test
    void aWorldTimePresetSurvives() {
        TimeOfDayTrigger original = TimeOfDayTrigger.of(TimePreset.NIGHT);
        TimeOfDayTrigger read = assertInstanceOf(TimeOfDayTrigger.class, roundTrip(original));

        assertEquals(TimePreset.NIGHT, read.resolvedPreset());
        assertEquals(13000, read.windowFrom());
        assertEquals(22999, read.windowTo());
        assertEquals(original.signature(), read.signature());
    }

    /** A preset carries no window of its own, so none should reach the file. */
    @Test
    void aWorldTimePresetWritesNoWindowFields() {
        JsonObject written = writtenTrigger(TimeOfDayTrigger.of(TimePreset.NIGHT));

        assertEquals("world_time", written.get("type").getAsString());
        assertEquals("night", written.get("preset").getAsString());
        assertFalse(written.has("from"), "a preset wrote a 'from' that means nothing");
        assertFalse(written.has("to"), "a preset wrote a 'to' that means nothing");
    }

    @Test
    void aCustomWorldTimeWindowSurvives() {
        TimeOfDayTrigger original = TimeOfDayTrigger.custom(22000, 2000);
        TimeOfDayTrigger read = assertInstanceOf(TimeOfDayTrigger.class, roundTrip(original));

        assertEquals(TimePreset.CUSTOM, read.resolvedPreset());
        assertEquals(22000, read.windowFrom());
        assertEquals(2000, read.windowTo());
        assertEquals(original.signature(), read.signature());
    }
}
