package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

import static net.bananemdnsa.historystages.util.ResourceLocationHelper.MOD_RESOURCE_LOCATION;

public class LockDecorator implements IItemDecorator {
    // Hier definieren wir den Pfad zur Textur: assets/historystages/textures/gui/lock_overlay.png
    private static final ResourceLocation LOCK_ICON = MOD_RESOURCE_LOCATION("textures/gui/lock_overlay.png");
    private static final ResourceLocation SILVER_LOCK_ICON = MOD_RESOURCE_LOCATION("textures/gui/lock_overlay_silver.png");

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

        // 3. Check global lock first, then individual lock
        boolean globallyLocked = isGloballyLocked(stack);
        boolean individuallyLocked = !globallyLocked && Config.CLIENT.showSilverLockIcons.get() && isIndividuallyLocked(stack);

        if (globallyLocked || individuallyLocked) {
            ResourceLocation icon = individuallyLocked ? SILVER_LOCK_ICON : LOCK_ICON;

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
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
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