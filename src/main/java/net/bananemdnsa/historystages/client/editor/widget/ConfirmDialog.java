package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A modal confirmation dialog overlay.
 */
public class ConfirmDialog extends Screen {
    private final Screen parent;
    private final Component message;
    private final Runnable onConfirm;

    public ConfirmDialog(Screen parent, Component title, Component message, Runnable onConfirm) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.confirm"),
                btn -> onConfirm.run(), centerX - 105, centerY + 20, 100, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.cancel"),
                btn -> this.minecraft.setScreen(parent), centerX + 5, centerY + 20, 100, 20));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Fully opaque dark background to prevent text bleed-through
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF0A0A0A);

        // Dialog box
        int boxW = 250;
        int boxH = 100;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF2D2D2D);
        guiGraphics.fill(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1, 0xFF1A1A1A);

        // Title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, boxY + 10, 0xFFFFFF);

        // Message
        guiGraphics.drawCenteredString(this.font, this.message, this.width / 2, boxY + 30, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
