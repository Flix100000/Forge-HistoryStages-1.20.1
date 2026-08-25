package net.bananemdnsa.historystages.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demo addon is the stand-in mod: it exercises all five extension points and is the example
 * an addon author gets pointed at. If it has to reach past {@code api/} to do something, so would
 * a real addon — and that is a hole in the surface, not a quirk of the demo.
 *
 * <p>Before Phase 9 this test could not have been written: the demo imported from sixteen
 * packages, and nothing said which of them were promises.
 *
 * <p>Two things outside {@code api/} are allowed. {@code HistoryStages} carries the mod id, which
 * an addon needs for its own {@code @EventBusSubscriber}. {@link #DATA_MODEL} is the stage data
 * the api hands out; it lives in {@code data/} deliberately — see the Phase 9 design §4.
 */
class DemoUsesOnlyApiTest {

    private static final Set<String> DATA_MODEL = Set.of(
            "StageEntry", "ItemEntry", "NamedLockEntry", "EntityLocks", "EntitySpawnLockEntry",
            "EntityInteractionLockEntry", "DependencyGroup", "AutoTrigger", "CombineMode",
            "StageMode", "TemporaryConfig", "DurationUnit", "TierMode", "HiddenDisplayConfig",
            "DisplayMode", "StructureGenerationRule", "GenerationPhase", "TextOverrideHolder");

    private static final Pattern IMPORT = Pattern.compile(
            "^import (?:static )?net\\.bananemdnsa\\.historystages\\.([\\w.]+);", Pattern.MULTILINE);

    @Test
    void theDemoAddonReachesForNothingOutsideTheApi() throws IOException {
        Path demo = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages", "demo");
        assertTrue(Files.isDirectory(demo), "expected the test to run from the project root");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(demo)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher imports = IMPORT.matcher(Files.readString(file));
                while (imports.find()) {
                    String path = imports.group(1);
                    if (path.startsWith("api.")) continue;
                    if (path.equals("HistoryStages")) continue;
                    String simple = path.substring(path.lastIndexOf('.') + 1);
                    if (DATA_MODEL.contains(simple)) continue;
                    offenders.add(file.getFileName() + " -> " + path);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "the demo addon reaches past the api, so a real addon would have to as well — "
                        + "either the type belongs in api/, or the demo should not need it:\n"
                        + String.join("\n", offenders));
    }
}
