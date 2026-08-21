package net.bananemdnsa.historystages.data.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.demo.DemoConfigSections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demo config sections are the worked example addon authors are pointed at, and they are the
 * in-game test vehicle for the common path in particular — a missing lang key on the common side
 * would otherwise first surface on somebody's real server. If one of its lang keys is missing, its
 * row would render with a blank label instead of failing loudly — this test is what actually
 * notices.
 *
 * <p>Modelled on {@code SettingsLangParityTest}: read both lang files as plain text and check for
 * the quoted key, rather than parsing them into a translation table, so the check has no
 * dependency on Minecraft's lang-loading machinery.
 */
class AddonConfigLangParityTest {

    private static final Path EN_US = Path.of("src", "main", "resources", "assets",
            "historystages", "lang", "en_us.json");
    private static final Path DE_DE = Path.of("src", "main", "resources", "assets",
            "historystages", "lang", "de_de.json");

    @Test
    void everyDemoConfigSectionLangKeyExistsInBothMaintainedLanguages() throws IOException {
        String en = Files.readString(EN_US);
        String de = Files.readString(DE_DE);

        List<AddonConfigSection> sections = DemoConfigSections.build();

        List<String> keys = new ArrayList<>();
        for (AddonConfigSection section : sections) {
            keys.add(section.titleLangKey());
            for (AddonConfigField field : section.fields()) {
                keys.add(field.labelLangKey());
                if (field.descLangKey() != null) keys.add(field.descLangKey());
                for (String value : field.optionValues()) {
                    keys.add(field.optionLangKey(value));
                }
            }
        }

        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (!en.contains('"' + key + '"')) missing.add("en_us: " + key);
            if (!de.contains('"' + key + '"')) missing.add("de_de: " + key);
        }

        assertTrue(missing.isEmpty(), "missing lang keys:\n" + String.join("\n", missing));
    }
}
