package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.toast.EditorToast;
import net.bananemdnsa.historystages.client.editor.toast.EditorToastHandler;
import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;
import net.bananemdnsa.historystages.api.editor.widget.ChoiceOverlay;
import net.bananemdnsa.historystages.api.editor.widget.CountInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.client.editor.dialog.TimeWindowScreen;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.client.editor.widget.EntityPreviewRenderer;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableAdvancementList;
import net.bananemdnsa.historystages.api.editor.widget.SearchBar;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableBiomeList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableDimensionList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableEffectList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableEntityList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableStatList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableStructureList;
import net.bananemdnsa.historystages.client.editor.widget.StageLockFilter;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DropdownChrome;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.CombineMode;
import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DayCountTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EffectTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntitySubMode;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StatCategory;
import net.bananemdnsa.historystages.data.auto.conditions.StatTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TimeOfDayTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TimePreset;
import net.bananemdnsa.historystages.data.auto.conditions.WeatherState;
import net.bananemdnsa.historystages.data.auto.conditions.WeatherTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.XpLevelTrigger;
import net.bananemdnsa.historystages.api.editor.GenericIdPicker;
import net.bananemdnsa.historystages.api.editor.TriggerEditor;
import net.bananemdnsa.historystages.client.editor.trigger.TriggerEditors;
import net.bananemdnsa.historystages.client.editor.trigger.TriggerLabels;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.auto.TriggerTypes;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Screen for editing an {@link AutoTrigger}: combine-mode pill toggle and a list of
 * trigger conditions with per-row icon + value rendering and a right-click menu.
 * Add and edit both reuse the existing Searchable widgets for value selection.
 */
public class AutoTriggerEditorScreen extends Screen {

    private static final int ROW_H = 22;
    private static final int ICON_W = 18;
    private static final int TYPE_COL_W = 90;

    /** Add-dropdown metrics — same as the editor's other dropdowns so the popups match. */
    private static final int ADD_BTN_H = 18;
    private static final int ADD_ROW_H = 18;
    private static final int ADD_POPUP_PAD = 2;
    /** Width reserved for the popup's scrollbar, only added when it has one. */
    private static final int ADD_POPUP_SCROLL_W = 4;

    /** Footer button width. Read by the unsaved marker, which right-aligns against Save. */
    private static final int FOOTER_BTN_W = 90;

    private static final TriggerType[] TYPES = TriggerType.values();

    /**
     * A row in the add menu: a label and what happens when it is clicked. Built-in types and types
     * another mod registered both become one of these, so the menu stops being a fixed list.
     *
     * <p>The component is deliberately not called {@code open}: {@code row.open()} would then read
     * as opening the picker while actually being the accessor, and dropping the returned Runnable
     * is silent. That is exactly how every row in this menu came to do nothing.
     */
    private record AddableTrigger(String label, Runnable onClick) {}

    /** Built-ins first, in their long-standing order, then whatever addons registered. */
    private List<AddableTrigger> addableTriggers() {
        List<AddableTrigger> rows = new ArrayList<>(TYPES.length);
        // Not filtered by scope: every built-in supports both scopes (see TriggerTypes), so a
        // filter here would be a no-op that only invites a later reader to wonder what it guards.
        for (TriggerType type : TYPES) {
            rows.add(new AddableTrigger(typeLabel(type), () -> openPickerFor(type)));
        }
        for (TriggerEditor editor : TriggerEditors.all()) {
            if (!TriggerTypes.scopesOf(editor.type()).contains(scope)) continue;
            rows.add(new AddableTrigger(
                    Component.translatable(editor.labelLangKey()).getString(),
                    () -> openAddonPicker(editor)));
        }
        return rows;
    }

    /**
     * Opens the picker an addon registered for this trigger type. False when it registered none,
     * which is the caller's cue that the trigger cannot be authored here.
     */
    private boolean openAddonEditorFor(String type) {
        TriggerEditor editor = TriggerEditors.byType(type);
        if (editor == null) return false;
        openAddonPicker(editor);
        return true;
    }

    /** Adding and editing go through the same overlay, so the two paths cannot drift apart. */
    private void openAddonPicker(TriggerEditor editor) {
        // An addon whose trigger carries more than one id supplies its own authoring screen; the
        // id picker below is only the default for the ones that do not.
        Screen authoring = editor.authoringScreen(this, this::placeTrigger);
        if (authoring != null) {
            this.minecraft.setScreen(authoring);
            return;
        }
        showAbstract(new GenericIdPicker(editor.searchPlaceholderLangKey(), editor::candidates,
                editor.placingInto(this::placeTrigger), null), null, false);
    }

    private final Screen parent;
    private final AutoTrigger trigger;
    private final Consumer<AutoTrigger> onChanged;
    /** Live snapshot of the parent stage's lock data, sampled per picker open. May be null. */
    private final Supplier<StageEntry> lockSnapshot;
    /** Persists the whole stage from the parent screen. May be null. */
    private final Runnable onPersist;
    /** Scope of the stage being edited — narrows the add menu to types that apply here. */
    private final StageScope scope;

    // Layout
    private int listX, listY, listW, listH;
    private int pillX, pillY, pillAnyW, pillAllW;
    private int addBtnX, addBtnY, addBtnW;
    /** Left edge of the Save button; the unsaved marker right-aligns against it. */
    private int saveBtnX;
    private int searchY;
    private int scrollOffset = 0;
    /** Sub-pixel scroll chasing {@link #scrollOffset}; render and the click paths both read it. */
    private final Anim smoothScroll = new Anim();
    private final Anim addBtnHover = new Anim();
    private final Anim listThumbHover = new Anim();
    private boolean draggingScrollbar = false;

    // List search / type filter
    private SearchBar listSearchBar;
    /** Indices into {@link AutoTrigger#getTriggers()} that pass the current search + type filter. */
    private final List<Integer> visibleIndices = new ArrayList<>();

    // Inline overlays
    private boolean addDropdownOpen = false;
    /** First visible row of the add popup, once it holds more types than fit on screen. */
    private int addDropdownScroll = 0;
    private boolean draggingAddScrollbar = false;
    /**
     * Whether anything changed since this screen last persisted.
     *
     * <p>Changes here are applied to the parent immediately, so nothing can be lost — but every
     * other editor screen says so on its own footer, and having to go back a screen to find out
     * was the one place that did not.
     */
    private boolean hasChanges = false;
    /** Reveal progress of the add popup; also drives the caret turning over. */
    private final Anim addOpen = new Anim();
    private final java.util.Map<Integer, Anim> addRowHover = new java.util.HashMap<>();
    /**
     * The one modal overlay slot. A searchable picker and a choice list are both a
     * {@link PickerOverlay}, so they take turns in the same field rather than each having one.
     */
    private PickerOverlay currentList = null;
    private int editIndex = -1;             // -1 = adding, ≥0 = replacing at this row

    // Context menu
    private ContextMenu contextMenu = new ContextMenu();

    public AutoTriggerEditorScreen(Screen parent, AutoTrigger trigger, Consumer<AutoTrigger> onChanged,
                                   StageScope scope) {
        this(parent, trigger, onChanged, null, null, scope);
    }

    public AutoTriggerEditorScreen(Screen parent, AutoTrigger trigger,
                                   Consumer<AutoTrigger> onChanged,
                                   Supplier<StageEntry> lockSnapshot,
                                   StageScope scope) {
        this(parent, trigger, onChanged, lockSnapshot, null, scope);
    }

    public AutoTriggerEditorScreen(Screen parent, AutoTrigger trigger,
                                   Consumer<AutoTrigger> onChanged,
                                   Supplier<StageEntry> lockSnapshot,
                                   Runnable onPersist,
                                   StageScope scope) {
        super(Component.translatable("editor.historystages.auto_trigger.title"));
        this.parent = parent;
        this.trigger = trigger;
        this.onChanged = onChanged;
        this.lockSnapshot = lockSnapshot;
        this.onPersist = onPersist;
        this.scope = scope;
    }

    @Override
    protected void init() {
        // Pill toggle (left side)
        pillX = 20;
        pillY = 28;
        pillAnyW = Math.max(32, this.font.width(combineLabel(CombineMode.ANY)) + 14);
        pillAllW = Math.max(32, this.font.width(combineLabel(CombineMode.ALL)) + 14);

        // Add button (right side)
        addBtnW = 90;
        addBtnX = this.width - 20 - addBtnW;
        addBtnY = 28;

        // Search bar row (between top row and list)
        searchY = 50;

        // List card geometry
        listX = 20;
        listY = searchY + SearchBar.HEIGHT + 4;
        listW = this.width - 40;
        listH = this.height - listY - 40;

        // List search bar — text matches against trigger value text; filter dropdown lets the
        // user narrow to a single trigger type. Filter options share the "type" mutex group so
        // picking one auto-deselects the others (none active = show all types).
        if (listSearchBar == null) {
            listSearchBar = new SearchBar(
                    Component.translatable("editor.historystages.auto_trigger.search").getString())
                    .setLightStyle(true);
            // Bar is part of the persistent editor chrome — start unfocused so it doesn't
            // claim the blinking cursor while the user is interacting with the trigger list.
            listSearchBar.setFocused(false);
            for (TriggerType tt : TYPES) {
                listSearchBar.filters().addOption(tt.id,
                        Component.translatable("editor.historystages.auto_trigger.type." + tt.id).getString(),
                        "type");
            }
            listSearchBar.onChange(q -> applyTriggerFilter());
        }
        applyTriggerFilter();

        // Footer buttons
        saveBtnX = this.width - 20 - FOOTER_BTN_W;
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> saveAndStay(),
                saveBtnX, this.height - 25, FOOTER_BTN_W, 20));
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.cancel"),
                btn -> this.minecraft.setScreen(parent),
                20, this.height - 25, FOOTER_BTN_W, 20));
    }

    // =============================================
    // Rendering
    // =============================================

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // No-op — own background in render()
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);

        smoothScroll.approach(scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle(scrollOffset, 0.5f);

        // Header
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        g.fill(10, 18, this.width - 10, 19, 0xFF555555);

        renderUnsavedMarker(g);

        renderCombinePill(g, mx, my);
        renderAddButton(g, mx, my);
        // The list search bar renders its text at z=300 internally — drawing it while a
        // modal overlay is active would let the placeholder + cursor bleed through the
        // dim layer. Suppress while any overlay/dialog is up.
        if (!isOverlayActive()) renderListSearchBar(g, mx, my);
        renderList(g, mx, my);

        super.render(g, mx, my, pt);

        // Tooltip for pill toggle (drawn here so it sits above the screen background but
        // below any overlays; the actual tooltip box is rendered with editor styling)
        if (isOver(mx, my, pillX, pillY, pillAnyW + pillAllW, 14)
                && currentList == null && !addDropdownOpen
                && !contextMenu.isVisible()) {
            drawEditorTooltip(g,
                    Component.translatable("editor.historystages.auto_trigger.combine.tooltip").getString(),
                    mx, my);
        }

        // Add-dropdown popup — always rendered; the reveal animation decides what is visible.
        renderAddDropdown(g, mx, my);

        // Dim the rest of the screen whenever a modal overlay is active (Searchable widget or
        // the choice list). Without this the editor's title and the empty-state text bleed
        // through around the overlay's panel.
        boolean overlayActive = isOverlayActive();
        if (overlayActive) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(0, 0, this.width, this.height, 0xC0000000);
            g.pose().popPose();
        }

        // Searchable list overlay
        if (currentList != null && currentList.isVisible()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 220);
            currentList.render(g, this.font, mx, my);
            g.pose().popPose();
        }

        // Context menu always on top
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        contextMenu.render(g, this.font, mx, my);
        g.pose().popPose();
    }

    private void renderCombinePill(GuiGraphics g, int mx, int my) {
        CombineMode active = trigger.resolvedMode();
        int x = pillX;
        int y = pillY;
        int h = 14;

        // ANY half
        boolean anyActive = active == CombineMode.ANY;
        int anyBg = anyActive ? 0x60FFCC00 : 0x25FFFFFF;
        g.fill(x, y, x + pillAnyW, y + h, anyBg);
        g.drawString(this.font, combineLabel(CombineMode.ANY),
                x + (pillAnyW - this.font.width(combineLabel(CombineMode.ANY))) / 2,
                y + 3, anyActive ? 0xFFFFFFFF : 0xFFCCCCCC, false);

        // ALL half
        boolean allActive = active == CombineMode.ALL;
        int allBg = allActive ? 0x60FFCC00 : 0x25FFFFFF;
        g.fill(x + pillAnyW, y, x + pillAnyW + pillAllW, y + h, allBg);
        g.drawString(this.font, combineLabel(CombineMode.ALL),
                x + pillAnyW + (pillAllW - this.font.width(combineLabel(CombineMode.ALL))) / 2,
                y + 3, allActive ? 0xFFFFFFFF : 0xFFCCCCCC, false);

        // Bottom accent
        g.fill(x, y + h, x + pillAnyW + pillAllW, y + h + 1, 0x60FFCC00);
    }

    private void renderListSearchBar(GuiGraphics g, int mx, int my) {
        if (listSearchBar == null) return;
        listSearchBar.setPosition(20, searchY, this.width - 40);
        listSearchBar.render(g, this.font, mx, my);
    }

    private void renderAddButton(GuiGraphics g, int mx, int my) {
        boolean hov = isOver(mx, my, addBtnX, addBtnY, addBtnW, ADD_BTN_H);
        float hp = Ease.outCubic(addBtnHover.ramp(hov, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        DropdownChrome.drawButton(g, this.font, addBtnX, addBtnY, addBtnW, ADD_BTN_H,
                Component.translatable("editor.historystages.auto_trigger.add").getString(),
                hp, addDropdownOpen, addOpen.value());
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        // Card frame
        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xFF555555);
        g.fill(listX, listY, listX + listW, listY + listH, 0xFF1A1A1A);

        List<TriggerCondition> triggers = trigger.getTriggers();
        if (visibleIndices.isEmpty()) {
            String key = triggers.isEmpty()
                    ? "editor.historystages.auto_trigger.empty"
                    : "editor.historystages.auto_trigger.no_matches";
            g.drawCenteredString(this.font, Component.translatable(key).getString(),
                    listX + listW / 2, listY + listH / 2 - 4, 0x888888);
            return;
        }

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY + 4 - Math.round(smoothScroll.value());
        for (int v = 0; v < visibleIndices.size(); v++) {
            int rowTop = y + v * ROW_H;
            if (rowTop + ROW_H < listY || rowTop > listY + listH) continue;
            int origIdx = visibleIndices.get(v);
            renderRow(g, triggers.get(origIdx), origIdx, rowTop, mx, my);
        }
        g.disableScissor();

        // Scrollbar — sized from visible (filtered) row count
        int contentH = visibleIndices.size() * ROW_H + 8;
        int maxScroll = Math.max(0, contentH - listH);
        if (maxScroll > 0) {
            int sbX = listX + listW - 4;
            int thumbH = Math.max(20, (int) ((float) listH / contentH * listH));
            int thumbY = listY + Math.round(smoothScroll.value() / maxScroll * (listH - thumbH));
            g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0x80FFFFFF);
        }
    }

    /** Rebuild {@link #visibleIndices} from the current search text + type filter selection. */
    private void applyTriggerFilter() {
        visibleIndices.clear();
        List<TriggerCondition> triggers = trigger.getTriggers();
        String query = listSearchBar == null ? "" : listSearchBar.getText();
        String activeType = activeTypeFilter();
        for (int i = 0; i < triggers.size(); i++) {
            TriggerCondition t = triggers.get(i);
            if (activeType != null && !t.type().equals(activeType)) continue;
            if (!query.isEmpty()) {
                String value = TriggerLabels.valueText(t).toLowerCase();
                String typeName = TriggerLabels.typeLabel(t).toLowerCase();
                if (!value.contains(query) && !typeName.contains(query)) continue;
            }
            visibleIndices.add(i);
        }
        // Clamp scroll to the new content height
        int contentH = visibleIndices.size() * ROW_H + 8;
        int maxScroll = Math.max(0, contentH - listH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    /** Returns the id of the active "type" filter option, or null if none is active. */
    private String activeTypeFilter() {
        if (listSearchBar == null) return null;
        for (TriggerType tt : TYPES) {
            if (listSearchBar.filters().isActive(tt.id)) return tt.id;
        }
        return null;
    }

    private void renderRow(GuiGraphics g, TriggerCondition t, int idx, int top, int mx, int my) {
        int rowH = ROW_H - 2;
        boolean hov = !isInputBlocked()
                && mx >= listX && mx <= listX + listW
                && my >= Math.max(top, listY) && my <= Math.min(top + rowH, listY + listH);

        g.fill(listX + 2, top, listX + listW - 6, top + rowH, hov ? 0x35FFFFFF : 0x18FFFFFF);
        if (hov) g.fill(listX + 2, top, listX + 4, top + rowH, 0xFFFFCC00);

        // Icon
        int iconX = listX + 8;
        int iconY = top + (rowH - 16) / 2;
        renderTriggerIcon(g, t, iconX, iconY);

        // Type name
        String typeName = TriggerLabels.typeLabel(t);
        // Clipped to its column, exactly as the value below is: the type name is not ours to
        // bound once other mods can add one.
        if (this.font.width(typeName) > TYPE_COL_W - 6) {
            typeName = this.font.plainSubstrByWidth(typeName, TYPE_COL_W - 12) + "...";
        }
        g.drawString(this.font, typeName, iconX + ICON_W + 4, top + 7, 0xFFCCCCCC, false);

        // Value
        String value = TriggerLabels.valueText(t);
        int valueX = iconX + ICON_W + 4 + TYPE_COL_W;
        int valueAvail = (listX + listW - 12) - valueX;
        if (this.font.width(value) > valueAvail) {
            value = this.font.plainSubstrByWidth(value, valueAvail - 10) + "...";
        }
        g.drawString(this.font, value, valueX, top + 7, 0xFFFFFFFF, false);
    }

    private void renderTriggerIcon(GuiGraphics g, TriggerCondition t, int x, int y) {
        // Entities render their actual living-entity model (spinning), matching SearchableEntityList.
        if (t instanceof EntityTrigger et) {
            LivingEntity living = EntityPreviewRenderer.getOrCreate(et.id());
            if (living != null) {
                try {
                    float angle = (System.currentTimeMillis() % 3600) / 10.0f;
                    int scale = (int) Math.max(3, 9.0f / Math.max(living.getBbWidth(), living.getBbHeight()));
                    g.enableScissor(x, y, x + 16, y + 16);
                    EntityPreviewRenderer.renderSpinning(g, x + 8, y + 15, scale, angle, living);
                    g.disableScissor();
                } catch (Exception ignored) {
                }
            }
            return;
        }
        // Items / blocks render via the item registry.
        ItemStack stack = iconStackFor(t);
        if (!stack.isEmpty()) g.renderItem(stack, x, y);
        // Other trigger types intentionally render no icon.
    }

    private ItemStack iconStackFor(TriggerCondition t) {
        return switch (t) {
            case ItemTrigger it -> resolveItem(it.id());
            case BlockPlaceTrigger b -> resolveBlock(b.id());
            case BlockBreakTrigger b -> resolveBlock(b.id());
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack resolveItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    private static ItemStack resolveBlock(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Block block = BuiltInRegistries.BLOCK.get(rl);
        ItemStack stack = new ItemStack(block);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    /**
     * The lang key naming a trigger's kind.
     *
     * <p>Built-in types each have their own; a type from a mod that is not loaded has none, and
     * building one from its id would print the raw key — long enough to run straight through the
     * next column. Such a row says "unknown" and puts the actual type in the value column, where
     * it is both readable and truncated.
     */
    /**
     * Geometry of the add popup as {x, y, w, h}. Widened to its longest type label and flipped
     * above the button when it would run off the bottom, so no row ends up unreachable.
     */
    /**
     * How many rows the popup shows before it starts scrolling.
     *
     * <p>Capped rather than grown: the list is nine built-ins plus one row per addon trigger type,
     * so on a short window or with a few addons installed it would otherwise run past the screen
     * edge with no way to reach the rows below.
     */
    private int addPopupVisibleRows() {
        int available = Math.max(0, this.height - 8 - ADD_POPUP_PAD * 2 - ADD_BTN_H - 4);
        int fits = Math.max(3, available / ADD_ROW_H);
        return Math.min(addableTriggers().size(), fits);
    }

    /** Largest first-row index the popup may be scrolled to. */
    private int addPopupMaxScroll() {
        return Math.max(0, addableTriggers().size() - addPopupVisibleRows());
    }

    private int[] addPopupGeometry() {
        List<AddableTrigger> rows = addableTriggers();
        // Measured over the rows actually shown, not over the built-in types: an addon's label is
        // free text and is usually the longest one, and the popup scissors at its own right edge.
        int pw = addBtnW;
        for (AddableTrigger row : rows) {
            int w = this.font.width(row.label()) + 16;
            if (w > pw) pw = w;
        }
        // Scrollbar track when there is more than fits, so a row is never hidden under it.
        if (addPopupMaxScroll() > 0) pw += ADD_POPUP_SCROLL_W;
        int ph = addPopupVisibleRows() * ADD_ROW_H + ADD_POPUP_PAD * 2;
        // Right-aligned with the button, which sits against the screen's right edge.
        int px = addBtnX + addBtnW - pw;
        int py = addBtnY + ADD_BTN_H + 2;
        if (px + pw > this.width - 4) px = this.width - pw - 4;
        if (px < 4) px = 4;
        if (py + ph > this.height - 4) py = addBtnY - ph - 2;
        if (py < 4) py = 4;
        return new int[] { px, py, pw, ph };
    }

    private void renderAddDropdown(GuiGraphics g, int mx, int my) {
        // A picker opened from this dropdown covers the screen at a lower depth than the popup,
        // so snap the reveal shut instead of letting it roll up over the picker.
        if (isOverlayActive()) {
            addOpen.set(0.0f);
            return;
        }
        // Kept rendering past the click that closed it, so the popup rolls back up instead of
        // vanishing. addOpen drives both the reveal here and the caret in renderAddButton.
        float t = addOpen.ramp(addDropdownOpen ? 1.0f : 0.0f, Timing.POPUP_MS);
        if (t < 0.02f) return;

        int[] geom = addPopupGeometry();
        int px = geom[0], py = geom[1], pw = geom[2], ph = geom[3];

        if (!DropdownChrome.begin(g, px, py, pw, ph, t, py < addBtnY)) return;

        List<AddableTrigger> rows = addableTriggers();
        int visible = addPopupVisibleRows();
        int maxScroll = addPopupMaxScroll();
        addDropdownScroll = Math.max(0, Math.min(maxScroll, addDropdownScroll));
        int rowW = maxScroll > 0 ? pw - ADD_POPUP_SCROLL_W : pw;

        for (int slot = 0; slot < visible; slot++) {
            int i = slot + addDropdownScroll;
            if (i >= rows.size()) break;
            int rowY = py + ADD_POPUP_PAD + slot * ADD_ROW_H;
            boolean hov = addDropdownOpen && isOver(mx, my, px, rowY, rowW, ADD_ROW_H);
            // Keyed by the entry, not the slot: scrolling must not carry a hover glow from the row
            // that used to sit there onto the one that moved into its place.
            float rh = Ease.outCubic(addRowHover.computeIfAbsent(i, k -> new Anim())
                    .ramp(hov, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            DropdownChrome.drawRowHighlight(g, px + 1, rowY, rowW - 2, ADD_ROW_H, rh);
            g.drawString(this.font, rows.get(i).label(), px + 5 + Math.round(rh * 2.0f), rowY + 5,
                    0xFFEEEEEE, false);
        }

        if (maxScroll > 0) {
            int trackX = px + pw - ADD_POPUP_SCROLL_W + 1;
            int trackY = py + ADD_POPUP_PAD;
            int trackH = visible * ADD_ROW_H;
            g.fill(trackX, trackY, trackX + ADD_POPUP_SCROLL_W - 2, trackY + trackH, 0x30FFFFFF);
            int thumbH = Math.max(8, trackH * visible / rows.size());
            int thumbY = trackY + Math.round((float) addDropdownScroll / maxScroll * (trackH - thumbH));
            g.fill(trackX, thumbY, trackX + ADD_POPUP_SCROLL_W - 2, thumbY + thumbH, 0x90FFFFFF);
        }
        DropdownChrome.end(g);
    }

    private static String typeLabel(TriggerType type) {
        return Component.translatable("editor.historystages.auto_trigger.type." + type.id).getString();
    }


    // =============================================
    // Input
    // =============================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Context menu first
        if (contextMenu.isVisible()) {
            contextMenu.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        // Modal overlay: a searchable picker or a choice list
        if (currentList != null && currentList.isVisible()) {
            return currentList.mouseClicked(mouseX, mouseY);
        }
        // Add dropdown
        if (addDropdownOpen) {
            return handleAddDropdownClick(mouseX, mouseY, button);
        }
        // List search bar (text field + filter dropdown). Handle first so the dropdown
        // popup catches clicks before they fall through to the list/pill below it.
        if (listSearchBar != null && listSearchBar.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        // Pill toggle
        if (button == 0 && isOver((int) mouseX, (int) mouseY, pillX, pillY, pillAnyW + pillAllW, 14)) {
            CombineMode picked = (mouseX < pillX + pillAnyW) ? CombineMode.ANY : CombineMode.ALL;
            if (trigger.resolvedMode() != picked) {
                trigger.setMode(picked.serialize());
                notifyChanged();
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        // Add button
        if (button == 0 && isOver((int) mouseX, (int) mouseY, addBtnX, addBtnY, addBtnW, ADD_BTN_H)) {
            addDropdownOpen = true;
            addDropdownScroll = 0;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        // Scrollbar thumb / track click (start drag)
        if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        // List row right-click (context menu)
        if (button == 1) {
            int rowIdx = rowAt(mouseX, mouseY);
            if (rowIdx >= 0) {
                showRowContextMenu(rowIdx, (int) mouseX, (int) mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOverScrollbar(double mx, double my) {
        if (scrollbarMaxScroll() <= 0) return false;
        int sbX = listX + listW - 4;
        return mx >= sbX - 1 && mx <= sbX + 4 && my >= listY && my <= listY + listH;
    }

    private int scrollbarMaxScroll() {
        int contentH = visibleIndices.size() * ROW_H + 8;
        return Math.max(0, contentH - listH);
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = scrollbarMaxScroll();
        if (maxScroll <= 0) return;
        int contentH = visibleIndices.size() * ROW_H + 8;
        int thumbH = Math.max(20, (int) ((float) listH / contentH * listH));
        float usableH = listH - thumbH;
        if (usableH <= 0) return;
        float ratio = (float) (mouseY - listY - thumbH / 2.0) / usableH;
        ratio = Math.max(0, Math.min(1, ratio));
        scrollOffset = Math.round(ratio * maxScroll);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        // Snapped, not eased: while the thumb is held the list must track the
        // cursor exactly, or the thumb drifts from where the pointer is.
        smoothScroll.set((float) scrollOffset);
    }

    /**
     * Track rectangle of the add popup's scrollbar, or null when it does not have one.
     *
     * @return [x, y, w, h]
     */
    private int[] addScrollbarTrack() {
        if (addPopupMaxScroll() <= 0) return null;
        int[] geom = addPopupGeometry();
        return new int[] {
                geom[0] + geom[2] - ADD_POPUP_SCROLL_W + 1,
                geom[1] + ADD_POPUP_PAD,
                ADD_POPUP_SCROLL_W - 2,
                addPopupVisibleRows() * ADD_ROW_H };
    }

    /** Places the thumb's centre under the cursor, inverting what {@link #renderAddDropdown} draws. */
    private void updateAddScrollFromMouse(double mouseY) {
        int[] track = addScrollbarTrack();
        if (track == null) return;
        int maxScroll = addPopupMaxScroll();
        int trackY = track[1];
        int trackH = track[3];
        int thumbH = Math.max(8, trackH * addPopupVisibleRows() / addableTriggers().size());
        float usableH = trackH - thumbH;
        if (usableH <= 0) {
            addDropdownScroll = 0;
            return;
        }
        float frac = (float) ((mouseY - trackY - thumbH / 2.0) / usableH);
        addDropdownScroll = Math.max(0, Math.min(maxScroll, Math.round(frac * maxScroll)));
    }

    private boolean handleAddDropdownClick(double mouseX, double mouseY, int button) {
        // Click back on the button closes rather than closing and immediately re-opening.
        if (button == 0 && isOver((int) mouseX, (int) mouseY, addBtnX, addBtnY, addBtnW, ADD_BTN_H)) {
            addDropdownOpen = false;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        // The scrollbar, before the rows: the popup used to draw a thumb nothing could grab, so
        // the only way past the visible rows was the wheel.
        int[] track = addScrollbarTrack();
        if (button == 0 && track != null
                && mouseX >= track[0] - 1 && mouseX <= track[0] + track[2] + 1
                && mouseY >= track[1] && mouseY <= track[1] + track[3]) {
            draggingAddScrollbar = true;
            updateAddScrollFromMouse(mouseY);
            return true;
        }
        int[] geom = addPopupGeometry();
        int px = geom[0], py = geom[1], pw = geom[2];
        List<AddableTrigger> clickable = addableTriggers();
        int visible = addPopupVisibleRows();
        int rowW = addPopupMaxScroll() > 0 ? pw - ADD_POPUP_SCROLL_W : pw;
        // Walk the visible slots and map each back to its entry, so a scrolled popup does not run
        // the row that happens to sit at that index unscrolled.
        for (int slot = 0; slot < visible; slot++) {
            int i = slot + addDropdownScroll;
            if (i >= clickable.size()) break;
            int rowY = py + ADD_POPUP_PAD + slot * ADD_ROW_H;
            if (button == 0 && isOver((int) mouseX, (int) mouseY, px, rowY, rowW, ADD_ROW_H)) {
                addDropdownOpen = false;
                editIndex = -1;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                clickable.get(i).onClick().run();
                return true;
            }
        }
        addDropdownOpen = false;
        return true;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listX || mouseX > listX + listW || mouseY < listY || mouseY > listY + listH) return -1;
        int relY = (int) mouseY - (listY + 4 - Math.round(smoothScroll.value()));
        int visRow = relY / ROW_H;
        if (visRow < 0 || visRow >= visibleIndices.size()) return -1;
        return visibleIndices.get(visRow);
    }

    private void showRowContextMenu(int rowIdx, int mx, int my) {
        TriggerCondition t = trigger.getTriggers().get(rowIdx);
        contextMenu = new ContextMenu();
        contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(),
                () -> openEditFor(rowIdx, t));
        contextMenu.addEntry(Component.translatable("editor.historystages.duplicate").getString(), () -> {
            trigger.getTriggers().add(rowIdx + 1, t);
            notifyChanged();
        });
        contextMenu.addEntry(Component.translatable("editor.historystages.remove").getString(), () -> {
            trigger.getTriggers().remove(rowIdx);
            notifyChanged();
        });
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        contextMenu.show(mx, my, this.font);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Before the list: the popup is drawn over it, so a wheel turn there belongs to the popup.
        if (addDropdownOpen) {
            addDropdownScroll = Math.max(0, Math.min(addPopupMaxScroll(),
                    addDropdownScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (currentList != null && currentList.isVisible()
                && currentList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int contentH = visibleIndices.size() * ROW_H + 8;
            int maxScroll = Math.max(0, contentH - listH);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY * 10));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingAddScrollbar) {
            updateAddScrollFromMouse(mouseY);
            return true;
        }
        if (currentList != null && currentList.isVisible()
                && currentList.mouseDragged(mouseX, mouseY)) return true;
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingAddScrollbar) { draggingAddScrollbar = false; return true; }
        if (currentList != null && currentList.isVisible() && currentList.mouseReleased()) return true;
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (currentList != null && currentList.isVisible()) {
            if (currentList.keyPressed(keyCode)) return true;
        }
        if (listSearchBar != null && listSearchBar.keyPressed(keyCode)) return true;
        if (keyCode == 256) {
            if (addDropdownOpen) { addDropdownOpen = false; draggingAddScrollbar = false; return true; }
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (currentList != null && currentList.isVisible()) {
            if (currentList.charTyped(c)) return true;
        }
        if (listSearchBar != null && listSearchBar.charTyped(c)) return true;
        return super.charTyped(c, modifiers);
    }

    // =============================================
    // Picker dispatch
    // =============================================

    private void openEditFor(int idx, TriggerCondition t) {
        editIndex = idx;
        switch (t) {
            case BiomeTrigger b -> showAbstract(new SearchableBiomeList(id -> placeTrigger(new BiomeTrigger(id))), null, false);
            case StructureTrigger s -> showAbstract(new SearchableStructureList(id -> placeTrigger(new StructureTrigger(id))), TriggerType.STRUCTURE, true);
            case DimensionTrigger d -> showAbstract(new SearchableDimensionList(id -> placeTrigger(new DimensionTrigger(id))), TriggerType.DIMENSION, true);
            case ItemTrigger i -> showItem(new SearchableItemList(id -> placeTrigger(new ItemTrigger(id))));
            case BlockPlaceTrigger bp -> showItem(new SearchableItemList(id -> placeTrigger(new BlockPlaceTrigger(id))));
            case BlockBreakTrigger bb -> showItem(new SearchableItemList(id -> placeTrigger(new BlockBreakTrigger(id))));
            case AdvancementTrigger a -> showAbstract(new SearchableAdvancementList(id -> placeTrigger(new AdvancementTrigger(id))), null, true);
            case EntityTrigger e -> showEntity(new SearchableEntityList(this::openSubmodeChooser));
            case PlaytimeTrigger p -> openCountDialog(idx,
                    Component.translatable("editor.historystages.auto_trigger.playtime.input_title"),
                    "", Math.max(1, p.days()), 1, 999999,
                    days -> placeTrigger(new PlaytimeTrigger(days)));
            case StatTrigger s -> openStatCategoryChooser(idx);
            case XpLevelTrigger x -> openCountDialog(idx,
                    Component.translatable("editor.historystages.auto_trigger.xp_level.input_title"),
                    "", x.requiredLevel(), 0, 10000,
                    level -> placeTrigger(new XpLevelTrigger(level)));
            case EffectTrigger e -> showAbstract(
                    new SearchableEffectList(id -> placeTrigger(new EffectTrigger(id))), null, false);
            case WeatherTrigger w -> openWeatherChooser();
            case DayCountTrigger d -> openCountDialog(idx,
                    Component.translatable("editor.historystages.auto_trigger.day_count.input_title"),
                    "", d.requiredDays(), 0, 999999,
                    days -> placeTrigger(new DayCountTrigger(days)));
            case TimeOfDayTrigger tod -> openTimePresetChooser(idx, tod.windowFrom(), tod.windowTo());
            // An addon that registered an editor for its type opens the same picker the add menu
            // uses. Without one — an unparsed trigger, or a type registered without an editor —
            // nothing here knows what would satisfy it, so there is no picker to open. The row
            // stays visible and stays in the file; it just cannot be edited here.
            default -> {
                if (!openAddonEditorFor(t.type())) {
                    EditorToastHandler.show(EditorToast.Level.INFO,
                            Component.translatable("editor.historystages.auto_trigger.unknown.title"),
                            Component.translatable("editor.historystages.auto_trigger.unknown.message", t.type()));
                }
            }
        }
    }

    private void openPickerFor(TriggerType type) {
        switch (type) {
            case BIOME -> showAbstract(new SearchableBiomeList(id -> placeTrigger(new BiomeTrigger(id))), null, false);
            case STRUCTURE -> showAbstract(new SearchableStructureList(id -> placeTrigger(new StructureTrigger(id))), TriggerType.STRUCTURE, true);
            case DIMENSION -> showAbstract(new SearchableDimensionList(id -> placeTrigger(new DimensionTrigger(id))), TriggerType.DIMENSION, true);
            case ITEM -> showItem(new SearchableItemList(id -> placeTrigger(new ItemTrigger(id))));
            case ENTITY -> showEntity(new SearchableEntityList(this::openSubmodeChooser));
            case BLOCK_PLACE -> showItem(new SearchableItemList(id -> placeTrigger(new BlockPlaceTrigger(id))));
            case BLOCK_BREAK -> showItem(new SearchableItemList(id -> placeTrigger(new BlockBreakTrigger(id))));
            case ADVANCEMENT -> showAbstract(new SearchableAdvancementList(id -> placeTrigger(new AdvancementTrigger(id))), null, true);
            case PLAYTIME -> openCountDialog(-1,
                    Component.translatable("editor.historystages.auto_trigger.playtime.input_title"),
                    "", 1, 1, 999999,
                    days -> placeTrigger(new PlaytimeTrigger(days)));
            case STAT -> openStatCategoryChooser(-1);
            case XP_LEVEL -> openCountDialog(-1,
                    Component.translatable("editor.historystages.auto_trigger.xp_level.input_title"),
                    "", 1, 0, 10000, level -> placeTrigger(new XpLevelTrigger(level)));
            case EFFECT -> showAbstract(
                    new SearchableEffectList(id -> placeTrigger(new EffectTrigger(id))), null, true);
            case WEATHER -> openWeatherChooser();
            case DAY_COUNT -> openCountDialog(-1,
                    Component.translatable("editor.historystages.auto_trigger.day_count.input_title"),
                    "", 1, 0, 999999, days -> placeTrigger(new DayCountTrigger(days)));
            case WORLD_TIME -> openTimePresetChooser(-1, 0, 23999);
        }
    }

    private void showAbstract(AbstractSearchableList<String> list, TriggerType type, boolean multi) {
        list.setMultiSelect(multi);
        // Wire the "Hide stage-locked" filter for trigger types that have a meaningful
        // lock source on the stage (dimension / structure / biome). Advancements have no
        // direct lock list, so we don't add a filter there.
        StageEntry stage = lockSnapshot == null ? null : lockSnapshot.get();
        if (stage != null && type != null) {
            String label = Component.translatable("editor.historystages.auto_trigger.filter.hide_locked").getString();
            switch (type) {
                case DIMENSION -> list.addLockedFilter(label, StageLockFilter.forDimensions(stage));
                case STRUCTURE -> list.addLockedFilter(label, StageLockFilter.forStructures(stage));
                case BIOME -> list.addLockedFilter(label, StageLockFilter.forBiomes(stage));
                default -> {}
            }
        }
        list.show(this.width / 2, this.height / 2, this.width);
        currentList = list;
    }

    private void showItem(SearchableItemList list) {
        showItem(list, true);
    }

    /**
     * Single-select when the picked id is only the first half of the answer — the statistic flow
     * asks for a count afterwards, and a multi-select list would have nothing to ask it about.
     */
    private void showItem(SearchableItemList list, boolean multi) {
        list.setMultiSelect(multi);
        StageEntry stage = lockSnapshot == null ? null : lockSnapshot.get();
        if (stage != null) {
            list.setLockedFilter(
                    Component.translatable("editor.historystages.auto_trigger.filter.hide_locked").getString(),
                    StageLockFilter.forItems(stage));
        }
        list.show(this.width / 2, this.height / 2, this.width);
        currentList = list;
    }

    private void showEntity(SearchableEntityList list) {
        StageEntry stage = lockSnapshot == null ? null : lockSnapshot.get();
        if (stage != null) {
            list.setLockedFilter(
                    Component.translatable("editor.historystages.auto_trigger.filter.hide_locked").getString(),
                    StageLockFilter.forEntities(stage));
        }
        list.show(this.width / 2, this.height / 2, this.width);
        currentList = list;
    }

    /**
     * Opens the shared count dialog on top of this screen.
     *
     * <p>{@code idx} is the row being replaced, or -1 when adding. It is re-applied inside the
     * callback rather than read from the field: the dialog is a separate screen, and by the time it
     * confirms, this one has been through {@code init()} again.
     */
    private void openCountDialog(int idx, Component title, String subject, int initial,
                                 int min, int max, IntConsumer onDone) {
        this.minecraft.setScreen(new CountInputScreen(this, title, subject, initial, min, max,
                value -> {
                    editIndex = idx;
                    onDone.accept(value);
                }));
    }

    /** Shows a choice list through the same overlay slot the searchable pickers use. */
    private void showChoices(String title, List<ChoiceOverlay.Option> options) {
        ChoiceOverlay overlay = new ChoiceOverlay(title, options);
        overlay.show(this.width / 2, this.height / 2, this.width);
        currentList = overlay;
    }

    /** The entity sub-mode, asked after the mob was picked. */
    private void openSubmodeChooser(String entityId) {
        showChoices(
                Component.translatable("editor.historystages.auto_trigger.entity.submode_label").getString(),
                List.of(
                        ChoiceOverlay.Option.of(
                                Component.translatable("editor.historystages.auto_trigger.entity.kill").getString(),
                                () -> placeTrigger(new EntityTrigger(entityId, EntitySubMode.KILL.serialize()))),
                        ChoiceOverlay.Option.of(
                                Component.translatable("editor.historystages.auto_trigger.entity.interact").getString(),
                                () -> placeTrigger(new EntityTrigger(entityId, EntitySubMode.INTERACT.serialize())))));
    }

    private void openWeatherChooser() {
        List<ChoiceOverlay.Option> options = new ArrayList<>();
        for (WeatherState state : WeatherState.values()) {
            options.add(ChoiceOverlay.Option.of(
                    Component.translatable("editor.historystages.auto_trigger.weather."
                            + state.serialize()).getString(),
                    () -> placeTrigger(new WeatherTrigger(state.serialize()))));
        }
        showChoices(
                Component.translatable("editor.historystages.auto_trigger.weather.label").getString(),
                options);
    }

    /**
     * The five time-of-day rows. {@code idx} is captured rather than read from {@link #editIndex}
     * later, because the custom window opens a screen of its own and this one re-inits on the way
     * back.
     */
    private void openTimePresetChooser(int idx, int initialFrom, int initialTo) {
        List<ChoiceOverlay.Option> options = new ArrayList<>();
        for (TimePreset preset : TimePreset.values()) {
            String label = Component.translatable(
                    "editor.historystages.auto_trigger.world_time." + preset.serialize()).getString();
            options.add(preset == TimePreset.CUSTOM
                    ? ChoiceOverlay.Option.more(label, () -> openTimeWindowDialog(idx, initialFrom, initialTo))
                    : ChoiceOverlay.Option.of(label, () -> {
                        editIndex = idx;
                        placeTrigger(TimeOfDayTrigger.of(preset));
                    }));
        }
        showChoices(
                Component.translatable("editor.historystages.auto_trigger.world_time.label").getString(),
                options);
    }

    private void openTimeWindowDialog(int idx, int initialFrom, int initialTo) {
        this.minecraft.setScreen(new TimeWindowScreen(this,
                Component.translatable("editor.historystages.auto_trigger.world_time.window_title"),
                initialFrom, initialTo,
                (from, to) -> {
                    editIndex = idx;
                    placeTrigger(TimeOfDayTrigger.custom(from, to));
                }));
    }

    /** Step one of the statistic flow: which of the nine statistic types. */
    private void openStatCategoryChooser(int idx) {
        List<ChoiceOverlay.Option> options = new ArrayList<>();
        for (StatCategory category : StatCategory.values()) {
            options.add(ChoiceOverlay.Option.more(
                    Component.translatable("editor.historystages.auto_trigger.stat.category."
                            + category.serialize()).getString(),
                    () -> openStatIdPicker(idx, category)));
        }
        showChoices(
                Component.translatable("editor.historystages.auto_trigger.stat.category_label").getString(),
                options);
    }

    /**
     * Step two: which id, from whichever registry the category counts.
     *
     * <p>{@code MINED} picks from the item list, the same compromise the block_place and
     * block_break triggers already make: a block's item shares its id, and a block without one
     * cannot be mined by hand anyway.
     */
    private void openStatIdPicker(int idx, StatCategory category) {
        switch (category) {
            case CUSTOM -> showAbstract(
                    new SearchableStatList(id -> openStatCountDialog(idx, category, id)), null, false);
            case KILLED, KILLED_BY -> showEntity(
                    new SearchableEntityList(id -> openStatCountDialog(idx, category, id)));
            default -> showItem(
                    new SearchableItemList(id -> openStatCountDialog(idx, category, id)), false);
        }
    }

    /** Step three: how many. */
    private void openStatCountDialog(int idx, StatCategory category, String id) {
        currentList = null;
        openCountDialog(idx,
                Component.translatable("editor.historystages.auto_trigger.stat.count_title"),
                id, 1, 1, 999999,
                count -> placeTrigger(new StatTrigger(category.serialize(), id, count)));
    }

    /** Insert (or replace at {@code editIndex}) the given trigger and notify the parent. */
    private void placeTrigger(TriggerCondition t) {
        if (editIndex >= 0 && editIndex < trigger.getTriggers().size()) {
            trigger.getTriggers().set(editIndex, t);
        } else {
            trigger.getTriggers().add(t);
        }
        editIndex = -1;
        currentList = null;
        notifyChanged();
    }

    /**
     * The breathing "Unsaved" marker every other editor screen shows.
     *
     * <p>Changes here reach the parent immediately, so nothing is at risk — but this was the only
     * editor of nine where you had to go back a screen to find out that something was pending.
     */
    private void renderUnsavedMarker(GuiGraphics g) {
        if (!hasChanges) return;
        float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS)
                / Timing.BREATHE_PERIOD_MS;
        int dotAlpha = (int) ((0.35f + 0.45f * Ease.breathe(phase)) * 255);
        String label = Component.translatable("editor.historystages.unsaved").getString();
        int labelW = this.font.width(label);
        // Right-aligned against the Save button's left edge, not the screen's. Against the screen's
        // it drew straight over the button, because this footer's button is 90px wide where the
        // screens this was copied from use 50.
        int labelX = saveBtnX - 8 - labelW;
        g.fill(labelX - 8, this.height - 17, labelX - 2, this.height - 11,
                (dotAlpha << 24) | 0xFFCC00);
        g.drawString(this.font, label, labelX, this.height - 18, 0xFFCC00, false);
    }

    private void notifyChanged() {
        hasChanges = true;
        if (onChanged != null) onChanged.accept(trigger);
        applyTriggerFilter();
    }

    /** Hands the trigger up and persists the whole stage, staying on this screen. */
    private void saveAndStay() {
        notifyChanged();
        if (onPersist != null) onPersist.run();
        hasChanges = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /**
     * True when a modal picker is up (Searchable widget or the choice list). Used to gate the dim
     * layer and suppress chrome that would otherwise show through the overlay.
     */
    private boolean isOverlayActive() {
        return currentList != null && currentList.isVisible();
    }

    /**
     * True when an overlay (add-dropdown, Searchable widget, choice list, context menu, or the
     * list search bar's own filter dropdown) is intercepting input. Used to suppress trigger-row
     * hover lighting that would otherwise bleed through the overlay.
     */
    private boolean isInputBlocked() {
        if (addDropdownOpen) return true;
        if (currentList != null && currentList.isVisible()) return true;
        if (contextMenu.isVisible()) return true;
        if (listSearchBar != null && listSearchBar.filters().isExpanded()) return true;
        return false;
    }

    /**
     * Renders a tooltip box in the editor's own style (dark bg, gold top accent, subtle border)
     * instead of the vanilla {@code renderTooltip} look. Supports {@code \n} line breaks.
     */
    private void drawEditorTooltip(GuiGraphics g, String text, int mx, int my) {
        String[] lines = text.split("\n");
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, this.font.width(l));

        int pad = 5;
        int lineH = this.font.lineHeight + 1;
        int boxW = maxW + pad * 2;
        int boxH = lines.length * lineH + pad * 2 - 1;

        int bx = mx + 10;
        int by = my - 4;
        if (bx + boxW > this.width - 4) bx = this.width - 4 - boxW;
        if (by + boxH > this.height - 4) by = this.height - 4 - boxH;
        if (by < 4) by = 4;

        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        // Border + background
        g.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, 0xFF555555);
        g.fill(bx, by, bx + boxW, by + boxH, 0xFF1A1A1A);
        // Gold top accent
        g.fill(bx, by, bx + boxW, by + 1, 0xFFFFCC00);

        for (int i = 0; i < lines.length; i++) {
            g.drawString(this.font, lines[i], bx + pad, by + pad + i * lineH, 0xFFDDDDDD, false);
        }
        g.pose().popPose();
    }

    private static String combineLabel(CombineMode m) {
        return Component.translatable(
                m == CombineMode.ANY
                        ? "editor.historystages.auto_trigger.combine.any"
                        : "editor.historystages.auto_trigger.combine.all"
        ).getString();
    }

    /** Trigger types in the order they appear in the add dropdown. */
    private enum TriggerType {
        BIOME("biome"),
        STRUCTURE("structure"),
        DIMENSION("dimension"),
        ITEM("item"),
        ENTITY("entity"),
        BLOCK_PLACE("block_place"),
        BLOCK_BREAK("block_break"),
        ADVANCEMENT("advancement"),
        PLAYTIME("playtime"),
        STAT("stat"),
        XP_LEVEL("xp_level"),
        EFFECT("effect"),
        WEATHER("weather"),
        DAY_COUNT("day_count"),
        WORLD_TIME("world_time");

        final String id;
        TriggerType(String id) { this.id = id; }
    }
}
