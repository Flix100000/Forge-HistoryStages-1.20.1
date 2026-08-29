package net.bananemdnsa.historystages.data.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every setting that exists today must be reachable from {@link LegacyConfigMap}.
 *
 * <p>A key the table forgets is not a crash and not a warning. The migration simply never writes
 * it, the new file keeps its default, and the pack author finds out weeks later that one of their
 * settings quietly reset itself. So the check has to be mechanical rather than a promise that
 * somebody remembered.
 *
 * <p>{@code Config} is read as text rather than reflected over, because {@code ModConfigSpec} is
 * not on the test classpath — build.gradle keeps NeoForge out of the test source set, which is
 * also why {@link LegacyConfigMap} itself is written in plain strings. Same trick as
 * {@code ApiSurfaceGuardTest}.
 *
 * <p>Reading source text means the parser can silently go wrong, so it is held to a floor as well
 * as to the table: a parser that stops finding keys would otherwise pass by finding none.
 *
 * <p>Delete this alongside {@link LegacyConfigMap} in {@value LegacyConfigMap#REMOVE_IN}.
 */
class LegacyConfigMapCoverageTest {

    /** The two spec classes in Config, and the target each one is registered as. */
    private static final Pattern SPEC_START = Pattern.compile(
            "public\\s+(Visual|Gameplay)\\(ModConfigSpec\\.Builder");

    private static final Pattern PUSH = Pattern.compile("\\.push\\(\"([^\"]+)\"\\)");
    private static final Pattern POP = Pattern.compile("\\.pop\\(\\)");
    private static final Pattern DEFINE = Pattern.compile("\\.define[A-Za-z]*\\(\"([^\"]+)\"");

    /**
     * Floors, not exact counts: the table is meant to survive a key being added without anyone
     * touching this test, but a parser that has stopped working needs to fail rather than report
     * full coverage of nothing. At the time of writing the real numbers are 54 and 50.
     */
    private static final int MIN_VISUAL = 45;
    private static final int MIN_GAMEPLAY = 40;

    @Test
    void everyCurrentConfigKeyHasSomewhereToComeFrom() throws IOException {
        List<String> keys = currentKeys();

        long visual = keys.stream().filter(k -> k.startsWith("VISUAL|")).count();
        long gameplay = keys.stream().filter(k -> k.startsWith("GAMEPLAY|")).count();
        assertTrue(visual >= MIN_VISUAL && gameplay >= MIN_GAMEPLAY,
                "the Config.java parser found " + visual + " visual and " + gameplay
                        + " gameplay keys, which is far too few — the parser is broken, and a "
                        + "broken parser reports perfect coverage of nothing. Found: " + keys);

        Set<String> destinations = LegacyConfigMap.destinations();
        List<String> missing = keys.stream().filter(k -> !destinations.contains(k)).toList();

        assertTrue(missing.isEmpty(),
                "these config keys are in no LegacyConfigMap destination, so a pack updating from "
                        + "5.x silently loses whatever it had set for them: " + missing);
    }

    @Test
    void everyDestinationIsAKeyThatStillExists() throws IOException {
        Set<String> keys = new LinkedHashSet<>(currentKeys());
        List<String> stale = LegacyConfigMap.destinations().stream()
                .filter(destination -> !keys.contains(destination))
                .sorted()
                .toList();

        assertTrue(stale.isEmpty(),
                "these LegacyConfigMap destinations name no key in Config.java — either the key "
                        + "was renamed and the table was not, or the destination is a typo, and "
                        + "either way the value it carries lands nowhere: " + stale);
    }

    /** Every key the two specs declare, as {@code "<TARGET>|<dotted path>"}. */
    private static List<String> currentKeys() throws IOException {
        Path config = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages",
                "Config.java");
        assertTrue(Files.isRegularFile(config), "expected the test to run from the project root");

        List<String> keys = new ArrayList<>();
        String target = null;
        // A stack, not a counter: [notifications] pushes [individual] inside itself, and a
        // counter would have produced "individual.useSounds" for a key that lives two deep.
        Deque<String> blocks = new ArrayDeque<>();

        for (String line : Files.readString(config).split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("//")) continue;

            Matcher spec = SPEC_START.matcher(line);
            if (spec.find()) {
                target = spec.group(1).equals("Visual") ? "VISUAL" : "GAMEPLAY";
                blocks.clear();
                continue;
            }
            if (target == null) continue;

            Matcher push = PUSH.matcher(line);
            while (push.find()) blocks.addLast(push.group(1));

            Matcher define = DEFINE.matcher(line);
            if (define.find()) {
                keys.add(target + "|" + String.join(".", blocks) + "." + define.group(1));
            }

            // After the defines on this line: a pop only ever follows the keys it closed over.
            Matcher pop = POP.matcher(line);
            while (pop.find() && !blocks.isEmpty()) blocks.removeLast();
        }

        return keys;
    }
}
