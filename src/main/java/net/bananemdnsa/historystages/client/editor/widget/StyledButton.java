package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Custom styled button matching the editor's gold/yellow theme.
 *
 * <p>The button auto-grows to fit its label: when a long translation would overflow the
 * assigned box, it renders wider (symmetrically around its assigned center) so the text
 * always fits, capped at the screen edges. The assigned geometry ({@link #getX()} /
 * {@link #width}) is treated as an immutable base and never mutated by rendering — the
 * grown bounds are derived each frame, so there is no runaway growth and screens that
 * reposition the button every frame keep working. Hover and click detection use the same
 * derived bounds so they always match what is drawn.</p>
 */
public class StyledButton extends Button {

    /** Minimum horizontal padding kept on each side of the label when auto-growing. */
    private static final int TEXT_PAD = 6;

    private float hoverProgress = 0.0f;

    public StyledButton(int x, int y, int w, int h, Component text, OnPress onPress) {
        super(x, y, w, h, text, onPress, DEFAULT_NARRATION);
    }

    /**
     * Effective render width: at least the assigned width, grown to fit the label (plus
     * padding) when it would otherwise overflow, and never wider than the screen.
     */
    private int effectiveWidth() {
        int textW = Minecraft.getInstance().font.width(this.getMessage()) + TEXT_PAD * 2;
        int w = Math.max(this.width, textW);
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return Math.min(w, Math.max(this.width, screenW - 8));
    }

    /**
     * Left edge for {@link #effectiveWidth()}: keeps the assigned center fixed so the button
     * grows symmetrically, then nudges it back on-screen if it would spill past an edge.
     */
    private int effectiveX(int effWidth) {
        int center = this.getX() + this.width / 2;
        int rx = center - effWidth / 2;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (rx + effWidth > screenW - 4) rx = screenW - 4 - effWidth;
        if (rx < 4) rx = 4;
        return rx;
    }

    /** @return true if {@code (mouseX, mouseY)} is over the grown bounds — used for hover + clicks. */
    private boolean overEffective(double mouseX, double mouseY) {
        int ew = effectiveWidth();
        int ex = effectiveX(ew);
        return mouseX >= ex && mouseY >= this.getY()
                && mouseX < ex + ew && mouseY < this.getY() + this.height;
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return this.active && this.visible && overEffective(mouseX, mouseY);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Hover from the grown bounds (this.isHovered() reflects the un-grown assigned box).
        boolean hovered = this.active && overEffective(mouseX, mouseY);

        int w = effectiveWidth();
        int x = effectiveX(w);
        int y = this.getY();
        int h = this.height;

        // Smooth hover transition
        if (hovered) {
            hoverProgress = Math.min(1.0f, hoverProgress + 0.1f);
        } else {
            hoverProgress = Math.max(0.0f, hoverProgress - 0.08f);
        }

        // Animated background - lerp from white-tint to gold-tint
        int bgAlpha = (int) (0x30 + hoverProgress * 0x20);
        int bgR = (int) (0xFF);
        int bgG = (int) (0xFF - hoverProgress * 0x33);
        int bgB = (int) (0xFF - hoverProgress * 0xFF);
        guiGraphics.fill(x, y, x + w, y + h, ((this.active ? bgAlpha : 0x15) << 24)
                | (bgR << 16) | (bgG << 8) | bgB);

        // Animated bottom accent line - opacity transitions. A disabled button keeps a
        // trace of it so it still reads as a button, just an unavailable one.
        int accentAlpha = this.active ? (int) (0x60 + hoverProgress * 0x9F) : 0x25;
        guiGraphics.fill(x, y + h - 2, x + w, y + h, (accentAlpha << 24) | 0xFFCC00);

        // Subtle top/side borders
        guiGraphics.fill(x, y, x + w, y + 1, 0x20FFFFFF);
        guiGraphics.fill(x, y, x + 1, y + h, 0x15FFFFFF);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0x15FFFFFF);

        // Text - smooth color transition, greyed out while inactive
        int textGray = this.active ? (int) (0xCC + hoverProgress * 0x33) : 0x66;
        int textColor = (0xFF << 24) | (textGray << 16) | (textGray << 8) | textGray;
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                x + w / 2, y + (h - 8) / 2, textColor);
    }

    public static StyledButton of(Component text, OnPress onPress, int x, int y, int w, int h) {
        return new StyledButton(x, y, w, h, text, onPress);
    }
}
