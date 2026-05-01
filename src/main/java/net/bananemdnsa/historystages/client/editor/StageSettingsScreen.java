package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StageSettingsScreen extends Screen {

    @FunctionalInterface
    public interface SaveCallback {
        void onSave(String stageId, String displayName, int researchTime);
    }

    private static final int FIELD_HEIGHT = 18;

    private final Screen parent;
    private final boolean isNewStage;
    private final SaveCallback onSave;

    private String editStageId;
    private String editDisplayName;
    private int editResearchTime;

    private final String origStageId;
    private final String origDisplayName;
    private final String origResearchTime;

    private boolean hasChanges = false;

    private EditBox stageIdField;
    private EditBox displayNameField;
    private EditBox researchTimeField;

    public StageSettingsScreen(Screen parent, String stageId, String displayName, int researchTime,
                               boolean isNewStage, SaveCallback onSave) {
        super(Component.translatable("editor.historystages.stage_settings.title"));
        this.parent = parent;
        this.isNewStage = isNewStage;
        this.onSave = onSave;

        this.editStageId = stageId;
        this.editDisplayName = displayName;
        this.editResearchTime = researchTime;

        this.origStageId = stageId;
        this.origDisplayName = displayName;
        this.origResearchTime = String.valueOf(researchTime);
    }

    @Override
    protected void init() {
        int labelX = 30;
        String labelId = Component.translatable("editor.historystages.field.stage_id").getString();
        String labelName = Component.translatable("editor.historystages.field.display_name").getString();
        String labelTime = Component.translatable("editor.historystages.field.research_time").getString();
        int maxLabelW = Math.max(this.font.width(labelId),
                Math.max(this.font.width(labelName), this.font.width(labelTime)));
        int fieldX = labelX + maxLabelW + 10;
        int fieldWidth = Math.min(200, this.width - fieldX - 40);

        stageIdField = new EditBox(this.font, fieldX, 22, fieldWidth, FIELD_HEIGHT,
                Component.translatable("editor.historystages.field.stage_id"));
        stageIdField.setMaxLength(64);
        stageIdField.setValue(editStageId);
        stageIdField.setEditable(isNewStage);
        stageIdField.setResponder(val -> {
            editStageId = val;
            if (!val.equals(origStageId))
                hasChanges = true;
        });
        this.addRenderableWidget(stageIdField);

        displayNameField = new EditBox(this.font, fieldX, 44, fieldWidth, FIELD_HEIGHT,
                Component.translatable("editor.historystages.field.display_name"));
        displayNameField.setMaxLength(128);
        displayNameField.setValue(editDisplayName);
        displayNameField.setResponder(val -> {
            editDisplayName = val;
            if (!val.equals(origDisplayName))
                hasChanges = true;
        });
        this.addRenderableWidget(displayNameField);

        researchTimeField = new EditBox(this.font, fieldX, 66, 80, FIELD_HEIGHT,
                Component.translatable("editor.historystages.field.research_time"));
        researchTimeField.setMaxLength(5);
        researchTimeField.setValue(String.valueOf(editResearchTime));
        researchTimeField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        researchTimeField.setResponder(val -> {
            try {
                editResearchTime = val.isEmpty() ? 0 : Integer.parseInt(val);
            } catch (NumberFormatException ignored) {
            }
            if (!val.equals(origResearchTime))
                hasChanges = true;
        });
        this.addRenderableWidget(researchTimeField);

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> confirmDiscard(), 10, this.height - 25, 50, 18));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> save(), this.width - 60, this.height - 25, 50, 18));
    }

    private void save() {
        onSave.onSave(editStageId, editDisplayName, editResearchTime);
        hasChanges = false;
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
        if (stageIdField.isFocused() || displayNameField.isFocused() || researchTimeField.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
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
                this.width / 2, 8, 0xFFFFFF);

        int labelX = 30;
        guiGraphics.drawString(this.font,
                Component.translatable("editor.historystages.field.stage_id").getString(),
                labelX, 27, 0xAAAAAA, false);
        guiGraphics.drawString(this.font,
                Component.translatable("editor.historystages.field.display_name").getString(),
                labelX, 49, 0xAAAAAA, false);
        guiGraphics.drawString(this.font,
                Component.translatable("editor.historystages.field.research_time").getString(),
                labelX, 71, 0xAAAAAA, false);
    }
}
