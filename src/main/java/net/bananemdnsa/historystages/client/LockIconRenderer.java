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
    private static final ResourceLocation LOCK_ICON            = ResourceLocation.fromNamespaceAndPath("historystages", "textures/gui/lock/overlay_global.png");
    private static final ResourceLocation SILVER_LOCK_ICON     = ResourceLocation.fromNamespaceAndPath("historystages", "textures/gui/lock/overlay_individual.png");
    private static final ResourceLocation DUAL_PHASE_LOCK_ICON = ResourceLocation.fromNamespaceAndPath("historystages", "textures/gui/lock/overlay_dual.png");

    private LockIconRenderer() {}

    /**
     * Returns the lock-icon texture to draw on the given stack for the local player,
     * or {@code null} if no icon should be shown (feature disabled, empty stack, or
     * stack not locked). Honours the {@code showLockIcons} / {@code showSilverLockIcons}
     * config and distinguishes global, individual, and dual-phase locks.
     */
    public static ResourceLocation iconFor(ItemStack stack) {
        if (!Config.VISUAL.showLockIcons.get()) return null;
        if (stack == null || stack.isEmpty()) return null;

        boolean globallyLocked     = StageLockHelper.isActionLockedForClient(stack, "icon");
        boolean dualPhaseGlobal    = globallyLocked && StageLockHelper.isDualPhaseGloballyLockedClient(stack);
        boolean individuallyLocked = !globallyLocked && Config.VISUAL.showSilverLockIcons.get()
                && StageLockHelper.isActionLockedByIndividualStageClient(stack, "icon");

        if (globallyLocked || individuallyLocked) {
            return dualPhaseGlobal ? DUAL_PHASE_LOCK_ICON
                 : individuallyLocked ? SILVER_LOCK_ICON
                 : LOCK_ICON;
        }
        return null;
    }

    /**
     * The lock-icon texture for a gated fluid, or {@code null} when none should be drawn.
     *
     * <p>A separate entry point because a fluid in a recipe viewer is not an {@link ItemStack}
     * and never passes through {@link #iconFor}: the vanilla decorator that draws the overlay is
     * an item decorator, so a fluid slot got nothing at all until this existed.
     *
     * <p>No dual-phase icon. That distinction is answered from a stack, and the fluid question
     * has none — a fluid gated globally and individually at once shows the global lock, which is
     * the truthful half of the answer rather than a guess at the other.
     */
    public static ResourceLocation iconForFluid(String fluidId) {
        if (!Config.VISUAL.showLockIcons.get()) return null;
        if (fluidId == null) return null;

        boolean globallyLocked = StageLockHelper.isFluidActionLockedForClient(fluidId, "icon");
        boolean individuallyLocked = !globallyLocked && Config.VISUAL.showSilverLockIcons.get()
                && StageLockHelper.isFluidActionLockedByIndividualStageClient(fluidId, "icon");

        if (globallyLocked) return LOCK_ICON;
        if (individuallyLocked) return SILVER_LOCK_ICON;
        return null;
    }

    /**
     * The three icons, for a surface that has already decided which one it wants.
     *
     * <p>{@link #iconFor} answers "is this stack locked, and how" and is the right entry point for
     * anything drawing over an item. A notice explaining an empty window has no stack to ask
     * about — it has been told the answer by the server.
     */
    public static ResourceLocation globalIcon() {
        return LOCK_ICON;
    }

    public static ResourceLocation individualIcon() {
        return SILVER_LOCK_ICON;
    }

    public static ResourceLocation dualIcon() {
        return DUAL_PHASE_LOCK_ICON;
    }

    /**
     * Draws a lock icon at a chosen size.
     *
     * <p>{@link #draw} stays as it is. It draws 8x8 because it sits in the corner of an item slot,
     * and every caller of it wants exactly that; a notice in the middle of an empty window has no
     * slot setting its size.
     */
    public static void drawSized(GuiGraphics guiGraphics, ResourceLocation icon,
                                 int x, int y, int size) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 250);
        float scale = size / 32.0f;
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.blit(icon, 0, 0, 0, 0, 32, 32, 32, 32);
        guiGraphics.pose().popPose();
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
