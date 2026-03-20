package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.AllRecipesCache;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.datafixers.util.Pair;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
    @Shadow private Map<ResourceLocation, Recipe<?>> byName;

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
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            StageData data = StageData.get(server.overworld());
            StageData.SERVER_CACHE.clear();
            StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
        }

        AllRecipesCache.set(new ArrayList<>(this.byName.values()));
        System.out.println("[HistoryStages] Recipe cache updated (query-time filtering active).");
    }

    /**
     * Filter single recipe lookups (3-arg) - used by crafting table.
     */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, remap = true)
    private <C extends Container, T extends Recipe<C>> void filterGetRecipeFor(
            RecipeType<T> type, C container, Level level,
            CallbackInfoReturnable<Optional<T>> cir) {
        Optional<T> result = cir.getReturnValue();
        if (result.isPresent() && isRecipeLocked(result.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    /**
     * Filter cached recipe lookups (4-arg) - used by furnace, smoker, blast furnace via CachedCheck.
     */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, remap = true)
    private <C extends Container, T extends Recipe<C>> void filterGetRecipeForCached(
            RecipeType<T> type, C container, Level level, @Nullable ResourceLocation lastRecipe,
            CallbackInfoReturnable<Optional<Pair<ResourceLocation, T>>> cir) {
        Optional<Pair<ResourceLocation, T>> result = cir.getReturnValue();
        if (result.isPresent() && isRecipeLocked(result.get().getSecond())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    /**
     * Filter list recipe lookups - prevents crafting locked recipes.
     */
    @Inject(method = "getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, remap = true)
    private <C extends Container, T extends Recipe<C>> void filterGetRecipesFor(
            RecipeType<T> type, C container, Level level,
            CallbackInfoReturnable<List<T>> cir) {
        List<T> recipes = cir.getReturnValue();
        List<T> filtered = recipes.stream()
                .filter(r -> !isRecipeLocked(r))
                .collect(Collectors.toList());
        if (filtered.size() != recipes.size()) {
            cir.setReturnValue(filtered);
        }
    }

    /**
     * Filter getAllRecipesFor - used by some mods and vanilla for recipe book lookups.
     */
    @Inject(method = "getAllRecipesFor", at = @At("RETURN"), cancellable = true, remap = true)
    private <C extends Container, T extends Recipe<C>> void filterGetAllRecipesFor(
            RecipeType<T> type,
            CallbackInfoReturnable<List<T>> cir) {
        List<T> recipes = cir.getReturnValue();
        List<T> filtered = recipes.stream()
                .filter(r -> !isRecipeLocked(r))
                .collect(Collectors.toList());
        if (filtered.size() != recipes.size()) {
            cir.setReturnValue(filtered);
        }
    }

    /**
     * Filter byKey - direct recipe lookup by ResourceLocation, used by some mods.
     */
    @Inject(method = "byKey", at = @At("RETURN"), cancellable = true, remap = true)
    private void filterByKey(ResourceLocation recipeId,
                             CallbackInfoReturnable<Optional<? extends Recipe<?>>> cir) {
        Optional<? extends Recipe<?>> result = cir.getReturnValue();
        if (result.isPresent() && isRecipeLocked(result.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    private static boolean isRecipeLocked(Recipe<?> recipe) {
        return RecipeHandler.isOutputLocked(recipe) || RecipeHandler.isRecipeIdLocked(recipe.getId());
    }
}
