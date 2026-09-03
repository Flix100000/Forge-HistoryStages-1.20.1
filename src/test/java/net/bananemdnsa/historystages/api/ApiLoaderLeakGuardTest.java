package net.bananemdnsa.historystages.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps NeoForge out of the api, except where NeoForge <em>is</em> the api.
 *
 * <p>The parent design wanted loader-free callbacks throughout. Phase 9 deliberately kept
 * NeoForge events instead, because "everything is a registry" is the familiar shape on this
 * loader and the registered types — {@code LockCategory}, {@code Requirement},
 * {@code StageSettingsGroup} — name no loader at all. A Fabric port rewrites the thirteen classes
 * listed here and inherits the rest.
 *
 * <p>That argument only holds while the leak stays where it was put. This test is what keeps it
 * there: eleven registration events, plus {@code StageStates}, which posts {@link
 * net.bananemdnsa.historystages.api.stage.StageEvent} onto the bus and is therefore the bridge
 * itself. See the Phase 9 design §5.
 */
class ApiLoaderLeakGuardTest {

    private static final Set<String> ALLOWED = Set.of(
            "RegisterLockCategoriesEvent", "RegisterRequirementTypesEvent",
            "RegisterTriggerTypesEvent", "RegisterStageSettingsGroupsEvent",
            "RegisterConfigSectionsEvent", "RegisterCategoryEditorsEvent",
            "RegisterRequirementEditorsEvent", "RegisterTriggerEditorsEvent",
            "RegisterCustomFieldScreensEvent", "RegisterRecipeTypeMetaEvent",
            "RegisterIndividualRecipeSupportEvent",
            "StageEvent", "StageStates");

    @Test
    void onlyTheRegistrationEventsAndTheEventBridgeNameTheLoader() throws IOException {
        Path api = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages", "api");
        assertTrue(Files.isDirectory(api), "expected the test to run from the project root");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(api)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                if (ALLOWED.contains(name)) continue;
                if (Files.readString(file).contains("net.neoforged")) {
                    offenders.add(name);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "these api types name NeoForge, which only the registration events and the event "
                        + "bridge may:\n" + String.join("\n", offenders));
    }
}
