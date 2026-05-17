package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

public class FilterDropdown {
    public static final int BUTTON_SIZE = 18;
    private static final int ROW_HEIGHT = 16;
    private static final int POPUP_PAD = 4;
    private static final int MIN_POPUP_WIDTH = 110;

    public static class Option {
        public final String id;
        public final String label;
        public final String groupId;
        public boolean active;

        public Option(String id, String label, String groupId, boolean active) {
            this.id = id;
            this.label = label;
            this.groupId = groupId;
            this.active = active;
        }
    }

    private final List<Option> options = new ArrayList<>();
    private final Runnable onChange;
    private boolean expanded = false;
    private int buttonX, buttonY;

    public FilterDropdown(Runnable onChange) {
        this.onChange = onChange;
    }

    public FilterDropdown addOption(String id, String label, String groupId) {
        options.add(new Option(id, label, groupId, false));
        return this;
    }

    public boolean isActive(String id) {
        for (Option opt : options) {
            if (opt.id.equals(id))
                return opt.active;
        }
        return false;
    }

    public boolean hasActiveFilters() {
        for (Option opt : options) {
            if (opt.active)
                return true;
        }
        return false;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void close() {
        expanded = false;
    }

    public boolean hasOptions() {
        return !options.isEmpty();
    }

    public boolean isMouseOver(double mx, double my) {
        if (options.isEmpty())
            return false;
        if (mx >= buttonX && mx < buttonX + BUTTON_SIZE && my >= buttonY && my < buttonY + BUTTON_SIZE)
            return true;
        if (!expanded)
            return false;
        int[] geom = popupGeometry(Minecraft.getInstance().font);
        return mx >= geom[0] && mx < geom[0] + geom[2] && my >= geom[1] && my < geom[1] + geom[3];
    }

    public void setButtonPosition(int x, int y) {
        this.buttonX = x;
        this.buttonY = y;
    }

    public int getButtonX() {
        return buttonX;
    }

    public int getButtonY() {
        return buttonY;
    }

    public void renderButton(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (options.isEmpty())
            return;
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + BUTTON_SIZE
                && mouseY >= buttonY && mouseY < buttonY + BUTTON_SIZE;
        boolean active = hasActiveFilters();

        int border = active ? 0xFFFFCC00 : (hovered ? 0xFF888888 : 0xFF4A4A4A);
        int bg = active ? 0xFF2A2510 : (hovered ? 0xFF252525 : 0xFF0D0D0D);
        g.fill(buttonX, buttonY, buttonX + BUTTON_SIZE, buttonY + BUTTON_SIZE, border);
        g.fill(buttonX + 1, buttonY + 1, buttonX + BUTTON_SIZE - 1, buttonY + BUTTON_SIZE - 1, bg);

        int color = active ? 0xFFFFCC00 : (hovered ? 0xFFDDDDDD : 0xFF999999);
        int cx = buttonX + BUTTON_SIZE / 2;
        int top = buttonY + 4;
        g.fill(cx - 5, top, cx + 5, top + 1, color);
        g.fill(cx - 4, top + 1, cx + 4, top + 2, color);
        g.fill(cx - 3, top + 2, cx + 3, top + 3, color);
        g.fill(cx - 2, top + 3, cx + 2, top + 4, color);
        g.fill(cx - 1, top + 4, cx + 1, top + 10, color);

        if (active) {
            g.fill(buttonX + BUTTON_SIZE - 5, buttonY + 2, buttonX + BUTTON_SIZE - 2, buttonY + 5, 0xFFFFCC00);
        }
    }

    public void renderPopup(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!expanded || options.isEmpty())
            return;
        int[] geom = popupGeometry(font);
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF555555);
        g.fill(px, py, px + pw, py + ph, 0xFF1A1A1A);

        for (int i = 0; i < options.size(); i++) {
            Option opt = options.get(i);
            int rowY = py + POPUP_PAD + i * ROW_HEIGHT;
            boolean rowHovered = mouseX >= px && mouseX < px + pw
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (rowHovered) {
                g.fill(px + 2, rowY, px + pw - 2, rowY + ROW_HEIGHT, 0x25FFFFFF);
            }

            int boxX = px + 6;
            int boxY = rowY + 3;
            int boxSize = 10;
            int boxBorder = opt.active ? 0xFFFFCC00 : 0xFF666666;
            int boxBg = opt.active ? 0xFF2A2510 : 0xFF0D0D0D;
            g.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, boxBorder);
            g.fill(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1, boxBg);
            if (opt.active) {
                g.fill(boxX + 3, boxY + 5, boxX + 4, boxY + 8, 0xFFFFCC00);
                g.fill(boxX + 4, boxY + 6, boxX + 5, boxY + 9, 0xFFFFCC00);
                g.fill(boxX + 5, boxY + 5, boxX + 6, boxY + 8, 0xFFFFCC00);
                g.fill(boxX + 6, boxY + 4, boxX + 7, boxY + 7, 0xFFFFCC00);
                g.fill(boxX + 7, boxY + 3, boxX + 8, boxY + 6, 0xFFFFCC00);
            }
            g.drawString(font, opt.label, boxX + boxSize + 5, rowY + 4, 0xFFEEEEEE, false);
        }
        g.pose().popPose();
    }

    private int[] popupGeometry(Font font) {
        int pw = MIN_POPUP_WIDTH;
        for (Option opt : options) {
            int w = font.width(opt.label) + 32;
            if (w > pw)
                pw = w;
        }
        int ph = options.size() * ROW_HEIGHT + POPUP_PAD * 2;
        int px = buttonX + BUTTON_SIZE - pw;
        int py = buttonY + BUTTON_SIZE + 2;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (px < 4)
            px = 4;
        if (px + pw > screenW - 4)
            px = screenW - pw - 4;
        if (py + ph > screenH - 4)
            py = buttonY - ph - 2;
        if (py < 4)
            py = 4;
        return new int[] { px, py, pw, ph };
    }

    public boolean mouseClicked(double mx, double my) {
        if (options.isEmpty())
            return false;

        if (mx >= buttonX && mx < buttonX + BUTTON_SIZE && my >= buttonY && my < buttonY + BUTTON_SIZE) {
            expanded = !expanded;
            playClick();
            return true;
        }
        if (!expanded)
            return false;

        Font font = Minecraft.getInstance().font;
        int[] geom = popupGeometry(font);
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];

        if (mx < px || mx >= px + pw || my < py || my >= py + ph) {
            expanded = false;
            return false;
        }

        int idx = (int) ((my - py - POPUP_PAD) / ROW_HEIGHT);
        if (idx >= 0 && idx < options.size()) {
            toggleOption(idx);
            playClick();
        }
        return true;
    }

    private void toggleOption(int idx) {
        Option opt = options.get(idx);
        boolean newState = !opt.active;
        opt.active = newState;
        if (newState && opt.groupId != null) {
            for (Option other : options) {
                if (other != opt && opt.groupId.equals(other.groupId)) {
                    other.active = false;
                }
            }
        }
        if (onChange != null)
            onChange.run();
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
