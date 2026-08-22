package net.bananemdnsa.historystages.client.editor.dep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the dependency editor and the requirement registry from drifting apart.
 *
 * <p>The lang-key half of this lives in {@code BuiltInRequirementMetadataTest}, which checks every
 * built-in's tab, tooltip and section key against both maintained languages. What is left here is
 * the structural half: the editor must not go back to keeping its own idea of which tabs exist.
 *
 * <p>Reading the source as text is deliberate, the same way {@code SeamGuardTest} does it — the
 * test runtime has no Minecraft, so loading a screen class is not an option.
 */
class RequirementEditorParityTest {

    private static final Path SCREEN = Path.of("src", "main", "java", "net", "bananemdnsa",
            "historystages", "client", "editor", "DependencyEditorScreen.java");

    @Test
    void theEditorKeepsNoTabKeyArrayOfItsOwn() throws IOException {
        assertTrue(Files.isRegularFile(SCREEN),
                "expected to find " + SCREEN + " — if the screen moved, move this guard with it,"
                        + " because a guard that cannot find its file passes for the wrong reason");

        String source = Files.readString(SCREEN);

        assertFalse(source.contains("TAB_KEYS"),
                "DependencyEditorScreen declares a tab-key array again. The strip must come from"
                        + " RequirementTypes.forScope, or the editor and the checker can disagree"
                        + " about which requirement kinds exist at a scope — which is exactly how a"
                        + " player-bound requirement stayed evaluable on a global stage.");
        assertFalse(source.contains("TOOLTIP_KEYS"), "same, for the tooltip keys");
    }

    @Test
    void theTabStripIsBuiltFromTheRegistry() throws IOException {
        String source = Files.readString(SCREEN);

        assertTrue(source.contains("RequirementTypes.forScope"),
                "DependencyEditorScreen no longer asks the registry which requirements a scope"
                        + " allows. Whatever replaced it is a second answer to a question that must"
                        + " have exactly one, and the checker will not be reading it.");
    }
}
