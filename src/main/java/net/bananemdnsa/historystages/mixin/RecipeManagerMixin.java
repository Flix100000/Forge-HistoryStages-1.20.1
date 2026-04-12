package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.AllRecipesCache;
import net.bananemdnsa.historystages.util.StageData;
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
import java.util.stream.Collectors;

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
            StageData.SERVER_CACHE.clear();
            StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
        }

        AllRecipesCache.set(new ArrayList<>(this.byName.values()));
        System.out.println("[HistoryStages] Recipe cache updated (query-time filtering active).");
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
     * Filter cached recipe lookups (4-arg with ResourceLocation) - used by furnace, smoker, blast furnace.
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
     * Filter list recipe lookups - prevents crafting locked recipes.
     */
    @Inject(method = "getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, remap = true)
    private <I extends RecipeInput, T extends Recipe<I>> void filterGetRecipesFor(
            RecipeType<T> type, I input, Level level,
            CallbackInfoReturnable<List<RecipeHolder<T>>> cir) {
        boolean isClient = level.isClientSide();
        List<RecipeHolder<T>> recipes = cir.getReturnValue();
        List<RecipeHolder<T>> filtered = recipes.stream()
                .filter(r -> !isRecipeLocked(r, isClient))
                .collect(Collectors.toList());
        if (filtered.size() != recipes.size()) {
            cir.setReturnValue(filtered);
        }
    }

    private static boolean isRecipeLocked(RecipeHolder<?> holder, boolean isClientSide) {
        return RecipeHandler.isOutputLocked(holder, isClientSide) || RecipeHandler.isRecipeIdLocked(holder.id(), isClientSide);
    }
}
