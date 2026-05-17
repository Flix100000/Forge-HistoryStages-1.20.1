package net.bananemdnsa.historystages.client;

import net.minecraft.client.Minecraft;

public final class OptionalRecipeViewHooks {
    private OptionalRecipeViewHooks() {
    }

    public static void refreshAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (!minecraft.isSameThread()) {
            minecraft.execute(OptionalRecipeViewHooks::refreshAll);
            return;
        }

        RecipeViewerVisibility.invalidateCache();
    }
}
