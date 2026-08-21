package net.bananemdnsa.historystages.data.lock.engine;

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
 * Guards the lock-check seam: lock questions must go through {@code StageLocks.engine()}, never
 * straight to {@code StageManager}. Without this the seam quietly leaks again over the phases
 * that follow, and swapping in a different lock engine stops being a one-line change.
 *
 * <p>Editor and command sources are exempt — they read the stage maps to display and edit them,
 * which is a different concern from asking whether something is locked.
 */
class SeamGuardTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "StageManager\\.(getAllStagesFor|getAllIndividualStagesFor|getAllStagesWithSpawnlockEntry"
            + "|globalStageCandidates|individualStageCandidates|isItemLocked|isRecipeIdLocked"
            + "|anyStageHas)");

    private static final List<String> EXEMPT_PATH_PARTS = List.of(
            "/data/lock/engine/",
            "/data/StageManager.java",
            "/client/editor/",
            "/commands/");

    @Test
    void noLockCheckBypassesTheEngine() throws IOException {
        Path mainJava = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(mainJava), "expected the test to run from the project root");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mainJava)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String unixPath = file.toString().replace('\\', '/');
                if (EXEMPT_PATH_PARTS.stream().anyMatch(unixPath::contains)) continue;

                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.trim().startsWith("*")) continue; // javadoc reference, not a call
                    if (FORBIDDEN.matcher(line).find()) {
                        offenders.add(unixPath + ":" + (i + 1) + " -> " + line.trim());
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "these lock checks bypass StageLocks.engine():\n" + String.join("\n", offenders));
    }
}
