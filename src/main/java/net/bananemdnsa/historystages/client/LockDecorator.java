package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.fml.ModList;

public class LockDecorator implements IItemDecorator {
    private static final ResourceLocation LOCK_ICON            = new ResourceLocation("historystages", "textures/gui/lock_overlay_global.png");
    private static final ResourceLocation SILVER_LOCK_ICON     = new ResourceLocation("historystages", "textures/gui/lock_overlay_individual.png");
    private static final ResourceLocation DUAL_PHASE_LOCK_ICON = new ResourceLocation("historystages", "textures/gui/lock_overlay_dual.png");

    // Performance-Check für EMI
    private static final boolean IS_EMI_INSTALLED = ModList.get().isLoaded("emi");

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        // 1. Abbruch-Bedingungen (EMI oder Config)
        if (IS_EMI_INSTALLED || !Config.CLIENT.showLockIcons.get()) {
            return false;
        }

        // 2. Sicherheitscheck
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // 3. Determine which lock icon to show (dual-phase > global > individual)
        boolean globallyLocked     = isGloballyLocked(stack);
        boolean dualPhaseGlobal    = globallyLocked && StageLockHelper.isDualPhaseGloballyLockedClient(stack);
        boolean individuallyLocked = !globallyLocked && Config.CLIENT.showSilverLockIcons.get() && isIndividuallyLocked(stack);

        if (globallyLocked || individuallyLocked) {
            ResourceLocation icon = dualPhaseGlobal ? DUAL_PHASE_LOCK_ICON
                                  : individuallyLocked ? SILVER_LOCK_ICON
                                  : LOCK_ICON;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(xOffset, yOffset, 250);
            // Both locks are 32x32 textures, scaled to 8x8 pixels
            guiGraphics.pose().scale(0.25f, 0.25f, 1.0f);
            guiGraphics.blit(icon, 0, 0, 0, 0, 32, 32, 32, 32);
            guiGraphics.pose().popPose();
            return false;
        }

        return false;
    }

    private boolean isGloballyLocked(ItemStack stack) {
        return StageLockHelper.isActionLockedForClient(stack, "icon");
    }

    private boolean isIndividuallyLocked(ItemStack stack) {
        return StageLockHelper.isActionLockedByIndividualStageClient(stack, "icon");
    }
}