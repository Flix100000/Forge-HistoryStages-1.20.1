package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

/**
 * Full-screen red tint drawn over the HUD when the local player's position lies inside any of
 * the lock shapes cached by {@link LockBorderClientCache}. Looks like the player is wearing
 * red glasses — a constant signal that they're inside a locked structure.
 *
 * <p>Containment is checked client-side against the cached shapes. No extra packet needed:
 * the same shape list that drives the wall force-field also drives this overlay, so the two
 * stay in sync automatically.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class LockOverlayRenderer {

    private LockOverlayRenderer() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!Config.CLIENT.structureLockOverlayEnabled.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return;
        if (mc.options.hideGui) return;

        if (!isInsideLockedShape(player.getX(), player.getY(), player.getZ())) return;

        double opacity = Config.CLIENT.structureLockOverlayOpacity.get();
        if (opacity <= 0.0) return;

        int alpha = (int) Math.round(Math.min(1.0, opacity) * 255.0);
        // ARGB: high alpha + saturated red; GuiGraphics#fill takes 0xAARRGGBB.
        int color = (alpha << 24) | 0x00C00000;

        GuiGraphics gg = event.getGuiGraphics();
        gg.fill(0, 0, gg.guiWidth(), gg.guiHeight(), color);
    }

    private static boolean isInsideLockedShape(double x, double y, double z) {
        List<BoundingBox> boxes = LockBorderClientCache.get();
        for (BoundingBox b : boxes) {
            if (x > b.minX() && x < b.maxX() + 1.0
                    && y > b.minY() && y < b.maxY() + 1.0
                    && z > b.minZ() && z < b.maxZ() + 1.0) {
                return true;
            }
        }
        return false;
    }
}
