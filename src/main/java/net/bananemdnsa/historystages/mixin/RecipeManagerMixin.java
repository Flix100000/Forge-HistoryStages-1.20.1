package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.data.lock.UngatedRecipes;
import net.bananemdnsa.historystages.data.lock.VisibleRecipes;
import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.AllRecipesCache;
import net.bananemdnsa.historystages.data.saveddata.StageData;
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

/**
 * Where a recipe lock is actually enforced.
 *
 * <p>There are three ways to reach a recipe, and they are gated differently on purpose.
 *
 * <p><strong>Asking</strong> — "which of your recipes fits what is inside me" — is gated, on both
 * sides. That is every vanilla station and most of Create.
 *
 * <p><strong>Reading the list</strong> — taking every recipe, or every recipe of one type, and
 * searching it yourself — is gated on the server only. A custom recipe type leaves a modded
 * machine no other option, so this is how most of them work; Create's basin is the visible case,
 * and it is why mixing and compacting stayed craftable inside a locked stage while pressing on the
 * belt, on the very same machine, was blocked. It stays unfiltered on the client because there the
 * same lists are what the player is allowed to <em>see</em>: JEI and EMI draw locked recipes with
 * a lock on them, and the editor's picker has to find a recipe that is already locked or nobody
 * could ever unlock it again.
 *
 * <p><strong>Looking one up by its id</strong> is <em>not</em> gated, and that is a decision rather
 * than an omission. A player's recipe book is a list of recipe ids in their player file, resolved
 * through {@code byKey} on every login; an id that does not resolve is not skipped but dropped,
 * logged as "unrecognized recipe, removed now", and the shortened book is written back on logout.
 * Gating it would quietly delete recipes players had already earned, once per login, for good —
 * an unlock afterwards would not bring them back. Awarding a recipe from an advancement goes the
 * same way, and so does the experience a furnace owes for what it smelted. Against that it buys
 * almost nothing: nothing in vanilla crafts through it, and placing a recipe from the book still
 * has to pass the resolution above.
 *
 * <p>On this version {@code getRecipes} is also what vanilla puts in the packet every client is
 * sent, so gating it would take the locked recipes off every client. {@link UngatedRecipes} is the
 * way back to the full list, and {@code PlayerListMixin} is where the packet uses it.
 */
@Mixin(RecipeManager.class)
public class RecipeManagerMixin implements UngatedRecipes {
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
            StageData.refreshCache(data.getUnlockedStages());
        }

        AllRecipesCache.set(new ArrayList<>(this.byName.values()));
        VisibleRecipes.invalidate();
        // Take note of what is gated right now, so the next stage change is compared against a
        // real answer rather than against nothing. Without this the first stage change on a fresh
        // server would look like a change to the gated set whatever it did, and pay for a datapack
        // reload it did not need.
        VisibleRecipes.gatedSetChanged(this.byName.values());
    }

    @Override
    public Collection<Recipe<?>> historystages$ungatedRecipes() {
        return this.byName.values();
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
        if (result.isPresent() && isRecipeLocked(result.get(), level.isClientSide())) {
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
        if (result.isPresent() && isRecipeLocked(result.get().getSecond(), level.isClientSide())) {
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
        boolean isClient = level.isClientSide();
        List<T> recipes = cir.getReturnValue();
        List<T> filtered = recipes.stream()
                .filter(r -> !isRecipeLocked(r, isClient))
                .collect(Collectors.toList());
        if (filtered.size() != recipes.size()) {
            cir.setReturnValue(filtered);
        }
    }

    /** Filter the whole recipe list — see the note on this class for which routes are gated. */
    @Inject(method = "getRecipes()Ljava/util/Collection;", at = @At("RETURN"), cancellable = true, remap = true)
    private void filterGetRecipes(CallbackInfoReturnable<Collection<Recipe<?>>> cir) {
        if (!isServerRecipeManager()) return;
        cir.setReturnValue(VisibleRecipes.all(this, cir.getReturnValue()));
    }

    /**
     * Filter one recipe type's list — the same route one size smaller, and the one most modded
     * machines take. Create reaches it through sequenced assembly and through applying an item to
     * a block by hand.
     */
    @Inject(method = "getAllRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, remap = true)
    private <C extends Container, T extends Recipe<C>> void filterGetAllRecipesFor(
            RecipeType<T> type, CallbackInfoReturnable<List<T>> cir) {
        if (!isServerRecipeManager()) return;
        cir.setReturnValue(VisibleRecipes.ofType(this, type, cir.getReturnValue()));
    }

    /**
     * Whether this is the server's recipe manager rather than a client's copy.
     *
     * <p>The two hooks above take no level and no player, so nothing in their arguments says
     * which side is asking — and the answer has to differ. On the server the list is what a
     * machine is allowed to make. On the client the same list is what the player is allowed to
     * <em>see</em>: JEI and EMI draw locked recipes with a lock on them, and the editor's picker
     * has to find a recipe that is already locked or nobody could ever unlock it again. Filtering
     * there would break both and gain nothing — the server decides what actually gets crafted.
     */
    private boolean isServerRecipeManager() {
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && server.getRecipeManager() == (Object) this;
    }

    private static boolean isRecipeLocked(Recipe<?> recipe, boolean isClientSide) {
        return RecipeHandler.isOutputLocked(recipe, isClientSide) || RecipeHandler.isRecipeIdLocked(recipe.getId(), isClientSide);
    }
}
