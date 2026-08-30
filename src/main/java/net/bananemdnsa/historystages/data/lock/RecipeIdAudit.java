package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds recipe ids that a stage gates but the game does not have.
 *
 * <p>Until this existed, {@code StageManager} checked recipe ids for being well-formed
 * ResourceLocations and nothing else, so a lock on a recipe that is not loaded simply gated
 * nothing — silently, with no line anywhere saying so.
 *
 * <p>Script-generated recipes turn that from an odd case into the normal one. KubeJS derives ids
 * such as {@code kubejs:crafting_shaped_7} from the order things appear in the script, so
 * reordering the script renumbers them and every lock pointing into it goes dead.
 *
 * <p>Minecraft-free by construction: the caller supplies both sets, so the comparison itself can
 * be tested on a classpath that has no game on it.
 */
public final class RecipeIdAudit {

    private RecipeIdAudit() {}

    /** One stage gating one recipe id that is not loaded. */
    public record MissingRecipe(String stageId, String recipeId) {}

    /**
     * @param recipesByStage stage id → the recipe ids that stage gates
     * @param loadedRecipeIds every recipe id the game currently knows
     * @return one entry per stage-and-recipe pair, ordered by stage then recipe so that two runs
     *         over the same data produce the same log
     */
    public static List<MissingRecipe> missing(Map<String, ? extends Collection<String>> recipesByStage,
                                              Set<String> loadedRecipeIds) {
        List<MissingRecipe> out = new ArrayList<>();

        recipesByStage.forEach((stageId, recipeIds) -> {
            if (recipeIds == null) return;
            for (String recipeId : recipeIds) {
                if (recipeId != null && !loadedRecipeIds.contains(recipeId)) {
                    out.add(new MissingRecipe(stageId, recipeId));
                }
            }
        });

        out.sort(Comparator.comparing(MissingRecipe::stageId).thenComparing(MissingRecipe::recipeId));
        return out;
    }
}
