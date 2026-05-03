package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class LockOverlayRenderer {
    private static final ResourceLocation LOCK_ICON = HistoryStages.id("textures/gui/lock_overlay.png");
    private static final ResourceLocation SILVER_LOCK_ICON = HistoryStages.id("textures/gui/lock_overlay_silver.png");

    private LockOverlayRenderer() {
    }

    public static void render(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty() || !Config.CLIENT.showLockIcons) {
            return;
        }

        boolean globallyLocked = isGloballyLocked(stack);
        boolean individuallyLocked = !globallyLocked
                && Config.CLIENT.showSilverLockIcons
                && StageLockHelper.isItemLockedByIndividualStageClient(stack);

        if (!globallyLocked && !individuallyLocked) {
            return;
        }

        ResourceLocation icon = individuallyLocked ? SILVER_LOCK_ICON : LOCK_ICON;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 250);
        guiGraphics.pose().scale(0.25f, 0.25f, 1.0f);
        guiGraphics.blit(icon, 0, 0, 0, 0, 32, 32, 32, 32);
        guiGraphics.pose().popPose();
    }

    private static boolean isGloballyLocked(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return false;
        }

        List<String> requiredStages = StageManager.getAllStagesForItemOrMod(itemId.toString(), itemId.getNamespace(), stack);
        if (requiredStages.isEmpty()) {
            return false;
        }

        for (String stage : requiredStages) {
            if (!ClientStageCache.isStageUnlocked(stage)) {
                return true;
            }
        }
        return false;
    }
}
