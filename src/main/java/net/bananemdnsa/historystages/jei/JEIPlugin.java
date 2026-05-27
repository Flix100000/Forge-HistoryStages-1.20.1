package net.bananemdnsa.historystages.jei;

import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
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
            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            if (tag.contains("StageResearch")) {
                return tag.getString("StageResearch");
            }
            return IIngredientSubtypeInterpreter.NONE;
        };
        registration.registerSubtypeInterpreter(ModItems.RESEARCH_SCROLL.get(), interpreter);
        registration.registerSubtypeInterpreter(ModItems.CREATIVE_SCROLL.get(), interpreter);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // Add one scroll variant per stage so they appear in JEI
        List<ItemStack> scrolls = new ArrayList<>();

        for (String stageId : StageManager.getStages().keySet()) {
            ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL.get());
            CompoundTag nbt = new CompoundTag();
            nbt.putString("StageResearch", stageId);
            scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            scrolls.add(scroll);
        }

        for (String stageId : StageManager.getIndividualStages().keySet()) {
            ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL.get());
            CompoundTag nbt = new CompoundTag();
            nbt.putString("StageResearch", stageId);
            scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            scrolls.add(scroll);
        }

        if (!scrolls.isEmpty()) {
            jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, scrolls);
            LOGGER.info("[HistoryStages] Added {} research scroll variants to JEI.", scrolls.size());
        }

        // --- Issue #64: initial hide pass ---
        RUNTIME = jeiRuntime;
        REFRESHER = new LockedJeiRefresher(new RuntimeOps(jeiRuntime));
        try {
            boolean hideItems = Config.CLIENT.hideLockedItemsInJei.get();
            boolean hideRecipes = Config.CLIENT.hideLockedRecipesInJei.get();
            REFRESHER.applyInitial(hideItems, () -> computeLockedItems(jeiRuntime.getIngredientManager()));
            applyRecipeHiding(hideRecipes, jeiRuntime);
            LOGGER.info("[HistoryStages/JEI] Initial hide pass complete (items={}, recipes={}).", hideItems, hideRecipes);
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
            boolean hideItems = Config.CLIENT.hideLockedItemsInJei.get();
            boolean hideRecipes = Config.CLIENT.hideLockedRecipesInJei.get();
            r.applyDiff(hideItems, () -> computeLockedItems(runtime.getIngredientManager()));
            applyRecipeHiding(hideRecipes, runtime);
        } catch (Exception e) {
            LOGGER.warn("[HistoryStages/JEI] applyDiff failed", e);
        }
    }

    private static Set<ItemStack> computeLockedItems(IIngredientManager mgr) {
        List<ItemStack> all = new ArrayList<>(mgr.getAllItemStacks());
        return LockedJeiVisibility.computeLockedItems(all, Config.CLIENT.lockedItemMultiStagePolicy.get());
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.getJeiHelpers().getAllRecipeTypes().forEach(recipeType -> {
            registration.addRecipeCategoryDecorator((mezz.jei.api.recipe.RecipeType) recipeType, new LockedRecipeDecorator<>());
        });
        LOGGER.info("[HistoryStages] Registered locked recipe decorators for all JEI recipe types.");
    }
}
