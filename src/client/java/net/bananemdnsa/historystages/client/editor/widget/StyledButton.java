package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Custom styled button matching the editor's gold/yellow theme.
 */
public class StyledButton extends Button {

    private float hoverProgress = 0.0f;

    public StyledButton(int x, int y, int w, int h, Component text, OnPress onPress) {
        super(x, y, w, h, text, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHovered();

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
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,
                (bgAlpha << 24) | (bgR << 16) | (bgG << 8) | bgB);

        // Animated bottom accent line - opacity transitions
        int accentAlpha = (int) (0x60 + hoverProgress * 0x9F);
        guiGraphics.fill(this.getX(), this.getY() + this.height - 2,
                this.getX() + this.width, this.getY() + this.height, (accentAlpha << 24) | 0xFFCC00);

        // Subtle top/side borders
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, 0x20FFFFFF);
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, 0x15FFFFFF);
        guiGraphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, 0x15FFFFFF);

        // Text - smooth color transition
        int textGray = (int) (0xCC + hoverProgress * 0x33);
        int textColor = (0xFF << 24) | (textGray << 16) | (textGray << 8) | textGray;
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor);
    }

    public static StyledButton of(Component text, OnPress onPress, int x, int y, int w, int h) {
        return new StyledButton(x, y, w, h, text, onPress);
    }
}
