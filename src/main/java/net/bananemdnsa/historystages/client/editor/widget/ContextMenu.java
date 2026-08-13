package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A simple right-click context menu overlay rendered on top of a screen.
 */
public class ContextMenu {
    private static final int ITEM_HEIGHT = 16;
    private static final int PADDING = 4;
    private static final int MIN_WIDTH = 80;

    /** How far the menu drops into place while fading in. */
    private static final int SLIDE_PX = 4;

    private final List<Entry> entries = new ArrayList<>();
    private int x, y;
    private int menuWidth;
    private int menuHeight;
    private boolean visible = false;
    /** Fade/slide progress of the whole menu, reset each time it is shown. */
    private final Anim open = new Anim();
    /** Per-entry hover progress, indexed the same way {@link #entries} is. */
    private final List<Anim> entryHover = new ArrayList<>();

    public void addEntry(String label, Runnable action) {
        entries.add(new Entry(label, action));
        entryHover.add(new Anim());
    }

    public void show(int x, int y, Font font) {
        this.x = x;
        this.y = y;
        this.visible = true;
        open.set(0.0f);
        for (Anim a : entryHover) {
            a.set(0.0f);
        }

        // Calculate width based on longest entry
        menuWidth = MIN_WIDTH;
        for (Entry e : entries) {
            menuWidth = Math.max(menuWidth, font.width(e.label) + PADDING * 2 + 4);
        }
        menuHeight = entries.size() * ITEM_HEIGHT + PADDING * 2;
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        float fade = Ease.outCubic(open.ramp(1.0f, Timing.POPUP_MS));
        if (fade < 0.02f) return;

        // Drop the last few pixels into place while fading, so the menu reads as arriving
        // from the click rather than blinking into existence.
        int slide = Math.round((1.0f - fade) * SLIDE_PX);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, slide, 0.0f);

        guiGraphics.fill(x, y, x + menuWidth, y + menuHeight, Fade.rgba(0x1A1A1A, fade));

        // Gold top accent line
        guiGraphics.fill(x, y, x + menuWidth, y + 2, Fade.rgba(0xFFCC00, fade));

        // Borders
        int borderColor = Fade.alpha(0x4A4A4A4A, fade);
        guiGraphics.fill(x, y + menuHeight - 1, x + menuWidth, y + menuHeight, borderColor);
        guiGraphics.fill(x, y, x + 1, y + menuHeight, borderColor);
        guiGraphics.fill(x + menuWidth - 1, y, x + menuWidth, y + menuHeight, borderColor);

        for (int i = 0; i < entries.size(); i++) {
            int entryY = y + PADDING + i * ITEM_HEIGHT;
            boolean hovered = mouseX >= x && mouseX <= x + menuWidth
                    && mouseY >= entryY && mouseY < entryY + ITEM_HEIGHT;
            float hp = Ease.outCubic(entryHover.get(i)
                    .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

            if (hp > 0.001f) {
                guiGraphics.fill(x + 1, entryY, x + menuWidth - 1, entryY + ITEM_HEIGHT,
                        Fade.rgba(0xFFFFFF, 0.25f * hp * fade));
                // Gold bar on the left edge — tells you which row will fire without relying
                // on the fill alone, which is easy to miss on a dark menu.
                guiGraphics.fill(x + 1, entryY, x + 2, entryY + ITEM_HEIGHT,
                        Fade.rgba(0xFFCC00, hp * fade));
            }

            // Hovered entries nudge right, following the highlight bar.
            int textX = x + PADDING + 2 + Math.round(hp * 2.0f);
            int textGrey = Math.round(0xCC + hp * 0x33);
            guiGraphics.drawString(font, entries.get(i).label, textX, entryY + 4,
                    Fade.alpha(Fade.grey(textGrey), fade), false);
        }

        guiGraphics.pose().popPose();
    }

    /**
     * @return true if the click was consumed by the context menu
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        if (mouseX >= x && mouseX <= x + menuWidth && mouseY >= y && mouseY <= y + menuHeight) {
            int index = (int) ((mouseY - y - PADDING) / ITEM_HEIGHT);
            if (index >= 0 && index < entries.size()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                entries.get(index).action.run();
            }
            hide();
            return true;
        }

        // Clicked outside — close menu
        hide();
        return true;
    }

    private record Entry(String label, Runnable action) {}
}
