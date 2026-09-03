package net.bananemdnsa.historystages.events;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.bananemdnsa.historystages.data.lock.FluidRecipeIndex;
import net.bananemdnsa.historystages.data.lock.FluidRecipeScanner;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/**
 * The single place that judges whether a recipe is locked.
 *
 * <p>Both questions below ask the same thing first: is anyone known to be crafting? A station that
 * knows sets a {@link RecipeCraftContext} around its one resolution and gets an answer across both
 * scopes; everything else — furnaces, hoppers, autocrafters, mod menus we do not hook — gets the
 * global-only answer it has always got.
 *
 * <p>Client and server move together on purpose. Were only one side to consult individual stages,
 * the player would see a preview they cannot take out, or an output the server refuses — worse
 * than either side being wrong on its own.
 */
public class RecipeHandler {
    /**
     * Checks if a recipe's output is locked based on history stages.
     * Uses ClientStageCache on the client side, SERVER_CACHE on the server side.
     */
    public static boolean isOutputLocked(RecipeHolder<?> holder, boolean isClientSide) {
        if (holder == null) return false;

        ItemStack result;
        try {
            result = holder.value().getResultItem(RegistryAccess.EMPTY);
        } catch (Exception e) {
            return false;
        }

        if (result.isEmpty()) return false;

        UUID crafter = crafter();

        // Respects lock_actions["recipe"]: if "recipe" is explicitly allowed, the recipe stays visible/craftable
        if (isClientSide) {
            return StageLockHelper.isActionLockedForClient(result, "recipe")
                    || (crafter != null
                            && StageLockHelper.isActionLockedByIndividualStageClient(result, "recipe"));
        }
        return StageLockHelper.isActionLockedForServer(result, "recipe")
                || (crafter != null
                        && StageLockHelper.isActionLockedByIndividualStage(result, crafter, "recipe"));
    }

    /**
     * Whether a fluid this recipe touches is gated for the action that side implies.
     *
     * <p>{@code recipe} covers the fluids a recipe produces and {@code ingredient} the ones it
     * consumes, because those are different intents: "you cannot make molten copper yet" is not
     * "everything needing lava is out of reach". Where the recipe's own spelling did not settle
     * which side a fluid sits on, either action gates it — a recipe too many is a nuisance, a
     * recipe too few is a hole.
     */
    public static boolean isFluidGatedForViewer(String recipeId) {
        // A recipe viewer has no resolution in progress and therefore no crafter, but the person
        // looking at the screen is the player — so their individual stages count here, exactly as
        // they do for the recipe-id lock the viewers already consult.
        return isFluidGated(recipeId, true, true, null);
    }

    private static boolean isFluidGated(String recipeId, boolean isClientSide,
                                        boolean includeIndividual, UUID crafter) {
        if (FluidRecipeIndex.isEmpty()) return false;

        Map<String, Set<FluidRecipeScanner.Position>> touched = FluidRecipeIndex.fluidsIn(recipeId);
        if (touched.isEmpty()) return false;

        for (Map.Entry<String, Set<FluidRecipeScanner.Position>> entry : touched.entrySet()) {
            String fluidId = entry.getKey();
            for (FluidRecipeScanner.Position position : entry.getValue()) {
                boolean gated = switch (position) {
                    case OUTPUT -> fluidActionGated(fluidId, "recipe", isClientSide,
                            includeIndividual, crafter);
                    case INPUT -> fluidActionGated(fluidId, "ingredient", isClientSide,
                            includeIndividual, crafter);
                    case UNKNOWN -> fluidActionGated(fluidId, "recipe", isClientSide,
                                    includeIndividual, crafter)
                            || fluidActionGated(fluidId, "ingredient", isClientSide,
                                    includeIndividual, crafter);
                };
                if (gated) return true;
            }
        }
        return false;
    }

    /**
     * The global half always counts; {@code includeIndividual} decides whether the per-player half
     * does. Client-side the individual set is the local player's, so no uuid is needed there —
     * server-side there is nobody to resolve it against without one.
     */
    private static boolean fluidActionGated(String fluidId, String action, boolean isClientSide,
                                            boolean includeIndividual, UUID crafter) {
        if (isClientSide) {
            return StageLockHelper.isFluidActionLockedForClient(fluidId, action)
                    || (includeIndividual
                            && StageLockHelper.isFluidActionLockedByIndividualStageClient(fluidId, action));
        }
        return StageLockHelper.isFluidActionLockedForServer(fluidId, action)
                || (includeIndividual && crafter != null
                        && StageLockHelper.isFluidActionLockedByIndividualStage(fluidId, crafter, action));
    }

    /** Overload without side info — defaults to server-side check. */
    public static boolean isOutputLocked(RecipeHolder<?> holder) {
        return isOutputLocked(holder, false);
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId, boolean isClientSide) {
        if (recipeId == null) return false;

        String id = recipeId.toString();
        UUID crafter = crafter();

        // A recipe can be gated by a fluid it touches without anyone having listed the recipe.
        // Checked first because it is a map lookup that misses for almost every recipe.
        if (isFluidGated(id, isClientSide, crafter != null, crafter)) return true;

        if (isClientSide) {
            // The client's crafter is always the local player, so the individual set to consult is
            // the one ClientStageStates already holds. The context only decides whether to ask.
            return crafter != null
                    ? StageLockHelper.isRecipeLockedForClient(id)
                    : StageLockHelper.isRecipeLockedForClientGlobalOnly(id);
        }
        return crafter != null
                ? StageLockHelper.isRecipeLockedForPlayer(id, crafter)
                : StageLockHelper.isRecipeLockedForServer(id);
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId) {
        return isRecipeIdLocked(recipeId, false);
    }

    /**
     * The crafter of the resolution in progress, or null when there is none — which is also the
     * answer while {@code individualLockRecipes} is off. Reading the switch here rather than at
     * every station means turning it off restores the previous behaviour everywhere at once,
     * including on the client.
     */
    @Nullable
    private static UUID crafter() {
        if (!Config.GAMEPLAY.individualLockRecipes.get()) return null;
        return RecipeCraftContext.crafter();
    }
}
