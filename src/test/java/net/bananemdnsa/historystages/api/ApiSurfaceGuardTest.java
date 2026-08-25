package net.bananemdnsa.historystages.api;

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
 * The boundary of the promise, checked rather than claimed.
 *
 * <p>A type under {@code api/} may reach anywhere it likes inside a method body — that is what a
 * facade does, and {@code CategoryLocks} could not exist otherwise. What it may not do is name an
 * internal type in a <em>public signature</em>, because that drags the type into the contract
 * whether anyone meant it or not. An earlier draft of this test forbade the imports instead and
 * would have failed on its first run against exactly the classes whose job is to be a facade.
 *
 * <p>{@link #DATA_MODEL} is the one deliberate exception. The api hands out the mod's own stage
 * data types, and those stay in {@code data/} because that is where the loader, the editor, the
 * network layer and the graph all look for them — {@code StageEntry} alone is named in over a
 * hundred files. The list is short on purpose: when it grows, that shows up in a diff and
 * somebody has to mean it.
 */
class ApiSurfaceGuardTest {

    private static final Set<String> DATA_MODEL = Set.of(
            "StageEntry", "ItemEntry", "NamedLockEntry", "EntityLocks", "EntitySpawnLockEntry",
            "EntityInteractionLockEntry", "DependencyGroup", "AutoTrigger", "CombineMode",
            "StageMode", "TemporaryConfig", "DurationUnit", "TierMode", "HiddenDisplayConfig",
            "DisplayMode", "StructureGenerationRule", "GenerationPhase", "TextOverrideHolder");

    private static final Pattern IMPORT = Pattern.compile(
            "^import (?:static )?net\\.bananemdnsa\\.historystages\\.([\\w.]+);", Pattern.MULTILINE);

    /** A declaration that is part of the visible surface, not a local or a body statement. */
    private static final Pattern DECL = Pattern.compile("^\\s*(public|protected)\\s+[^;{]*[;{]");

    @Test
    void noApiSignatureNamesAnInternalType() throws IOException {
        Path api = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages", "api");
        assertTrue(Files.isDirectory(api), "expected the test to run from the project root");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(api)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);

                StringBuilder surface = new StringBuilder();
                for (String line : text.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("*") || trimmed.startsWith("//")) continue;
                    if (DECL.matcher(line).find()) surface.append(line).append('\n');
                }
                String visible = surface.toString();

                Matcher imports = IMPORT.matcher(text);
                while (imports.find()) {
                    String path = imports.group(1);
                    if (path.startsWith("api.")) continue;
                    String simple = path.substring(path.lastIndexOf('.') + 1);
                    if (simple.isEmpty() || !Character.isUpperCase(simple.charAt(0))) continue;
                    if (DATA_MODEL.contains(simple)) continue;
                    if (Pattern.compile("(?<![\\w.])" + Pattern.quote(simple) + "(?![\\w])")
                            .matcher(visible).find()) {
                        offenders.add(file.getFileName() + " exposes " + simple);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "an api type may not name an internal type in a public signature — either that "
                        + "type belongs in api/, or the signature is cut wrong:\n"
                        + String.join("\n", offenders));
    }
}
