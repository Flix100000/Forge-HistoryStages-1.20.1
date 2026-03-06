package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class LockDecorator implements IItemDecorator {
    // Hier definieren wir den Pfad zur Textur: assets/historystages/textures/gui/lock_overlay.png
    private static final ResourceLocation LOCK_ICON = new ResourceLocation("historystages", "textures/gui/lock_overlay.png");

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

        // 3. Logik-Abfrage (Nutzt deine vorhandenen Methoden)
        if (isLocked(stack)) {
            guiGraphics.pose().pushPose();

            // Z-Achse nach vorne (250), damit es über dem Item, aber unter dem Tooltip liegt.
            // Dann skalieren wir die 32x32 Textur auf 8x8 (oberer linker Quadrant des 16x16 Slots).
            guiGraphics.pose().translate(xOffset, yOffset, 250);
            guiGraphics.pose().scale(0.25f, 0.25f, 1.0f);

            // ZEICHNEN DER TEXTUR (Position 0,0 da Translation bereits gesetzt)
            guiGraphics.blit(LOCK_ICON, 0, 0, 0, 0, 32, 32, 32, 32);

            guiGraphics.pose().popPose();
            return false; // false, damit Haltbarkeitsbalken etc. noch gezeichnet werden
        }

        return false;
    }

    private boolean isLocked(ItemStack stack) {
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (res == null) return false;

        // Nutzt StageManager (Logik-Prüfung)
        List<String> requiredStages = StageManager.getAllStagesForItemOrMod(res.toString(), res.getNamespace());

        if (requiredStages.isEmpty()) return false;

        for (String stage : requiredStages) {
            // Nutzt ClientStageCache (Freischaltungs-Prüfung)
            if (!ClientStageCache.isStageUnlocked(stage)) {
                return true;
            }
        }
        return false;
    }
}