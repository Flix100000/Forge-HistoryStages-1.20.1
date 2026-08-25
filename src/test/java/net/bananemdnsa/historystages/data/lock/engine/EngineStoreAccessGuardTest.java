package net.bananemdnsa.historystages.data.lock.engine;

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
 * Guards what is left of the engine's reach into the stage store.
 *
 * <p>After Phase 8 the engine asks {@code StageManager} for exactly two things: the global stage
 * map and the individual one. Everything else it needs, it derives itself through the category
 * model. This test names those two and rejects the rest, because the way the old typed query
 * methods would grow back is one convenient accessor at a time — and each one on its own always
 * looks reasonable.
 *
 * <p>{@link SeamGuardTest} guards the other direction: that nobody outside the engine asks the
 * store a lock question. Together they close the seam from both sides.
 */
class EngineStoreAccessGuardTest {

    private static final Pattern CALL = Pattern.compile("StageManager\\.([a-zA-Z]+)");

    private static final List<String> ALLOWED = List.of("getStages", "getIndividualStages");

    @Test
    void theEngineOnlyAsksTheStoreForItsTwoStageMaps() throws IOException {
        Path engine = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages",
                "data", "lock", "engine");
        assertTrue(Files.isDirectory(engine), "expected the test to run from the project root");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(engine)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.trim().startsWith("*")) continue; // javadoc reference, not a call
                    Matcher matcher = CALL.matcher(line);
                    while (matcher.find()) {
                        if (ALLOWED.contains(matcher.group(1))) continue;
                        offenders.add(file.getFileName() + ":" + (i + 1) + " -> " + matcher.group());
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "the lock engine may only read the two stage maps from the store, "
                        + "everything else it derives itself:\n" + String.join("\n", offenders));
    }
}
