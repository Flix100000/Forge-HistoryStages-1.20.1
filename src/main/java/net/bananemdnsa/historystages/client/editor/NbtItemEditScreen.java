package net.bananemdnsa.historystages.client.editor;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.nbt.ComponentCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.ComponentShapes;
import net.bananemdnsa.historystages.client.editor.nbt.CriterionCard;
import net.bananemdnsa.historystages.client.editor.nbt.CustomDataCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.EnchantmentListCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.NbtCriteriaCodec;
import net.bananemdnsa.historystages.client.editor.nbt.NbtCriteriaValidator;
import net.bananemdnsa.historystages.client.editor.nbt.NbtCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.NbtPresets;
import net.bananemdnsa.historystages.client.editor.nbt.TextListCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.ValueKind;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.Scrollbar;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.toast.EditorToast;
import net.bananemdnsa.historystages.client.editor.toast.EditorToastHandler;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableEnchantmentList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemPropertyList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableNbtCriterionList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Editor for the NBT criteria on one item or tag entry.
 *
 * <p>Shows only the criteria that are actually set, one card each, and adds new ones through a
 * searchable picker. What the picker offers follows {@code NbtMatcher}: data components go into
 * {@code components}, the two enchantment lists stay top-level because the matcher synthesises
 * them, and a free top-level key means "look this up in the item's custom_data".
 */
public class NbtItemEditScreen extends Screen {
    private final Screen parent;
    private final String itemId;
    private final boolean tagMode;
    private final JsonObject currentNbt;
    private final Consumer<JsonObject> onSave;

    /** The criteria being edited. Survives {@link #init()} — see {@link #criteriaLoaded}. */
    private final List<NbtCriterion> criteria = new ArrayList<>();
    /** One laid-out card per criterion, in the same order. Rebuilt by {@link #refresh()}. */
    private final List<CriterionCard.Built> cards = new ArrayList<>();
    private List<NbtCriteriaValidator.Warning> warnings = List.of();
    private CriterionCard card;

    /**
     * Loading happens once, not on every {@link #init()}. The screen is re-initialised whenever a
     * value dialog closes, and re-reading {@link #currentNbt} there would throw away every edit —
     * including a deliberate "clear all".
     */
    private boolean criteriaLoaded = false;

    private PickerOverlay pickerOverlay;
    /** What the criteria looked like when last saved, so "unsaved" means something. */
    private JsonObject savedSnapshot = new JsonObject();
    private boolean dirty = false;
    private boolean showJson = false;
    private List<FormattedCharSequence> jsonLines = List.of();
    private int jsonScroll = 0;

    private double scrollOffset = 0;
    /** Sub-pixel scroll chasing {@link #scrollOffset}; render and the click paths both read it. */
    private final Anim smoothScroll = new Anim();
    private int maxScroll = 0;
    private final Scrollbar scrollbar = new Scrollbar();

    // Layout
    private static final int PADDING = 20;
    /**
     * Item row, then the toolbar row, then the divider. Sized so nothing touches: the id ends at
     * 36 and the buttons start at {@link #TOOLBAR_Y}.
     */
    private static final int HEADER_HEIGHT = 74;
    private static final int TOOLBAR_Y = 42;
    private static final int TOOLBAR_H = 18;
    private static final int CARD_GAP = 6;
    private static final int FOOTER_HEIGHT = 40;

    // Cached suggestion lists
    private static List<String> potionIds = null;

    public NbtItemEditScreen(Screen parent, String itemId, JsonObject currentNbt, Consumer<JsonObject> onSave) {
        this(parent, itemId, false, currentNbt, onSave);
    }

    public NbtItemEditScreen(Screen parent, String itemId, boolean tagMode, JsonObject currentNbt, Consumer<JsonObject> onSave) {
        super(Component.translatable("editor.historystages.nbt.title"));
        this.parent = parent;
        this.itemId = itemId;
        this.tagMode = tagMode;
        this.currentNbt = currentNbt;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        if (!criteriaLoaded) {
            criteria.addAll(NbtCriteriaCodec.load(currentNbt));
            // The baseline is what the criteria write out to, not the file as it was: loading and
            // writing back can differ harmlessly (a number that had been stored as a string), and
            // flagging that as an unsaved change would cry wolf on every open.
            savedSnapshot = NbtCriteriaCodec.write(criteria);
            criteriaLoaded = true;
        }
        this.card = new CriterionCard(this.font);

        Component addLabel = Component.translatable("editor.historystages.nbt.add_criterion");
        int addW = this.font.width(addLabel) + 16;
        this.addRenderableWidget(StyledButton.of(addLabel, btn -> openCriterionPicker(),
                PADDING, TOOLBAR_Y, addW, TOOLBAR_H));

        Component fromItemLabel = Component.translatable("editor.historystages.nbt.from_item");
        int fromItemW = this.font.width(fromItemLabel) + 16;
        this.addRenderableWidget(StyledButton.of(fromItemLabel, btn -> openItemCriteriaPicker(),
                PADDING + addW + 6, TOOLBAR_Y, fromItemW, TOOLBAR_H));

        Component jsonLabel = Component.translatable(showJson
                ? "editor.historystages.nbt.hide_json"
                : "editor.historystages.nbt.show_json");
        this.addRenderableWidget(StyledButton.of(jsonLabel, btn -> {
            showJson = !showJson;
            rebuild();
        }, PADDING + addW + fromItemW + 12, TOOLBAR_Y, this.font.width(jsonLabel) + 16, TOOLBAR_H));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> this.minecraft.setScreen(parent),
                PADDING, this.height - 30, 60, 20));

        Component clearLabel = Component.translatable("editor.historystages.nbt.clear_all");
        this.addRenderableWidget(StyledButton.of(clearLabel, btn -> confirmClearAll(),
                PADDING + 66, this.height - 30, this.font.width(clearLabel) + 16, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.nbt.save"),
                btn -> saveNbt(),
                this.width - PADDING - 100, this.height - 30, 100, 20));

        refresh();
    }

    /** Re-runs {@link #init()} so the JSON button's label and width follow its new state. */
    private void rebuild() {
        this.rebuildWidgets();
    }

    // ==========================================
    // Derived state
    // ==========================================

    /**
     * Recomputes everything that hangs off {@link #criteria}. Called after every change rather
     * than piecemeal, because warnings, card heights, the JSON preview and the scroll extent all
     * depend on each other — updating them separately is how they drift apart.
     */
    private void refresh() {
        revalidate();
        relayout();
        JsonObject written = NbtCriteriaCodec.write(criteria);
        dirty = !written.equals(savedSnapshot);
        refreshJson(written);
        updateMaxScroll();
    }

    private void revalidate() {
        if (this.minecraft == null || this.minecraft.level == null) {
            // No world means no registries to check against; a warning here would be a guess.
            warnings = List.of();
            return;
        }
        warnings = new NbtCriteriaValidator(this::maxEnchantmentLevel, this::potionExists)
                .validate(criteria);
    }

    private void relayout() {
        cards.clear();
        if (card == null) return;
        int width = contentWidth();
        for (int i = 0; i < criteria.size(); i++) {
            cards.add(card.layout(criteria.get(i), width, warningsFor(i)));
        }
    }

    private List<NbtCriteriaValidator.Warning> warningsFor(int criterionIndex) {
        return warnings.stream()
                .filter(w -> w.criterionIndex() == criterionIndex)
                .collect(Collectors.toList());
    }

    private void refreshJson(JsonObject nbt) {
        if (!showJson) {
            jsonLines = List.of();
            jsonScroll = 0;
            return;
        }
        String text = nbt.isEmpty()
                ? Component.translatable("editor.historystages.nbt.json.empty").getString()
                : new GsonBuilder().setPrettyPrinting().create().toJson(nbt);
        jsonLines = this.font.split(Component.literal(text), jsonPanelWidth() - 16);
        jsonScroll = 0;
    }

    private int jsonPanelWidth() {
        return Math.min(380, this.width - PADDING * 2);
    }

    private int jsonPanelHeight() {
        return Math.min(this.height - 80, jsonLines.size() * 10 + 22);
    }

    private Integer maxEnchantmentLevel(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        Registry<Enchantment> registry = this.minecraft.level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.get(rl);
        return enchantment == null ? null : enchantment.getMaxLevel();
    }

    private boolean potionExists(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && BuiltInRegistries.POTION.get(rl) != null;
    }

    /** The bar keeps its column even while nothing is drawn there, so text never reflows. */
    private int scrollbarX() {
        return this.width - PADDING - Scrollbar.WIDTH;
    }

    private int contentWidth() {
        return this.width - PADDING * 2 - Scrollbar.WIDTH - 4;
    }

    private int listTop() {
        return HEADER_HEIGHT;
    }

    private int listBottom() {
        return this.height - FOOTER_HEIGHT;
    }

    private void updateMaxScroll() {
        int total = 0;
        for (CriterionCard.Built built : cards) {
            total += built.height() + CARD_GAP;
        }
        maxScroll = Math.max(0, total - (listBottom() - listTop()));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    // ==========================================
    // Rendering
    // ==========================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op — we draw our own background in render() and want to avoid 1.21's menu blur shader
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);

        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

        renderHeader(g);

        int listTop = listTop();
        int listBottom = listBottom();
        g.enableScissor(0, listTop, this.width, listBottom);

        if (criteria.isEmpty()) {
            renderEmptyState(g, listTop, listBottom);
        } else {
            int y = listTop - Math.round(smoothScroll.value());
            for (int i = 0; i < cards.size(); i++) {
                CriterionCard.Built built = cards.get(i);
                if (y + built.height() > listTop && y < listBottom) {
                    card.render(g, built, PADDING, y, mouseX, mouseY);
                }
                y += built.height() + CARD_GAP;
            }
        }

        g.disableScissor();

        scrollbar.render(g, scrollbarX(), listTop, listBottom,
                smoothScroll.value(), maxScroll, mouseX, mouseY);

        if (dirty) renderUnsavedMarker(g);

        super.render(g, mouseX, mouseY, partialTick);

        // Lifted onto its own Z layer and dimmed underneath: text is batched and flushed after the
        // picker's panel fills, so the cards and button labels below would otherwise bleed through
        // it. Same treatment every other picker in the editor gets.
        // A picker can hide itself without telling anyone — Escape goes straight to its own key
        // handler. Dropping the stale reference here rather than only in the input paths keeps the
        // dim from outliving the panel it belongs to.
        syncPickerState();
        if (pickerOverlay != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(0, 0, this.width, this.height, 0x80000000);
            pickerOverlay.render(g, this.font, mouseX, mouseY);
            g.pose().popPose();
        } else if (showJson) {
            renderJsonOverlay(g);
        }
    }

    private void renderHeader(GuiGraphics g) {
        if (tagMode) {
            g.drawString(this.font, "#" + itemId, PADDING, 16, 0xFFFFFF);
        } else {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item != null) {
                g.renderItem(new ItemStack(item), PADDING, 12);
                g.drawString(this.font, item.getDescription(), PADDING + 22, 14, 0xFFFFFF);
            }
            g.drawString(this.font, itemId, PADDING + 22, 28, 0x888888);
        }

        String count = criteria.isEmpty()
                ? Component.translatable("editor.historystages.nbt.count.none").getString()
                : criteria.size() == 1
                        ? Component.translatable("editor.historystages.nbt.count.one").getString()
                        : Component.translatable("editor.historystages.nbt.count", criteria.size()).getString();
        g.drawString(this.font, count, this.width - PADDING - this.font.width(count),
                TOOLBAR_Y + (TOOLBAR_H - 8) / 2, 0x888888);

        g.fill(PADDING, HEADER_HEIGHT - 6, this.width - PADDING, HEADER_HEIGHT - 5, 0x40FFCC00);
    }

    /**
     * The JSON view as a panel over the screen rather than a strip wedged under the header. Wedging
     * it in pushed the cards down and shrank the list every time it opened; a panel leaves the
     * screen underneath exactly where it was.
     */
    private void renderJsonOverlay(GuiGraphics g) {
        int panelW = jsonPanelWidth();
        int panelH = jsonPanelHeight();
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(0, 0, this.width, this.height, 0x80000000);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF2F2F2F);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, 0xFF0D0D0D);

        String title = Component.translatable("editor.historystages.nbt.show_json").getString();
        g.drawString(this.font, title, panelX + 8, panelY + 6, 0xFFCC00);
        g.fill(panelX + 8, panelY + 17, panelX + panelW - 8, panelY + 18, 0x40FFCC00);

        int textTop = panelY + 22;
        int textBottom = panelY + panelH - 4;
        g.enableScissor(panelX + 1, textTop, panelX + panelW - 1, textBottom);
        int y = textTop - jsonScroll;
        for (FormattedCharSequence line : jsonLines) {
            g.drawString(this.font, line, panelX + 8, y, 0xCCCCCC);
            y += 10;
        }
        g.disableScissor();

        g.pose().popPose();
    }

    private int maxJsonScroll() {
        int viewport = jsonPanelHeight() - 26;
        return Math.max(0, jsonLines.size() * 10 - viewport);
    }

    private void closeJson() {
        showJson = false;
        rebuild();
    }

    private void renderEmptyState(GuiGraphics g, int listTop, int listBottom) {
        String subject = tagMode
                ? "#" + itemId
                : itemDisplayName();
        String title = Component.translatable("editor.historystages.nbt.empty.title", subject).getString();
        String hint = Component.translatable("editor.historystages.nbt.empty.hint").getString();

        int centerY = (listTop + listBottom) / 2;
        g.drawString(this.font, title, (this.width - this.font.width(title)) / 2, centerY - 10, 0x7A7A7A);
        g.drawString(this.font, hint, (this.width - this.font.width(hint)) / 2, centerY + 4, 0x5F5F5F);
    }

    /**
     * Breathing gold dot and label left of the save button — the marker the rest of the editor
     * uses, on the same {@link Timing#BREATHE_PERIOD_MS} cycle so the two never pulse out of step.
     */
    private void renderUnsavedMarker(GuiGraphics g) {
        float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS)
                / Timing.BREATHE_PERIOD_MS;
        int alpha = (int) ((0.35f + 0.45f * Ease.breathe(phase)) * 255);

        String label = Component.translatable("editor.historystages.unsaved").getString();
        int labelX = this.width - PADDING - 100 - 8 - this.font.width(label);
        int y = this.height - 24;

        g.fill(labelX - 10, y + 1, labelX - 4, y + 7, (alpha << 24) | 0xFFCC00);
        g.drawString(this.font, label, labelX, y, 0xFFCC00);
    }

    private String itemDisplayName() {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        return item != null ? item.getDescription().getString() : itemId;
    }

    // ==========================================
    // Input
    // ==========================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncPickerState();
        if (pickerOverlay != null) {
            // The picker owns the pointer while it is up: a click outside its panel dismisses it
            // rather than falling through to a card underneath.
            pickerOverlay.mouseClicked(mouseX, mouseY);
            syncPickerState();
            return true;
        }

        // Same rule for the JSON panel: it is a look at the result, so any click dismisses it
        // rather than reaching a card behind it.
        if (showJson) {
            closeJson();
            return true;
        }

        int listTop = listTop();
        int listBottom = listBottom();

        if (scrollbar.mouseClicked(mouseX, mouseY)) {
            scrollOffset = scrollbar.scrollFor(mouseY);
            return true;
        }

        if (mouseY >= listTop && mouseY < listBottom) {
            int y = listTop - Math.round(smoothScroll.value());
            for (int i = 0; i < cards.size(); i++) {
                CriterionCard.Built built = cards.get(i);
                CriterionCard.Hit hit = card.hitTest(built, PADDING, y, mouseX, mouseY);
                if (hit != null) {
                    playClick();
                    handleHit(i, hit);
                    return true;
                }
                y += built.height() + CARD_GAP;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleHit(int index, CriterionCard.Hit hit) {
        NbtCriterion criterion = criteria.get(index);

        if (hit instanceof CriterionCard.Hit.Remove) {
            criteria.remove(index);
            refresh();
        } else if (hit instanceof CriterionCard.Hit.ConvertLegacy) {
            convertLegacy(index, (CustomDataCriterion) criterion);
        } else if (hit instanceof CriterionCard.Hit.EditKey) {
            CustomDataCriterion custom = (CustomDataCriterion) criterion;
            openInput(Component.translatable("editor.historystages.nbt.custom.key_label").getString(),
                    custom.key, List.of(), value -> {
                        custom.key = value;
                        custom.legacySuspect = NbtPresets.isLegacyKey(value);
                    });
        } else if (hit instanceof CriterionCard.Hit.EditValue) {
            editValue(criterion);
        } else if (hit instanceof CriterionCard.Hit.EditLine(int line)) {
            editLine(criterion, line);
        } else if (hit instanceof CriterionCard.Hit.EditLevel(int line)) {
            EnchantmentListCriterion ench = (EnchantmentListCriterion) criterion;
            openInput(Component.translatable("editor.historystages.nbt.input.level_range").getString(),
                    ench.lines.get(line).level, List.of(), value -> ench.lines.get(line).level = value);
        } else if (hit instanceof CriterionCard.Hit.RemoveLine(int line)) {
            removeLine(criterion, line);
            refresh();
        } else if (hit instanceof CriterionCard.Hit.AddLine) {
            addLine(criterion);
            refresh();
        } else if (hit instanceof CriterionCard.Hit.FillFromItem) {
            openItemValuePicker((ComponentCriterion) criterion);
        }
    }

    private void editValue(NbtCriterion criterion) {
        if (criterion instanceof ComponentCriterion comp) {
            // A raw-JSON field for a value that is simply a name or a number is what made this
            // screen unusable; only the shapes that really are free-form get the JSON dialog.
            if (comp.valueKind == ValueKind.JSON) {
                this.minecraft.setScreen(new ComponentValueEditScreen(this, comp.componentId(),
                        comp.valueJson.isEmpty() ? "{}" : comp.valueJson,
                        json -> comp.valueJson = json));
            } else if (comp.valueKind != ValueKind.PRESENCE) {
                String title = Component.translatable(comp.valueKind == ValueKind.NUMBER
                        ? "editor.historystages.nbt.hint.number"
                        : "editor.historystages.nbt.input.value").getString();
                openInput(title, comp.displayValue(), List.of(), comp::setFromDisplay);
            }
            return;
        }
        CustomDataCriterion custom = (CustomDataCriterion) criterion;
        List<String> suggestions = "Potion".equals(custom.key) ? getPotionSuggestions() : List.of();
        openInput(Component.translatable("editor.historystages.nbt.custom.value_label").getString(),
                custom.valueText, suggestions, value -> custom.valueText = value);
    }

    private void editLine(NbtCriterion criterion, int line) {
        if (criterion instanceof EnchantmentListCriterion ench) {
            openEnchantmentPicker(ench, line);
            return;
        }
        TextListCriterion list = (TextListCriterion) criterion;
        openInput(Component.translatable("editor.historystages.nbt.input.value").getString(),
                list.lines.get(line), List.of(), value -> list.lines.set(line, value));
    }

    private void removeLine(NbtCriterion criterion, int line) {
        if (criterion instanceof EnchantmentListCriterion ench) {
            ench.lines.remove(line);
        } else if (criterion instanceof TextListCriterion list) {
            list.lines.remove(line);
        }
    }

    private void addLine(NbtCriterion criterion) {
        if (criterion instanceof EnchantmentListCriterion ench) {
            ench.lines.add(new EnchantmentListCriterion.Line("", ""));
        } else if (criterion instanceof TextListCriterion list) {
            list.lines.add("");
        }
    }

    /**
     * Swaps a legacy top-level key for the component it was almost certainly meant to be. Only
     * ever on request — the same key may be a pack's own custom_data entry, and rewriting that
     * silently would break it.
     */
    private void convertLegacy(int index, CustomDataCriterion custom) {
        String componentId = NbtPresets.componentForLegacyKey(custom.key);
        if (componentId == null) return;

        NbtPresets.Preset preset = NbtPresets.byComponentId(componentId);
        String presetName = preset == null ? null : preset.nameKey();
        criteria.set(index, preset != null && preset.valueKind() == ValueKind.TEXT_LIST
                ? new TextListCriterion(componentId, presetName)
                : new ComponentCriterion(componentId, preset == null ? "" : preset.defaultValue(), presetName));
        refresh();
    }

    private void openInput(String title, String current, List<String> suggestions, Consumer<String> onDone) {
        this.minecraft.setScreen(new SuggestingInputScreen(this, title, current, suggestions, value -> {
            onDone.accept(value);
            refresh();
        }));
    }

    private void playClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    // ==========================================
    // Adding criteria
    // ==========================================

    private void openCriterionPicker() {
        SearchableNbtCriterionList picker = new SearchableNbtCriterionList(
                selection -> {
                    closePicker();
                    addFromSelection(selection);
                },
                () -> criteria.stream().map(NbtCriterion::identity).collect(Collectors.toList()));
        pickerOverlay = picker;
        picker.show(this.width / 2, this.height / 2, this.width);
    }

    private void addFromSelection(String selection) {
        if (selection.startsWith(SearchableNbtCriterionList.PRESET)) {
            String id = selection.substring(SearchableNbtCriterionList.PRESET.length());
            NbtPresets.Preset preset = NbtPresets.byComponentId(id);
            if (preset == null) return;
            if (preset.valueKind() == ValueKind.TEXT_LIST) {
                TextListCriterion list = new TextListCriterion(id, preset.nameKey());
                list.lines.add("");
                criteria.add(list);
            } else {
                criteria.add(new ComponentCriterion(id, preset.defaultValue(), preset.nameKey()));
            }

        } else if (selection.startsWith(SearchableNbtCriterionList.COMPONENT)) {
            String id = selection.substring(SearchableNbtCriterionList.COMPONENT.length());
            NbtPresets.Preset preset = NbtPresets.byComponentId(id);
            criteria.add(new ComponentCriterion(id, preset == null ? "" : preset.defaultValue(),
                    preset == null ? null : preset.nameKey()));

        } else if (selection.startsWith(SearchableNbtCriterionList.ENCHANTMENTS)) {
            String key = selection.substring(SearchableNbtCriterionList.ENCHANTMENTS.length());
            EnchantmentListCriterion ench = new EnchantmentListCriterion(key);
            ench.lines.add(new EnchantmentListCriterion.Line("", ""));
            criteria.add(ench);

        } else if (selection.startsWith(SearchableNbtCriterionList.CUSTOM_DATA)) {
            this.minecraft.setScreen(new CustomNbtInputScreen(this, (key, value) -> {
                criteria.add(new CustomDataCriterion(key, value, NbtPresets.isLegacyKey(key)));
                refresh();
            }));
            return;
        }

        refresh();
    }

    /**
     * The other way round: pick an item, then tick what it actually has.
     *
     * <p>Adding a criterion by hand means naming a component and writing its value in the encoded
     * form the matcher compares against — for a modded item there is no way to know either. Reading
     * both off the item removes the question instead of explaining it.
     */
    private void openItemCriteriaPicker() {
        SearchableItemList picker = new SearchableItemList(id -> {
            closePicker();
            EditorToastHandler.show(EditorToast.Level.INFO,
                    Component.translatable("editor.historystages.nbt.from_item"),
                    Component.translatable("editor.historystages.nbt.fill_from_item.needs_stack"));
        });
        picker.setValueMode();
        picker.setOnSelectWithNbt((id, nbt) -> {
            closePicker();
            offerItemCriteria(id, nbt);
        });
        pickerOverlay = picker;
        picker.show(this.width / 2, this.height / 2, this.width);
    }

    private void offerItemCriteria(String itemName, JsonObject nbt) {
        List<NbtCriterion> candidates = NbtCriteriaCodec.load(nbt);
        if (candidates.isEmpty()) {
            EditorToastHandler.show(EditorToast.Level.INFO,
                    Component.translatable("editor.historystages.nbt.from_item"),
                    Component.translatable("editor.historystages.nbt.from_item.nothing", itemName));
            return;
        }

        // Keyed by identity because that is what the multi-select callback hands back, one call
        // per ticked row.
        java.util.Map<String, NbtCriterion> byIdentity = new java.util.LinkedHashMap<>();
        for (NbtCriterion candidate : candidates) {
            byIdentity.putIfAbsent(candidate.identity(), candidate);
        }

        SearchableItemPropertyList list = new SearchableItemPropertyList(
                List.copyOf(byIdentity.values()),
                identity -> {
                    NbtCriterion picked = byIdentity.get(identity);
                    if (picked != null && criteria.stream().noneMatch(c -> c.identity().equals(identity))) {
                        criteria.add(picked);
                    }
                    refresh();
                },
                () -> criteria.stream().map(NbtCriterion::identity).collect(Collectors.toList()));

        pickerOverlay = list;
        list.show(this.width / 2, this.height / 2, this.width);
    }

    /**
     * Reads one component's value off an item the author picks, and drops the encoded JSON into the
     * field.
     *
     * <p>This is the answer to "what am I supposed to write in there" for every component the
     * preset table does not cover, mod-defined ones included: the value comes out of the same
     * encoder {@code NbtMatcher} runs, so whatever lands in the field is by definition a value that
     * matches the item it was taken from.
     */
    private void openItemValuePicker(ComponentCriterion comp) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(ResourceLocation.parse(comp.componentId()));

        SearchableItemList picker = new SearchableItemList(id -> {
            // Reached only from the registry tab, where there is no stack behind the entry and so
            // nothing to read a value off.
            closePicker();
            EditorToastHandler.show(EditorToast.Level.INFO,
                    Component.translatable("editor.historystages.nbt.fill_from_item"),
                    Component.translatable("editor.historystages.nbt.fill_from_item.needs_stack"));
        });
        picker.setValueMode();
        // Only offer stacks that carry the component at all — "which item has this?" is half the
        // question, and the inventory can answer it instead of the author guessing.
        if (type != null) picker.setStackFilter(stack -> stack.get(type) != null);
        picker.setOnSelectWithNbt((id, nbt) -> {
            closePicker();
            JsonObject components = nbt != null && nbt.has("components")
                    ? nbt.getAsJsonObject("components")
                    : null;
            if (components == null || !components.has(comp.componentId())) {
                EditorToastHandler.show(EditorToast.Level.INFO,
                        Component.translatable("editor.historystages.nbt.fill_from_item"),
                        Component.translatable("editor.historystages.nbt.fill_from_item.missing",
                                id, comp.componentId()));
                return;
            }
            comp.valueJson = components.get(comp.componentId()).toString();
            refresh();
        });
        pickerOverlay = picker;
        picker.show(this.width / 2, this.height / 2, this.width);
    }

    /**
     * Picks the enchantment from the registry instead of having it typed.
     *
     * <p>The ids the other lines already use are greyed out — naming the same enchantment twice in
     * one criterion says nothing the first line did not.
     */
    private void openEnchantmentPicker(EnchantmentListCriterion ench, int line) {
        SearchableEnchantmentList picker = new SearchableEnchantmentList(
                id -> {
                    closePicker();
                    ench.lines.get(line).id = id;
                    refresh();
                },
                () -> {
                    List<String> taken = new ArrayList<>();
                    for (int i = 0; i < ench.lines.size(); i++) {
                        if (i != line && !ench.lines.get(i).id.isBlank()) {
                            taken.add(ench.lines.get(i).id);
                        }
                    }
                    return taken;
                });
        pickerOverlay = picker;
        picker.show(this.width / 2, this.height / 2, this.width);
    }

    private void syncPickerState() {
        if (pickerOverlay != null && !pickerOverlay.isVisible()) pickerOverlay = null;
    }

    private void closePicker() {
        if (pickerOverlay != null) {
            pickerOverlay.hide();
            pickerOverlay = null;
        }
    }

    // ==========================================
    // Scrolling and drag
    // ==========================================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        syncPickerState();
        if (pickerOverlay != null) return pickerOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (showJson) {
            jsonScroll = (int) Math.max(0, Math.min(maxJsonScroll(), jsonScroll - scrollY * 12));
            return true;
        }
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 12));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.mouseDragged(mouseX, mouseY)) return true;
        if (scrollbar.isDragging()) {
            scrollOffset = scrollbar.scrollFor(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.mouseReleased()) return true;
        if (scrollbar.isDragging()) {
            scrollbar.mouseReleased();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.keyPressed(keyCode)) {
            syncPickerState();
            return true;
        }
        if (showJson && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeJson();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Escape goes back to the screen that opened this one, not out of the editor altogether.
     * {@code Screen.onClose} defaults to setting the screen to null, which drops the author into
     * the world and loses the way back.
     */
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.charTyped(c)) return true;
        return super.charTyped(c, modifiers);
    }

    // ==========================================
    // Suggestions
    // ==========================================

    private static List<String> getPotionSuggestions() {
        if (potionIds == null || potionIds.isEmpty()) {
            potionIds = new ArrayList<>();
            for (ResourceLocation key : BuiltInRegistries.POTION.keySet()) {
                potionIds.add(key.toString());
            }
            Collections.sort(potionIds);
        }
        return potionIds;
    }

    // ==========================================
    // Save and clear
    // ==========================================

    /**
     * Hands the criteria up and stays put. Warnings live on the cards and do not block this —
     * they are guesses about registries this client happens to know, not errors.
     */
    private void saveNbt() {
        JsonObject nbt = NbtCriteriaCodec.write(criteria);
        onSave.accept(nbt.isEmpty() ? null : nbt);
        savedSnapshot = nbt;
        dirty = false;
    }

    private void confirmClearAll() {
        if (criteria.isEmpty()) return;
        this.minecraft.setScreen(new ConfirmDialog(this,
                Component.translatable("editor.historystages.nbt.clear_all.confirm.title"),
                Component.translatable("editor.historystages.nbt.clear_all.confirm.message", criteria.size()),
                () -> {
                    criteria.clear();
                    refresh();
                }));
    }

    // ==========================================
    // Input screen with autocomplete suggestions
    // ==========================================

    static class SuggestingInputScreen extends AbstractInputScreen {
        private static final int MAX_VISIBLE_SUGGESTIONS = 6;
        private static final int SUGGESTION_HEIGHT = 14;

        private final Screen parent;
        private final String currentValue;
        private final List<String> allSuggestions;
        private final Consumer<String> onDone;
        private List<String> filteredSuggestions = new ArrayList<>();
        private int suggestionScroll = 0;
        /** Hover progress of the suggestion rows, keyed by visible slot. */
        private final java.util.Map<Integer, Anim> suggestionHover = new java.util.HashMap<>();
        private boolean draggingScrollbar = false;
        /** Last query the list was filtered against, so a re-filter can tell a change from a repoll. */
        private String lastFilterInput = null;

        /** List bounds, recomputed every frame from the area the base class hands us. */
        private int listX, listY, listW;

        /** Distance of the scrollbar's left edge from the list's right edge. */
        private static final int SCROLLBAR_INSET_X = 3;

        SuggestingInputScreen(Screen parent, String title, String currentValue, List<String> suggestions, Consumer<String> onDone) {
            super(parent, Component.literal(title));
            this.parent = parent;
            this.currentValue = currentValue;
            this.allSuggestions = suggestions;
            this.onDone = onDone;
            updateSuggestions(currentValue);
        }

        @Override
        protected Component confirmLabel() {
            return Component.translatable("editor.historystages.nbt.ok");
        }

        @Override
        protected List<InputField> fields() {
            return List.of(InputField.text("value").maxLength(512).initial(currentValue));
        }

        /**
         * Reserves the maximum list height rather than the current one: the box geometry is
         * computed once in init(), so a height that tracked the hit count would make the
         * dialog jump on every keystroke.
         */
        @Override
        protected int extraContentHeight() {
            return allSuggestions.isEmpty() ? 0 : MAX_VISIBLE_SUGGESTIONS * SUGGESTION_HEIGHT;
        }

        /**
         * Re-filters the list, and rewinds the scroll whenever the query actually changed —
         * the base class owns the EditBox responder, so the query is polled each frame rather
         * than pushed, and only a real change should move the user's scroll position.
         */
        private void updateSuggestions(String input) {
            boolean queryChanged = !input.equals(lastFilterInput);
            lastFilterInput = input;
            if (queryChanged) suggestionScroll = 0;

            if (allSuggestions.isEmpty() || input.isEmpty()) {
                filteredSuggestions = allSuggestions.isEmpty() ? Collections.emptyList() : new ArrayList<>(allSuggestions);
                return;
            }
            String lower = input.toLowerCase();
            filteredSuggestions = allSuggestions.stream()
                    .filter(s -> s.toLowerCase().contains(lower))
                    .limit(50)
                    .collect(Collectors.toList());
        }

        private int maxScroll() {
            return Math.max(0, filteredSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
        }

        /** The 5px-wide strip the scrollbar reacts in — wider than the 2px it draws, to be hittable. */
        private boolean inScrollbar(double mx, double my) {
            if (maxScroll() <= 0) return false;
            int barX = listX + listW - SCROLLBAR_INSET_X;
            int listH = visibleCount() * SUGGESTION_HEIGHT;
            return mx >= barX - 2 && mx <= barX + 4 && my >= listY && my < listY + listH;
        }

        /** Maps a y inside the bar onto a scroll row, centring the thumb on the cursor. */
        private void scrollFromMouse(double my) {
            int listH = visibleCount() * SUGGESTION_HEIGHT;
            int thumbH = Math.max(4, listH * MAX_VISIBLE_SUGGESTIONS / Math.max(1, filteredSuggestions.size()));
            float usable = listH - thumbH;
            if (usable <= 0) return;
            float ratio = (float) ((my - listY - thumbH / 2.0) / usable);
            ratio = Math.max(0.0f, Math.min(1.0f, ratio));
            suggestionScroll = Math.round(ratio * maxScroll());
        }

        private int visibleCount() {
            return Math.min(MAX_VISIBLE_SUGGESTIONS, filteredSuggestions.size());
        }

        @Override
        protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            // The base class owns the EditBox responder (it drives validation), so the filter is
            // refreshed from the live value each frame instead of on a change callback.
            updateSuggestions(box(0).getValue());
            suggestionScroll = Math.min(suggestionScroll, maxScroll());

            listX = x;
            listY = y;
            listW = w;

            if (filteredSuggestions.isEmpty()) return;

            int visible = visibleCount();
            int listH = visible * SUGGESTION_HEIGHT;
            g.fill(listX, listY, listX + listW, listY + listH, 0xF0222222);

            String input = box(0).getValue().toLowerCase();
            for (int i = 0; i < visible; i++) {
                int idx = i + suggestionScroll;
                if (idx >= filteredSuggestions.size()) break;
                String suggestion = filteredSuggestions.get(idx);
                int itemY = listY + i * SUGGESTION_HEIGHT;
                boolean hovered = mouseX >= listX && mouseX < listX + listW
                        && mouseY >= itemY && mouseY < itemY + SUGGESTION_HEIGHT;

                float sh = Ease.outCubic(suggestionHover.computeIfAbsent(i, k -> new Anim())
                        .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
                if (sh > 0.001f) {
                    g.fill(listX, itemY, listX + listW, itemY + SUGGESTION_HEIGHT,
                            Fade.rgba(0xFFCC00, 0.25f * sh));
                    g.fill(listX, itemY, listX + 1, itemY + SUGGESTION_HEIGHT, Fade.rgba(0xFFCC00, sh));
                }

                // Highlight the part matching the current input
                int matchIdx = suggestion.toLowerCase().indexOf(input);
                if (matchIdx >= 0 && !input.isEmpty()) {
                    String before = suggestion.substring(0, matchIdx);
                    String match = suggestion.substring(matchIdx, matchIdx + input.length());
                    String after = suggestion.substring(matchIdx + input.length());
                    int tx = listX + 4;
                    g.drawString(this.font, before, tx, itemY + 3, 0x999999);
                    tx += this.font.width(before);
                    g.drawString(this.font, match, tx, itemY + 3, 0xFFCC00);
                    tx += this.font.width(match);
                    g.drawString(this.font, after, tx, itemY + 3, 0x999999);
                } else {
                    g.drawString(this.font, suggestion, listX + 4, itemY + 3, 0x999999);
                }
            }

            // Scrollbar — draggable, and brightening on hover so that reads as an affordance.
            if (filteredSuggestions.size() > MAX_VISIBLE_SUGGESTIONS) {
                int barX = listX + listW - SCROLLBAR_INSET_X;
                int thumbH = Math.max(4, listH * MAX_VISIBLE_SUGGESTIONS / filteredSuggestions.size());
                int thumbY = listY + (int) ((float) suggestionScroll / maxScroll() * (listH - thumbH));
                int thumbColor = draggingScrollbar ? 0xFFFFCC00
                        : (inScrollbar(mouseX, mouseY) ? 0xC0FFCC00 : 0x80FFCC00);
                g.fill(barX, listY, barX + 2, listY + listH, 0x20FFFFFF);
                g.fill(barX, thumbY, barX + 2, thumbY + thumbH, thumbColor);
            }
        }

        @Override
        protected boolean extraContentMouseClicked(double mx, double my, int button) {
            if (button != 0) return false;
            if (filteredSuggestions.isEmpty()) return false;

            // Scrollbar wins over the rows it sits on top of.
            if (inScrollbar(mx, my)) {
                draggingScrollbar = true;
                scrollFromMouse(my);
                return true;
            }

            int listH = visibleCount() * SUGGESTION_HEIGHT;
            if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) return false;

            int idx = (int) ((my - listY) / SUGGESTION_HEIGHT) + suggestionScroll;
            if (idx < 0 || idx >= filteredSuggestions.size()) return false;

            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            box(0).setValue(filteredSuggestions.get(idx));
            return true;
        }

        @Override
        protected boolean extraContentMouseDragged(double mx, double my, int button) {
            if (!draggingScrollbar) return false;
            scrollFromMouse(my);
            return true;
        }

        @Override
        protected boolean extraContentMouseReleased(double mx, double my, int button) {
            if (!draggingScrollbar) return false;
            draggingScrollbar = false;
            return true;
        }

        @Override
        protected boolean extraContentMouseScrolled(double mx, double my, double scrollY) {
            if (filteredSuggestions.isEmpty()) return false;
            suggestionScroll = (int) Math.max(0, Math.min(maxScroll(), suggestionScroll - scrollY));
            return true;
        }

        @Override
        protected void onConfirm(InputValues values) {
            onDone.accept(values.getString("value"));
            this.minecraft.setScreen(parent);
        }
    }

    // ==========================================
    // Custom NBT input screen
    // ==========================================

    static class CustomNbtInputScreen extends AbstractInputScreen {
        private final Screen parent;
        private final java.util.function.BiConsumer<String, String> onDone;

        CustomNbtInputScreen(Screen parent, java.util.function.BiConsumer<String, String> onDone) {
            super(parent, Component.translatable("editor.historystages.nbt.custom.heading"));
            this.parent = parent;
            this.onDone = onDone;
        }

        @Override
        protected Component confirmLabel() {
            return Component.translatable("editor.historystages.nbt.ok");
        }

        @Override
        protected List<InputField> fields() {
            return List.of(
                    InputField.text("key")
                            .label(Component.translatable("editor.historystages.nbt.custom.key_label"))
                            .hint(Component.translatable("editor.historystages.nbt.custom.key_hint"))
                            .maxLength(128)
                            .validator(v -> v.isEmpty()
                                    ? Component.translatable("editor.historystages.input.empty") : null),
                    InputField.text("value")
                            .label(Component.translatable("editor.historystages.nbt.custom.value_label"))
                            .hint(Component.translatable("editor.historystages.nbt.custom.value_hint"))
                            .maxLength(512));
        }

        @Override
        protected void onConfirm(InputValues values) {
            onDone.accept(values.getString("key"), values.getString("value"));
            this.minecraft.setScreen(parent);
        }
    }

    // ==========================================
    // Component value editor (raw JSON object for one data-component)
    // ==========================================

    static class ComponentValueEditScreen extends AbstractInputScreen {
        private final Screen parent;
        private final String componentId;
        private final String initialValue;
        private final Consumer<String> onDone;
        /** A real value of this component, read once on open. Null when nothing in game has one. */
        private final String example;
        /** Smallest value the codec accepts, found by trial. Null when trial came up empty. */
        private final String skeleton;
        /** Whether {@code {}} is valid, i.e. a bare "is this property set" criterion works. */
        private final boolean presenceWorks;
        /** Last resort when neither an example nor a skeleton turned up. */
        private final String requirement;

        ComponentValueEditScreen(Screen parent, String componentId, String initialValue, Consumer<String> onDone) {
            super(parent, Component.translatable("editor.historystages.nbt.component.heading"));
            this.parent = parent;
            this.componentId = componentId;
            this.initialValue = initialValue != null ? initialValue : "{}";
            this.onDone = onDone;
            this.example = ComponentShapes.exampleFor(componentId);
            this.skeleton = example != null ? null : ComponentShapes.skeletonFor(componentId);
            this.presenceWorks = "{}".equals(skeleton) || ComponentShapes.acceptsEmptyObject(componentId);
            this.requirement = example != null || skeleton != null
                    ? null
                    : ComponentShapes.requirementHint(componentId);
        }

        @Override
        protected Component subtitle() {
            return Component.literal(componentId);
        }

        @Override
        protected int dialogWidth() { return 460; }

        @Override
        protected Component confirmLabel() {
            return Component.translatable("editor.historystages.nbt.ok");
        }

        @Override
        protected List<InputField> fields() {
            return List.of(InputField.text("json")
                    .maxLength(8192)
                    .hint(Component.literal(example != null ? example
                            : skeleton != null ? skeleton : "{\"key\": \"value\"}"))
                    .initial(initialValue)
                    .validator(this::validateJson));
        }

        @Override
        protected int extraContentHeight() {
            if (example != null) return 14 + exampleLines().size() * 10 + 14;
            if (skeleton != null) return 14 + skeletonLines().size() * 10 + 14;
            return requirement == null ? 0 : 14 + requirementLines().size() * 10;
        }

        /**
         * Shows what this component looks like on a real item, and says that a criterion only has
         * to name the fields it cares about. Without that second sentence the example reads as a
         * form to fill in completely, which would match far fewer items than intended.
         */
        @Override
        protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            if (example == null && skeleton != null) {
                g.drawString(this.font,
                        Component.translatable("editor.historystages.nbt.shape.skeleton"), x, y, 0xFF8A8A8A);
                int skelY = y + 12;
                for (FormattedCharSequence line : skeletonLines()) {
                    g.drawString(this.font, line, x, skelY, 0xFFCCCCCC);
                    skelY += 10;
                }
                g.drawString(this.font, Component.translatable(presenceWorks
                                ? "editor.historystages.nbt.shape.presence_ok"
                                : "editor.historystages.nbt.shape.subset"),
                        x, skelY + 3, 0xFF8A8A8A);
                return;
            }

            if (example == null) {
                if (requirement == null) return;
                g.drawString(this.font,
                        Component.translatable("editor.historystages.nbt.shape.requires"), x, y, 0xFF8A8A8A);
                int reqY = y + 12;
                for (FormattedCharSequence line : requirementLines()) {
                    g.drawString(this.font, line, x, reqY, 0xFFCCCCCC);
                    reqY += 10;
                }
                return;
            }

            g.drawString(this.font,
                    Component.translatable("editor.historystages.nbt.shape.example"), x, y, 0xFF8A8A8A);
            int lineY = y + 12;
            for (FormattedCharSequence line : exampleLines()) {
                g.drawString(this.font, line, x, lineY, 0xFFCCCCCC);
                lineY += 10;
            }
            g.drawString(this.font,
                    Component.translatable("editor.historystages.nbt.shape.subset"), x, lineY + 3, 0xFF8A8A8A);
        }

        private List<FormattedCharSequence> exampleLines() {
            return this.font.split(Component.literal(example), dialogWidth() - 40);
        }

        private List<FormattedCharSequence> skeletonLines() {
            return this.font.split(Component.literal(skeleton), dialogWidth() - 40);
        }

        private List<FormattedCharSequence> requirementLines() {
            return this.font.split(Component.literal(requirement), dialogWidth() - 40);
        }

        /** Any JSON type is allowed — objects, arrays, strings, numbers, booleans — but not null. */
        private Component validateJson(String raw) {
            if (raw.isEmpty()) return Component.translatable("editor.historystages.nbt.json.empty");
            try {
                com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(raw);
                if (parsed.isJsonNull()) {
                    return Component.translatable("editor.historystages.nbt.json.not_object");
                }
                return null;
            } catch (Exception e) {
                return Component.translatable("editor.historystages.nbt.json.invalid", e.getMessage());
            }
        }

        @Override
        protected void onConfirm(InputValues values) {
            onDone.accept(values.getString("json"));
            this.minecraft.setScreen(parent);
        }
    }
}
