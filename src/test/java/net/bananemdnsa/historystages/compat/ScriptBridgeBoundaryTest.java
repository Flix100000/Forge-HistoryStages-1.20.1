package net.bananemdnsa.historystages.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the two script bridges thin.
 *
 * <p>The point of the shared facade is that a fix reaches KubeJS and CraftTweaker at once. That
 * only holds while the bridges translate and nothing else. A bridge that reaches past
 * {@code compat.script} into {@code StageManager}, {@code StageStates}, a SavedData class or a
 * packet has started deciding things on its own, and the two languages will drift the moment one
 * of them is fixed and the other is not — which is exactly how {@code ToggleStageLockPacket}
 * drifted away from {@code StageStates} once already.
 *
 * <p>A source scan, not a classpath scan: this way the test needs neither KubeJS nor
 * CraftTweaker nor Minecraft, all three of which are absent from the test source set.
 */
class ScriptBridgeBoundaryTest {

    /** Reaching into any of these means the bridge stopped being a translation layer. */
    private static final List<String> FORBIDDEN = List.of(
            "net.bananemdnsa.historystages.data.",
            "net.bananemdnsa.historystages.api.stage.StageStates",
            "net.bananemdnsa.historystages.api.lock.CategoryLocks",
            "net.bananemdnsa.historystages.network.",
            "net.bananemdnsa.historystages.events.",
            "net.bananemdnsa.historystages.screen.",
            "net.bananemdnsa.historystages.research.");

    /**
     * What a bridge legitimately needs: the facade, its own package, the event type it forwards,
     * the client caches the read-only bindings answer from, and the logger.
     */
    private static final List<String> ALLOWED = List.of(
            "net.bananemdnsa.historystages.compat.script.",
            "net.bananemdnsa.historystages.compat.kubejs.",
            "net.bananemdnsa.historystages.compat.crafttweaker.",
            "net.bananemdnsa.historystages.api.stage.StageEvent",
            "net.bananemdnsa.historystages.client.cache.",
            "net.bananemdnsa.historystages.util.DebugLogger");

    @Test
    void theBridgesOnlyTalkToTheFacade() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (String bridge : List.of("kubejs", "crafttweaker")) {
            Path dir = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages",
                    "compat", bridge);
            assertTrue(Files.isDirectory(dir), "expected " + dir + " to exist");

            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(file)) {
                        if (!line.startsWith("import ")) continue;
                        String imported = line.substring("import ".length())
                                .replace("static ", "").replace(";", "").trim();
                        if (!imported.startsWith("net.bananemdnsa.historystages.")) continue;
                        if (ALLOWED.stream().anyMatch(imported::startsWith)) continue;
                        if (FORBIDDEN.stream().anyMatch(imported::startsWith)) {
                            offenders.add(file.getFileName() + " → " + imported);
                        }
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "script bridges must go through compat.script instead of reaching past it:\n"
                        + String.join("\n", offenders));
    }
}
