package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.AllRecipesCache;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.*;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;

    /**
     * Only refresh stage cache and populate AllRecipesCache on apply().
     * Recipe filtering is now done at query time, not load time.
     * This ensures compatibility with KubeJS/CraftTweaker which modify recipes after apply().
     */
    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"),
            remap = true
    )
    private void onApplyPost(Map<ResourceLocation, com.google.gson.JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler, CallbackInfo ci) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            StageData data = StageData.get(server.overworld());
            StageData.refreshCache(data.getUnlockedStages());
        }

        AllRecipesCache.set(new ArrayList<>(this.byName.values()));
        net.bananemdnsa.historystages.data.lock.FluidRecipeIndex.markDirty();
        auditRecipeLocks();
    }

    /**
     * Warns about stage recipe locks that point at recipes which are not loaded.
     *
     * <p>Here rather than in {@code StageManager} because this is the first moment the answer
     * exists: stages load at server start, recipes load later, and script-generated recipes land
     * during this very call. It also re-runs on every {@code /reload}, which is exactly when a
     * pack author has just changed the script that renumbered the recipe.
     *
     * <p>It warns and never removes. A recipe can be legitimately absent — a mod temporarily out,
     * a recipe disabled by a datapack — and deleting the entry would take the lock with it for
     * good, which is worse than the problem being reported.
     */
    private void auditRecipeLocks() {
        Map<String, List<String>> byStage = new HashMap<>();
        net.bananemdnsa.historystages.data.StageManager.getStages()
                .forEach((stageId, entry) -> byStage.put(stageId, entry.getRecipes()));
        // Individual stages hold recipes since 6.0.0, so a lock pointing at nothing can hide
        // there just as well.
        net.bananemdnsa.historystages.data.StageManager.getIndividualStages()
                .forEach((stageId, entry) -> byStage.put(stageId, entry.getRecipes()));

        Set<String> loaded = new HashSet<>();
        for (ResourceLocation id : this.byName.keySet()) {
            loaded.add(id.toString());
        }

        List<net.bananemdnsa.historystages.data.lock.RecipeIdAudit.MissingRecipe> missing =
                net.bananemdnsa.historystages.data.lock.RecipeIdAudit.missing(byStage, loaded);

        for (var entry : missing) {
            net.bananemdnsa.historystages.util.DebugLogger.warn("Recipe Locks",
                    "Stage '" + entry.stageId() + "' gates recipe '" + entry.recipeId()
                            + "', which is not loaded. The lock does nothing. Script-generated "
                            + "ids change when the script is reordered.");
        }

        net.bananemdnsa.historystages.data.lock.MissingRecipeIds.set(missing);

        auditIndividualRecipeTypes();
    }

    /**
     * Warns when an individual stage gates a recipe whose station never knows who is crafting.
     *
     * <p>The editor's picker keeps these out, but a hand-edited stage file does not go through it.
     * Without this line the entry sits in the file looking correct and gating nothing — which is
     * the exact complaint this whole feature came from, one level down.
     *
     * <p>Here rather than in {@code StageManager} for the same reason as the check above: stages
     * load before recipes do, so a recipe's type does not exist yet when the stage is read.
     *
     * <p>Warns and never removes. The entry stays as the author wrote it.
     */
    private void auditIndividualRecipeTypes() {
        net.bananemdnsa.historystages.data.StageManager.getIndividualStages()
                .forEach((stageId, entry) -> {
                    for (String recipeId : entry.getRecipes()) {
                        if (recipeId == null) continue;

                        ResourceLocation id = ResourceLocation.tryParse(recipeId);
                        if (id == null) continue;

                        RecipeHolder<?> holder = this.byName.get(id);
                        if (holder == null) continue; // already reported as not loaded

                        ResourceLocation typeKey = net.minecraft.core.registries.BuiltInRegistries
                                .RECIPE_TYPE.getKey(holder.value().getType());
                        if (typeKey == null
                                || net.bananemdnsa.historystages.data.lock.IndividualRecipeSupport
                                        .supports(typeKey.toString())) {
                            continue;
                        }

                        net.bananemdnsa.historystages.util.DebugLogger.warn("Recipe Locks",
                                "Individual stage '" + stageId + "' gates recipe '" + recipeId
                                        + "' of type '" + typeKey + "'. That station resolves its "
                                        + "recipes with no player present, so an individual stage "
                                        + "cannot gate it — the entry does nothing. Put it on a "
                                        + "global stage instead. The entry is left as written.");
                    }
                });
    }

    /**
     * Filter single recipe lookups (3-arg) - used by crafting table.
     */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, remap = true)
    private <I extends RecipeInput, T extends Recipe<I>> void filterGetRecipeFor(
            RecipeType<T> type, I input, Level level,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        Optional<RecipeHolder<T>> result = cir.getReturnValue();
        if (result.isPresent() && isRecipeLocked(result.get(), level.isClientSide())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    /**
     * Filter cached recipe lookups (4-arg with ResourceLocation) - used by CachedCheck (furnace, smoker, blast furnace).
     */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, remap = true)
    private <I extends RecipeInput, T extends Recipe<I>> void filterGetRecipeForCached(
            RecipeType<T> type, I input, Level level, @Nullable ResourceLocation lastRecipe,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        Optional<RecipeHolder<T>> result = cir.getReturnValue();
        if (result.isPresent() && isRecipeLocked(result.get(), level.isClientSide())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    /**
     * Filter cached recipe lookups (4-arg with RecipeHolder) - the core overload called by CraftingMenu and others.
     */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, remap = true)
    private <I extends RecipeInput, T extends Recipe<I>> void filterGetRecipeForHolder(
            RecipeType<T> type, I input, Level level, @Nullable RecipeHolder<T> lastRecipe,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        Optional<RecipeHolder<T>> result = cir.getReturnValue();
        if (result.isPresent() && isRecipeLocked(result.get(), level.isClientSide())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    /**
     * Filter list recipe lookups - prevents crafting locked recipes.
     */
    @Inject(method = "getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, remap = true)
    private <I extends RecipeInput, T extends Recipe<I>> void filterGetRecipesFor(
            RecipeType<T> type, I input, Level level,
            CallbackInfoReturnable<List<RecipeHolder<T>>> cir) {
        boolean isClient = level.isClientSide();
        List<RecipeHolder<T>> recipes = cir.getReturnValue();

        // Nothing is filtered in the overwhelming majority of calls, and this one is on the
        // crafting path. Find the first locked recipe before allocating anything; with none,
        // the original list goes back untouched.
        int firstLocked = -1;
        for (int i = 0; i < recipes.size(); i++) {
            if (isRecipeLocked(recipes.get(i), isClient)) {
                firstLocked = i;
                break;
            }
        }
        if (firstLocked < 0) return;

        List<RecipeHolder<T>> filtered = new ArrayList<>(recipes.size() - 1);
        filtered.addAll(recipes.subList(0, firstLocked));
        for (int i = firstLocked + 1; i < recipes.size(); i++) {
            RecipeHolder<T> recipe = recipes.get(i);
            if (!isRecipeLocked(recipe, isClient)) filtered.add(recipe);
        }
        cir.setReturnValue(filtered);
    }

    private static boolean isRecipeLocked(RecipeHolder<?> holder, boolean isClientSide) {
        return RecipeHandler.isOutputLocked(holder, isClientSide) || RecipeHandler.isRecipeIdLocked(holder.id(), isClientSide);
    }
}
