package net.bananemdnsa.historystages.data.settings;

import net.bananemdnsa.historystages.api.settings.Setting;

import net.bananemdnsa.historystages.api.settings.StageSettingsGroup;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.stage.StageScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageSettingsResolutionTest {

    private static final Setting<Integer> PRICE =
            Setting.integer("price").range(0, 64).defaultValue(12).langKey("l.price").build();

    private static StageEntry stageWithPrice30() {
        return new Gson().fromJson("""
                {"addon_settings": {"mymod:trades": {"price": 30}}}
                """, StageEntry.class);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        StageSettingsGroups.resetForTesting();
    }

    @Test
    void aRegisteredGroupReadsItsStoredValue() {
        StageSettingsGroups.register(StageSettingsGroup.builder("mymod:trades")
                .titleLangKey("l").field(PRICE).build());

        assertEquals(30, StageSettingsGroups
                .valuesOf("mymod:trades", stageWithPrice30(), StageScope.GLOBAL).get(PRICE));
    }

    @Test
    void anUnknownGroupYieldsDefaults() {
        assertEquals(12, StageSettingsGroups
                .valuesOf("nope:nope", stageWithPrice30(), StageScope.GLOBAL).get(PRICE));
    }

    @Test
    void aGroupThatDoesNotSupportTheScopeYieldsDefaults() {
        StageSettingsGroups.register(StageSettingsGroup.builder("mymod:trades")
                .titleLangKey("l").field(PRICE).supportedScopes(StageScope.GLOBAL).build());

        assertEquals(12, StageSettingsGroups
                .valuesOf("mymod:trades", stageWithPrice30(), StageScope.INDIVIDUAL).get(PRICE));
    }

    @Test
    void aNullStageYieldsDefaults() {
        StageSettingsGroups.register(StageSettingsGroup.builder("mymod:trades")
                .titleLangKey("l").field(PRICE).build());

        assertEquals(12, StageSettingsGroups
                .valuesOf("mymod:trades", null, StageScope.GLOBAL).get(PRICE));
    }
}
