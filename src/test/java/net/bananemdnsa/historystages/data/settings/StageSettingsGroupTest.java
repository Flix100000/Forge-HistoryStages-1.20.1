package net.bananemdnsa.historystages.data.settings;

import net.bananemdnsa.historystages.api.settings.SettingsValues;

import net.bananemdnsa.historystages.api.settings.Setting;

import net.bananemdnsa.historystages.api.settings.StageSettingsGroup;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.stage.StageScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageSettingsGroupTest {

    private static final Setting<Integer> PRICE =
            Setting.integer("price").range(0, 64).defaultValue(12).langKey("l.price").build();

    private static StageSettingsGroup group() {
        return StageSettingsGroup.builder("mymod:trades")
                .titleLangKey("l.title").field(PRICE).build();
    }

    @Test
    void bothScopesAreSupportedByDefault() {
        assertTrue(group().supportedScopes().contains(StageScope.GLOBAL));
        assertTrue(group().supportedScopes().contains(StageScope.INDIVIDUAL));
    }

    @Test
    void aDeclaredScopeNarrowsIt() {
        StageSettingsGroup g = StageSettingsGroup.builder("mymod:trades")
                .titleLangKey("l.title").field(PRICE)
                .supportedScopes(StageScope.GLOBAL).build();

        assertTrue(g.supportedScopes().contains(StageScope.GLOBAL));
        assertFalse(g.supportedScopes().contains(StageScope.INDIVIDUAL));
    }

    @Test
    void loadReadsFromTheStagesSettingsBlock() {
        StageEntry stage = new Gson().fromJson("""
                {"addon_settings": {"mymod:trades": {"price": 30}}}
                """, StageEntry.class);

        assertEquals(30, group().load(stage, StageScope.GLOBAL).get(PRICE));
    }

    @Test
    void loadOnAStageWithoutTheGroupYieldsDefaults() {
        assertEquals(12, group().load(new StageEntry(), StageScope.GLOBAL).get(PRICE));
    }

    @Test
    void storeWritesIntoTheStagesSettingsBlock() {
        StageEntry stage = new StageEntry();
        SettingsValues v = group().load(stage, StageScope.GLOBAL).copy();
        v.set(PRICE, 40);

        group().store(stage, v);

        assertEquals(40, group().load(stage, StageScope.GLOBAL).get(PRICE));
        assertTrue(stage.toJson().contains("addon_settings"));
    }

    @Test
    void storingAllDefaultsRemovesTheGroupFromTheStage() {
        StageEntry stage = new Gson().fromJson("""
                {"addon_settings": {"mymod:trades": {"price": 30}}}
                """, StageEntry.class);

        SettingsValues v = group().load(stage, StageScope.GLOBAL).copy();
        v.set(PRICE, 12);
        group().store(stage, v);

        assertFalse(stage.toJson().contains("addon_settings"),
                "a group back at its defaults should leave no block behind");
    }

    @Test
    void storeNeverTouchesAnotherGroupsBlock() {
        StageEntry stage = new Gson().fromJson("""
                {"addon_settings": {"other:group": {"x": 1}, "mymod:trades": {"price": 30}}}
                """, StageEntry.class);

        SettingsValues v = group().load(stage, StageScope.GLOBAL).copy();
        v.set(PRICE, 40);
        group().store(stage, v);

        assertTrue(stage.toJson().contains("other:group"),
                "an uninstalled group's data must survive another group being saved");
    }

    @Test
    void theMapOverloadIgnoresAGroupTheMapDoesNotMention() {
        StageEntry stage = new Gson().fromJson("""
                {"addon_settings": {"mymod:trades": {"price": 30}}}
                """, StageEntry.class);

        group().store(stage, java.util.Map.of());

        assertEquals(30, group().load(stage, StageScope.GLOBAL).get(PRICE),
                "a group the editor never showed must not wipe its own block");
    }

    @Test
    void theMapOverloadStoresTheEntryItFinds() {
        StageEntry stage = new StageEntry();
        SettingsValues v = group().load(stage, StageScope.GLOBAL).copy();
        v.set(PRICE, 40);

        group().store(stage, java.util.Map.of("mymod:trades", v));

        assertEquals(40, group().load(stage, StageScope.GLOBAL).get(PRICE));
    }

    @Test
    void aGlobalOnlyFieldIsAbsentFromAnIndividualReadAndYieldsItsDefault() {
        Setting<Integer> globalOnlyPrice = Setting.integer("price").range(0, 64).defaultValue(12)
                .langKey("l.price").supportedScopes(StageScope.GLOBAL).build();
        StageSettingsGroup g = StageSettingsGroup.builder("mymod:trades")
                .titleLangKey("l.title").field(globalOnlyPrice).build();

        StageEntry stage = new Gson().fromJson("""
                {"addon_settings": {"mymod:trades": {"price": 30}}}
                """, StageEntry.class);

        assertEquals(12, g.load(stage, StageScope.INDIVIDUAL).get(globalOnlyPrice),
                "a global-only field must fall back to its default on an individual read");
    }

    @Test
    void aGlobalOnlyFieldsStoredValueSurvivesALoadThenStoreRoundTripOnAnIndividualStage() {
        Setting<Integer> globalOnlyPrice = Setting.integer("price").range(0, 64).defaultValue(12)
                .langKey("l.price").supportedScopes(StageScope.GLOBAL).build();
        StageSettingsGroup g = StageSettingsGroup.builder("mymod:trades")
                .titleLangKey("l.title").field(globalOnlyPrice).build();

        StageEntry stage = new Gson().fromJson("""
                {"addon_settings": {"mymod:trades": {"price": 30}}}
                """, StageEntry.class);

        // The editor only ever sees the individual scope here, so the field it reads is not
        // "price" at all — it never claims that JSON key, which is exactly what must keep the
        // stored value alive across a save the field itself never touches.
        SettingsValues individualView = g.load(stage, StageScope.INDIVIDUAL).copy();
        g.store(stage, individualView);

        assertEquals(30, g.load(stage, StageScope.GLOBAL).get(globalOnlyPrice),
                "a global-only field's stored value must survive being loaded and re-stored "
                        + "from a scope that never sees it");
    }

    @Test
    void anIdWithoutANamespaceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> StageSettingsGroup.builder("trades").titleLangKey("l").field(PRICE).build());
    }

    @Test
    void theHistorystagesNamespaceIsReserved() {
        assertThrows(IllegalArgumentException.class,
                () -> StageSettingsGroup.builder("historystages:trades")
                        .titleLangKey("l").field(PRICE).build());
    }

    @Test
    void aMissingTitleLangKeyIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> StageSettingsGroup.builder("mymod:trades").field(PRICE).build());
    }

    @Test
    void aGroupWithoutFieldsIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> StageSettingsGroup.builder("mymod:trades").titleLangKey("l").build());
    }

    @Test
    void twoFieldsWithTheSameKeyAreRejected() {
        Setting<Integer> clash =
                Setting.integer("price").range(0, 9).defaultValue(1).langKey("l2").build();

        assertThrows(IllegalArgumentException.class,
                () -> StageSettingsGroup.builder("mymod:trades")
                        .titleLangKey("l").field(PRICE).field(clash).build());
    }

    @Test
    void supportingNoScopeAtAllIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> StageSettingsGroup.builder("mymod:trades")
                        .titleLangKey("l").field(PRICE).supportedScopes().build());
    }

    @Test
    void fieldsAreExposedInDeclarationOrder() {
        Setting<Boolean> hide =
                Setting.bool("hide").defaultValue(false).langKey("l.hide").build();

        StageSettingsGroup g = StageSettingsGroup.builder("mymod:trades")
                .titleLangKey("l").field(PRICE).field(hide).build();

        assertEquals(java.util.List.of("price", "hide"),
                g.fields().stream().map(Setting::key).toList());
    }
}
