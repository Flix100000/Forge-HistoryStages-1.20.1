package net.bananemdnsa.historystages.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class JeiVisibilityManager {
    private static IJeiRuntime runtime;
    private static final List<ItemStack> hiddenStacks = new ArrayList<>();

    private JeiVisibilityManager() {
    }

    public static void setRuntime(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        refresh();
    }

    public static void clearRuntime() {
        runtime = null;
        hiddenStacks.clear();
    }

    public static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (!minecraft.isSameThread()) {
            minecraft.execute(JeiVisibilityManager::refresh);
            return;
        }

        IJeiRuntime jeiRuntime = runtime;
        if (jeiRuntime == null) {
            return;
        }

        IIngredientManager ingredientManager = jeiRuntime.getIngredientManager();
        if (!hiddenStacks.isEmpty()) {
            ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.copyOf(hiddenStacks));
            hiddenStacks.clear();
        }

        if (!Config.CLIENT.hideInJei) {
            return;
        }

        Collection<ItemStack> allStacks = ingredientManager.getAllItemStacks();
        if (allStacks.isEmpty()) {
            return;
        }

        List<ItemStack> lockedStacks = new ArrayList<>();
        for (ItemStack stack : allStacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            ItemStack copy = stack.copy();
            if (StageLockHelper.isItemLockedForClient(copy)) {
                lockedStacks.add(copy);
            }
        }

        if (lockedStacks.isEmpty()) {
            return;
        }

        ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, lockedStacks);
        hiddenStacks.addAll(lockedStacks);
    }
}
