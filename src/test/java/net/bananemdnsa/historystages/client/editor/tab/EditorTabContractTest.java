package net.bananemdnsa.historystages.client.editor.tab;

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
 * Guards the one thing Phase 3 was for: there is a single notion of "tab" across the editor axes.
 *
 * <p>Two tab contracts saying the same thing is what this replaced. Nothing would stop a later
 * axis from declaring its own — it would compile, work, and quietly mean an addon author has to
 * learn the idea twice, with the two free to drift apart afterwards.
 *
 * <p>Reads the source as text, the way {@code SeamGuardTest} does: the test runtime has no
 * Minecraft, so loading a screen or tab class is not an option.
 */
class EditorTabContractTest {

    private static final Path EDITOR = Path.of("src", "main", "java", "net", "bananemdnsa",
            "historystages", "client", "editor");

    /** Any interface whose name ends in Tab — the naming every axis has followed so far. */
    private static final Pattern TAB_INTERFACE =
            Pattern.compile("\\binterface\\s+(\\w*Tab)\\b([^{]*)\\{");

    @Test
    void everyTabInterfaceExtendsTheSharedContract() throws IOException {
        assertTrue(Files.isDirectory(EDITOR),
                "expected to find " + EDITOR + " — if the editor moved, move this guard with it,"
                        + " because a guard that cannot find its files passes for the wrong reason");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(EDITOR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher matcher = TAB_INTERFACE.matcher(source);
                while (matcher.find()) {
                    String name = matcher.group(1);
                    String declaration = matcher.group(2);
                    if (name.equals("EditorTab")) continue;
                    if (declaration.contains("EditorTab")) continue;
                    offenders.add(file.getFileName() + " -> interface " + name);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "these tab interfaces do not extend EditorTab:\n" + String.join("\n", offenders)
                        + "\nOne contract across the axes is what Phase 3 was for. A second one"
                        + " compiles and works, and means an addon author learns the same idea"
                        + " twice while the two are free to drift apart.");
    }
}
