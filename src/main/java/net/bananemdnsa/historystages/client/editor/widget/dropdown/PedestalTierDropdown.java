package net.bananemdnsa.historystages.client.editor.widget.dropdown;

import net.bananemdnsa.historystages.init.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.IntConsumer;

/**
 * Compact pedestal-tier picker. Collapsed shows the current tier's icon + name
 * with a ▾; expanded shows one row per tier (1..4). Single-select.
 *
 * <p>Render order: call {@link #renderButton} during normal widget rendering,
 * {@link #renderPopup} AFTER all other widgets, and route mouse clicks through
 * {@link #mouseClicked} BEFORE other widgets.</p>
 */
public class PedestalTierDropdown {
    public static final int BUTTON_HEIGHT = 18;
    private static final int ROW_HEIGHT = 18;
    private static final int POPUP_PAD = 2;

    private final IntConsumer onChange;
    private int tier;
    private int buttonX, buttonY, buttonW;
    private boolean expanded = false;

    public PedestalTierDropdown(int initialTier, int buttonWidth, IntConsumer onChange) {
        this.tier = clamp(initialTier);
        this.buttonW = buttonWidth;
        this.onChange = onChange;
    }

    public int getTier() { return tier; }

    public void setTier(int tier) { this.tier = clamp(tier); }

    public boolean isExpanded() { return expanded; }

    public void close() { expanded = false; }

    public void setPosition(int x, int y) {
        this.buttonX = x;
        this.buttonY = y;
    }

    public boolean isMouseOver(double mx, double my) {
        if (mx >= buttonX && mx < buttonX + buttonW && my >= buttonY && my < buttonY + BUTTON_HEIGHT) {
            return true;
        }
        if (!expanded) return false;
        int[] g = popupGeometry();
        return mx >= g[0] && mx < g[0] + g[2] && my >= g[1] && my < g[1] + g[3];
    }

    public void renderButton(GuiGraphics g, Font font, int mouseX, int mouseY) {
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonW
                && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT;
        int border = expanded ? 0xFFFFCC00 : (hovered ? 0xFF888888 : 0xFF4A4A4A);
        int bg = hovered ? 0xFF252525 : 0xFF0D0D0D;
        g.fill(buttonX, buttonY, buttonX + buttonW, buttonY + BUTTON_HEIGHT, border);
        g.fill(buttonX + 1, buttonY + 1, buttonX + buttonW - 1, buttonY + BUTTON_HEIGHT - 1, bg);

        drawRow(g, font, buttonX + 2, buttonY + 1, tier, false);

        // Caret on the right
        int cx = buttonX + buttonW - 7;
        int cy = buttonY + BUTTON_HEIGHT / 2 - 1;
        int caret = hovered ? 0xFFDDDDDD : 0xFF999999;
        g.fill(cx, cy, cx + 5, cy + 1, caret);
        g.fill(cx + 1, cy + 1, cx + 4, cy + 2, caret);
        g.fill(cx + 2, cy + 2, cx + 3, cy + 3, caret);
    }

    public void renderPopup(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!expanded) return;
        int[] geom = popupGeometry();
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF555555);
        g.fill(px, py, px + pw, py + ph, 0xFF1A1A1A);

        for (int i = 0; i < 4; i++) {
            int rowTier = i + 1;
            int rowY = py + POPUP_PAD + i * ROW_HEIGHT;
            boolean rowHovered = mouseX >= px && mouseX < px + pw
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (rowHovered) {
                g.fill(px + 1, rowY, px + pw - 1, rowY + ROW_HEIGHT, 0x25FFFFFF);
            }
            drawRow(g, font, px + 2, rowY + 1, rowTier, rowTier == tier);
        }
        g.pose().popPose();
    }

    private static void drawRow(GuiGraphics g, Font font, int x, int y, int tier, boolean selected) {
        Block block = blockForTier(tier);
        if (block != null) {
            ItemStack stack = new ItemStack(block);
            g.renderItem(stack, x, y);
        }
        String name = tierName(tier);
        int textColor = selected ? 0xFFFFCC00 : 0xFFEEEEEE;
        g.drawString(font, name, x + 20, y + 5, textColor, false);
    }

    private static String tierName(int tier) {
        Block b = blockForTier(tier);
        if (b == null) return "Tier " + tier;
        return Component.translatable(b.getDescriptionId()).getString();
    }

    private static Block blockForTier(int tier) {
        return switch (tier) {
            case 1 -> ModBlocks.RESEARCH_PEDESTAL.get();
            case 2 -> ModBlocks.RESEARCH_PEDESTAL_TIER_2.get();
            case 3 -> ModBlocks.RESEARCH_PEDESTAL_TIER_3.get();
            case 4 -> ModBlocks.RESEARCH_PEDESTAL_TIER_4.get();
            default -> null;
        };
    }

    private int[] popupGeometry() {
        int pw = buttonW;
        int ph = 4 * ROW_HEIGHT + POPUP_PAD * 2;
        int px = buttonX;
        int py = buttonY + BUTTON_HEIGHT + 2;
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (py + ph > screenH - 4) py = buttonY - ph - 2;
        if (py < 4) py = 4;
        return new int[] { px, py, pw, ph };
    }

    /** @return true if the click was consumed (button toggle or row pick). */
    public boolean mouseClicked(double mx, double my) {
        if (mx >= buttonX && mx < buttonX + buttonW && my >= buttonY && my < buttonY + BUTTON_HEIGHT) {
            expanded = !expanded;
            playClick();
            return true;
        }
        if (!expanded) return false;

        int[] geom = popupGeometry();
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];
        if (mx < px || mx >= px + pw || my < py || my >= py + ph) {
            expanded = false;
            return false;
        }
        int idx = (int) ((my - py - POPUP_PAD) / ROW_HEIGHT);
        if (idx >= 0 && idx < 4) {
            int picked = idx + 1;
            if (picked != tier) {
                tier = picked;
                if (onChange != null) onChange.accept(tier);
            }
            expanded = false;
            playClick();
        }
        return true;
    }

    private static int clamp(int t) {
        if (t < 1) return 1;
        if (t > 4) return 4;
        return t;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
