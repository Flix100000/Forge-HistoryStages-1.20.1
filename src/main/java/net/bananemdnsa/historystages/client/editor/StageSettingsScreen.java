package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DisplayModeDropdown;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DropdownChrome;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DurationUnitDropdown;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.PedestalTierDropdown;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.widget.list.PickerOverlay;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemList;
import net.bananemdnsa.historystages.data.display.DisplayMode;
import net.bananemdnsa.historystages.client.editor.widget.dialog.FormattedTextScreen;
import net.bananemdnsa.historystages.data.display.HiddenDisplayConfig;
import net.bananemdnsa.historystages.data.ScrollCompletion;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphInfoPacket;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import net.bananemdnsa.historystages.data.settings.Setting;
import net.bananemdnsa.historystages.data.settings.SettingKind;
import net.bananemdnsa.historystages.data.settings.SettingsValues;
import net.bananemdnsa.historystages.data.settings.StageSettingsGroup;
import net.bananemdnsa.historystages.data.settings.StageSettingsGroups;
import net.bananemdnsa.historystages.data.temporary.TemporaryConfig;
import net.bananemdnsa.historystages.research.TierMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class StageSettingsScreen extends Screen {

    @FunctionalInterface
    public interface SaveCallback {
        void onSave(String stageId, String displayName, int researchTime,
                    int minPedestalTier, TierMode pedestalTierMode,
                    StageMode mode, AutoTrigger autoTrigger, TemporaryConfig temporary,
                    HiddenDisplayConfig hiddenDisplay, boolean loseOnDeath,
                    String scrollCompletion,
                    Map<String, SettingsValues> addonSettings);
    }

    private static final int FIELD_HEIGHT = 18;

    /**
     * Top of the first card. Everything above it is the four stacked fields — id, display name,
     * mode, description — so adding a field means moving this and nothing else.
     */
    private static final int CARD_TOP = 140;
    private static final float SMALL_SCALE = 0.85f;

    private final Screen parent;
    private final boolean isNewStage;
    /** Individual stages get an extra settings card; global stages never show it. */
    private final boolean isIndividual;
    private final SaveCallback onSave;
    /** Live snapshot of the parent stage's lock data, forwarded to the trigger editor. */
    private final Supplier<StageEntry> lockSnapshot;

    private String saveError = "";

    /**
     * The stage's graph description. Held here and sent by {@link #save()} rather than saved by
     * the text dialog itself, so it follows the same rule as every other field on this screen:
     * closing without Save changes nothing.
     */
    private String editDescription;
    private final String origDescription;
    private StyledButton descriptionButton;

    /**
     * Per-stage override for what happens to the scroll when this stage finishes. Empty means
     * "follow the config default", exactly the way an empty icon falls back to the default icon —
     * the global value stays in the config editor, the exception lives with the stage.
     */
    private String editScrollCompletion;
    private final String origScrollCompletion;
    private EnumDropdown scrollCompletionDropdown;

    /** Empty first: "follow the config default" is the normal state and belongs at the top. */
    private static final List<String> SCROLL_COMPLETION_OPTIONS = List.of(
            "", ScrollCompletion.CONSUME.serialize(),
            ScrollCompletion.REPLACE.serialize(), ScrollCompletion.OPEN.serialize());

    private String editStageId;
    private String editDisplayName;
    private int editResearchTime;
    private int editMinTier;
    private TierMode editTierMode;
    private StageMode editMode;
    private AutoTrigger editAutoTrigger;
    private TemporaryConfig editTemporary;
    private final HiddenDisplayConfig editHiddenDisplay;
    private boolean editLoseOnDeath;
    /**
     * Working copy of the stage's addon settings, keyed by group id. Copied on construction and
     * handed back only from {@link #save()}, exactly like every other field on this screen, so
     * closing without saving changes nothing.
     */
    private final Map<String, SettingsValues> editAddonSettings;

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

    // Individual card state (only laid out and rendered when isIndividual).
    private int indivCardX, indivCardY, indivCardW, indivCardH;
    private int loseRowY, loseToggleX, loseToggleW;
    // Individual-card vertical metrics (kept in sync with computeIndividualCardHeight()).
    private static final int INDIV_BODY_TOP = 28;
    private static final int INDIV_TOGGLE_H = 14;
    private static final int INDIV_HINT_GAP = 4;
    private static final int INDIV_HINT_H = 10;
    private static final int INDIV_BOTTOM_PAD = 8;

    /**
     * Addon settings cards, one per group returned by {@link #applicableGroups()}, laid out below
     * every fixed card (including the Individual card, when present). Rebuilt in {@link #init()};
     * their editable state lives in {@link #editAddonSettings}, not in the widgets themselves, so
     * it survives a resize the same way every other field on this screen does.
     */
    private final List<AddonCard> addonCards = new ArrayList<>();
    private static final int ADDON_BODY_TOP = 28;
    private static final int ADDON_ROW_SPACING = 22;
    private static final int ADDON_BOTTOM_PAD = 8;
    private static final int ADDON_TOGGLE_H = 14;
    private static final int ADDON_INT_FIELD_W = 80;
    private static final int ADDON_DROPDOWN_MIN_W = 100;

    /**
     * The open item picker for an ITEM addon field, or null. Mirrors {@link ConfigEditorScreen}'s
     * field of the same purpose: one slot rather than one per row, since only one row's picker can
     * be open at a time and every dismiss/render/input path is identical regardless of which row
     * opened it.
     */
    private PickerOverlay itemPickerOverlay;

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
    private final Anim smoothScrollOffset = new Anim();

    /** Reveal of the mode dropdown; also turns its caret over. */
    private final Anim modeOpen = new Anim();
    private final Anim modeHover = new Anim();
    private final Map<Integer, Anim> modeRowHover = new HashMap<>();
    /** Hover state of the two card toggles, which used to switch colour in one frame. */
    private final Anim lockHintsHover = new Anim();
    private final Anim loseHover = new Anim();
    private final Anim scrollThumbHover = new Anim();
    /**
     * Gold wash over the button row after a successful save. Save deliberately stays on this
     * screen, so without it the only confirmation is a toast the eye may already have left.
     */
    private long saveFlashAt = -1L;
    private int renderScroll = 0;
    /** Content widgets rendered manually inside the scrolled viewport (not auto-rendered). */
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();

    public StageSettingsScreen(Screen parent, String stageId, String displayName, int researchTime,
                               int minPedestalTier, TierMode pedestalTierMode,
                               StageMode mode, AutoTrigger autoTrigger, TemporaryConfig temporary,
                               HiddenDisplayConfig hiddenDisplay, boolean loseOnDeath,
                               String scrollCompletion, Map<String, SettingsValues> addonSettings,
                               boolean isNewStage, boolean isIndividual, SaveCallback onSave) {
        this(parent, stageId, displayName, researchTime, minPedestalTier, pedestalTierMode,
                mode, autoTrigger, temporary, hiddenDisplay, loseOnDeath, scrollCompletion, addonSettings,
                isNewStage, isIndividual, onSave, null);
    }

    public StageSettingsScreen(Screen parent, String stageId, String displayName, int researchTime,
                               int minPedestalTier, TierMode pedestalTierMode,
                               StageMode mode, AutoTrigger autoTrigger, TemporaryConfig temporary,
                               HiddenDisplayConfig hiddenDisplay, boolean loseOnDeath,
                               String scrollCompletion, Map<String, SettingsValues> addonSettings,
                               boolean isNewStage, boolean isIndividual, SaveCallback onSave,
                               Supplier<StageEntry> lockSnapshot) {
        super(Component.translatable("editor.historystages.stage_settings.title"));
        this.parent = parent;
        this.isNewStage = isNewStage;
        this.isIndividual = isIndividual;
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
        this.editLoseOnDeath = loseOnDeath;
        this.editAddonSettings = copyAddonSettings(addonSettings);

        this.editScrollCompletion = scrollCompletion == null ? "" : scrollCompletion;
        this.origScrollCompletion = this.editScrollCompletion;

        String description = GraphStageData.get().description(stageId, isIndividual);
        this.editDescription = description == null ? "" : description;
        this.origDescription = this.editDescription;

        this.origStageId = stageId;
        this.origDisplayName = displayName;
        this.origResearchTime = String.valueOf(researchTime);
        this.origMinTier = minPedestalTier;
        this.origTierMode = this.editTierMode;
        this.origMode = this.editMode;
    }

    /**
     * Deep-copies {@code source} — a new map whose values are each {@link SettingsValues#copy()}
     * — so this screen never holds the caller's own value objects. A shallow copy would still let
     * a field edit here mutate a {@link SettingsValues} the caller (or another open screen) also
     * holds, breaking "closing without Save changes nothing". A null argument becomes empty.
     */
    private static Map<String, SettingsValues> copyAddonSettings(Map<String, SettingsValues> source) {
        Map<String, SettingsValues> copy = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, SettingsValues> entry : source.entrySet()) {
                copy.put(entry.getKey(), entry.getValue().copy());
            }
        }
        return copy;
    }

    /** Registers a content widget for events + manual scrolled rendering (not auto-rendered). */
    private <T extends AbstractWidget> T addContentWidget(T w) {
        this.addWidget(w);
        contentWidgets.add(w);
        return w;
    }

    /** Groups that apply to this stage's scope, in the registry's stable id order. */
    private List<StageSettingsGroup> applicableGroups() {
        return StageSettingsGroups.forScope(isIndividual ? StageScope.INDIVIDUAL : StageScope.GLOBAL);
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
        String labelDescription = Component.translatable("editor.historystages.field.description").getString();
        String labelCompletion = Component.translatable("editor.historystages.field.scroll_completion").getString();
        int maxLabelW = Math.max(
                Math.max(this.font.width(labelId), this.font.width(labelName)),
                Math.max(Math.max(this.font.width(labelStageMode), this.font.width(labelDescription)),
                        this.font.width(labelCompletion)));
        fieldX = labelX + maxLabelW + 10;
        fieldWidth = Math.min(220, this.width - fieldX - 40);

        // Cached card geometry (used by render + mouseClicked)
        cardX = labelX;
        cardW = this.width - cardX - 30;
        cardY = CARD_TOP;

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

        // The description lives in graph_stages.json rather than in the stage entry, so it never
        // reaches SaveCallback — save() sends its own packet. It is edited here because the graph
        // canvas was the only way in, and a pack maker writing a stage should not have to go
        // hunting through a second editor for its one line of prose.
        descriptionButton = StyledButton.of(
                descriptionLabel(),
                btn -> this.minecraft.setScreen(new FormattedTextScreen(
                        this,
                        Component.translatable("editor.historystages.graph.info.title"),
                        editDescription,
                        Component.translatable("editor.historystages.graph.info.hint").getString(),
                        List.of(),
                        text -> {
                            editDescription = text == null ? "" : text;
                            if (!editDescription.equals(origDescription)) hasChanges = true;
                            descriptionButton.setMessage(descriptionLabel());
                        })),
                fieldX, 88, fieldWidth, FIELD_HEIGHT);
        addContentWidget(descriptionButton);

        // The inherit state is one of the options rather than a separate checkbox: it is a choice
        // about where the value comes from, and it belongs in the same list as the values.
        scrollCompletionDropdown = new EnumDropdown(
                SCROLL_COMPLETION_OPTIONS, editScrollCompletion, fieldWidth,
                StageSettingsScreen::scrollCompletionLabel,
                value -> {
                    editScrollCompletion = value == null ? "" : value;
                    if (!editScrollCompletion.equals(origScrollCompletion)) hasChanges = true;
                });
        scrollCompletionDropdown.setPosition(fieldX, 110);

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

        initAddonCards();
        rebuildModeSubsections();
    }

    /**
     * Builds one {@link AddonCard} per applicable group, skipping a group whose {@link
     * SettingsValues} is missing from {@link #editAddonSettings} (it registered after the map was
     * built) rather than throwing. Within a card that is kept, a field is skipped when its
     * {@link Setting#supportedScopes()} does not contain this screen's scope — a field declares
     * its own scope, independently of its group's. A card left with no rows at all is dropped
     * rather than added, so it never renders as an empty header.
     */
    private void initAddonCards() {
        addonCards.clear();
        StageScope scope = isIndividual ? StageScope.INDIVIDUAL : StageScope.GLOBAL;
        for (StageSettingsGroup group : applicableGroups()) {
            SettingsValues values = editAddonSettings.get(group.id());
            if (values == null) continue;
            AddonCard card = new AddonCard(group, values);
            for (Setting<?> field : group.fields()) {
                if (!field.supportedScopes().contains(scope)) continue;
                card.rows.add(createAddonFieldRow(field, values));
            }
            if (card.rows.isEmpty()) continue;
            addonCards.add(card);
        }
    }

    /** Builds the row widget (if any) for one field, wiring it to read/write {@code values}. */
    private AddonFieldRow createAddonFieldRow(Setting<?> field, SettingsValues values) {
        AddonFieldRow row = new AddonFieldRow(field);
        switch (field.kind()) {
            case BOOL -> {
                // Hand-drawn toggle, same as the lock-hints and lose-on-death rows — no widget to build.
            }
            case INTEGER -> {
                @SuppressWarnings("unchecked")
                Setting<Integer> intField = (Setting<Integer>) field;
                // A value SettingsValues#set silently clamps must become visible the moment it is
                // committed — focus lost or Enter — rather than only on the next screen open. The
                // clamp itself already happens per keystroke via the responder below; this box
                // only ever rewrites its own displayed text, and only at commit, so it never fights
                // the user mid-type (typing "100" into a 0-100 field stays uninterrupted).
                EditBox box = new EditBox(this.font, 0, 0, ADDON_INT_FIELD_W, FIELD_HEIGHT,
                        Component.translatable(field.langKey())) {
                    @Override
                    public void setFocused(boolean focused) {
                        boolean wasFocused = this.isFocused();
                        super.setFocused(focused);
                        if (wasFocused && !focused) setValue(String.valueOf(values.get(intField)));
                    }

                    @Override
                    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                        if (keyCode == 257 || keyCode == 335) { // Enter / numpad Enter
                            setValue(String.valueOf(values.get(intField)));
                            return true;
                        }
                        return super.keyPressed(keyCode, scanCode, modifiers);
                    }
                };
                box.setMaxLength(10);
                box.setValue(String.valueOf(values.get(intField)));
                box.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
                box.setResponder(val -> {
                    // Empty or unparseable input leaves the stored value alone rather than writing 0,
                    // same reasoning as researchTimeField but without its own "always writes" behaviour.
                    if (val.isEmpty()) return;
                    try {
                        values.set(intField, Integer.parseInt(val));
                        hasChanges = true;
                    } catch (NumberFormatException ignored) {
                        // Partial input while typing (e.g. a lone '-'); wait for a full number.
                    }
                });
                row.editBox = addContentWidget(box);
            }
            case TEXT -> {
                @SuppressWarnings("unchecked")
                Setting<String> textField = (Setting<String>) field;
                EditBox box = new EditBox(this.font, 0, 0, 100, FIELD_HEIGHT,
                        Component.translatable(field.langKey()));
                box.setMaxLength(256);
                box.setValue(values.get(textField));
                box.setResponder(val -> {
                    values.set(textField, val);
                    hasChanges = true;
                });
                row.editBox = addContentWidget(box);
            }
            case LONG_TEXT -> {
                @SuppressWarnings("unchecked")
                Setting<String> longTextField = (Setting<String>) field;
                // Opens the same wrapping, previewing dialog as the stage description
                // (see descriptionButton above) rather than a one-line EditBox — this field is
                // meant to carry format codes and/or the placeholders it declares.
                StyledButton button = StyledButton.of(
                        addonLongTextLabel(values, longTextField, 100),
                        btn -> this.minecraft.setScreen(new FormattedTextScreen(
                                this,
                                Component.translatable(field.langKey()),
                                values.get(longTextField),
                                field.hintLangKey() != null
                                        ? Component.translatable(field.hintLangKey()).getString() : "",
                                field.placeholders(),
                                text -> {
                                    values.set(longTextField, text == null ? "" : text);
                                    hasChanges = true;
                                    row.button.setMessage(
                                            addonLongTextLabel(values, longTextField, row.button.getWidth()));
                                })),
                        0, 0, 100, FIELD_HEIGHT);
                row.button = addContentWidget(button);
            }
            case CHOICE -> {
                @SuppressWarnings("unchecked")
                Setting<String> choiceField = (Setting<String>) field;
                row.dropdown = new EnumDropdown(choiceField.optionValues(), values.get(choiceField),
                        ADDON_DROPDOWN_MIN_W,
                        value -> Component.translatable(choiceField.optionLangKey(value)),
                        value -> {
                            values.set(choiceField, value);
                            hasChanges = true;
                        });
            }
            case ITEM -> {
                @SuppressWarnings("unchecked")
                Setting<String> itemField = (Setting<String>) field;
                // Same shape as LONG_TEXT: a button whose label is re-derived on every write,
                // opening the item picker instead of a text dialog. The icon that goes with the
                // label is drawn separately, over the button's left edge, in
                // renderAddonCardsContent() — a StyledButton has nowhere to hang an ItemStack.
                StyledButton button = StyledButton.of(
                        addonItemLabel(values, itemField),
                        btn -> openAddonItemPicker(values, itemField, row),
                        0, 0, 100, FIELD_HEIGHT);
                row.button = addContentWidget(button);
            }
        }
        return row;
    }

    /**
     * Shows an ITEM field's current value: the item's display name when the id resolves to a
     * registered item, or the raw id text when it does not. A pack may reference a mod that is
     * temporarily absent, so a non-resolving id is shown as-is rather than replaced or cleared —
     * the same reasoning the raw-JSON settings design applies everywhere else.
     */
    private Component addonItemLabel(SettingsValues values, Setting<String> field) {
        String id = values.get(field);
        if (id == null || id.isEmpty()) {
            return Component.translatable("editor.historystages.field.item.empty");
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null && item != Items.AIR) {
                return new ItemStack(item).getHoverName();
            }
        }
        return Component.literal(id);
    }

    /**
     * Opens the item picker for one ITEM addon field. Mirrors {@link
     * ConfigEditorScreen#openItemPicker}: the picker is shown centred in screen coordinates, not
     * anchored to the row, so it keeps working after the card list has been scrolled.
     */
    private void openAddonItemPicker(SettingsValues values, Setting<String> field, AddonFieldRow row) {
        SearchableItemList picker = new SearchableItemList(itemId -> {
            values.set(field, itemId);
            hasChanges = true;
            row.button.setMessage(addonItemLabel(values, field));
            closePicker();
        });
        itemPickerOverlay = picker;
        picker.show(this.width / 2, this.height / 2, this.width);
    }

    /**
     * Drops the picker reference once it has hidden itself. Clicking outside the panel makes the
     * list hide and still report the click as consumed, so without this the screen would keep a
     * non-null but invisible overlay and never lift the dim layer again.
     */
    private void syncPickerState() {
        if (itemPickerOverlay != null && !itemPickerOverlay.isVisible()) closePicker();
    }

    /** Closes the picker and reports the interaction as consumed. */
    private boolean closePicker() {
        itemPickerOverlay = null;
        return true;
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

        layoutIndividualCard();
        layoutAddonCards();
    }

    /** Computes the Individual card geometry below the Display card. Individual stages only. */
    private void layoutIndividualCard() {
        if (!isIndividual) {
            indivCardH = 0;
            return;
        }
        indivCardX = displayCardX;
        indivCardW = displayCardW;
        indivCardY = displayCardY + displayCardH + 6;
        indivCardH = computeIndividualCardHeight();

        loseRowY = indivCardY + INDIV_BODY_TOP;
        String label = Component.translatable("editor.historystages.individual.lose_on_death").getString();
        loseToggleX = indivCardX + 12 + this.font.width(label) + 8;
        loseToggleW = this.font.width(loseOnDeathValue()) + 8;
    }

    private String loseOnDeathValue() {
        return Component.translatable(editLoseOnDeath
                ? "editor.historystages.display.on"
                : "editor.historystages.display.off").getString();
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
        cardY = CARD_TOP - renderScroll;

        stageIdField.setPosition(fieldX, 22 - renderScroll);
        displayNameField.setPosition(fieldX, 44 - renderScroll);
        modeDropdownX = fieldX;
        modeDropdownY = 66 - renderScroll;
        modeDropdownW = computeModeDropdownWidth();
        descriptionButton.setPosition(fieldX, 88 - renderScroll);
        scrollCompletionDropdown.setPosition(fieldX, 110 - renderScroll);

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

    /** Individual-card height (scroll-invariant): one toggle row plus its hint line. */
    private int computeIndividualCardHeight() {
        return INDIV_BODY_TOP + INDIV_TOGGLE_H + INDIV_HINT_GAP + INDIV_HINT_H + INDIV_BOTTOM_PAD;
    }

    /**
     * Computes geometry for every addon settings card, stacked below the Display card and (when
     * present) the Individual card, in {@link #addonCards} order — which is already
     * {@link #applicableGroups()} order because {@link #initAddonCards()} built it that way.
     */
    private void layoutAddonCards() {
        if (addonCards.isEmpty()) return;
        int y = displayCardY + displayCardH + 6;
        if (isIndividual) y += indivCardH + 6;

        for (AddonCard card : addonCards) {
            card.x = displayCardX;
            card.w = displayCardW;
            card.y = y;
            card.h = computeAddonCardHeight(card);
            layoutAddonCardRows(card);
            y += card.h + 6;
        }
    }

    /** Positions one card's rows and their controls. Labels share one inset, like the Display card. */
    private void layoutAddonCardRows(AddonCard card) {
        if (card.rows.isEmpty()) return;
        int bodyY = card.y + ADDON_BODY_TOP;
        int labelX = card.x + 12;

        int labelW = 0;
        for (AddonFieldRow row : card.rows) {
            labelW = Math.max(labelW, this.font.width(Component.translatable(row.field.langKey()).getString()));
        }
        int controlX = labelX + labelW + 8;
        int controlMaxW = Math.max(30, (card.x + card.w - 12) - controlX);

        for (int i = 0; i < card.rows.size(); i++) {
            AddonFieldRow row = card.rows.get(i);
            row.rowY = bodyY + i * ADDON_ROW_SPACING;
            switch (row.field.kind()) {
                case BOOL -> {
                    String value = boolValueLabel(boolValue(card.values, row.field));
                    row.toggleX = controlX;
                    row.toggleW = this.font.width(value) + 8;
                }
                case INTEGER -> row.editBox.setPosition(controlX, row.rowY);
                case TEXT -> {
                    row.editBox.setPosition(controlX, row.rowY);
                    row.editBox.setWidth(controlMaxW);
                }
                case LONG_TEXT -> {
                    @SuppressWarnings("unchecked")
                    Setting<String> longTextField = (Setting<String>) row.field;
                    row.button.setPosition(controlX, row.rowY);
                    row.button.setWidth(controlMaxW);
                    row.button.setMessage(addonLongTextLabel(card.values, longTextField, controlMaxW));
                }
                case CHOICE -> row.dropdown.setPosition(controlX, row.rowY);
                case ITEM -> {
                    @SuppressWarnings("unchecked")
                    Setting<String> itemField = (Setting<String>) row.field;
                    row.button.setPosition(controlX, row.rowY);
                    row.button.setWidth(controlMaxW);
                    row.button.setMessage(addonItemLabel(card.values, itemField));
                }
            }
        }
    }

    /** Addon-card height (scroll-invariant): header plus one row per row actually built. */
    private int computeAddonCardHeight(AddonCard card) {
        return ADDON_BODY_TOP + card.rows.size() * ADDON_ROW_SPACING + ADDON_BOTTOM_PAD;
    }

    @SuppressWarnings("unchecked")
    private static boolean boolValue(SettingsValues values, Setting<?> field) {
        return values.get((Setting<Boolean>) field);
    }

    @SuppressWarnings("unchecked")
    private static void setBoolValue(SettingsValues values, Setting<?> field, boolean value) {
        values.set((Setting<Boolean>) field, value);
    }

    private String boolValueLabel(boolean value) {
        return Component.translatable(value
                ? "editor.historystages.display.on"
                : "editor.historystages.display.off").getString();
    }

    private void clampScroll() {
        int contentBottom = CARD_TOP + computeCardHeight() + 6 + computeDisplayCardHeight();
        if (isIndividual) contentBottom += 6 + computeIndividualCardHeight();
        for (AddonCard card : addonCards) contentBottom += 6 + computeAddonCardHeight(card);
        maxScroll = Math.max(0, contentBottom + 6 - viewBottom);
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        // The picker sits on top and owns the wheel while it is open; the grid it lists items in
        // scrolls on its own, independently of this screen's card list.
        syncPickerState();
        if (itemPickerOverlay != null) return itemPickerOverlay.mouseScrolled(mouseX, mouseY, dx, dy);

        if (maxScroll > 0) {
            modeDropdownOpen = false;
            if (durationUnitDropdown != null) durationUnitDropdown.close();
            if (cooldownUnitDropdown != null) cooldownUnitDropdown.close();
            if (nameModeDropdown != null) nameModeDropdown.close();
            if (tooltipModeDropdown != null) tooltipModeDropdown.close();
            // An expanded popup would keep its old screen position while the rows move under it.
            if (scrollCompletionDropdown != null) scrollCompletionDropdown.close();
            for (AddonCard card : addonCards) {
                for (AddonFieldRow row : card.rows) {
                    if (row.dropdown != null) row.dropdown.close();
                }
            }
            scrollY -= (int) (dy * 12);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // The picker sits on top and owns the pointer while it is open. Without this its own
        // scrollbar takes the press, sets itself dragging, and then never hears another mouse
        // move — the thumb stays where it jumped to and nothing follows the cursor.
        syncPickerState();
        if (itemPickerOverlay != null && itemPickerOverlay.mouseDragged(mouseX, mouseY)) return true;

        if (scrollBarDragging) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Likewise: without this the picker's drag flag is never cleared, so its thumb keeps
        // rendering as held and the next press behaves as though the button were still down.
        syncPickerState();
        if (itemPickerOverlay != null && itemPickerOverlay.mouseReleased()) return true;

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
            smoothScrollOffset.set(scrollY); // snap while dragging for responsiveness
        }
    }

    private static Component scrollCompletionLabel(String value) {
        if (value == null || value.isEmpty()) {
            return Component.translatable("editor.historystages.field.scroll_completion.inherit");
        }
        return Component.translatable("editor.historystages.enum.scrollcompletion."
                + value.toLowerCase(java.util.Locale.ROOT));
    }

    /** Shows the first line of the description, or a prompt when there is none yet. */
    private Component descriptionLabel() {
        if (editDescription == null || editDescription.isBlank()) {
            return Component.translatable("editor.historystages.field.description.empty");
        }
        String flat = editDescription.replace('\n', ' ');
        return Component.literal(this.font.plainSubstrByWidth(flat, Math.max(20, fieldWidth - 12)));
    }

    /**
     * Shows the first line of a {@link SettingKind#LONG_TEXT} field's current value, truncated to
     * {@code maxWidth}, or the shared "nothing written yet" prompt when the value is empty. Mirrors
     * {@link #descriptionLabel()}.
     */
    private Component addonLongTextLabel(SettingsValues values, Setting<String> field, int maxWidth) {
        String value = values.get(field);
        if (value == null || value.isBlank()) {
            return Component.translatable("editor.historystages.field.long_text.empty");
        }
        String flat = value.replace('\n', ' ');
        return Component.literal(this.font.plainSubstrByWidth(flat, Math.max(20, maxWidth - 12)));
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
        // This screen stays open after Save, so handing back the live map would let every later
        // keystroke reach straight into the editor's state, including keystrokes the user then
        // abandons by closing without saving. Copy it out, same as it was copied in.
        onSave.onSave(editStageId, editDisplayName, editResearchTime, editMinTier, editTierMode,
                editMode, editAutoTrigger, editTemporary, editHiddenDisplay, editLoseOnDeath,
                editScrollCompletion, copyAddonSettings(editAddonSettings));

        // The description rides in graph_stages.json, not in the stage entry, so it has its own
        // packet. Keyed on the original id: a rename is the rename logic's business, and writing
        // the text under a fresh id here would leave the old entry behind.
        if (!editDescription.equals(origDescription)) {
            PacketHandler.sendToServer(new SaveStageGraphInfoPacket(origStageId, isIndividual, editDescription));
            // Optimistic local update, the same reason StageInfoTextScreen does it: the graph
            // reads GraphStageData directly and would otherwise show stale text until the
            // broadcast reply lands.
            GraphStageData.set(GraphStageData.get()
                    .withDescription(origStageId, isIndividual, editDescription));
        }

        hasChanges = false;
        saveFlashAt = System.currentTimeMillis();
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
        syncPickerState();
        if (itemPickerOverlay != null) {
            if (keyCode == 256) return closePicker(); // ESC
            boolean consumed = itemPickerOverlay.keyPressed(keyCode);
            syncPickerState();
            return consumed;
        }
        if (stageIdField.isFocused() || displayNameField.isFocused() || researchTimeField.isFocused()
                || durationField.isFocused() || maxTriggersField.isFocused() || cooldownField.isFocused()
                || (nameTextField != null && nameTextField.isFocused())
                || (tooltipTextField != null && tooltipTextField.isFocused())
                || isAnyAddonFieldFocused()) {
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
    public boolean charTyped(char c, int modifiers) {
        syncPickerState();
        if (itemPickerOverlay != null) return itemPickerOverlay.charTyped(c);
        return super.charTyped(c, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op — we draw our own background in render() and want to avoid 1.21's menu blur shader
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        syncPickerState();

        guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);

        clampScroll();
        smoothScrollOffset.approach(scrollY, Timing.SCROLL_HALF_LIFE_MS);
        smoothScrollOffset.settle(scrollY, 0.5f);
        renderScroll = Math.round(smoothScrollOffset.value());
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
        guiGraphics.drawString(this.font,
                Component.translatable("editor.historystages.field.description"),
                labelX, 93 - renderScroll, 0xAAAAAA, false);
        guiGraphics.drawString(this.font,
                Component.translatable("editor.historystages.field.scroll_completion"),
                labelX, 115 - renderScroll, 0xAAAAAA, false);

        // Card chrome (before widgets so they sit on top)
        int cardH = computeCardHeight();
        renderCard(guiGraphics, cardX, cardY, cardW, cardH, modeCardKey(editMode));
        renderCard(guiGraphics, displayCardX, displayCardY, displayCardW, displayCardH,
                "editor.historystages.display.card");
        if (isIndividual) {
            renderCard(guiGraphics, indivCardX, indivCardY, indivCardW, indivCardH,
                    "editor.historystages.individual.card");
        }
        for (AddonCard card : addonCards) {
            renderCard(guiGraphics, card.x, card.y, card.w, card.h, card.group.titleLangKey());
        }

        // Content widgets (manually rendered at their scrolled positions)
        for (AbstractWidget w : contentWidgets) {
            w.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        renderDisplayCardContent(guiGraphics, mouseX, mouseY);
        renderIndividualCardContent(guiGraphics, mouseX, mouseY);
        renderAddonCardsContent(guiGraphics, mouseX, mouseY);

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
        scrollCompletionDropdown.renderButton(guiGraphics, this.font, mouseX, mouseY);

        guiGraphics.disableScissor();

        // Scrollbar (matches the StageDetailScreen style)
        if (maxScroll > 0) {
            int scrollAreaHeight = viewBottom - viewTop;
            int barHeight = Math.max(20, (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
            int barY = viewTop + (int) ((float) renderScroll / maxScroll * (scrollAreaHeight - barHeight));
            int barX = this.width - 28;
            boolean barHovered = mouseX >= barX - 2 && mouseX <= barX + 7
                    && mouseY >= barY && mouseY <= barY + barHeight;
            float th = Ease.outCubic(scrollThumbHover.ramp(scrollBarDragging || barHovered,
                    Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            guiGraphics.fill(barX, viewTop, barX + 5, viewBottom, 0x30FFFFFF);
            guiGraphics.fill(barX, barY, barX + 5, barY + barHeight,
                    Fade.mix(0x80FFFFFF, 0xFFFFCC00, th));
        }

        // Fixed Back/Save buttons (the only auto-rendered widgets), outside the viewport
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Confirmation ring around Save. It swells and fades once, which is enough to be seen
        // without competing with the unsaved indicator that just went away.
        if (saveFlashAt >= 0) {
            long age = System.currentTimeMillis() - saveFlashAt;
            if (age >= Timing.FLASH_MS) {
                saveFlashAt = -1L;
            } else {
                float f = Ease.pulse((float) age / Timing.FLASH_MS);
                int bx = this.width - 60;
                int by = this.height - 25;
                guiGraphics.fill(bx - 2, by - 2, bx + 52, by, Fade.rgba(0xFFCC00, f));
                guiGraphics.fill(bx - 2, by + 18, bx + 52, by + 20, Fade.rgba(0xFFCC00, f));
                guiGraphics.fill(bx - 2, by, bx, by + 18, Fade.rgba(0xFFCC00, f));
                guiGraphics.fill(bx + 50, by, bx + 52, by + 18, Fade.rgba(0xFFCC00, f));
            }
        }

        // Unsaved changes animation (left of Save button)
        if (hasChanges) {
            float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS)
                    / Timing.BREATHE_PERIOD_MS;
            int dotAlpha = (int) ((0.35f + 0.45f * Ease.breathe(phase)) * 255);
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
        renderModeDropdownPopup(guiGraphics, mouseX, mouseY);
        scrollCompletionDropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
        nameModeDropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
        tooltipModeDropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
        for (AddonCard card : addonCards) {
            for (AddonFieldRow row : card.rows) {
                if (row.dropdown != null) row.dropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
            }
        }

        // Item picker overlay for an addon ITEM field. Lifted above everything drawn so far —
        // same treatment ConfigEditorScreen gives its own picker: text is batched and flushed
        // after the picker's panel fills, so the cards and button labels underneath would
        // otherwise bleed through it.
        if (itemPickerOverlay != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 200);
            guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
            itemPickerOverlay.render(guiGraphics, this.font, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
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
        float tp = Ease.outCubic(lockHintsHover.ramp(tHov, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        g.fill(lockHintsToggleX, lockHintsRowY, lockHintsToggleX + lockHintsToggleW, lockHintsRowY + DISP_TOGGLE_H,
                Fade.mix(0xFF2A2A2A, 0xFF3D3520, tp));
        // Gold underline grows in with the hover, matching StyledButton's accent.
        if (tp > 0.001f) {
            int w = Math.round(lockHintsToggleW * tp);
            g.fill(lockHintsToggleX, lockHintsRowY + DISP_TOGGLE_H - 1, lockHintsToggleX + w,
                    lockHintsRowY + DISP_TOGGLE_H, Fade.rgba(0xFFCC00, tp));
        }
        g.drawString(this.font, lockHintsValue(), lockHintsToggleX + 4, lockHintsRowY + 3,
                Fade.mix(0xFFCCCCCC, 0xFFFFCC00, tp), false);
    }

    private void renderIndividualCardContent(GuiGraphics g, int mouseX, int mouseY) {
        if (!isIndividual || indivCardH <= 0) return;
        int labelX = indivCardX + 12;

        g.drawString(this.font,
                Component.translatable("editor.historystages.individual.lose_on_death").getString(),
                labelX, loseRowY + 3, 0xAAAAAA, false);

        boolean hov = mouseX >= loseToggleX && mouseX < loseToggleX + loseToggleW
                && mouseY >= loseRowY && mouseY < loseRowY + INDIV_TOGGLE_H;
        float hp = Ease.outCubic(loseHover.ramp(hov, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        g.fill(loseToggleX, loseRowY, loseToggleX + loseToggleW, loseRowY + INDIV_TOGGLE_H,
                Fade.mix(0xFF2A2A2A, 0xFF3D3520, hp));
        if (hp > 0.001f) {
            int w = Math.round(loseToggleW * hp);
            g.fill(loseToggleX, loseRowY + INDIV_TOGGLE_H - 1, loseToggleX + w,
                    loseRowY + INDIV_TOGGLE_H, Fade.rgba(0xFFCC00, hp));
        }
        g.drawString(this.font, loseOnDeathValue(), loseToggleX + 4, loseRowY + 3,
                Fade.mix(0xFFCCCCCC, 0xFFFFCC00, hp), false);

        drawSmallText(g,
                Component.translatable("editor.historystages.individual.lose_on_death.hint").getString(),
                labelX, loseRowY + INDIV_TOGGLE_H + INDIV_HINT_GAP, 0x888888);
    }

    /**
     * Renders every addon card's row content: the field label, plus a BOOL toggle or CHOICE
     * dropdown button. INTEGER/TEXT rows are plain {@link EditBox}es and already render themselves
     * as part of {@link #contentWidgets}.
     */
    private void renderAddonCardsContent(GuiGraphics g, int mouseX, int mouseY) {
        for (AddonCard card : addonCards) {
            if (card.h <= 0) continue;
            int labelX = card.x + 12;
            for (AddonFieldRow row : card.rows) {
                boolean isToggle = row.field.kind() == SettingKind.BOOL;
                int labelY = isToggle ? row.rowY + 3 : row.rowY + 5;
                g.drawString(this.font, Component.translatable(row.field.langKey()).getString(),
                        labelX, labelY, 0xAAAAAA, false);

                if (isToggle) {
                    renderAddonToggle(g, card, row, mouseX, mouseY);
                } else if (row.field.kind() == SettingKind.CHOICE) {
                    row.dropdown.renderButton(g, this.font, mouseX, mouseY);
                } else if (row.field.kind() == SettingKind.ITEM) {
                    renderAddonItemIcon(g, card, row);
                }
            }
        }
    }

    /**
     * Draws the resolved item's icon over the left edge of an ITEM row's button — the button
     * itself already drew its (centred) label. Same pairing {@link
     * net.bananemdnsa.historystages.client.editor.widget.list.ConfigRowList}'s ITEM row draws:
     * icon, then id/name text. Nothing is drawn when the id does not resolve; the raw id the
     * button already shows is the honest fallback then.
     */
    @SuppressWarnings("unchecked")
    private void renderAddonItemIcon(GuiGraphics g, AddonCard card, AddonFieldRow row) {
        if (row.button == null) return;
        Setting<String> field = (Setting<String>) row.field;
        String id = card.values.get(field);
        if (id == null || id.isEmpty()) return;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == Items.AIR) return;
        g.renderItem(new ItemStack(item), row.button.getX() + 3, row.rowY + 1);
    }

    /** Draws one BOOL row's toggle button, matching the lock-hints/lose-on-death toggle chrome. */
    private void renderAddonToggle(GuiGraphics g, AddonCard card, AddonFieldRow row, int mouseX, int mouseY) {
        String value = boolValueLabel(boolValue(card.values, row.field));
        boolean hov = mouseX >= row.toggleX && mouseX < row.toggleX + row.toggleW
                && mouseY >= row.rowY && mouseY < row.rowY + ADDON_TOGGLE_H;
        float hp = Ease.outCubic(row.toggleHover.ramp(hov, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        g.fill(row.toggleX, row.rowY, row.toggleX + row.toggleW, row.rowY + ADDON_TOGGLE_H,
                Fade.mix(0xFF2A2A2A, 0xFF3D3520, hp));
        if (hp > 0.001f) {
            int w = Math.round(row.toggleW * hp);
            g.fill(row.toggleX, row.rowY + ADDON_TOGGLE_H - 1, row.toggleX + w,
                    row.rowY + ADDON_TOGGLE_H, Fade.rgba(0xFFCC00, hp));
        }
        g.drawString(this.font, value, row.toggleX + 4, row.rowY + 3,
                Fade.mix(0xFFCCCCCC, 0xFFFFCC00, hp), false);
    }

    /** Returns true if an addon card's dropdown or toggle consumed the click. */
    private boolean handleAddonCardsClick(double mouseX, double mouseY) {
        for (AddonCard card : addonCards) {
            for (AddonFieldRow row : card.rows) {
                if (row.dropdown != null && row.dropdown.mouseClicked(mouseX, mouseY)) return true;
            }
        }
        for (AddonCard card : addonCards) {
            if (card.h <= 0) continue;
            for (AddonFieldRow row : card.rows) {
                if (row.field.kind() != SettingKind.BOOL) continue;
                if (mouseX >= row.toggleX && mouseX < row.toggleX + row.toggleW
                        && mouseY >= row.rowY && mouseY < row.rowY + ADDON_TOGGLE_H) {
                    setBoolValue(card.values, row.field, !boolValue(card.values, row.field));
                    hasChanges = true;
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns true if an Individual-card control consumed the click. */
    private boolean handleIndividualCardClick(double mouseX, double mouseY) {
        if (!isIndividual || indivCardH <= 0) return false;

        if (mouseX >= loseToggleX && mouseX < loseToggleX + loseToggleW
                && mouseY >= loseRowY && mouseY < loseRowY + INDIV_TOGGLE_H) {
            editLoseOnDeath = !editLoseOnDeath;
            onDisplayChanged();
            return true;
        }
        return false;
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
        float hp = Ease.outCubic(modeHover.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        g.fill(modeDropdownX, modeDropdownY, modeDropdownX + modeDropdownW, modeDropdownY + FIELD_HEIGHT,
                Fade.mix(0x25FFFFFF, 0x40FFFFFF, hp));
        g.fill(modeDropdownX, modeDropdownY + FIELD_HEIGHT - 1,
                modeDropdownX + modeDropdownW, modeDropdownY + FIELD_HEIGHT,
                Fade.mix(0x60FFCC00, 0xFFFFCC00, hp));

        String label = Component.translatable(modeLabelKey(editMode)).getString();
        g.drawString(this.font, label, modeDropdownX + 6, modeDropdownY + 5, 0xFFFFFF, false);

        // Turned by the same progress that reveals the popup, replacing the two static
        // glyphs that used to swap in a single frame.
        DropdownChrome.drawCaret(g, modeDropdownX + modeDropdownW - 10, modeDropdownY + 8,
                Fade.mix(0xFF999999, 0xFFDDDDDD, hp), modeOpen.value());
    }

    private void renderModeDropdownPopup(GuiGraphics g, int mouseX, int mouseY) {
        // Called every frame regardless of state so the popup can roll back up on close.
        float t = modeOpen.ramp(modeDropdownOpen ? 1.0f : 0.0f, Timing.POPUP_MS);
        if (t < 0.02f) return;

        StageMode[] modes = StageMode.values();
        int rowH = 22;
        int popupH = modes.length * rowH;
        int px = modeDropdownX;
        int py = modeDropdownY + FIELD_HEIGHT + 1;
        int pw = modeDropdownW;

        if (!DropdownChrome.begin(g, px, py, pw, popupH, t, false)) return;

        for (int i = 0; i < modes.length; i++) {
            StageMode m = modes[i];
            int rowY = py + i * rowH;
            boolean hov = modeDropdownOpen && mouseX >= px && mouseX <= px + pw
                    && mouseY >= rowY && mouseY < rowY + rowH;
            float rh = Ease.outCubic(modeRowHover.computeIfAbsent(i, k -> new Anim())
                    .ramp(hov, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            if (rh > 0.001f) g.fill(px, rowY, px + pw, rowY + rowH, Fade.rgba(0xFFCC00, 0.19f * rh));
            if (m == editMode) g.fill(px, rowY, px + 2, rowY + rowH, 0xFFFFCC00);

            String name = Component.translatable(modeLabelKey(m)).getString();
            String desc = Component.translatable(modeDescKey(m)).getString();
            int nameColor = m == editMode
                    ? Fade.mix(0xFFFFCC00, 0xFFFFFFFF, rh)
                    : Fade.mix(0xFFDDDDDD, 0xFFFFFFFF, rh);
            int textX = px + 8 + Math.round(rh * 2.0f);
            g.drawString(this.font, name, textX, rowY + 3, nameColor, false);
            g.drawString(this.font, desc, textX, rowY + 13, 0x888888, false);
        }

        DropdownChrome.end(g);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Item picker overlay: it is drawn on top of everything, so it must get the click before
        // anything underneath it does.
        syncPickerState();
        if (itemPickerOverlay != null) {
            boolean consumed = itemPickerOverlay.mouseClicked(mouseX, mouseY);
            syncPickerState();
            return consumed || closePicker();
        }

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
        if (scrollCompletionDropdown.mouseClicked(mouseX, mouseY)) return true;
        if (nameModeDropdown.mouseClicked(mouseX, mouseY)) return true;
        if (tooltipModeDropdown.mouseClicked(mouseX, mouseY)) return true;
        if (button == 0 && handleDisplayCardClick(mouseX, mouseY)) return true;
        if (button == 0 && handleIndividualCardClick(mouseX, mouseY)) return true;
        if (button == 0 && handleAddonCardsClick(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawSmallText(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
        g.drawString(this.font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Whether an INTEGER/TEXT field in any addon card currently has keyboard focus. */
    private boolean isAnyAddonFieldFocused() {
        for (AddonCard card : addonCards) {
            for (AddonFieldRow row : card.rows) {
                if (row.editBox != null && row.editBox.isFocused()) return true;
            }
        }
        return false;
    }

    /**
     * One rendered card for an addon-declared settings group. Geometry is recomputed every frame
     * in {@link #layoutAddonCards()}; {@link #values} is the same {@link SettingsValues} instance
     * held in {@link #editAddonSettings}, so a row's edit writes straight into the save seam.
     */
    private static final class AddonCard {
        final StageSettingsGroup group;
        final SettingsValues values;
        final List<AddonFieldRow> rows = new ArrayList<>();
        int x, y, w, h;

        AddonCard(StageSettingsGroup group, SettingsValues values) {
            this.group = group;
            this.values = values;
        }
    }

    /** One row inside an {@link AddonCard}, one per non-ITEM field the group declares. */
    private static final class AddonFieldRow {
        final Setting<?> field;
        int rowY;
        // BOOL only: hand-drawn toggle geometry + hover animation.
        int toggleX, toggleW;
        final Anim toggleHover = new Anim();
        // INTEGER/TEXT only.
        EditBox editBox;
        // CHOICE only.
        EnumDropdown dropdown;
        // LONG_TEXT only.
        StyledButton button;

        AddonFieldRow(Setting<?> field) {
            this.field = field;
        }
    }
}
