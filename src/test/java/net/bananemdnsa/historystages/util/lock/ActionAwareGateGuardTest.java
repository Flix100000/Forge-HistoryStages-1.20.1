package net.bananemdnsa.historystages.util.lock;

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
 * Nothing that blocks a player may ask the action-blind form of the item question.
 *
 * <p>{@code StageLockHelper.isItemLocked*} answers "does any stage mention this item at all" and
 * throws the entry's {@code unlock_actions} away. A gate built on it ignores every narrowing the
 * maintainer set: a mod entry limited to {@code recipe} still blocked container clicks, curio
 * slots, item frames and the anvil, which is Issue #117. The action-keyed
 * {@code isActionLocked*} forms are the ones a gate wants.
 *
 * <p>The trap is that both forms read the same and the wrong one is shorter, so it comes back
 * one convenient call at a time. Three places may still ask the blind question, and each has a
 * reason that is not a gate:
 *
 * <ul>
 *   <li>{@code StageLockHelper} defines them and uses them among themselves.</li>
 *   <li>{@code HistoryStages} logs which locked items a player carries — a report, not a block.</li>
 *   <li>The GameTests assert that an item is gated at all, which is the blind question.</li>
 * </ul>
 */
class ActionAwareGateGuardTest {

    /** {@code ::} as well as {@code .} — JEI reached for the blind form as a method reference. */
    private static final Pattern CALL = Pattern.compile("StageLockHelper(?:\\.|::)(isItemLocked\\w*)");

    private static final List<String> ALLOWED_FILES = List.of(
            "StageLockHelper.java",   // defines them
            "HistoryStages.java",     // inventory logging
            "LockTests.java",         // GameTest: asserts an item is gated at all
            "FluidLockTests.java");   // GameTest: same

    @Test
    void gatesAskWhichActionIsLockedRatherThanWhetherTheItemIsMentioned() throws IOException {
        Path main = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages");
        assertTrue(Files.isDirectory(main), "expected the test to run from the project root");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (ALLOWED_FILES.contains(file.getFileName().toString())) continue;
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.trim().startsWith("*")) continue; // javadoc reference, not a call
                    Matcher matcher = CALL.matcher(line);
                    while (matcher.find()) {
                        offenders.add(file.getFileName() + ":" + (i + 1) + " -> " + matcher.group());
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "these gates ignore the entry's unlock_actions — ask isActionLocked*(…, action) "
                        + "instead:\n" + String.join("\n", offenders));
    }
}
