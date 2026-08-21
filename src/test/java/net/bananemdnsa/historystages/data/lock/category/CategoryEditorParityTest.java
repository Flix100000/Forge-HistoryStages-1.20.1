package net.bananemdnsa.historystages.data.lock.category;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The category registry is meant to be the one list of lock categories. This fails the moment the
 * editor's tabs and the registry disagree, which is the only way that claim quietly stops holding.
 *
 * <p>The tab-order half of this is gone: the editor no longer keeps its own array of tab keys,
 * it builds the strip from the registry, so the two cannot drift apart any more. What is still
 * worth checking is that every built-in category names a lang key that actually exists — nothing
 * else notices a typo there until a tab renders blank.
 */
class CategoryEditorParityTest {

    private static final Path SCREEN = Path.of("src", "main", "java", "net", "bananemdnsa",
            "historystages", "client", "editor", "StageDetailScreen.java");
    private static final Path EN_US = Path.of("src", "main", "resources", "assets",
            "historystages", "lang", "en_us.json");
    private static final Path DE_DE = Path.of("src", "main", "resources", "assets",
            "historystages", "lang", "de_de.json");

    @Test
    void everyBuiltInCategoryLangKeyExistsInBothMaintainedLanguages() throws IOException {
        String en = Files.readString(EN_US);
        String de = Files.readString(DE_DE);

        List<String> missing = new ArrayList<>();
        for (LockCategory<?> category : LockCategories.builtIns()) {
            for (String key : List.of(category.tabLangKey(), category.tooltipLangKey())) {
                if (!en.contains('"' + key + '"')) missing.add("en_us: " + key);
                if (!de.contains('"' + key + '"')) missing.add("de_de: " + key);
            }
        }

        assertTrue(missing.isEmpty(), "missing lang keys:\n" + String.join("\n", missing));
    }
}
