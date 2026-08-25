package net.bananemdnsa.historystages.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every field a stage file may carry has to be listed as a known key.
 *
 * <p>{@code StageManager} warns about any top-level key it does not recognise and says the key
 * will be ignored. When the {@code addons} block was added, nobody added it to that list, so every
 * stage carrying addon data reported a typo and claimed the data would be dropped — alarming,
 * wrong, and exactly the sort of message that gets someone to delete the thing.
 *
 * <p>Reading the list by reflection rather than restating it is the point: a restated copy drifts,
 * which is how this happened in the first place.
 */
class KnownStageKeysTest {

    @Test
    void everyStageEntryFieldIsARecognisedKey() throws java.io.IOException {
        Set<String> knownKeys = knownKeysFromSource();

        List<String> unlisted = new ArrayList<>();
        for (Field field : StageEntry.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;

            SerializedName annotation = field.getAnnotation(SerializedName.class);
            String jsonKey = annotation != null ? annotation.value() : field.getName();

            if (!knownKeys.contains(jsonKey)) unlisted.add(jsonKey);
        }

        assertTrue(unlisted.isEmpty(),
                "these stage fields are written to disk but would be reported as unknown keys, "
                        + "telling the maintainer their data is ignored: " + unlisted);
    }

    /**
     * Read out of the source rather than off the class: loading {@code StageManager} pulls in its
     * Minecraft-typed fields, and the test runtime has no Minecraft on it.
     */
    private static Set<String> knownKeysFromSource() throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Path.of("src", "main", "java", "net",
                "bananemdnsa", "historystages", "data", "StageManager.java");
        assertTrue(java.nio.file.Files.exists(file), "expected to run from the project root");

        String source = java.nio.file.Files.readString(file);
        int start = source.indexOf("KNOWN_KEYS = Set.of(");
        assertTrue(start >= 0, "could not find KNOWN_KEYS in StageManager");
        String block = source.substring(start, source.indexOf(");", start));

        Set<String> keys = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([a-z_]+)\"").matcher(block);
        while (m.find()) keys.add(m.group(1));
        assertTrue(keys.size() > 5, "KNOWN_KEYS parsed to something implausible: " + keys);
        return keys;
    }
}
