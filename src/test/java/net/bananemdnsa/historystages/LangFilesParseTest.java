package net.bananemdnsa.historystages;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped lang file has to be valid JSON.
 *
 * <p>Minecraft does not complain about one that is not — it just fails to load it, and the whole
 * screen falls back to showing raw translation keys. That is a long way from the actual mistake,
 * which was a single unescaped quotation mark inside one value, so the fault is worth catching
 * here instead of in game.
 *
 * <p>Checks every language, not only the two that are maintained by hand: a broken file is broken
 * whoever wrote it.
 */
class LangFilesParseTest {

    private static final Path LANG_DIR =
            Path.of("src", "main", "resources", "assets", "historystages", "lang");

    @Test
    void everyLangFileIsValidJson() throws IOException {
        assertTrue(Files.isDirectory(LANG_DIR), "expected to run from the project root");

        List<String> broken = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> found = Files.list(LANG_DIR)) {
            files = found.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(!files.isEmpty(), "no lang files found — has the path changed?");

        Gson gson = new Gson();
        for (Path file : files) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject parsed = gson.fromJson(reader, JsonObject.class);
                if (parsed == null || parsed.isEmpty()) {
                    broken.add(file.getFileName() + ": parsed to nothing");
                }
            } catch (JsonSyntaxException | IOException e) {
                broken.add(file.getFileName() + ": " + e.getMessage());
            }
        }

        assertTrue(broken.isEmpty(),
                "these lang files do not parse, so Minecraft would silently show raw keys:\n"
                        + String.join("\n", broken));
    }

    /**
     * The two maintained languages should describe the same set of keys. A key in one and not the
     * other shows up as a raw key for half the players and nowhere in testing.
     */
    @Test
    void germanAndEnglishCoverTheSameKeys() throws IOException {
        Gson gson = new Gson();
        JsonObject en;
        JsonObject de;
        try (Reader r = Files.newBufferedReader(LANG_DIR.resolve("en_us.json"))) {
            en = gson.fromJson(r, JsonObject.class);
        }
        try (Reader r = Files.newBufferedReader(LANG_DIR.resolve("de_de.json"))) {
            de = gson.fromJson(r, JsonObject.class);
        }

        List<String> missing = new ArrayList<>();
        for (String key : en.keySet()) {
            if (!de.has(key)) missing.add("de_de is missing " + key);
        }
        for (String key : de.keySet()) {
            if (!en.has(key)) missing.add("en_us is missing " + key);
        }

        assertTrue(missing.isEmpty(), String.join("\n", missing));
    }
}
