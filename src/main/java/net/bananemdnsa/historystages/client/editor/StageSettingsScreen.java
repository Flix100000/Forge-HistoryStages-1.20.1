package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StageSettingsScreen extends Screen {
    private final Screen parent;
    private final Runnable onSave;
    private boolean hasChanges = false;

    public StageSettingsScreen(Screen parent, Runnable onSave) {
        super(Component.translatable("editor.historystages.stage_settings.title"));
        this.parent = parent;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> confirmDiscard(), 10, this.height - 25, 50, 18));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> save(), this.width - 60, this.height - 25, 50, 18));
    }

    private void save() {
        hasChanges = false;
        onSave.run();
        this.minecraft.setScreen(parent);
    }

    private void confirmDiscard() {
        if (hasChanges) {
            this.minecraft.setScreen(new ConfirmDialog(this,
                    Component.translatable("editor.historystages.unsaved_warning_title"),
                    Component.translatable("editor.historystages.unsaved_warning"),
                    () -> this.minecraft.setScreen(parent)));
        } else {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        confirmDiscard();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            confirmDiscard();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("editor.historystages.stage_settings.title"),
                this.width / 2, 10, 0xFFFFFF);
    }
}
