package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.fml.ModList;

import java.util.List;

public class LockDecorator implements IItemDecorator {
    private static final ResourceLocation LOCK_ICON = ResourceLocation.fromNamespaceAndPath("historystages", "textures/gui/lock_overlay.png");
    private static final ResourceLocation SILVER_LOCK_ICON = ResourceLocation.fromNamespaceAndPath("historystages", "textures/gui/lock_overlay_silver.png");

    private static final boolean IS_EMI_INSTALLED = ModList.get().isLoaded("emi");

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        if (IS_EMI_INSTALLED || !Config.CLIENT.showLockIcons.get()) {
            return false;
        }

        if (stack == null || stack.isEmpty()) {
            return false;
        }

        boolean globallyLocked = isGloballyLocked(stack);
        boolean individuallyLocked = !globallyLocked && Config.CLIENT.showSilverLockIcons.get() && isIndividuallyLocked(stack);

        if (globallyLocked || individuallyLocked) {
            ResourceLocation icon = individuallyLocked ? SILVER_LOCK_ICON : LOCK_ICON;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(xOffset, yOffset, 250);
            guiGraphics.pose().scale(0.25f, 0.25f, 1.0f);
            guiGraphics.blit(icon, 0, 0, 0, 0, 32, 32, 32, 32);
            guiGraphics.pose().popPose();
            return false;
        }

        return false;
    }

    private boolean isGloballyLocked(ItemStack stack) {
        ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (res == null) return false;

        List<String> requiredStages = StageManager.getAllStagesForItemOrMod(res.toString(), res.getNamespace(), stack);
        if (requiredStages.isEmpty()) return false;

        for (String stage : requiredStages) {
            if (!ClientStageCache.isStageUnlocked(stage)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIndividuallyLocked(ItemStack stack) {
        return StageLockHelper.isItemLockedByIndividualStageClient(stack);
    }
}
