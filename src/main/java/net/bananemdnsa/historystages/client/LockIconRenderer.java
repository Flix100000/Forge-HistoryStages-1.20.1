package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Shared lock-icon logic used by both the vanilla-inventory {@link LockDecorator}
 * (a NeoForge {@code IItemDecorator}) and the EMI ingredient-panel mixin, so the
 * icon selection and drawing stay identical across surfaces.
 */
public final class LockIconRenderer {
    private static final ResourceLocation LOCK_ICON            = ResourceLocation.fromNamespaceAndPath("historystages", "textures/gui/lock_overlay_global.png");
    private static final ResourceLocation SILVER_LOCK_ICON     = ResourceLocation.fromNamespaceAndPath("historystages", "textures/gui/lock_overlay_individual.png");
    private static final ResourceLocation DUAL_PHASE_LOCK_ICON = ResourceLocation.fromNamespaceAndPath("historystages", "textures/gui/lock_overlay_dual.png");

    private LockIconRenderer() {}

    /**
     * Returns the lock-icon texture to draw on the given stack for the local player,
     * or {@code null} if no icon should be shown (feature disabled, empty stack, or
     * stack not locked). Honours the {@code showLockIcons} / {@code showSilverLockIcons}
     * config and distinguishes global, individual, and dual-phase locks.
     */
    public static ResourceLocation iconFor(ItemStack stack) {
        if (!Config.CLIENT.showLockIcons.get()) return null;
        if (stack == null || stack.isEmpty()) return null;

        boolean globallyLocked     = StageLockHelper.isActionLockedForClient(stack, "icon");
        boolean dualPhaseGlobal    = globallyLocked && StageLockHelper.isDualPhaseGloballyLockedClient(stack);
        boolean individuallyLocked = !globallyLocked && Config.CLIENT.showSilverLockIcons.get()
                && StageLockHelper.isActionLockedByIndividualStageClient(stack, "icon");

        if (globallyLocked || individuallyLocked) {
            return dualPhaseGlobal ? DUAL_PHASE_LOCK_ICON
                 : individuallyLocked ? SILVER_LOCK_ICON
                 : LOCK_ICON;
        }
        return null;
    }

    /** Draws the given lock icon as an 8x8 overlay at the slot's top-left corner. */
    public static void draw(GuiGraphics guiGraphics, ResourceLocation icon, int x, int y) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 250);
        guiGraphics.pose().scale(0.25f, 0.25f, 1.0f);
        guiGraphics.blit(icon, 0, 0, 0, 0, 32, 32, 32, 32);
        guiGraphics.pose().popPose();
    }
}
