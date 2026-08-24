package net.bananemdnsa.historystages.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every GameTest method that creates a stage removes it again, from a {@code finally}.
 *
 * <p>A leaked stage does not fail the test that leaked it. It fails whichever test runs next and
 * happens to look at the stage list, and the report then names a test that did nothing wrong — an
 * afternoon spent reading the wrong file.
 *
 * <p><strong>Checked per method, not per file.</strong> A file-level check was the first attempt
 * and was close to useless: the realistic mistake is one new test added beside nine correct ones,
 * and a file that already contains a {@code finally} somewhere would have passed. That is exactly
 * the case this has to catch.
 *
 * <p>Reads the sources as text, the way the other guards here do. This runs in the JUnit suite,
 * which has no Minecraft and cannot load a GameTest class at all.
 */
class GameTestCleanupGuardTest {

    private static final Path GAMETESTS = Path.of("src", "main", "java", "net", "bananemdnsa",
            "historystages", "gametest");

    /** A test method: the annotation, then a signature, then everything up to the next one. */
    private static final Pattern TEST_METHOD = Pattern.compile(
            "@GameTest\\b[^\\n]*\\n\\s*public static void (\\w+)\\(", Pattern.MULTILINE);

    @Test
    void everyTestThatCreatesAStageAlsoRemovesIt() throws IOException {
        assertTrue(Files.isDirectory(GAMETESTS),
                "expected to find " + GAMETESTS + " — if the gametests moved, move this guard with"
                        + " them, because a guard that cannot find its files passes for the wrong"
                        + " reason");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(GAMETESTS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                checkFile(file, Files.readString(file), offenders);
            }
        }

        assertTrue(offenders.isEmpty(),
                "these gametest methods do not clean up their stages:\n"
                        + String.join("\n", offenders)
                        + "\nA leaked stage fails the next test instead of this one, and the report"
                        + " names the wrong file.");
    }

    private static void checkFile(Path file, String source, List<String> offenders) {
        Matcher matcher = TEST_METHOD.matcher(source);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            names.add(matcher.group(1));
        }

        for (int i = 0; i < starts.size(); i++) {
            // Up to the next test, or to the end of the file for the last one. Helper methods after
            // the last test end up inside that slice, which is harmless: they do not create stages.
            int end = i + 1 < starts.size() ? starts.get(i + 1) : source.length();
            String body = source.substring(starts.get(i), end);

            boolean creates = body.contains("GameTestStages.global(")
                    || body.contains("GameTestStages.individual(");
            if (!creates) continue;

            String where = file.getFileName() + "#" + names.get(i);
            if (!body.contains("GameTestStages.removeAll()")) {
                offenders.add(where + " -> creates stages, never removes them");
            } else if (!body.contains("} finally {")) {
                offenders.add(where + " -> removes stages outside a finally, so a test that fails"
                        + " partway leaves them behind");
            }
        }
    }
}
