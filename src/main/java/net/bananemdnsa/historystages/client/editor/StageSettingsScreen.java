package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DisplayModeDropdown;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DurationUnitDropdown;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.PedestalTierDropdown;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.display.DisplayMode;
import net.bananemdnsa.historystages.data.display.HiddenDisplayConfig;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.temporary.TemporaryConfig;
import net.bananemdnsa.historystages.research.TierMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class StageSettingsScreen extends Screen {

    @FunctionalInterface
    public interface SaveCallback {
        void onSave(String stageId, String displayName, int researchTime,
                    int minPedestalTier, TierMode pedestalTierMode,
                    StageMode mode, AutoTrigger autoTrigger, TemporaryConfig temporary,
                    HiddenDisplayConfig hiddenDisplay);
    }

    private static final int FIELD_HEIGHT = 18;
    private static final float SMALL_SCALE = 0.85f;

    private final Screen parent;
    private final boolean isNewStage;
    private final SaveCallback onSave;
    /** Live snapshot of the parent stage's lock data, forwarded to the trigger editor. */
    private final Supplier<StageEntry> lockSnapshot;

    private String saveError = "";

    private String editStageId;
    private String editDisplayName;
    private int editResearchTime;
    private int editMinTier;
    private TierMode editTierMode;
    private StageMode editMode;
    private AutoTrigger editAutoTrigger;
    private TemporaryConfig editTemporary;
    private final HiddenDisplayConfig editHiddenDisplay;

    // Display card state
    private EditBox nameTextField;
    private EditBox tooltipTextField;
    private int displayCardX, displayCardY, displayCardW, displayCardH;
    private DisplayModeDropdown nameModeDropdown;
    private DisplayModeDropdown tooltipModeDropdown;
    private int displayControlX;
    private int displayNameRowY, displayTooltipRowY;
    private int lockHintsRowY, lockHintsToggleX, lockHintsToggleW;
    // Display-card vertical metrics (kept in sync with computeDisplayCardHeight()).
    private static final int DISP_BODY_TOP = 28;
    private static final int DISP_ROW_GAP = 12;
    private static final int DISP_TOGGLE_H = 14;
    private static final int DISP_BOTTOM_PAD = 8;
    private static final int DISP_DROPDOWN_W = 72;
    // Name only supports OFF/REPLACE (the title is never blanked); tooltip adds HIDDEN.
    private static final DisplayMode[] NAME_MODES = {DisplayMode.OFF, DisplayMode.REPLACE};
    private static final DisplayMode[] TOOLTIP_MODES = {DisplayMode.OFF, DisplayMode.HIDDEN, DisplayMode.REPLACE};

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
    private Button autoTriggerButton;

    // TEMPORARY-mode widgets
    private EditBox durationField;
    private DurationUnitDropdown durationUnitDropdown;
    private EditBox maxTriggersField;
    private EditBox cooldownField;
    private DurationUnitDropdown cooldownUnitDropdown;

    // Mode dropdown state (inline, no widget class needed)
    private boolean modeDropdownOpen = false;
    private int modeDropdownX, modeDropdownY, modeDropdownW;

    // Card geometry — recomputed in render()
    private int cardX, cardY, cardW;
    private int fieldX, fieldWidth;

    // Scroll viewport
    private int scrollY = 0;
    private int maxScroll = 0;
    private int viewTop, viewBottom;
    private boolean scrollBarDragging = false;
    private float smoothScrollOffset = 0;
    private int renderScroll = 0;
    /** Content widgets rendered manually inside the scrolled viewport (not auto-rendered). */
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();

    public StageSettingsScreen(Screen parent, String stageId, String displayName, int researchTime,
                               int minPedestalTier, TierMode pedestalTierMode,
                               StageMode mode, AutoTrigger autoTrigger, TemporaryConfig temporary,
                               HiddenDisplayConfig hiddenDisplay,
                               boolean isNewStage, SaveCallback onSave) {
        this(parent, stageId, displayName, researchTime, minPedestalTier, pedestalTierMode,
                mode, autoTrigger, temporary, hiddenDisplay, isNewStage, onSave, null);
    }

    public StageSettingsScreen(Screen parent, String stageId, String displayName, int researchTime,
                               int minPedestalTier, TierMode pedestalTierMode,
                               StageMode mode, AutoTrigger autoTrigger, TemporaryConfig temporary,
                               HiddenDisplayConfig hiddenDisplay,
                               boolean isNewStage, SaveCallback onSave,
                               Supplier<StageEntry> lockSnapshot) {
        super(Component.translatable("editor.historystages.stage_settings.title"));
        this.parent = parent;
        this.isNewStage = isNewStage;
        this.onSave = onSave;
        this.lockSnapshot = lockSnapshot;

        this.editStageId = stageId;
        this.editDisplayName = displayName;
        this.editResearchTime = researchTime;
        this.editMinTier = minPedestalTier;
        this.editTierMode = pedestalTierMode != null ? pedestalTierMode : TierMode.MIN;
        this.editMode = mode != null ? mode : StageMode.DEFAULT;
        this.editAutoTrigger = autoTrigger;
        this.editTemporary = temporary;
        this.editHiddenDisplay = hiddenDisplay != null ? hiddenDisplay : new HiddenDisplayConfig();

        this.origStageId = stageId;
        this.origDisplayName = displayName;
        this.origResearchTime = String.valueOf(researchTime);
        this.origMinTier = minPedestalTier;
        this.origTierMode = this.editTierMode;
        this.origMode = this.editMode;
    }

    /** Registers a content widget for events + manual scrolled rendering (not auto-rendered). */
    private <T extends AbstractWidget> T addContentWidget(T w) {
        this.addWidget(w);
        contentWidgets.add(w);
        return w;
    }

    @Override
    protected void init() {
        contentWidgets.clear();
        viewTop = 20;
        viewBottom = this.height - 30;
        scrollBarDragging = false;
        int labelX = 30;
        String labelId = Component.translatable("editor.historystages.field.stage_id").getString();
        String labelName = Component.translatable("editor.historystages.field.display_name").getString();
        String labelStageMode = Component.translatable("editor.historystages.mode.label").getString();
        int maxLabelW = Math.max(Math.max(this.font.width(labelId), this.font.width(labelName)),
                this.font.width(labelStageMode));
        fieldX = labelX + maxLabelW + 10;
        fieldWidth = Math.min(220, this.width - fieldX - 40);

        // Cached card geometry (used by render + mouseClicked)
        cardX = labelX;
        cardW = this.width - cardX - 30;
        cardY = 96;

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
        addContentWidget(stageIdField);

        displayNameField = new EditBox(this.font, fieldX, 44, fieldWidth, FIELD_HEIGHT,
                Component.translatable("editor.historystages.field.display_name"));
        displayNameField.setMaxLength(128);
        displayNameField.setValue(editDisplayName);
        displayNameField.setResponder(val -> {
            editDisplayName = val;
            if (!val.equals(origDisplayName))
                hasChanges = true;
        });
        addContentWidget(displayNameField);

        // Mode dropdown is rendered inline in render() / handled in mouseClicked() (not a Button widget)
        modeDropdownX = fieldX;
        modeDropdownY = 66;
        modeDropdownW = computeModeDropdownWidth();

        // --- Card-internal widgets ---
        // Positions inside the card body (cardY + 28 = body start)
        int bodyY = cardY + 28;
        int cardFieldX = cardX + 12 + labelInsetW();
        int cardFieldW = cardW - 12 - labelInsetW() - 12;

        // Research time (DEFAULT card)
        researchTimeField = new EditBox(this.font, cardFieldX, bodyY, 80, FIELD_HEIGHT,
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
        addContentWidget(researchTimeField);

        // Min pedestal tier dropdown (DEFAULT card)
        tierDropdown = new PedestalTierDropdown(editMinTier, 160, picked -> {
            editMinTier = picked;
            if (picked != origMinTier) hasChanges = true;
        });
        tierDropdown.setPosition(cardFieldX, bodyY + 22);

        // Tier mode toggle (DEFAULT card)
        tierModeButton = StyledButton.of(
                Component.translatable(tierModeLabelKey(editTierMode)),
                btn -> {
                    editTierMode = (editTierMode == TierMode.MIN) ? TierMode.EXACT : TierMode.MIN;
                    btn.setMessage(Component.translatable(tierModeLabelKey(editTierMode)));
                    if (editTierMode != origTierMode) hasChanges = true;
                },
                cardFieldX, bodyY + 44, 160, FIELD_HEIGHT);
        addContentWidget(tierModeButton);

        // Auto-trigger configure button (AUTO card)
        autoTriggerButton = StyledButton.of(
                buildAutoTriggerLabel(),
                btn -> {
                    if (editAutoTrigger == null) {
                        editAutoTrigger = new AutoTrigger();
                        hasChanges = true;
                    }
                    this.minecraft.setScreen(new AutoTriggerEditorScreen(this, editAutoTrigger,
                            updated -> {
                                editAutoTrigger = updated;
                                hasChanges = true;
                                if (autoTriggerButton != null) {
                                    autoTriggerButton.setMessage(buildAutoTriggerLabel());
                                }
                            }, lockSnapshot, this::save));
                },
                cardX + 12, bodyY, cardW - 24, FIELD_HEIGHT);
        addContentWidget(autoTriggerButton);

        // --- TEMPORARY card widgets ---
        TemporaryConfig tShown = editTemporary != null ? editTemporary : new TemporaryConfig();
        int tempFieldX = cardX + 12 + tempLabelInsetW();
        int durRowY = bodyY + 22;

        durationField = new EditBox(this.font, tempFieldX, durRowY, 48, FIELD_HEIGHT,
                Component.translatable("editor.historystages.temporary.duration"));
        durationField.setMaxLength(6);
        durationField.setValue(String.valueOf(tShown.getDuration()));
        durationField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        durationField.setResponder(val -> {
            ensureTemporary().setDuration(val.isEmpty() ? 0 : safeParse(val));
            hasChanges = true;
        });
        addContentWidget(durationField);

        durationUnitDropdown = new DurationUnitDropdown(tShown.getDurationUnit(), 80, picked -> {
            ensureTemporary().setDurationUnit(picked);
            hasChanges = true;
        });
        durationUnitDropdown.setPosition(tempFieldX + 52, durRowY);

        maxTriggersField = new EditBox(this.font, tempFieldX, bodyY + 44, 48, FIELD_HEIGHT,
                Component.translatable("editor.historystages.temporary.max_triggers"));
        maxTriggersField.setMaxLength(6);
        maxTriggersField.setValue(String.valueOf(tShown.getMaxTriggers()));
        maxTriggersField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        maxTriggersField.setResponder(val -> {
            ensureTemporary().setMaxTriggers(val.isEmpty() ? 1 : safeParse(val));
            hasChanges = true;
            rebuildModeSubsections();
        });
        addContentWidget(maxTriggersField);

        int cdRowY = bodyY + 66;
        cooldownField = new EditBox(this.font, tempFieldX, cdRowY, 48, FIELD_HEIGHT,
                Component.translatable("editor.historystages.temporary.cooldown"));
        cooldownField.setMaxLength(6);
        cooldownField.setValue(String.valueOf(tShown.getCooldown()));
        cooldownField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        cooldownField.setResponder(val -> {
            ensureTemporary().setCooldown(val.isEmpty() ? 0 : safeParse(val));
            hasChanges = true;
        });
        addContentWidget(cooldownField);

        cooldownUnitDropdown = new DurationUnitDropdown(tShown.getCooldownUnit(), 80, picked -> {
            ensureTemporary().setCooldownUnit(picked);
            hasChanges = true;
        });
        cooldownUnitDropdown.setPosition(tempFieldX + 52, cdRowY);

        // --- Display card text fields (positioned by layoutDisplayCard) ---
        // Create with a sane width and reset the cursor to the start AFTER setValue, so the
        // existing text is scrolled into view immediately (not only after clicking in).
        nameTextField = new EditBox(this.font, 0, 0, 100, FIELD_HEIGHT,
                Component.translatable("editor.historystages.display.name_text"));
        nameTextField.setMaxLength(128);
        nameTextField.setValue(editHiddenDisplay.getNameText());
        nameTextField.setCursorPosition(0);
        nameTextField.setHighlightPos(0);
        nameTextField.setResponder(v -> { editHiddenDisplay.setNameText(v); hasChanges = true; });
        addContentWidget(nameTextField);

        tooltipTextField = new EditBox(this.font, 0, 0, 100, FIELD_HEIGHT,
                Component.translatable("editor.historystages.display.tooltip_text"));
        tooltipTextField.setMaxLength(256);
        tooltipTextField.setValue(editHiddenDisplay.getTooltipText());
        tooltipTextField.setCursorPosition(0);
        tooltipTextField.setHighlightPos(0);
        tooltipTextField.setResponder(v -> { editHiddenDisplay.setTooltipText(v); hasChanges = true; });
        addContentWidget(tooltipTextField);

        // Name/Tooltip mode dropdowns (off/replace and off/hidden/replace)
        DisplayMode initialName = editHiddenDisplay.getNameMode() == DisplayMode.HIDDEN
                ? DisplayMode.REPLACE : editHiddenDisplay.getNameMode();
        editHiddenDisplay.setNameMode(initialName); // name never supports HIDDEN
        nameModeDropdown = new DisplayModeDropdown(NAME_MODES, initialName, DISP_DROPDOWN_W, mode -> {
            editHiddenDisplay.setNameMode(mode);
            tooltipModeDropdown.close();
            onDisplayChanged();
        });
        tooltipModeDropdown = new DisplayModeDropdown(TOOLTIP_MODES, editHiddenDisplay.getTooltipMode(),
                DISP_DROPDOWN_W, mode -> {
                    editHiddenDisplay.setTooltipMode(mode);
                    nameModeDropdown.close();
                    onDisplayChanged();
                });

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> confirmDiscard(), 10, this.height - 25, 50, 18));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> save(), this.width - 60, this.height - 25, 50, 18));

        rebuildModeSubsections();
    }

    /** Width reserved for inline labels inside the card body (research time / tier / tier mode). */
    private int labelInsetW() {
        int w = 0;
        w = Math.max(w, this.font.width(Component.translatable("editor.historystages.field.research_time").getString()));
        w = Math.max(w, this.font.width(Component.translatable("editor.historystages.field.min_pedestal_tier").getString()));
        w = Math.max(w, this.font.width(Component.translatable("editor.historystages.field.pedestal_tier_mode").getString()));
        return w + 6;
    }

    private Component buildAutoTriggerLabel() {
        int count = editAutoTrigger == null ? 0 : editAutoTrigger.getTriggers().size();
        return Component.translatable("editor.historystages.auto_trigger.configure", count);
    }

    /** Lazily creates the temporary config so field edits have somewhere to write. */
    private TemporaryConfig ensureTemporary() {
        if (editTemporary == null) editTemporary = new TemporaryConfig();
        return editTemporary;
    }

    private static int safeParse(String val) {
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 0; }
    }

    /** Width reserved for inline labels inside the TEMPORARY card body. */
    private int tempLabelInsetW() {
        int w = 0;
        w = Math.max(w, this.font.width(Component.translatable("editor.historystages.temporary.duration").getString()));
        w = Math.max(w, this.font.width(Component.translatable("editor.historystages.temporary.max_triggers").getString()));
        w = Math.max(w, this.font.width(Component.translatable("editor.historystages.temporary.cooldown").getString()));
        return w + 6;
    }

    private void rebuildModeSubsections() {
        boolean isDefault = editMode == StageMode.DEFAULT;
        boolean isAuto = editMode == StageMode.AUTO;
        boolean isTemporary = editMode == StageMode.TEMPORARY;
        // Cooldown only matters when the stage can be unlocked more than once.
        boolean reTrig = isTemporary && editTemporary != null && editTemporary.allowsMultiple();

        researchTimeField.visible = isDefault;
        researchTimeField.active = isDefault;
        tierModeButton.visible = isDefault;
        tierModeButton.active = isDefault;
        // tierDropdown is not a registered widget — visibility handled in render()/mouseClicked()
        // AUTO and TEMPORARY both configure discovery triggers via the same editor.
        autoTriggerButton.visible = isAuto || isTemporary;
        autoTriggerButton.active = isAuto || isTemporary;

        durationField.visible = isTemporary;
        durationField.active = isTemporary;
        maxTriggersField.visible = isTemporary;
        maxTriggersField.active = isTemporary;
        cooldownField.visible = reTrig;
        cooldownField.active = reTrig;
        // Unit dropdowns aren't registered widgets — render/click is gated on mode
        // in render()/mouseClicked(). Collapse them when hidden so a stale popup
        // doesn't linger after switching modes.
        if (!isTemporary) durationUnitDropdown.close();
        if (!reTrig) cooldownUnitDropdown.close();

        layoutDisplayCard();
    }

    /** Computes the Display card geometry below the (variable-height) mode card. */
    private void layoutDisplayCard() {
        if (nameTextField == null || tooltipTextField == null) return;

        displayCardX = cardX;
        displayCardW = cardW;
        displayCardY = cardY + computeCardHeight() + 6;

        int labelW = Math.max(
                this.font.width(Component.translatable("editor.historystages.display.name").getString()),
                this.font.width(Component.translatable("editor.historystages.display.tooltip").getString()));
        displayControlX = displayCardX + 12 + labelW + 8;

        boolean nameReplace = editHiddenDisplay.getNameMode() == DisplayMode.REPLACE;
        boolean tipReplace = editHiddenDisplay.getTooltipMode() == DisplayMode.REPLACE;

        displayNameRowY = displayCardY + DISP_BODY_TOP;
        displayTooltipRowY = displayNameRowY + 18 + DISP_ROW_GAP;
        lockHintsRowY = displayTooltipRowY + 18 + DISP_ROW_GAP;
        displayCardH = (lockHintsRowY + DISP_TOGGLE_H) - displayCardY + DISP_BOTTOM_PAD;

        // Dropdowns on the left of each row; REPLACE input field to their right.
        // Both rows share one fieldX so they stay aligned, sized to whichever dropdown needs more room.
        nameModeDropdown.setPosition(displayControlX, displayNameRowY);
        tooltipModeDropdown.setPosition(displayControlX, displayTooltipRowY);
        int dropdownW = Math.max(nameModeDropdown.getWidth(), tooltipModeDropdown.getWidth());

        int fieldX = displayControlX + dropdownW + 8;
        int fieldW = Math.max(30, (displayCardX + displayCardW - 12) - fieldX);
        nameTextField.setPosition(fieldX, displayNameRowY);
        nameTextField.setWidth(fieldW);
        nameTextField.visible = nameReplace;
        nameTextField.active = nameReplace;
        tooltipTextField.setPosition(fieldX, displayTooltipRowY);
        tooltipTextField.setWidth(fieldW);
        tooltipTextField.visible = tipReplace;
        tooltipTextField.active = tipReplace;

        // Lock-hints toggle button geometry (label on the left, button to its right).
        String label = Component.translatable("editor.historystages.display.show_lock_hints").getString();
        String value = lockHintsValue();
        lockHintsToggleX = displayCardX + 12 + this.font.width(label) + 8;
        lockHintsToggleW = this.font.width(value) + 8;
    }

    private String lockHintsValue() {
        return Component.translatable(editHiddenDisplay.isShowLockHints()
                ? "editor.historystages.display.on"
                : "editor.historystages.display.off").getString();
    }

    /** Repositions every content widget for the current scroll offset. Called each frame. */
    private void layoutAll() {
        cardX = 30;
        cardW = this.width - cardX - 30;
        cardY = 96 - renderScroll;

        stageIdField.setPosition(fieldX, 22 - renderScroll);
        displayNameField.setPosition(fieldX, 44 - renderScroll);
        modeDropdownX = fieldX;
        modeDropdownY = 66 - renderScroll;
        modeDropdownW = computeModeDropdownWidth();

        int bodyY = cardY + 28;
        int cardFieldX = cardX + 12 + labelInsetW();
        researchTimeField.setPosition(cardFieldX, bodyY);
        tierDropdown.setPosition(cardFieldX, bodyY + 22);
        tierModeButton.setPosition(cardFieldX, bodyY + 44);
        autoTriggerButton.setPosition(cardX + 12, bodyY);

        int tempFieldX = cardX + 12 + tempLabelInsetW();
        durationField.setPosition(tempFieldX, bodyY + 22);
        durationUnitDropdown.setPosition(tempFieldX + 52, bodyY + 22);
        maxTriggersField.setPosition(tempFieldX, bodyY + 44);
        cooldownField.setPosition(tempFieldX, bodyY + 66);
        cooldownUnitDropdown.setPosition(tempFieldX + 52, bodyY + 66);

        layoutDisplayCard();
    }

    /** Display-card height (scroll-invariant). Dropdown + input share one row per axis. */
    private int computeDisplayCardHeight() {
        int nameRow = DISP_BODY_TOP;
        int tooltipRow = nameRow + 18 + DISP_ROW_GAP;
        int lockHintsRow = tooltipRow + 18 + DISP_ROW_GAP;
        return lockHintsRow + DISP_TOGGLE_H + DISP_BOTTOM_PAD;
    }

    private void clampScroll() {
        int contentBottom = 96 + computeCardHeight() + 6 + computeDisplayCardHeight();
        maxScroll = Math.max(0, contentBottom + 6 - viewBottom);
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (maxScroll > 0) {
            modeDropdownOpen = false;
            if (durationUnitDropdown != null) durationUnitDropdown.close();
            if (cooldownUnitDropdown != null) cooldownUnitDropdown.close();
            if (nameModeDropdown != null) nameModeDropdown.close();
            if (tooltipModeDropdown != null) tooltipModeDropdown.close();
            scrollY -= (int) (dy * 12);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollBarDragging) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollBarDragging) {
            scrollBarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollFromMouse(double mouseY) {
        int scrollAreaHeight = viewBottom - viewTop;
        int thumbHeight = Math.max(20, (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
        float usableH = scrollAreaHeight - thumbHeight;
        if (usableH > 0) {
            float ratio = (float) (mouseY - viewTop - thumbHeight / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollY = Math.round(ratio * maxScroll);
            clampScroll();
            smoothScrollOffset = scrollY; // snap while dragging for responsiveness
        }
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
        // The callback hands the values up and persists the stage; staying put is deliberate,
        // so Save never yanks the user out of the screen they are working in.
        onSave.onSave(editStageId, editDisplayName, editResearchTime, editMinTier, editTierMode,
                editMode, editAutoTrigger, editTemporary, editHiddenDisplay);
        hasChanges = false;
    }

    private static String tierModeLabelKey(TierMode mode) {
        return mode == TierMode.EXACT
                ? "editor.historystages.tier_mode.exact"
                : "editor.historystages.tier_mode.min";
    }

    /**
     * Width for the mode dropdown (button + popup), grown to fit the longest name/description
     * across all modes instead of shrinking text. Floored at {@code fieldWidth} (so it lines up
     * with the fields above it) and capped at the same screen-edge margin {@code fieldWidth} uses.
     */
    private int computeModeDropdownWidth() {
        int w = fieldWidth;
        for (StageMode m : StageMode.values()) {
            int nameW = this.font.width(Component.translatable(modeLabelKey(m)).getString());
            int descW = this.font.width(Component.translatable(modeDescKey(m)).getString());
            w = Math.max(w, nameW + 20);
            w = Math.max(w, Math.max(nameW, descW) + 16);
        }
        int cap = Math.max(fieldWidth, this.width - fieldX - 40);
        return Math.min(w, cap);
    }

    private static String modeLabelKey(StageMode m) {
        return switch (m) {
            case DEFAULT -> "editor.historystages.mode.default";
            case AUTO -> "editor.historystages.mode.auto";
            case EXTERNAL -> "editor.historystages.mode.external";
            case TEMPORARY -> "editor.historystages.mode.temporary";
        };
    }

    private static String modeDescKey(StageMode m) {
        return switch (m) {
            case DEFAULT -> "editor.historystages.mode.default.desc";
            case AUTO -> "editor.historystages.mode.auto.desc";
            case EXTERNAL -> "editor.historystages.mode.external.desc";
            case TEMPORARY -> "editor.historystages.mode.temporary.desc";
        };
    }

    private static String modeCardKey(StageMode m) {
        return switch (m) {
            case DEFAULT -> "editor.historystages.mode.card.default";
            case AUTO -> "editor.historystages.mode.card.auto";
            case EXTERNAL -> "editor.historystages.mode.card.external";
            case TEMPORARY -> "editor.historystages.mode.card.temporary";
        };
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
        if (stageIdField.isFocused() || displayNameField.isFocused() || researchTimeField.isFocused()
                || durationField.isFocused() || maxTriggersField.isFocused() || cooldownField.isFocused()
                || (nameTextField != null && nameTextField.isFocused())
                || (tooltipTextField != null && tooltipTextField.isFocused())) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 256) { // ESC
            if (modeDropdownOpen) { modeDropdownOpen = false; return true; }
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
        guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);

        clampScroll();
        smoothScrollOffset += (scrollY - smoothScrollOffset) * 0.25f;
        if (Math.abs(smoothScrollOffset - scrollY) < 0.5f) smoothScrollOffset = scrollY;
        renderScroll = Math.round(smoothScrollOffset);
        layoutAll();

        // --- Fixed title bar (above the viewport) ---
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("editor.historystages.stage_settings.title"),
                this.width / 2, 8, 0xFFFFFF);
        guiGraphics.fill(10, 18, this.width - 10, 19, 0xFF555555);

        // --- Scrolled content, clipped to the viewport ---
        guiGraphics.enableScissor(0, viewTop, this.width, viewBottom);

        int labelX = 30;
        guiGraphics.drawString(this.font,
                Component.translatable("editor.historystages.field.stage_id").getString(),
                labelX, 27 - renderScroll, 0xAAAAAA, false);
        guiGraphics.drawString(this.font,
                Component.translatable("editor.historystages.field.display_name").getString(),
                labelX, 49 - renderScroll, 0xAAAAAA, false);
        guiGraphics.drawString(this.font,
                Component.translatable("editor.historystages.mode.label").getString(),
                labelX, 71 - renderScroll, 0xAAAAAA, false);

        // Card chrome (before widgets so they sit on top)
        int cardH = computeCardHeight();
        renderCard(guiGraphics, cardX, cardY, cardW, cardH, modeCardKey(editMode));
        renderCard(guiGraphics, displayCardX, displayCardY, displayCardW, displayCardH,
                "editor.historystages.display.card");

        // Content widgets (manually rendered at their scrolled positions)
        for (AbstractWidget w : contentWidgets) {
            w.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        renderDisplayCardContent(guiGraphics, mouseX, mouseY);

        int bodyY = cardY + 28;

        if (editMode == StageMode.DEFAULT) {
            int cardLabelX = cardX + 12;
            guiGraphics.drawString(this.font,
                    Component.translatable("editor.historystages.field.research_time").getString(),
                    cardLabelX, bodyY + 5, 0xAAAAAA, false);
            guiGraphics.drawString(this.font,
                    Component.translatable("editor.historystages.field.min_pedestal_tier").getString(),
                    cardLabelX, bodyY + 27, 0xAAAAAA, false);
            guiGraphics.drawString(this.font,
                    Component.translatable("editor.historystages.field.pedestal_tier_mode").getString(),
                    cardLabelX, bodyY + 49, 0xAAAAAA, false);
            tierDropdown.renderButton(guiGraphics, this.font, mouseX, mouseY);
        } else if (editMode == StageMode.AUTO) {
            int count = editAutoTrigger == null ? 0 : editAutoTrigger.getTriggers().size();
            if (count == 0) {
                String warn = Component.translatable("editor.historystages.auto_trigger.no_triggers_warn").getString();
                guiGraphics.drawString(this.font, warn, cardX + 12, bodyY + FIELD_HEIGHT + 8, 0xFFAA55, false);
            }
        } else if (editMode == StageMode.EXTERNAL) {
            String help = Component.translatable("editor.historystages.mode.external.help").getString();
            guiGraphics.drawString(this.font, help, cardX + 12, bodyY + 6, 0xAAAAAA, false);
        } else if (editMode == StageMode.TEMPORARY) {
            int cardLabelX = cardX + 12;
            guiGraphics.drawString(this.font,
                    Component.translatable("editor.historystages.temporary.duration").getString(),
                    cardLabelX, bodyY + 22 + 5, 0xAAAAAA, false);
            durationUnitDropdown.renderButton(guiGraphics, this.font, mouseX, mouseY);
            guiGraphics.drawString(this.font,
                    Component.translatable("editor.historystages.temporary.max_triggers").getString(),
                    cardLabelX, bodyY + 44 + 5, 0xAAAAAA, false);
            String maxHint = editTemporary != null && editTemporary.isUnlimited()
                    ? Component.translatable("editor.historystages.temporary.max_triggers.unlimited").getString()
                    : Component.translatable("editor.historystages.temporary.max_triggers.hint").getString();
            drawSmallText(guiGraphics, maxHint, maxTriggersField.getX() + 52, bodyY + 44 + 6, 0x888888);
            if (editTemporary != null && editTemporary.allowsMultiple()) {
                guiGraphics.drawString(this.font,
                        Component.translatable("editor.historystages.temporary.cooldown").getString(),
                        cardLabelX, bodyY + 66 + 5, 0xAAAAAA, false);
                cooldownUnitDropdown.renderButton(guiGraphics, this.font, mouseX, mouseY);
            }
        }

        // Mode dropdown button (inside the viewport)
        renderModeDropdownButton(guiGraphics, mouseX, mouseY);

        guiGraphics.disableScissor();

        // Scrollbar (matches the StageDetailScreen style)
        if (maxScroll > 0) {
            int scrollAreaHeight = viewBottom - viewTop;
            int barHeight = Math.max(20, (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
            int barY = viewTop + (int) ((float) renderScroll / maxScroll * (scrollAreaHeight - barHeight));
            int barX = this.width - 28;
            boolean barHovered = mouseX >= barX - 2 && mouseX <= barX + 7
                    && mouseY >= barY && mouseY <= barY + barHeight;
            guiGraphics.fill(barX, viewTop, barX + 5, viewBottom, 0x30FFFFFF);
            int barColor = (scrollBarDragging || barHovered) ? 0xCCFFFFFF : 0x80FFFFFF;
            guiGraphics.fill(barX, barY, barX + 5, barY + barHeight, barColor);
        }

        // Fixed Back/Save buttons (the only auto-rendered widgets), outside the viewport
        super.render(guiGraphics, mouseX, mouseY, partialTick);

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

        // Popups (must be last so they overlay everything; drawn unclipped)
        if (editMode == StageMode.DEFAULT) {
            tierDropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
        }
        if (editMode == StageMode.TEMPORARY) {
            durationUnitDropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
            if (editTemporary != null && editTemporary.allowsMultiple()) {
                cooldownUnitDropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
            }
        }
        if (modeDropdownOpen) {
            renderModeDropdownPopup(guiGraphics, mouseX, mouseY);
        }
        nameModeDropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
        tooltipModeDropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
    }

    private int computeCardHeight() {
        return switch (editMode) {
            case DEFAULT -> 28 + 66 + 8;   // header + 3 rows
            case AUTO    -> 28 + FIELD_HEIGHT + 24; // header + button + warn area
            case EXTERNAL -> 28 + 20;
            // header + auto-trigger button + duration row + re-trigger row (+ cooldown row when re-triggerable)
            case TEMPORARY -> 28 + (editTemporary != null && editTemporary.allowsMultiple() ? 88 : 66) + 8;
        };
    }

    private void renderCard(GuiGraphics g, int x, int y, int w, int h, String headerKey) {
        // Outer border
        g.fill(x, y, x + w, y + h, 0xFF555555);
        // Inner background
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF1A1A1A);
        // Header band
        g.fill(x + 1, y + 1, x + w - 1, y + 20, 0xFF2D2D2D);
        g.fill(x + 1, y + 20, x + w - 1, y + 21, 0xFF555555);
        // Header text
        g.drawString(this.font, Component.translatable(headerKey).getString(),
                x + 8, y + 7, 0xFFCC00, false);
    }

    private void renderDisplayCardContent(GuiGraphics g, int mouseX, int mouseY) {
        if (displayCardH <= 0) return;
        int labelX = displayCardX + 12;

        g.drawString(this.font, Component.translatable("editor.historystages.display.name").getString(),
                labelX, displayNameRowY + 5, 0xAAAAAA, false);
        g.drawString(this.font, Component.translatable("editor.historystages.display.tooltip").getString(),
                labelX, displayTooltipRowY + 5, 0xAAAAAA, false);

        nameModeDropdown.renderButton(g, this.font, mouseX, mouseY);
        tooltipModeDropdown.renderButton(g, this.font, mouseX, mouseY);

        // Show-lock-hints toggle button (DependencyEditorScreen style)
        g.drawString(this.font, Component.translatable("editor.historystages.display.show_lock_hints").getString(),
                labelX, lockHintsRowY + 3, 0xAAAAAA, false);
        boolean tHov = mouseX >= lockHintsToggleX && mouseX < lockHintsToggleX + lockHintsToggleW
                && mouseY >= lockHintsRowY && mouseY < lockHintsRowY + DISP_TOGGLE_H;
        g.fill(lockHintsToggleX, lockHintsRowY, lockHintsToggleX + lockHintsToggleW, lockHintsRowY + DISP_TOGGLE_H,
                tHov ? 0xFF3D3520 : 0xFF2A2A2A);
        g.drawString(this.font, lockHintsValue(), lockHintsToggleX + 4, lockHintsRowY + 3,
                tHov ? 0xFFCC00 : 0xCCCCCC, false);
    }

    /** Returns true if a Display-card control consumed the click. */
    private boolean handleDisplayCardClick(double mouseX, double mouseY) {
        if (displayCardH <= 0) return false;

        if (mouseX >= lockHintsToggleX && mouseX < lockHintsToggleX + lockHintsToggleW
                && mouseY >= lockHintsRowY && mouseY < lockHintsRowY + DISP_TOGGLE_H) {
            editHiddenDisplay.setShowLockHints(!editHiddenDisplay.isShowLockHints());
            onDisplayChanged();
            return true;
        }
        return false;
    }

    private void onDisplayChanged() {
        hasChanges = true;
        layoutDisplayCard();
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void renderModeDropdownButton(GuiGraphics g, int mouseX, int mouseY) {
        boolean hovered = mouseX >= modeDropdownX && mouseX <= modeDropdownX + modeDropdownW
                && mouseY >= modeDropdownY && mouseY < modeDropdownY + FIELD_HEIGHT;

        int bg = hovered ? 0x40FFFFFF : 0x25FFFFFF;
        g.fill(modeDropdownX, modeDropdownY, modeDropdownX + modeDropdownW, modeDropdownY + FIELD_HEIGHT, bg);
        g.fill(modeDropdownX, modeDropdownY + FIELD_HEIGHT - 1,
                modeDropdownX + modeDropdownW, modeDropdownY + FIELD_HEIGHT,
                hovered ? 0xFFFFCC00 : 0x60FFCC00);

        String label = Component.translatable(modeLabelKey(editMode)).getString();
        g.drawString(this.font, label, modeDropdownX + 6, modeDropdownY + 5, 0xFFFFFF, false);

        String arrow = modeDropdownOpen ? "▲" : "▼";
        g.drawString(this.font, arrow,
                modeDropdownX + modeDropdownW - 10, modeDropdownY + 5, 0x999999, false);
    }

    private void renderModeDropdownPopup(GuiGraphics g, int mouseX, int mouseY) {
        StageMode[] modes = StageMode.values();
        int rowH = 22;
        int popupH = modes.length * rowH;
        int px = modeDropdownX;
        int py = modeDropdownY + FIELD_HEIGHT + 1;
        int pw = modeDropdownW;

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        // Frame
        g.fill(px - 1, py - 1, px + pw + 1, py + popupH + 1, 0xFF555555);
        g.fill(px, py, px + pw, py + popupH, 0xFF1A1A1A);

        for (int i = 0; i < modes.length; i++) {
            StageMode m = modes[i];
            int rowY = py + i * rowH;
            boolean hov = mouseX >= px && mouseX <= px + pw
                    && mouseY >= rowY && mouseY < rowY + rowH;
            if (hov) g.fill(px, rowY, px + pw, rowY + rowH, 0x30FFCC00);
            if (m == editMode) g.fill(px, rowY, px + 2, rowY + rowH, 0xFFFFCC00);

            String name = Component.translatable(modeLabelKey(m)).getString();
            String desc = Component.translatable(modeDescKey(m)).getString();
            int nameColor = hov ? 0xFFFFFF : (m == editMode ? 0xFFCC00 : 0xDDDDDD);
            g.drawString(this.font, name, px + 8, rowY + 3, nameColor, false);
            g.drawString(this.font, desc, px + 8, rowY + 13, 0x888888, false);
        }

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Scrollbar drag start (takes priority)
        if (button == 0 && maxScroll > 0) {
            int barX = this.width - 28;
            if (mouseX >= barX - 2 && mouseX <= barX + 7 && mouseY >= viewTop && mouseY <= viewBottom) {
                scrollBarDragging = true;
                updateScrollFromMouse(mouseY);
                return true;
            }
        }

        if (button == 0 && modeDropdownOpen) {
            StageMode[] modes = StageMode.values();
            int rowH = 22;
            int px = modeDropdownX;
            int py = modeDropdownY + FIELD_HEIGHT + 1;
            int pw = modeDropdownW;
            for (int i = 0; i < modes.length; i++) {
                int rowY = py + i * rowH;
                if (mouseX >= px && mouseX <= px + pw && mouseY >= rowY && mouseY < rowY + rowH) {
                    StageMode picked = modes[i];
                    modeDropdownOpen = false;
                    if (picked != editMode) {
                        editMode = picked;
                        if (editMode != origMode) hasChanges = true;
                        // Materialize a default config so saving TEMPORARY without
                        // touching the fields still writes sensible values.
                        if (editMode == StageMode.TEMPORARY) ensureTemporary();
                        rebuildModeSubsections();
                    }
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
            modeDropdownOpen = false;
            return true;
        }

        if (button == 0 && mouseX >= modeDropdownX && mouseX <= modeDropdownX + modeDropdownW
                && mouseY >= modeDropdownY && mouseY < modeDropdownY + FIELD_HEIGHT) {
            modeDropdownOpen = !modeDropdownOpen;
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (editMode == StageMode.DEFAULT && tierDropdown != null
                && tierDropdown.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        if (editMode == StageMode.TEMPORARY) {
            if (durationUnitDropdown.mouseClicked(mouseX, mouseY)) return true;
            if (editTemporary != null && editTemporary.allowsMultiple()
                    && cooldownUnitDropdown.mouseClicked(mouseX, mouseY)) {
                return true;
            }
        }
        if (nameModeDropdown.mouseClicked(mouseX, mouseY)) return true;
        if (tooltipModeDropdown.mouseClicked(mouseX, mouseY)) return true;
        if (button == 0 && handleDisplayCardClick(mouseX, mouseY)) return true;
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
