package net.bananemdnsa.historystages.data.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonConfigSectionsTest {

    private static AddonConfigField boolField(String key) {
        return AddonConfigField.bool(key)
                .labelLangKey("l." + key)
                .defaultValue("false")
                .read(() -> "false")
                .write(v -> { })
                .build();
    }

    private static AddonConfigSection section(String id, ConfigSide side) {
        return AddonConfigSection.builder(id)
                .titleLangKey("l." + id)
                .side(side)
                .field(boolField("enabled"))
                .build();
    }

    @BeforeEach
    @AfterEach
    void reset() {
        AddonConfigSections.resetForTesting();
    }

    @Test
    void aRegisteredSectionIsFindableById() {
        AddonConfigSections.register(section("mymod:trades", ConfigSide.CLIENT));

        assertEquals("mymod:trades", AddonConfigSections.byId("mymod:trades").id());
    }

    @Test
    void anUnknownIdReadsAsNull() {
        assertNull(AddonConfigSections.byId("nope:nope"));
    }

    @Test
    void allIsSortedByIdSoTheScreenOrderIsStable() {
        AddonConfigSections.register(section("zmod:z", ConfigSide.CLIENT));
        AddonConfigSections.register(section("amod:a", ConfigSide.CLIENT));

        assertEquals(List.of("amod:a", "zmod:z"),
                AddonConfigSections.all().stream().map(AddonConfigSection::id).toList());
    }

    @Test
    void forSideSplitsClientFromCommon() {
        AddonConfigSections.register(section("mymod:client", ConfigSide.CLIENT));
        AddonConfigSections.register(section("mymod:common", ConfigSide.COMMON));

        assertEquals(List.of("mymod:client"),
                AddonConfigSections.forSide(ConfigSide.CLIENT).stream()
                        .map(AddonConfigSection::id).toList());
        assertEquals(List.of("mymod:common"),
                AddonConfigSections.forSide(ConfigSide.COMMON).stream()
                        .map(AddonConfigSection::id).toList());
    }

    @Test
    void registeringTheSameIdTwiceIsRejected() {
        AddonConfigSections.register(section("mymod:trades", ConfigSide.CLIENT));

        assertThrows(IllegalArgumentException.class,
                () -> AddonConfigSections.register(section("mymod:trades", ConfigSide.CLIENT)));
    }

    @Test
    void registeringAfterFreezeIsRejected() {
        AddonConfigSections.freeze();

        assertThrows(IllegalStateException.class,
                () -> AddonConfigSections.register(section("mymod:trades", ConfigSide.CLIENT)));
    }

    @Test
    void theReadAndWriteCallbacksAreTheAddonsOwn() {
        AtomicReference<String> stored = new AtomicReference<>("false");
        AddonConfigField field = AddonConfigField.bool("enabled")
                .labelLangKey("l.enabled")
                .defaultValue("false")
                .read(stored::get)
                .write(stored::set)
                .build();

        field.write().accept("true");

        assertEquals("true", stored.get(), "the write callback did not reach the addon's own state");
        assertEquals("true", field.read().get());
    }

    @Test
    void anEnumFieldKeepsItsOptionsInDeclarationOrder() {
        AddonConfigField field = AddonConfigField.choice("mode")
                .option("easy", "l.easy")
                .option("hard", "l.hard")
                .labelLangKey("l.mode")
                .defaultValue("easy")
                .read(() -> "easy")
                .write(v -> { })
                .build();

        assertEquals(List.of("easy", "hard"), field.optionValues());
        assertEquals("l.hard", field.optionLangKey("hard"));
    }

    // --- build() validation ---

    @Test
    void anIdWithoutANamespaceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AddonConfigSection.builder("trades").titleLangKey("l").side(ConfigSide.CLIENT)
                        .field(boolField("enabled")).build());
    }

    @Test
    void theHistorystagesNamespaceIsReserved() {
        assertThrows(IllegalArgumentException.class,
                () -> AddonConfigSection.builder("historystages:trades").titleLangKey("l")
                        .side(ConfigSide.CLIENT).field(boolField("enabled")).build());
    }

    @Test
    void aMissingTitleLangKeyIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> AddonConfigSection.builder("mymod:trades").side(ConfigSide.CLIENT)
                        .field(boolField("enabled")).build());
    }

    @Test
    void aSectionWithoutFieldsIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> AddonConfigSection.builder("mymod:trades").titleLangKey("l")
                        .side(ConfigSide.CLIENT).build());
    }

    @Test
    void twoFieldsWithTheSameKeyAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AddonConfigSection.builder("mymod:trades").titleLangKey("l")
                        .side(ConfigSide.CLIENT)
                        .field(boolField("enabled")).field(boolField("enabled")).build());
    }

    @Test
    void aNumberDefaultOutsideItsBoundsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AddonConfigField.integer("count").range(0, 10)
                        .labelLangKey("l.count").defaultValue("99")
                        .read(() -> "0").write(v -> { }).build());
    }

    @Test
    void aFieldWithoutReadOrWriteIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> AddonConfigField.bool("enabled").labelLangKey("l").defaultValue("false").build());
    }

    @Test
    void aSectionThatNeverDeclaredItsSideIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> AddonConfigSection.builder("mymod:trades").titleLangKey("l")
                        .field(boolField("enabled")).build(),
                "side decides whether a value is written on the client or shipped to the "
                        + "server; there is no safe default to fall back on");
    }

    // --- publishable common entries ---

    @Test
    void onlyCommonSectionsContributePublishableEntries() {
        AddonConfigSections.register(section("mymod:client", ConfigSide.CLIENT));
        AddonConfigSections.register(section("mymod:common", ConfigSide.COMMON));

        assertEquals(List.of("mymod:common.enabled"),
                AddonConfigSections.commonEntries().stream()
                        .map(AddonConfigSections.CommonEntry::wireKey).toList(),
                "a client section's fields must never reach the synced registry, and a common "
                        + "section's must");
    }

    @Test
    void commonEntriesUseTheSharedWireKeyHelper() {
        AddonConfigField field = boolField("enabled");
        AddonConfigSection section = AddonConfigSection.builder("mymod:common")
                .titleLangKey("l").side(ConfigSide.COMMON).field(field).build();
        AddonConfigSections.register(section);

        assertEquals(AddonConfigSections.wireKey(section, field),
                AddonConfigSections.commonEntries().get(0).wireKey(),
                "commonEntries built its own key instead of using the shared helper; the config "
                        + "screen uses the helper, so the two would silently stop matching");
    }

    @Test
    void aPublishableEntryCarriesTheAddonsOwnCallbacks() {
        AtomicReference<String> stored = new AtomicReference<>("false");
        AddonConfigSections.register(AddonConfigSection.builder("mymod:common")
                .titleLangKey("l").side(ConfigSide.COMMON)
                .field(AddonConfigField.bool("enabled").labelLangKey("l.enabled")
                        .defaultValue("false").read(stored::get).write(stored::set).build())
                .build());

        AddonConfigSections.commonEntries().get(0).write().accept("true");

        assertEquals("true", stored.get());
    }

    // --- placeholders ---

    @Test
    void aFieldWithNoDeclaredPlaceholdersHasAnEmptyList() {
        AddonConfigField field = AddonConfigField.richText("message")
                .labelLangKey("l.message")
                .defaultValue("hi")
                .read(() -> "hi")
                .write(v -> { })
                .build();

        assertEquals(List.of(), field.placeholders());
    }

    @Test
    void declaredPlaceholdersKeepDeclarationOrder() {
        AddonConfigField field = AddonConfigField.richText("message")
                .placeholder("{player}")
                .placeholder("{stage}")
                .labelLangKey("l.message")
                .defaultValue("hi")
                .read(() -> "hi")
                .write(v -> { })
                .build();

        assertEquals(List.of("{player}", "{stage}"), field.placeholders());
    }

    @Test
    void aBlankPlaceholderIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AddonConfigField.richText("message")
                        .placeholder("  ")
                        .labelLangKey("l.message")
                        .defaultValue("hi")
                        .read(() -> "hi")
                        .write(v -> { })
                        .build());
    }

    @Test
    void aDuplicatePlaceholderIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AddonConfigField.richText("message")
                        .placeholder("{player}")
                        .placeholder("{player}")
                        .labelLangKey("l.message")
                        .defaultValue("hi")
                        .read(() -> "hi")
                        .write(v -> { })
                        .build());
    }
}
