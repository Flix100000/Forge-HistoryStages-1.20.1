package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class ClientToastHandler {
    public static void showToast(String stageName) {
        showToast(stageName, "");
    }

    public static void showToast(String stageName, String iconId) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack icon = resolveIcon(iconId);
        mc.execute(() -> mc.getToasts().addToast(new StageUnlockedToast(stageName, icon)));
    }

    private static ItemStack resolveIcon(String iconId) {
        String id = (iconId != null && !iconId.isEmpty())
                ? iconId
                : Config.COMMON.defaultStageIcon.get();
        if (id != null && !id.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    return new ItemStack(item);
                }
            }
        }
        return new ItemStack(ModItems.RESEARCH_SCROLL.get());
    }
}
