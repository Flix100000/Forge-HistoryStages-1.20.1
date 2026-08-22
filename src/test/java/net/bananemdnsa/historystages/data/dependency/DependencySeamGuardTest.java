package net.bananemdnsa.historystages.data.dependency;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the dependency seam: every kind of requirement a group can declare must be reached
 * through a {@code Requirement} view, never by naming the field on {@code DependencyGroup}.
 *
 * <p>The checker knew eight kinds by hand once, and a ninth block added straight into
 * {@code checkGroup} would compile, work, and quietly be a kind the axis cannot open to addons.
 * Nothing else would notice — there is no behavioural test on the checker at all.
 *
 * <p>Reading the source as text is deliberate, the same way {@code SeamGuardTest} does it: the
 * test runtime has no Minecraft, so loading the class is not an option.
 */
class DependencySeamGuardTest {

    /**
     * The eight accessors. A receiver dot is required so this reads calls rather than the
     * declarations themselves, and {@code player.} is excluded because {@code ServerPlayer} has
     * a {@code getStats()} of its own that has nothing to do with a dependency group.
     */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?<!player)\\.\\s*(getItems|getStages|getIndividualStages|getAdvancements"
            + "|getXpLevel|getEntityKills|getStats|getScoreboard)\\s*\\(");

    private static final Path CHECKER = Path.of("src", "main", "java", "net", "bananemdnsa",
            "historystages", "data", "dependency", "DependencyChecker.java");

    @Test
    void noRequirementKindBypassesTheViews() throws IOException {
        assertTrue(Files.isRegularFile(CHECKER),
                "expected to find " + CHECKER + " — if the checker moved, move this guard with it,"
                        + " because a guard that cannot find its file passes for the wrong reason");

        List<String> offenders = new ArrayList<>();
        List<String> lines = Files.readAllLines(CHECKER);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().startsWith("*")) continue; // javadoc reference, not a call
            if (FORBIDDEN.matcher(line).find()) {
                offenders.add("DependencyChecker.java:" + (i + 1) + " -> " + line.trim());
            }
        }

        assertTrue(offenders.isEmpty(),
                "these dependency fields are read straight from the checker instead of through a"
                        + " Requirement view:\n" + String.join("\n", offenders)
                        + "\nA kind that goes around the views is a kind that cannot be registered,"
                        + " scoped or shown in the editor, so the axis stays closed for it.");
    }
}
