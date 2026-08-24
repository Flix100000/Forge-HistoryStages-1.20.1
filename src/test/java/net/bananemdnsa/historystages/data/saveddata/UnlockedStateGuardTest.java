package net.bananemdnsa.historystages.data.saveddata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the unlocked sets mutable from one place, because a version counter is only as good as
 * that.
 *
 * <p>Since Phase 10 the player's unlocked stages are also held as a bitmask, rebuilt when a
 * counter beside the data changes. A counter was chosen over a notification precisely because it
 * cannot be forgotten — but only while every write goes through the class that owns it. One
 * {@code SERVER_CACHE.add(...)} from somewhere else and the mask keeps answering from before the
 * unlock, for that player, until something unrelated happens to bump the counter.
 *
 * <p>That is not hypothetical. The research pedestal cleared and refilled the global set itself;
 * it now calls {@code StageData.replaceCache} instead, and this test is what stops the next one.
 *
 * <p>Reading is free — {@code contains}, {@code get}, {@code isEmpty} and friends change nothing.
 */
class UnlockedStateGuardTest {

    private static final Pattern MUTATION = Pattern.compile(
            "(StageData|IndividualStageData)\\.SERVER_CACHE\\s*\\.\\s*"
                    + "(add|addAll|remove|removeAll|removeIf|clear|put|putAll|putIfAbsent|"
                    + "computeIfAbsent|compute|merge|retainAll)\\b");

    /** The two classes that own the sets, and are therefore allowed to change them. */
    private static final List<String> OWNERS =
            List.of("/data/saveddata/StageData.java", "/data/saveddata/IndividualStageData.java");

    @Test
    void onlyTheSavedDataClassesChangeTheUnlockedSets() throws IOException {
        Path main = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(main), "expected the test to run from the project root");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String unixPath = file.toString().replace('\\', '/');
                if (OWNERS.stream().anyMatch(unixPath::endsWith)) continue;

                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.trim().startsWith("*") || line.trim().startsWith("//")) continue;
                    if (MUTATION.matcher(line).find()) {
                        offenders.add(unixPath + ":" + (i + 1) + " -> " + line.trim());
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "the unlocked sets may only be changed by the class that owns them, or the "
                        + "version counter behind the stage masks stops being trustworthy:\n"
                        + String.join("\n", offenders));
    }
}
