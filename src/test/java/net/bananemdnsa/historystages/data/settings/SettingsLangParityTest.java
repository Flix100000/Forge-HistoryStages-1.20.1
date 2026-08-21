package net.bananemdnsa.historystages.data.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.demo.DemoSettingsGroup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demo settings group is the worked example addon authors are pointed at, and it is the
 * in-game test vehicle for this whole feature. If one of its lang keys is missing, its card would
 * render with a blank label instead of failing loudly — this test is what actually notices.
 *
 * <p>Modelled on {@code CategoryEditorParityTest}: read both lang files as plain text and check
 * for the quoted key, rather than parsing them into a translation table, so the check has no
 * dependency on Minecraft's lang-loading machinery.
 */
class SettingsLangParityTest {

    private static final Path EN_US = Path.of("src", "main", "resources", "assets",
            "historystages", "lang", "en_us.json");
    private static final Path DE_DE = Path.of("src", "main", "resources", "assets",
            "historystages", "lang", "de_de.json");

    @Test
    void everyDemoSettingsGroupLangKeyExistsInBothMaintainedLanguages() throws IOException {
        String en = Files.readString(EN_US);
        String de = Files.readString(DE_DE);

        StageSettingsGroup group = DemoSettingsGroup.build();

        List<String> keys = new ArrayList<>();
        keys.add(group.titleLangKey());
        for (Setting<?> field : group.fields()) {
            keys.add(field.langKey());
            if (field.hintLangKey() != null) keys.add(field.hintLangKey());
            for (String value : field.optionValues()) {
                keys.add(field.optionLangKey(value));
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
