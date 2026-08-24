package net.bananemdnsa.historystages.client.editor.dep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the tab seam: the dependency editor must reach every requirement through a
 * {@code DependencyTab}, never through a branch on the requirement's id.
 *
 * <p>The screen dispatched on eight ids by hand once. A ninth branch added straight into
 * {@code renderTabContent} would compile, work, and quietly be a requirement whose UI an addon
 * cannot replace — which is the thing Phase 3b removed. Nothing else would notice: the screen has
 * no behavioural test and cannot have one, because the test runtime has no Minecraft.
 *
 * <p>Reads the source as text, like {@code DependencySeamGuardTest} and {@code SeamGuardTest}.
 */
class DependencyTabSeamGuardTest {

    /** The eight built-in requirement ids, as they would appear in a switch arm. */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "case\\s+\"(item|stage|individual_stage|advancement|xp_level|entity_kill|stat|scoreboard)\"");

    private static final Path SCREEN = Path.of("src", "main", "java", "net", "bananemdnsa",
            "historystages", "client", "editor", "DependencyEditorScreen.java");

    @Test
    void noBuiltInRequirementIsDispatchedByName() throws IOException {
        assertTrue(Files.isRegularFile(SCREEN),
                "expected to find " + SCREEN + " — if the screen moved, move this guard with it,"
                        + " because a guard that cannot find its file passes for the wrong reason");

        List<String> offenders = new ArrayList<>();
        List<String> lines = Files.readAllLines(SCREEN);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().startsWith("*")) continue; // javadoc reference, not a branch
            if (FORBIDDEN.matcher(line).find()) {
                offenders.add("DependencyEditorScreen.java:" + (i + 1) + " -> " + line.trim());
            }
        }

        assertTrue(offenders.isEmpty(),
                "these requirements are dispatched by id inside the screen instead of through a"
                        + " DependencyTab:\n" + String.join("\n", offenders)
                        + "\nA requirement the screen draws itself is a requirement whose editor"
                        + " an addon cannot replace, which is what Phase 3b removed.");
    }
}
