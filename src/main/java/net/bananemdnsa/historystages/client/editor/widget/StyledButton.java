package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Custom styled button matching the editor's gold/yellow theme.
 */
public class StyledButton extends Button {

    public StyledButton(int x, int y, int w, int h, Component text, OnPress onPress) {
        super(x, y, w, h, text, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHoveredOrFocused();

        // Background
        int bg = hovered ? 0x50FFCC00 : 0x30FFFFFF;
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bg);

        // Border (bottom accent line)
        int borderColor = hovered ? 0xFFFFCC00 : 0x60FFCC00;
        guiGraphics.fill(this.getX(), this.getY() + this.height - 2,
                this.getX() + this.width, this.getY() + this.height, borderColor);

        // Subtle top/side borders
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, 0x20FFFFFF);
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, 0x15FFFFFF);
        guiGraphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, 0x15FFFFFF);

        // Text
        int textColor = hovered ? 0xFFFFFF : 0xCCCCCC;
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor);
    }

    public static StyledButton of(Component text, OnPress onPress, int x, int y, int w, int h) {
        return new StyledButton(x, y, w, h, text, onPress);
    }
}
