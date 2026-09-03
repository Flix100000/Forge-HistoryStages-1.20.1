package net.bananemdnsa.historystages.data.dependency;

import net.bananemdnsa.historystages.api.dependency.Requirement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.api.stage.StageScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The built-ins' own declarations. The scope table here is the thing that used to live as two
 * hardcoded arrays of tab keys in the editor, so this test is what stops it drifting back.
 */
class BuiltInRequirementMetadataTest {

    private static final Path EN_US = Path.of("src", "main", "resources", "assets",
            "historystages", "lang", "en_us.json");
    private static final Path DE_DE = Path.of("src", "main", "resources", "assets",
            "historystages", "lang", "de_de.json");

    private static Requirement byId(String id) {
        return BuiltInRequirements.ALL.stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no built-in requirement '" + id + "'"));
    }

    @Test
    void thePlayerBoundKindsAreIndividualOnly() {
        for (String id : List.of("advancement", "xp_level", "entity_kill", "stat")) {
            assertEquals(Set.of(StageScope.INDIVIDUAL), byId(id).supportedScopes(),
                    id + " belongs to a player, so it cannot be demanded of a global stage");
        }
    }

    @Test
    void theWorldWideKindsSupportBothScopes() {
        for (String id : List.of("item", "item_tag", "stage", "individual_stage", "scoreboard")) {
            assertEquals(EnumSet.allOf(StageScope.class), byId(id).supportedScopes(), id);
        }
    }

    @Test
    void everyBuiltInLangKeyExistsInBothMaintainedLanguages() throws IOException {
        String en = Files.readString(EN_US);
        String de = Files.readString(DE_DE);

        List<String> missing = new ArrayList<>();
        for (Requirement requirement : BuiltInRequirements.ALL) {
            for (String key : List.of(requirement.tabLangKey(), requirement.tooltipLangKey(),
                    requirement.sectionLangKey())) {
                if (!en.contains('"' + key + '"')) missing.add("en_us: " + key);
                if (!de.contains('"' + key + '"')) missing.add("de_de: " + key);
            }
        }

        assertTrue(missing.isEmpty(), "missing lang keys:\n" + String.join("\n", missing));
    }
}
