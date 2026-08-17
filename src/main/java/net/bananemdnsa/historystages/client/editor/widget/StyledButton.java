package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
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

    /** How long the gold flash after a press takes to fade out. */
    private static final float PRESS_FLASH_MS = 320.0f;

    private final Anim hover = new Anim();
    /** Driven to 1 by a click and left to decay, so a press is visibly acknowledged. */
    private final Anim press = new Anim();

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
    public void onClick(double mouseX, double mouseY) {
        press.set(1.0f);
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Hover from the grown bounds (this.isHovered() reflects the un-grown assigned box).
        boolean hovered = this.active && overEffective(mouseX, mouseY);

        int w = effectiveWidth();
        int x = effectiveX(w);
        int y = this.getY();
        int h = this.height;

        float hp = Ease.outCubic(hover.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        float flash = Ease.outCubic(press.ramp(0.0f, PRESS_FLASH_MS));

        // Background — white tint at rest, warming towards gold as the cursor settles.
        // A disabled button (active = false) keeps only a faint trace of the fill, so it
        // still reads as a button — just one that is not clickable right now.
        int bgAlpha = this.active ? (int) (0x30 + hp * 0x20) : 0x15;
        int bgG = (int) (0xFF - hp * 0x33);
        int bgB = (int) (0xFF - hp * 0xFF);
        guiGraphics.fill(x, y, x + w, y + h, (bgAlpha << 24) | (0xFF << 16) | (bgG << 8) | bgB);

        // A press briefly washes the whole face gold, so the click registers even when the
        // action it triggers happens somewhere else on screen.
        if (flash > 0.001f) {
            guiGraphics.fill(x, y, x + w, y + h, Fade.rgba(0xFFCC00, flash * 0.22f));
        }

        // Bottom accent. The full-width line carries the resting state; a brighter segment
        // grows out from the centre on hover, which reads as the button reacting rather than
        // just changing colour. Disabled keeps only a faint trace of it.
        int accentAlpha = this.active ? (int) (0x60 + hp * 0x50) : 0x25;
        guiGraphics.fill(x, y + h - 2, x + w, y + h, (accentAlpha << 24) | 0xFFCC00);
        if (this.active && hp > 0.001f) {
            int half = Math.round(w / 2.0f * hp);
            int cx = x + w / 2;
            guiGraphics.fill(cx - half, y + h - 2, cx + half, y + h, Fade.rgba(0xFFCC00, hp));
        }

        // Subtle top/side borders
        guiGraphics.fill(x, y, x + w, y + 1, 0x20FFFFFF);
        guiGraphics.fill(x, y, x + 1, y + h, 0x15FFFFFF);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0x15FFFFFF);

        // Text — brightens with hover, greyed out while inactive.
        int textGray = this.active ? (int) (0xCC + hp * 0x33) : 0x66;
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                x + w / 2, y + (h - 8) / 2, Fade.grey(textGray));
    }

    public static StyledButton of(Component text, OnPress onPress, int x, int y, int w, int h) {
        return new StyledButton(x, y, w, h, text, onPress);
    }
}
