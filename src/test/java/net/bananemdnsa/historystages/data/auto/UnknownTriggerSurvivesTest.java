package net.bananemdnsa.historystages.data.auto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.bananemdnsa.historystages.data.auto.conditions.UnknownTrigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A trigger whose mod is absent used to be dropped on read and gone on the next save, so editing
 * a stage without that mod destroyed it silently. These pin the behaviour that replaced it.
 */
class UnknownTriggerSurvivesTest {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(AutoTrigger.class, new AutoTriggerAdapter())
            .create();

    private static final String WITH_UNKNOWN = """
            {
              "mode": "any",
              "triggers": [
                {"type": "biome", "id": "minecraft:desert"},
                {"type": "mymod:relic_found", "relic": "amber", "count": 3}
              ]
            }
            """;

    @AfterEach
    void reset() {
        TriggerTypes.resetForTesting();
    }

    @Test
    void anUnknownTriggerIsKeptRatherThanDropped() {
        AutoTrigger parsed = GSON.fromJson(WITH_UNKNOWN, AutoTrigger.class);

        assertEquals(2, parsed.getTriggers().size(), "the unknown trigger was dropped");
        assertInstanceOf(UnknownTrigger.class, parsed.getTriggers().get(1));
        assertEquals("mymod:relic_found", parsed.getTriggers().get(1).type());
    }

    @Test
    void itIsWrittenBackWithEveryFieldThisBuildNeverUnderstood() {
        AutoTrigger parsed = GSON.fromJson(WITH_UNKNOWN, AutoTrigger.class);
        String written = GSON.toJson(parsed);

        assertTrue(written.contains("mymod:relic_found"), "the type vanished on save");
        assertTrue(written.contains("amber"), "a field of the unknown trigger was lost on save");
        assertTrue(written.contains("\"count\":3") || written.contains("\"count\": 3"),
                "a field of the unknown trigger was lost on save: " + written);
    }

    @Test
    void aKnownTriggerBesideItIsUnaffected() {
        AutoTrigger parsed = GSON.fromJson(WITH_UNKNOWN, AutoTrigger.class);

        TriggerCondition first = parsed.getTriggers().get(0);
        assertInstanceOf(BiomeTrigger.class, first);
        assertEquals("minecraft:desert", ((BiomeTrigger) first).id());
    }

    @Test
    void itSurvivesRepeatedLoadAndSave() {
        String once = GSON.toJson(GSON.fromJson(WITH_UNKNOWN, AutoTrigger.class));
        String twice = GSON.toJson(GSON.fromJson(once, AutoTrigger.class));

        assertEquals(once, twice, "the trigger degraded on the second round trip");
        assertTrue(twice.contains("amber"));
    }

    @Test
    void twoDifferentUnknownTriggersDoNotShareAnIdentity() {
        AutoTrigger parsed = GSON.fromJson("""
                {"triggers": [
                  {"type": "mymod:a", "v": 1},
                  {"type": "mymod:a", "v": 2}
                ]}
                """, AutoTrigger.class);

        assertNotEquals(parsed.getTriggers().get(0).signature(),
                parsed.getTriggers().get(1).signature(),
                "progress is stored against the signature, so these must differ");
    }

    @Test
    void aRegisteredTypeIsParsedProperlyInsteadOfKept() {
        TriggerTypes.register("mymod:mirror_biome", BiomeTrigger.class);

        AutoTrigger parsed = GSON.fromJson(
                "{\"triggers\": [{\"type\": \"mymod:mirror_biome\", \"id\": \"minecraft:taiga\"}]}",
                AutoTrigger.class);

        assertInstanceOf(BiomeTrigger.class, parsed.getTriggers().get(0));
    }

    @Test
    void anUnnamespacedTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TriggerTypes.register("relic_found", BiomeTrigger.class));
    }

    @Test
    void aTypeAlreadyTakenIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TriggerTypes.register("biome", BiomeTrigger.class));
    }

    @Test
    void registeringAfterTheFreezeIsRejected() {
        TriggerTypes.freeze();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> TriggerTypes.register("mymod:toolate", BiomeTrigger.class));
        assertTrue(thrown.getMessage().contains("mymod:toolate"));
    }
}
