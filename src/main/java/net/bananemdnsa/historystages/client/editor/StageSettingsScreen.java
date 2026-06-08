package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.PedestalTierDropdown;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.research.TierMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StageSettingsScreen extends Screen {

    @FunctionalInterface
    public interface SaveCallback {
        void onSave(String stageId, String displayName, int researchTime,
                    int minPedestalTier, TierMode pedestalTierMode,
                    StageMode mode, AutoTrigger autoTrigger);
    }

    private static final int FIELD_HEIGHT = 18;
    private static final float SMALL_SCALE = 0.85f;

    private final Screen parent;
    private final boolean isNewStage;
    private final SaveCallback onSave;

    private String saveError = "";

    private String editStageId;
    private String editDisplayName;
    private int editResearchTime;
    private int editMinTier;
    private TierMode editTierMode;
    private StageMode editMode;
    private AutoTrigger editAutoTrigger;

    private final String origStageId;
    private final String origDisplayName;
    private final String origResearchTime;
    private final int origMinTier;
    private final TierMode origTierMode;
    private final StageMode origMode;

    private boolean hasChanges = false;

    private EditBox stageIdField;
    private EditBox displayNameField;
    private EditBox researchTimeField;
    private PedestalTierDropdown tierDropdown;
    private Button tierModeButton;
    private Button stageModeButton;
    private Button autoTriggerButton;

    public StageSettingsScreen(Screen parent, String stageId, String displayName, int researchTime,
                               int minPedestalTier, TierMode pedestalTierMode,
                               StageMode mode, AutoTrigger autoTrigger,
                               boolean isNewStage, SaveCallback onSave) {
        super(Component.translatable("editor.historystages.stage_settings.title"));
        this.parent = parent;
        this.isNewStage = isNewStage;
        this.onSave = onSave;

        this.editStageId = stageId;
        this.editDisplayName = displayName;
        this.editResearchTime = researchTime;
        this.editMinTier = minPedestalTier;
        this.editTierMode = pedestalTierMode != null ? pedestalTierMode : TierMode.MIN;
        this.editMode = mode != null ? mode : StageMode.DEFAULT;
        this.editAutoTrigger = autoTrigger;

        this.origStageId = stageId;
        this.origDisplayName = displayName;
        this.origResearchTime = String.valueOf(researchTime);
        this.origMinTier = minPedestalTier;
        this.origTierMode = this.editTierMode;
        this.origMode = this.editMode;
    }

    @Override
    protected void init() {
        int labelX = 30;
        String labelId = Component.translatable("editor.historystages.field.stage_id").getString();
        String labelName = Component.translatable("editor.historystages.field.display_name").getString();
        String labelTime = Component.translatable("editor.historystages.field.research_time").getString();
        String labelTier = Component.translatable("editor.historystages.field.min_pedestal_tier").getString();
        String labelTierMode = Component.translatable("editor.historystages.field.pedestal_tier_mode").getString();
        String labelStageMode = "Mode";
        int maxLabelW = Math.max(Math.max(this.font.width(labelId),
                Math.max(this.font.width(labelName), this.font.width(labelTime))),
                Math.max(Math.max(this.font.width(labelTier), this.font.width(labelTierMode)),
                        this.font.width(labelStageMode)));
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

        // Stage Mode button (row at y=66) — cycles DEFAULT -> AUTO -> EXTERNAL
        stageModeButton = StyledButton.of(
                Component.literal(stageModeLabel(editMode)),
                btn -> {
                    editMode = nextMode(editMode);
                    if (editMode != origMode) hasChanges = true;
                    rebuildModeSubsections();
                },
                fieldX, 66, fieldWidth, FIELD_HEIGHT);
        this.addRenderableWidget(stageModeButton);

        // Research time (row at y=88) — DEFAULT only
        researchTimeField = new EditBox(this.font, fieldX, 88, 80, FIELD_HEIGHT,
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

        // Min pedestal tier dropdown (row at y=110) — DEFAULT only
        tierDropdown = new PedestalTierDropdown(editMinTier, 160, picked -> {
            editMinTier = picked;
            if (picked != origMinTier) hasChanges = true;
        });
        tierDropdown.setPosition(fieldX, 110);

        // Tier mode toggle (row at y=132) — DEFAULT only
        tierModeButton = StyledButton.of(
                Component.translatable(tierModeLabelKey(editTierMode)),
                btn -> {
                    editTierMode = (editTierMode == TierMode.MIN) ? TierMode.EXACT : TierMode.MIN;
                    btn.setMessage(Component.translatable(tierModeLabelKey(editTierMode)));
                    if (editTierMode != origTierMode) hasChanges = true;
                },
                fieldX, 132, 160, FIELD_HEIGHT);
        this.addRenderableWidget(tierModeButton);

        // Auto-trigger config button — AUTO only
        autoTriggerButton = StyledButton.of(
                Component.literal("Auto-Trigger konfigurieren →"),
                btn -> {
                    if (editAutoTrigger == null) {
                        editAutoTrigger = new AutoTrigger();
                        hasChanges = true;
                    }
                    this.minecraft.setScreen(new AutoTriggerEditorScreen(this, editAutoTrigger,
                            updated -> {
                                editAutoTrigger = updated;
                                hasChanges = true;
                            }));
                },
                fieldX, 88, fieldWidth, FIELD_HEIGHT);
        this.addRenderableWidget(autoTriggerButton);

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> confirmDiscard(), 10, this.height - 25, 50, 18));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> save(), this.width - 60, this.height - 25, 50, 18));

        rebuildModeSubsections();
    }

    private void rebuildModeSubsections() {
        boolean isDefault = editMode == StageMode.DEFAULT;
        boolean isAuto = editMode == StageMode.AUTO;
        // EXTERNAL — only the help text (rendered in render())

        researchTimeField.visible = isDefault;
        researchTimeField.active = isDefault;
        tierModeButton.visible = isDefault;
        tierModeButton.active = isDefault;
        // tierDropdown is not a registered widget — visibility handled in render()/mouseClicked()
        autoTriggerButton.visible = isAuto;
        autoTriggerButton.active = isAuto;
    }

    private static StageMode nextMode(StageMode m) {
        return switch (m) {
            case DEFAULT -> StageMode.AUTO;
            case AUTO -> StageMode.EXTERNAL;
            case EXTERNAL -> StageMode.DEFAULT;
        };
    }

    private static String stageModeLabel(StageMode m) {
        return switch (m) {
            case DEFAULT -> "Default (Pedestal research)";
            case AUTO -> "Auto (discovery triggers)";
            case EXTERNAL -> "External (scripted unlock)";
        };
    }

    private void save() {
        String id = editStageId.trim();
        if (id.isEmpty()) {
            saveError = Component.translatable("editor.historystages.id_empty").getString();
            return;
        }
        if (!id.matches("[a-zA-Z0-9_\\-]+")) {
            saveError = Component.translatable("editor.historystages.id_invalid").getString();
            return;
        }
        if (editDisplayName.trim().isEmpty()) {
            saveError = Component.translatable("editor.historystages.display_name_empty").getString();
            return;
        }
        saveError = "";
        onSave.onSave(editStageId, editDisplayName, editResearchTime, editMinTier, editTierMode,
                editMode, editAutoTrigger);
        hasChanges = false;
        this.minecraft.setScreen(parent);
    }

    private static String tierModeLabelKey(TierMode mode) {
        return mode == TierMode.EXACT
                ? "editor.historystages.tier_mode.exact"
                : "editor.historystages.tier_mode.min";
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
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op — we draw our own background in render() and want to avoid 1.21's menu blur shader
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xC0000000);
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
        guiGraphics.drawString(this.font, "Mode", labelX, 71, 0xAAAAAA, false);

        boolean isDefault = editMode == StageMode.DEFAULT;
        boolean isAuto = editMode == StageMode.AUTO;
        boolean isExternal = editMode == StageMode.EXTERNAL;

        if (isDefault) {
            guiGraphics.drawString(this.font,
                    Component.translatable("editor.historystages.field.research_time").getString(),
                    labelX, 93, 0xAAAAAA, false);
            guiGraphics.drawString(this.font,
                    Component.translatable("editor.historystages.field.min_pedestal_tier").getString(),
                    labelX, 115, 0xAAAAAA, false);
            guiGraphics.drawString(this.font,
                    Component.translatable("editor.historystages.field.pedestal_tier_mode").getString(),
                    labelX, 137, 0xAAAAAA, false);

            // Tier dropdown: render the button inline, popup over everything.
            tierDropdown.renderButton(guiGraphics, this.font, mouseX, mouseY);
        } else if (isAuto) {
            guiGraphics.drawString(this.font, "Auto-Trigger", labelX, 93, 0xAAAAAA, false);
            int count = editAutoTrigger == null ? 0 : editAutoTrigger.getTriggers().size();
            guiGraphics.drawString(this.font, count + " trigger(s) configured",
                    labelX, 115, 0x888888, false);
        } else if (isExternal) {
            guiGraphics.drawString(this.font,
                    "Scroll exists but cannot be researched at the Pedestal.",
                    labelX, 93, 0xAAAAAA, false);
            guiGraphics.drawString(this.font,
                    "Unlock via /stage unlock or scripts.",
                    labelX, 105, 0xAAAAAA, false);
        }

        // Unsaved changes animation (left of Save button)
        if (hasChanges) {
            float pulse = (System.currentTimeMillis() % 1000) / 1000.0f;
            pulse = 0.4f + (float) Math.sin(pulse * 3.14159f * 2) * 0.3f;
            int dotAlpha = (int) (pulse * 255);
            String unsavedLabel = Component.translatable("editor.historystages.unsaved").getString();
            int unsavedW = (int) (this.font.width(unsavedLabel) * SMALL_SCALE);
            int dotX = this.width - 60 - 8 - 6;
            guiGraphics.fill(dotX - unsavedW - 4, this.height - 18, dotX - unsavedW + 2, this.height - 12,
                    (dotAlpha << 24) | 0xFFCC00);
            drawSmallText(guiGraphics, unsavedLabel, dotX - unsavedW + 5, this.height - 18, 0xFFCC00);
        }

        if (!saveError.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, saveError, this.width / 2, this.height - 38, 0xFF5555);
        }

        // Popup always last so it overlays everything.
        if (isDefault) {
            tierDropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editMode == StageMode.DEFAULT && tierDropdown != null
                && tierDropdown.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawSmallText(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
        g.drawString(this.font, text, 0, 0, color, false);
        g.pose().popPose();
    }
}
