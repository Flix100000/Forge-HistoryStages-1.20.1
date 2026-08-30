package net.bananemdnsa.historystages.data.lock;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeIdAuditTest {

    @Test
    void anIdThatExistsIsNotReported() {
        var missing = RecipeIdAudit.missing(
                Map.of("bronze", List.of("minecraft:stick")),
                Set.of("minecraft:stick"));
        assertTrue(missing.isEmpty());
    }

    @Test
    void anIdThatDoesNotExistIsReportedWithItsStage() {
        var missing = RecipeIdAudit.missing(
                Map.of("bronze", List.of("kubejs:crafting_shaped_7")),
                Set.of("minecraft:stick"));
        assertEquals(1, missing.size());
        assertEquals("bronze", missing.get(0).stageId());
        assertEquals("kubejs:crafting_shaped_7", missing.get(0).recipeId());
    }

    @Test
    void theSameMissingIdInTwoStagesIsReportedTwice() {
        var missing = RecipeIdAudit.missing(
                Map.of("bronze", List.of("kubejs:x"), "iron", List.of("kubejs:x")),
                Set.of());
        assertEquals(2, missing.size(), "each stage needs its own line, that is where the fix goes");
    }

    @Test
    void anEmptyRecipeSetReportsEverythingRatherThanNothing() {
        // Guards against the tempting "if we know no recipes, assume all are fine" shortcut:
        // that would silence the check exactly when recipe loading broke.
        var missing = RecipeIdAudit.missing(
                Map.of("bronze", List.of("minecraft:stick")),
                Set.of());
        assertEquals(1, missing.size());
    }

    @Test
    void aStageWithoutRecipesContributesNothing() {
        var missing = RecipeIdAudit.missing(
                Map.of("bronze", List.of()),
                Set.of("minecraft:stick"));
        assertTrue(missing.isEmpty());
    }

    @Test
    void theReportIsOrderedSoTheLogDoesNotShuffleBetweenReloads() {
        var missing = RecipeIdAudit.missing(
                Map.of("iron", List.of("kubejs:b", "kubejs:a"), "bronze", List.of("kubejs:c")),
                Set.of());
        assertEquals(List.of("bronze", "iron", "iron"),
                missing.stream().map(RecipeIdAudit.MissingRecipe::stageId).toList());
        assertEquals(List.of("kubejs:c", "kubejs:a", "kubejs:b"),
                missing.stream().map(RecipeIdAudit.MissingRecipe::recipeId).toList());
    }
}
