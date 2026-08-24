package net.bananemdnsa.historystages.client.editor.tab;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four built-in right-click actions exist and are translated.
 *
 * <p>§7.5 of the API design asks that a category may "pick from the built-in set (NBT /
 * lock-actions / dimension-filter / spawn-sources) or supply custom". The custom half shipped in
 * Phase 3; these factories are the other half, and without them an addon has to rebuild a popup
 * the mod already has.
 *
 * <p>The factories themselves cannot be exercised here: they build popups that read dimension and
 * registry data, and the test runtime has no Minecraft. So what is checked is that they are
 * declared and that every key they name is translated — a key present in only one language ships
 * a raw {@code editor.historystages...} string to half the users.
 */
class EntryActionFactoryTest {

    private static final Path SOURCE = Path.of("src", "main", "java", "net", "bananemdnsa",
            "historystages", "client", "editor", "tab", "EntryAction.java");

    private static final List<String> FACTORIES =
            List.of("editNbt", "dimensionFilter", "spawnSources", "interactionActions");

    private static final List<String> KEYS = List.of(
            "editor.historystages.context.edit_nbt",
            "editor.historystages.context.dimension_filter",
            "editor.historystages.context.spawn_sources",
            "editor.historystages.context.interaction_actions");

    @Test
    void everyBuiltInFactoryIsDeclared() throws IOException {
        assertTrue(Files.isRegularFile(SOURCE),
                "expected to find " + SOURCE + " — if EntryAction moved, move this guard with it,"
                        + " because a guard that cannot find its file passes for the wrong reason");

        String source = Files.readString(SOURCE);
        for (String name : FACTORIES) {
            assertTrue(source.contains("EntryAction " + name + "("),
                    "EntryAction." + name + " is gone. It is one of the four built-ins the design"
                            + " asks for; without it an addon has to rebuild a popup that exists.");
        }
    }

    @Test
    void everyBuiltInActionIsTranslated() throws IOException {
        Gson gson = new Gson();
        for (String language : List.of("en_us", "de_de")) {
            Path file = Path.of("src", "main", "resources", "assets", "historystages", "lang",
                    language + ".json");
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject lang = gson.fromJson(reader, JsonObject.class);
                for (String key : KEYS) {
                    assertTrue(lang.has(key), key + " is missing from " + language + ".json");
                }
            }
        }
    }
}
