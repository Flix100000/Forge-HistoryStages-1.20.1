package net.bananemdnsa.historystages.data.settings;

import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingTest {

    @Test
    void boolSettingCarriesKeyDefaultAndLangKey() {
        Setting<Boolean> s = Setting.bool("hide").defaultValue(true).langKey("l.hide").build();

        assertEquals("hide", s.key());
        assertEquals(SettingKind.BOOL, s.kind());
        assertEquals(Boolean.TRUE, s.defaultValue());
        assertEquals("l.hide", s.langKey());
    }

    @Test
    void integerSettingKeepsItsRange() {
        Setting<Integer> s = Setting.integer("n").range(0, 10).defaultValue(4).langKey("l.n").build();

        assertEquals(0, s.min());
        assertEquals(10, s.max());
    }

    @Test
    void choiceSettingKeepsItsOptionsInOrder() {
        Setting<String> s = Setting.choice("m")
                .option("a", "l.a").option("b", "l.b")
                .defaultValue("a").langKey("l.m").build();

        assertEquals(java.util.List.of("a", "b"), s.optionValues());
        assertEquals("l.b", s.optionLangKey("b"));
    }

    @Test
    void itemSettingHoldsAnItemIdString() {
        Setting<String> s = Setting.item("icon")
                .defaultValue("minecraft:stone").langKey("l.icon").build();

        assertEquals(SettingKind.ITEM, s.kind());
        assertEquals("minecraft:stone", s.defaultValue());
    }

    @Test
    void longTextSettingCarriesItsHintAndPlaceholdersInDeclarationOrder() {
        Setting<String> s = Setting.longText("note")
                .defaultValue("").langKey("l.note")
                .hintLangKey("l.note.hint")
                .placeholder("{player}")
                .placeholder("{stage}")
                .build();

        assertEquals(SettingKind.LONG_TEXT, s.kind());
        assertEquals("l.note.hint", s.hintLangKey());
        assertEquals(java.util.List.of("{player}", "{stage}"), s.placeholders());
    }

    @Test
    void longTextPlaceholdersAreEmptyAndHintIsNullByDefault() {
        Setting<String> s = Setting.longText("note").defaultValue("").langKey("l.note").build();

        assertTrue(s.placeholders().isEmpty());
        assertNull(s.hintLangKey());
    }

    @Test
    void aBlankPlaceholderIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Setting.longText("note").defaultValue("").langKey("l.note").placeholder("  "));
    }

    @Test
    void aDuplicatePlaceholderIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Setting.longText("note").defaultValue("").langKey("l.note")
                        .placeholder("{player}").placeholder("{player}"));
    }

    @Test
    void aBlankKeyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Setting.bool("  ").defaultValue(false).langKey("l").build());
    }

    @Test
    void aMissingLangKeyIsRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Setting.bool("hide").defaultValue(false).build());
        assertTrue(e.getMessage().contains("hide"), "the message must name the offending field");
    }

    @Test
    void aMissingDefaultIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> Setting.bool("hide").langKey("l").build());
    }

    @Test
    void anIntegerWithoutARangeIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> Setting.integer("n").defaultValue(1).langKey("l").build());
    }

    @Test
    void anIntegerDefaultOutsideItsRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Setting.integer("n").range(0, 5).defaultValue(9).langKey("l").build());
    }

    @Test
    void anInvertedRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Setting.integer("n").range(5, 0).defaultValue(1).langKey("l").build());
    }

    @Test
    void aChoiceWithoutOptionsIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> Setting.choice("m").defaultValue("a").langKey("l").build());
    }

    @Test
    void aChoiceDefaultThatIsNotAnOptionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Setting.choice("m").option("a", "l.a").defaultValue("z").langKey("l").build());
    }

    @Test
    void aDuplicateOptionValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Setting.choice("m").option("a", "l.a").option("a", "l.a2")
                        .defaultValue("a").langKey("l").build());
    }

    @Test
    void aSettingSupportsBothScopesByDefault() {
        Setting<Boolean> s = Setting.bool("hide").defaultValue(true).langKey("l.hide").build();

        assertTrue(s.supportedScopes().contains(StageScope.GLOBAL));
        assertTrue(s.supportedScopes().contains(StageScope.INDIVIDUAL));
    }

    @Test
    void aDeclaredScopeNarrowsIt() {
        Setting<Boolean> s = Setting.bool("hide").defaultValue(true).langKey("l.hide")
                .supportedScopes(StageScope.GLOBAL).build();

        assertTrue(s.supportedScopes().contains(StageScope.GLOBAL));
        assertFalse(s.supportedScopes().contains(StageScope.INDIVIDUAL));
    }

    @Test
    void declaringNoScopeAtAllIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Setting.bool("hide").defaultValue(true).langKey("l.hide").supportedScopes());
    }
}
