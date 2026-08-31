package net.bananemdnsa.historystages.compat.jei;

import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.compat.ScrollVariants;
import net.bananemdnsa.historystages.compat.StageDisplayPath;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    // --- Issue #64: hide-locked state ---
    private static volatile LockedJeiRefresher REFRESHER;
    private static volatile IJeiRuntime RUNTIME;
    /** Item IDs whose hidden-display name was active last pass — used to detect name changes. */
    private static volatile Set<String> prevNameAffectedIds = Set.of();
    /** Tracks which recipes WE hid last pass, so we can unhide them on next refresh. */
    private static final Map<RecipeType<?>, List<Object>> currentlyHiddenRecipes = new HashMap<>();

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Tell JEI that scrolls with different StageResearch values are different items
        IIngredientSubtypeInterpreter<ItemStack> interpreter = (stack, context) -> {
            String stage = ScrollVariants.readStageResearch(stack);
            return stage != null ? stage : IIngredientSubtypeInterpreter.NONE;
        };
        registration.registerSubtypeInterpreter(ModItems.RESEARCH_SCROLL.get(), interpreter);
        registration.registerSubtypeInterpreter(ModItems.CREATIVE_SCROLL.get(), interpreter);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // Add one scroll variant per stage so they appear in JEI
        List<ItemStack> scrolls = ScrollVariants.buildAllStageScrolls();

        if (!scrolls.isEmpty()) {
            jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, scrolls);
            LOGGER.info("[HistoryStages] Added {} research scroll variants to JEI.", scrolls.size());
        }

        // --- Issue #64: initial hide pass ---
        RUNTIME = jeiRuntime;
        REFRESHER = new LockedJeiRefresher(new RuntimeOps(jeiRuntime));
        try {
            boolean hideItems = Config.VISUAL.hideLockedItemsInJei.get();
            boolean hideRecipes = Config.VISUAL.hideLockedRecipesInJei.get();
            // The count, not just the switches. A pass that reports items=true and then hides
            // nothing looks identical in the log to one that worked, and telling those two apart
            // is the whole question when items come back after a stage unlock.
            java.util.concurrent.atomic.AtomicInteger lockedCount = new java.util.concurrent.atomic.AtomicInteger(-1);
            REFRESHER.applyInitial(hideItems, () -> {
                Set<ItemStack> locked = computeLockedItems(jeiRuntime.getIngredientManager());
                lockedCount.set(locked.size());
                return locked;
            });
            applyRecipeHiding(hideRecipes, jeiRuntime);
            // Seed the name-affected baseline without churn (items were already added with the
            // replaced name in effect).
            prevNameAffectedIds = computeNameAffectedIds(jeiRuntime.getIngredientManager());
            LOGGER.info("[HistoryStages/JEI] Initial hide pass complete (items={}, recipes={}, lockedItems={}).", hideItems, hideRecipes, lockedCount.get());
        } catch (Exception e) {
            LOGGER.warn("[HistoryStages/JEI] Initial hide pass failed", e);
        }
    }

    /**
     * Re-applies the hide-state to match current config + stage cache.
     * Called from network handlers (after stage sync) and from the config editor (after save).
     * Null-safe — no-op if JEI is not yet ready or not installed.
     */
    public static void tryApplyDiff() {
        LockedJeiRefresher r = REFRESHER;
        IJeiRuntime runtime = RUNTIME;
        if (r == null || runtime == null) return;

        try {
            boolean hideItems = Config.VISUAL.hideLockedItemsInJei.get();
            boolean hideRecipes = Config.VISUAL.hideLockedRecipesInJei.get();
            r.applyDiff(hideItems, () -> computeLockedItems(runtime.getIngredientManager()));
            applyRecipeHiding(hideRecipes, runtime);
            // Re-read items whose hidden-display name just changed (e.g. a stage unlocked),
            // so JEI's cached names + search index reflect the new name.
            refreshNameChangedItems(runtime);
            // Kept from the debugging round: says outright how much is hidden after a stage
            // change, which is the number nobody had when JEI last misbehaved here.
            LOGGER.info("[HistoryStages/JEI] Stage change applied (items={}, stillHidden={}).",
                    hideItems, r.currentlyHiddenItems().size());
        } catch (Exception e) {
            LOGGER.warn("[HistoryStages/JEI] applyDiff failed", e);
        }
    }

    /** Item IDs of currently-visible ingredients whose hidden-display name is active. */
    private static Set<String> computeNameAffectedIds(IIngredientManager mgr) {
        Set<String> ids = new HashSet<>();
        for (ItemStack stack : mgr.getAllItemStacks()) {
            if (stack.isEmpty()) continue;
            if (net.bananemdnsa.historystages.client.display.HiddenDisplayResolver.resolve(stack).changesName()) {
                ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (rl != null) ids.add(rl.toString());
            }
        }
        return ids;
    }

    /**
     * Forces JEI to re-read the names/search index of items whose hidden-display name changed
     * since the last pass (entered or left the name-affected set). Only touches currently-visible
     * ingredients — hidden items are managed by {@link #tryApplyDiff}'s hide diff.
     */
    private static void refreshNameChangedItems(IJeiRuntime runtime) {
        IIngredientManager mgr = runtime.getIngredientManager();
        Set<String> nowIds = computeNameAffectedIds(mgr);

        Set<String> changed = new HashSet<>();
        for (String id : nowIds) if (!prevNameAffectedIds.contains(id)) changed.add(id);
        for (String id : prevNameAffectedIds) if (!nowIds.contains(id)) changed.add(id);
        prevNameAffectedIds = nowIds;
        if (changed.isEmpty()) return;

        Set<ItemStack> refresh = new HashSet<>();
        for (ItemStack stack : mgr.getAllItemStacks()) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (rl != null && changed.contains(rl.toString())) refresh.add(stack);
        }
        if (refresh.isEmpty()) return;
        try {
            mgr.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, refresh);
            mgr.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, refresh);
        } catch (Exception e) {
            LOGGER.warn("[HistoryStages/JEI] name refresh failed", e);
        }
    }

    private static Set<ItemStack> computeLockedItems(IIngredientManager mgr) {
        List<ItemStack> all = new ArrayList<>(mgr.getAllItemStacks());
        return LockedJeiVisibility.computeLockedItems(all, Config.VISUAL.lockedItemMultiStagePolicy.get());
    }

    /**
     * Hides/un-hides recipes whose OUTPUT is a locked item.
     * Only recipes whose recipe object is a {@link RecipeHolder} (vanilla pattern: crafting,
     * smelting, blasting, smoking, campfire, stonecutting, smithing) are matched. Modded
     * categories with custom recipe types fall through to the lock-overlay decorator.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyRecipeHiding(boolean enabled, IJeiRuntime runtime) {
        IRecipeManager rm = runtime.getRecipeManager();

        // First: unhide everything we hid last pass.
        synchronized (currentlyHiddenRecipes) {
            for (Map.Entry<RecipeType<?>, List<Object>> entry : currentlyHiddenRecipes.entrySet()) {
                try {
                    rm.unhideRecipes((RecipeType) entry.getKey(), (List) entry.getValue());
                } catch (Exception e) {
                    LOGGER.warn("[HistoryStages/JEI] unhideRecipes failed for {}: {}", entry.getKey(), e.toString());
                }
            }
            currentlyHiddenRecipes.clear();
        }

        if (!enabled || REFRESHER == null) return;
        Set<ItemStack> lockedItems = REFRESHER.currentlyHiddenItems();
        if (lockedItems.isEmpty()) {
            // If items-hide is off but recipes-hide is on, we still need locked items.
            lockedItems = computeLockedItems(runtime.getIngredientManager());
            if (lockedItems.isEmpty()) return;
        }

        final Set<ItemStack> lockedFinal = lockedItems;
        RegistryAccess registryAccess = RegistryAccess.EMPTY;

        runtime.getJeiHelpers().getAllRecipeTypes().forEach(type ->
                hideForType(rm, (RecipeType) type, lockedFinal, registryAccess));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <R> void hideForType(IRecipeManager rm, RecipeType<R> type,
                                         Set<ItemStack> lockedItems, RegistryAccess registryAccess) {
        try {
            List<R> all = rm.createRecipeLookup(type).includeHidden().get().toList();
            List<R> toHide = LockedJeiVisibility.filterRecipesWithLockedOutput(
                    all,
                    recipe -> extractOutputs(recipe, registryAccess),
                    lockedItems);
            if (toHide.isEmpty()) return;
            rm.hideRecipes(type, toHide);
            synchronized (currentlyHiddenRecipes) {
                currentlyHiddenRecipes.put(type, (List) new ArrayList<>(toHide));
            }
        } catch (Exception e) {
            LOGGER.warn("[HistoryStages/JEI] hideForType {} failed: {}", type, e.toString());
        }
    }

    /**
     * Best-effort output extraction. Covers vanilla {@link RecipeHolder} pattern
     * (crafting, smelting, blasting, smoking, campfire, stonecutting, smithing).
     * Modded recipe types with custom recipe objects return empty — the lock
     * overlay decorator still works for them.
     */
    private static List<ItemStack> extractOutputs(Object recipe, RegistryAccess registryAccess) {
        if (recipe instanceof RecipeHolder<?> holder) {
            try {
                ItemStack out = holder.value().getResultItem(registryAccess);
                if (out != null && !out.isEmpty()) return List.of(out);
            } catch (Exception ignored) {
                // Some modded recipes throw if registryAccess is empty — skip them.
            }
        }
        return List.of();
    }

    /** Adapter from {@link LockedJeiRefresher.JeiOps} to the JEI runtime. */
    private record RuntimeOps(IJeiRuntime runtime) implements LockedJeiRefresher.JeiOps {
        @Override
        public void removeItems(Set<ItemStack> items) {
            if (items.isEmpty()) return;
            try {
                runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, items);
            } catch (Exception e) {
                LOGGER.warn("[HistoryStages/JEI] removeIngredientsAtRuntime failed", e);
            }
        }
        @Override
        public void addItems(Set<ItemStack> items) {
            if (items.isEmpty()) return;
            try {
                runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, items);
            } catch (Exception e) {
                LOGGER.warn("[HistoryStages/JEI] addIngredientsAtRuntime failed", e);
            }
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new BoosterRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<BoosterRecipe> recipes = new ArrayList<>();
        ResearchBoosterRegistry.forEachStack((stack, booster) -> recipes.add(new BoosterRecipe(
                stack,
                BoosterUtil.percent(booster.speedReduction()),
                BoosterUtil.percent(booster.costReduction()))));
        registration.addRecipes(BoosterRecipeCategory.TYPE, recipes);

        // The resealing recipe is a special recipe with no declared ingredients, so JEI cannot
        // derive it from the recipe manager — one shapeless stand-in per stage is added by hand.
        // These are display only; the real matching still happens in ResealScrollRecipe.
        if (net.bananemdnsa.historystages.Config.GAMEPLAY.enableScrollResealing.get()) {
            List<RecipeHolder<CraftingRecipe>> reseal = new ArrayList<>();
            for (String stageId : ScrollVariants.scrollableStageIds()) {
                NonNullList<Ingredient> inputs = NonNullList.create();
                inputs.add(Ingredient.of(ScrollVariants.createOpenScroll(stageId)));
                inputs.add(Ingredient.of(Items.PAPER));
                reseal.add(new RecipeHolder<>(
                        ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID,
                                "reseal_scroll/" + StageDisplayPath.of(stageId)),
                        new ShapelessRecipe("", CraftingBookCategory.MISC,
                                ScrollVariants.createScroll(stageId), inputs)));
            }
            registration.addRecipes(RecipeTypes.CRAFTING, reseal);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Catalyst on the pedestal itself + each booster block so right-click opens this category.
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.RESEARCH_PEDESTAL.get()), BoosterRecipeCategory.TYPE);
        ResearchBoosterRegistry.forEachStack((stack, booster) ->
                registration.addRecipeCatalyst(stack, BoosterRecipeCategory.TYPE));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.getJeiHelpers().getAllRecipeTypes().forEach(recipeType -> {
            registration.addRecipeCategoryDecorator((mezz.jei.api.recipe.RecipeType) recipeType, new LockedRecipeDecorator<>());
        });
        LOGGER.info("[HistoryStages] Registered locked recipe decorators for all JEI recipe types.");
    }
}
