package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ClientToastHandler {

    /** Shows a toast using the default icon (research scroll). */
    public static void showToast(String stageName) {
        showToast(stageName, "");
    }

    /** Shows a toast using the given icon item ID (falls back to the default if invalid). */
    public static void showToast(String stageName, String iconId) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack icon = resolveIcon(iconId);
        mc.execute(() -> mc.getToasts().addToast(new StageUnlockedToast(stageName, icon)));
    }

    /**
     * Resolves the icon item. Priority:
     *   1. iconId from the packet (stage-specific icon)
     *   2. defaultStageIcon from common config
     *   3. Hardcoded fallback: research scroll
     */
    public static ItemStack resolveIcon(String iconId) {
        if (iconId != null && !iconId.isEmpty()) {
            ItemStack resolved = tryParseItem(iconId);
            if (resolved != null) return resolved;
        }
        // Try config default
        try {
            String configDefault = Config.COMMON.defaultStageIcon.get();
            if (configDefault != null && !configDefault.isEmpty()) {
                ItemStack resolved = tryParseItem(configDefault);
                if (resolved != null) return resolved;
            }
        } catch (Exception ignored) {}
        return fallbackIcon();
    }

    private static ItemStack tryParseItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == Items.AIR) return null;
        return new ItemStack(item);
    }

    private static ItemStack fallbackIcon() {
        return new ItemStack(ModItems.RESEARCH_SCROLL.get());
    }
}
