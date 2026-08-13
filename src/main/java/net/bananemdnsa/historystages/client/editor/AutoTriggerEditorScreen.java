package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.widget.list.AbstractSearchableList;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.client.editor.widget.EntityPreviewRenderer;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableAdvancementList;
import net.bananemdnsa.historystages.client.editor.widget.SearchBar;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableBiomeList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableDimensionList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableEntityList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemList;
import net.bananemdnsa.historystages.client.editor.widget.StageLockFilter;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableStructureList;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.CombineMode;
import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntitySubMode;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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

    private static final TriggerType[] TYPES = TriggerType.values();

    private final Screen parent;
    private final AutoTrigger trigger;
    private final Consumer<AutoTrigger> onChanged;
    /** Live snapshot of the parent stage's lock data, sampled per picker open. May be null. */
    private final Supplier<StageEntry> lockSnapshot;

    // Layout
    private int listX, listY, listW, listH;
    private int pillX, pillY, pillAnyW, pillAllW;
    private int addBtnX, addBtnY, addBtnW;
    private int searchY;
    private int scrollOffset = 0;
    /** Sub-pixel scroll chasing {@link #scrollOffset}; render and the click paths both read it. */
    private final Anim smoothScroll = new Anim();
    private final Anim addBtnHover = new Anim();
    /** Hover progress of the soft buttons, keyed by their left edge — stable per button. */
    private final java.util.Map<Integer, Anim> softBtnHover = new java.util.HashMap<>();
    private final Anim listThumbHover = new Anim();
    private boolean draggingScrollbar = false;

    // List search / type filter
    private SearchBar listSearchBar;
    /** Indices into {@link AutoTrigger#getTriggers()} that pass the current search + type filter. */
    private final List<Integer> visibleIndices = new ArrayList<>();

    // Inline overlays
    private boolean addDropdownOpen = false;
    private OverlayHandler currentList = null;
    private int editIndex = -1;             // -1 = adding, >=0 = replacing at this row
    private String pendingEntityId = null;  // entity picked, waiting for sub-mode choice

    // Playtime dialog state
    private boolean playtimeDialogOpen = false;
    private int playtimeEditIndex = -1;
    private EditBox playtimeField;

    // Context menu
    private ContextMenu contextMenu = new ContextMenu();

    public AutoTriggerEditorScreen(Screen parent, AutoTrigger trigger, Consumer<AutoTrigger> onChanged) {
        this(parent, trigger, onChanged, null);
    }

    public AutoTriggerEditorScreen(Screen parent, AutoTrigger trigger,
                                   Consumer<AutoTrigger> onChanged,
                                   Supplier<StageEntry> lockSnapshot) {
        super(Component.translatable("editor.historystages.auto_trigger.title"));
        this.parent = parent;
        this.trigger = trigger;
        this.onChanged = onChanged;
        this.lockSnapshot = lockSnapshot;
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

        // List search bar - text matches against trigger value text; filter dropdown lets the
        // user narrow to a single trigger type. Filter options share the "type" mutex group so
        // picking one auto-deselects the others (none active = show all types).
        if (listSearchBar == null) {
            listSearchBar = new SearchBar(
                    Component.translatable("editor.historystages.auto_trigger.search").getString())
                    .setLightStyle(true);
            // Bar is part of the persistent editor chrome - start unfocused so it doesn't
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
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> this.minecraft.setScreen(parent),
                this.width - 110, this.height - 25, 90, 20));
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.cancel"),
                btn -> this.minecraft.setScreen(parent),
                20, this.height - 25, 90, 20));

        // Playtime field (re-positioned each time the dialog opens; visible flag controls display)
        playtimeField = new EditBox(this.font, this.width / 2 - 60, this.height / 2 - 5, 120, 18,
                Component.translatable("editor.historystages.auto_trigger.playtime.input_title"));
        playtimeField.setMaxLength(6);
        playtimeField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        playtimeField.visible = false;
    }

    // =============================================
    // Rendering
    // =============================================

    @Override
    public void renderBackground(GuiGraphics g) {
        // No-op - own background in render()
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);

        smoothScroll.approach(scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle(scrollOffset, 0.5f);

        // Header
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        g.fill(10, 18, this.width - 10, 19, 0xFF555555);

        renderCombinePill(g, mx, my);
        renderAddButton(g, mx, my);
        // The list search bar renders its text at z=300 internally - drawing it while a
        // modal overlay is active would let the placeholder + cursor bleed through the
        // dim layer. Suppress while any overlay/dialog is up.
        if (!isOverlayActive()) renderListSearchBar(g, mx, my);
        renderList(g, mx, my);

        super.render(g, mx, my, pt);

        // Tooltip for pill toggle (drawn here so it sits above the screen background but
        // below any overlays; the actual tooltip box is rendered with editor styling)
        if (isOver(mx, my, pillX, pillY, pillAnyW + pillAllW, 14)
                && currentList == null && !addDropdownOpen
                && pendingEntityId == null && !playtimeDialogOpen
                && !contextMenu.isVisible()) {
            drawEditorTooltip(g,
                    Component.translatable("editor.historystages.auto_trigger.combine.tooltip").getString(),
                    mx, my);
        }

        // Add-dropdown popup
        if (addDropdownOpen) renderAddDropdown(g, mx, my);

        // Dim the rest of the screen whenever a modal overlay is active (Searchable widget,
        // sub-mode chooser, or playtime dialog). Without this the editor's title and the
        // empty-state text bleed through around the overlay's panel.
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

        // Entity sub-mode chooser
        if (pendingEntityId != null) renderSubmodeChooser(g, mx, my);

        // Playtime dialog
        if (playtimeDialogOpen) renderPlaytimeDialog(g, mx, my);

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
        boolean hov = isOver(mx, my, addBtnX, addBtnY, addBtnW, 18);
        float hp = Ease.outCubic(addBtnHover.ramp(hov, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        g.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + 18, Fade.mix(0x25FFFFFF, 0x40FFFFFF, hp));
        g.fill(addBtnX, addBtnY + 17, addBtnX + addBtnW, addBtnY + 18, Fade.mix(0x60FFCC00, 0xFFFFCC00, hp));
        String label = Component.translatable("editor.historystages.auto_trigger.add").getString();
        g.drawString(this.font, label, addBtnX + 6, addBtnY + 5, 0xFFFFFFFF, false);
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

        // Scrollbar - sized from visible (filtered) row count
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
                String value = triggerValueText(t).toLowerCase();
                String typeName = Component.translatable(triggerTypeKey(t)).getString().toLowerCase();
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
        String typeName = Component.translatable(triggerTypeKey(t)).getString();
        g.drawString(this.font, typeName, iconX + ICON_W + 4, top + 7, 0xFFCCCCCC, false);

        // Value
        String value = triggerValueText(t);
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
        if (t instanceof ItemTrigger it) return resolveItem(it.id());
        if (t instanceof BlockPlaceTrigger b) return resolveBlock(b.id());
        if (t instanceof BlockBreakTrigger b) return resolveBlock(b.id());
        return ItemStack.EMPTY;
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

    private static String triggerTypeKey(TriggerCondition t) {
        return "editor.historystages.auto_trigger.type." + t.type();
    }

    private String triggerValueText(TriggerCondition t) {
        if (t instanceof BiomeTrigger b) return b.id();
        if (t instanceof StructureTrigger s) return s.id();
        if (t instanceof DimensionTrigger d) return d.id();
        if (t instanceof ItemTrigger i) return i.id();
        if (t instanceof EntityTrigger e) {
            return e.id() + " ("
                    + Component.translatable("editor.historystages.auto_trigger.entity." + e.resolvedSubMode().serialize()).getString()
                    + ")";
        }
        if (t instanceof BlockPlaceTrigger bp) return bp.id();
        if (t instanceof BlockBreakTrigger bb) return bb.id();
        if (t instanceof AdvancementTrigger a) return a.id();
        if (t instanceof PlaytimeTrigger p) {
            return Component.translatable("editor.historystages.auto_trigger.playtime.days", p.days()).getString();
        }
        return "";
    }

    private void renderAddDropdown(GuiGraphics g, int mx, int my) {
        int rowH = 16;
        int pw = addBtnW;
        int ph = TYPES.length * rowH;
        int px = addBtnX;
        int py = addBtnY + 18 + 1;

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF555555);
        g.fill(px, py, px + pw, py + ph, 0xFF1A1A1A);
        for (int i = 0; i < TYPES.length; i++) {
            int rowY = py + i * rowH;
            boolean hov = isOver(mx, my, px, rowY, pw, rowH);
            if (hov) g.fill(px, rowY, px + pw, rowY + rowH, 0x40FFCC00);
            g.drawString(this.font,
                    Component.translatable("editor.historystages.auto_trigger.type." + TYPES[i].id).getString(),
                    px + 6, rowY + 4, hov ? 0xFFFFFF : 0xCCCCCC, false);
        }
        g.pose().popPose();
    }

    private void renderSubmodeChooser(GuiGraphics g, int mx, int my) {
        int boxW = 200;
        int boxH = 80;
        int bx = (this.width - boxW) / 2;
        int by = (this.height - boxH) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        g.fill(0, 0, this.width, this.height, 0xA0000000);
        g.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, 0xFF555555);
        g.fill(bx, by, bx + boxW, by + boxH, 0xFF1A1A1A);
        g.drawCenteredString(this.font,
                Component.translatable("editor.historystages.auto_trigger.entity.submode_label").getString(),
                bx + boxW / 2, by + 10, 0xFFFFFF);

        int btnY = by + 30;
        int btnW = 80;
        for (int i = 0; i < 2; i++) {
            EntitySubMode m = i == 0 ? EntitySubMode.KILL : EntitySubMode.INTERACT;
            int btnX = bx + 10 + i * (btnW + 10);
            boolean hov = isOver(mx, my, btnX, btnY, btnW, 20);
            float hp = Ease.outCubic(softBtnHover.computeIfAbsent(btnX, k -> new Anim())
                    .ramp(hov, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            g.fill(btnX, btnY, btnX + btnW, btnY + 20, Fade.mix(0x25FFFFFF, 0x40FFFFFF, hp));
            g.fill(btnX, btnY + 19, btnX + btnW, btnY + 20, Fade.mix(0x60FFCC00, 0xFFFFCC00, hp));
            String label = Component.translatable("editor.historystages.auto_trigger.entity." + m.serialize()).getString();
            g.drawString(this.font, label, btnX + (btnW - this.font.width(label)) / 2, btnY + 6,
                    0xFFFFFFFF, false);
        }
        g.pose().popPose();
    }

    private void renderPlaytimeDialog(GuiGraphics g, int mx, int my) {
        int boxW = 200;
        int boxH = 90;
        int bx = (this.width - boxW) / 2;
        int by = (this.height - boxH) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        g.fill(0, 0, this.width, this.height, 0xA0000000);
        g.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, 0xFF555555);
        g.fill(bx, by, bx + boxW, by + boxH, 0xFF1A1A1A);
        g.drawCenteredString(this.font,
                Component.translatable("editor.historystages.auto_trigger.playtime.input_title").getString(),
                bx + boxW / 2, by + 10, 0xFFFFFF);

        playtimeField.setPosition(bx + 40, by + 30);
        playtimeField.setWidth(120);
        playtimeField.visible = true;
        playtimeField.render(g, mx, my, 0);

        // Confirm / Cancel buttons
        int btnY = by + 60;
        int btnW = 80;
        int okX = bx + 10;
        int cancelX = bx + boxW - 10 - btnW;
        drawDialogButton(g, mx, my, okX, btnY, btnW,
                Component.translatable("editor.historystages.confirm").getString());
        drawDialogButton(g, mx, my, cancelX, btnY, btnW,
                Component.translatable("editor.historystages.cancel").getString());
        g.pose().popPose();
    }

    private void drawDialogButton(GuiGraphics g, int mx, int my, int x, int y, int w, String label) {
        boolean hov = isOver(mx, my, x, y, w, 20);
        float hp = Ease.outCubic(softBtnHover.computeIfAbsent(x, k -> new Anim())
                .ramp(hov, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        g.fill(x, y, x + w, y + 20, Fade.mix(0x25FFFFFF, 0x40FFFFFF, hp));
        g.fill(x, y + 19, x + w, y + 20, Fade.mix(0x60FFCC00, 0xFFFFCC00, hp));
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + 6, 0xFFFFFF, false);
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
        // Playtime dialog
        if (playtimeDialogOpen) {
            return handlePlaytimeDialogClick(mouseX, mouseY, button);
        }
        // Sub-mode chooser
        if (pendingEntityId != null) {
            return handleSubmodeClick(mouseX, mouseY, button);
        }
        // Searchable list overlay
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
        if (button == 0 && isOver((int) mouseX, (int) mouseY, addBtnX, addBtnY, addBtnW, 18)) {
            addDropdownOpen = true;
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

    private boolean handleAddDropdownClick(double mouseX, double mouseY, int button) {
        int rowH = 16;
        int pw = addBtnW;
        int px = addBtnX;
        int py = addBtnY + 18 + 1;
        for (int i = 0; i < TYPES.length; i++) {
            int rowY = py + i * rowH;
            if (button == 0 && isOver((int) mouseX, (int) mouseY, px, rowY, pw, rowH)) {
                addDropdownOpen = false;
                editIndex = -1;
                openPickerFor(TYPES[i]);
                return true;
            }
        }
        addDropdownOpen = false;
        return true;
    }

    private boolean handleSubmodeClick(double mouseX, double mouseY, int button) {
        int boxW = 200;
        int boxH = 80;
        int bx = (this.width - boxW) / 2;
        int by = (this.height - boxH) / 2;
        int btnY = by + 30;
        int btnW = 80;
        if (button == 0) {
            for (int i = 0; i < 2; i++) {
                int btnX = bx + 10 + i * (btnW + 10);
                if (isOver((int) mouseX, (int) mouseY, btnX, btnY, btnW, 20)) {
                    EntitySubMode m = i == 0 ? EntitySubMode.KILL : EntitySubMode.INTERACT;
                    placeTrigger(new EntityTrigger(pendingEntityId, m.serialize()));
                    pendingEntityId = null;
                    return true;
                }
            }
            // click outside dialog box -> cancel
            if (!isOver((int) mouseX, (int) mouseY, bx, by, boxW, boxH)) {
                pendingEntityId = null;
                return true;
            }
        }
        return true;
    }

    private boolean handlePlaytimeDialogClick(double mouseX, double mouseY, int button) {
        if (playtimeField.mouseClicked(mouseX, mouseY, button)) return true;

        int boxW = 200;
        int boxH = 90;
        int bx = (this.width - boxW) / 2;
        int by = (this.height - boxH) / 2;
        int btnY = by + 60;
        int btnW = 80;
        int okX = bx + 10;
        int cancelX = bx + boxW - 10 - btnW;
        if (button == 0 && isOver((int) mouseX, (int) mouseY, okX, btnY, btnW, 20)) {
            confirmPlaytimeDialog();
            return true;
        }
        if (button == 0 && isOver((int) mouseX, (int) mouseY, cancelX, btnY, btnW, 20)) {
            closePlaytimeDialog();
            return true;
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentList != null && currentList.isVisible()
                && currentList.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int contentH = visibleIndices.size() * ROW_H + 8;
            int maxScroll = Math.max(0, contentH - listH);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta * 10));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
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
        if (currentList != null && currentList.isVisible() && currentList.mouseReleased()) return true;
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (playtimeDialogOpen) {
            if (keyCode == 256) { closePlaytimeDialog(); return true; }
            if (keyCode == 257) { confirmPlaytimeDialog(); return true; }
            return playtimeField.keyPressed(keyCode, scanCode, modifiers)
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (currentList != null && currentList.isVisible()) {
            if (currentList.keyPressed(keyCode)) return true;
        }
        if (listSearchBar != null && listSearchBar.keyPressed(keyCode)) return true;
        if (keyCode == 256) {
            if (addDropdownOpen) { addDropdownOpen = false; return true; }
            if (pendingEntityId != null) { pendingEntityId = null; return true; }
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (playtimeDialogOpen) {
            return playtimeField.charTyped(c, modifiers) || super.charTyped(c, modifiers);
        }
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
        if (t instanceof BiomeTrigger) {
            showAbstract(new SearchableBiomeList(id -> placeTrigger(new BiomeTrigger(id))), null, false);
        } else if (t instanceof StructureTrigger) {
            showAbstract(new SearchableStructureList(id -> placeTrigger(new StructureTrigger(id))), TriggerType.STRUCTURE, true);
        } else if (t instanceof DimensionTrigger) {
            showAbstract(new SearchableDimensionList(id -> placeTrigger(new DimensionTrigger(id))), TriggerType.DIMENSION, true);
        } else if (t instanceof ItemTrigger) {
            showItem(new SearchableItemList(id -> placeTrigger(new ItemTrigger(id))));
        } else if (t instanceof BlockPlaceTrigger) {
            showItem(new SearchableItemList(id -> placeTrigger(new BlockPlaceTrigger(id))));
        } else if (t instanceof BlockBreakTrigger) {
            showItem(new SearchableItemList(id -> placeTrigger(new BlockBreakTrigger(id))));
        } else if (t instanceof AdvancementTrigger) {
            showAbstract(new SearchableAdvancementList(id -> placeTrigger(new AdvancementTrigger(id))), null, true);
        } else if (t instanceof EntityTrigger) {
            showEntity(new SearchableEntityList(id -> pendingEntityId = id));
        } else if (t instanceof PlaytimeTrigger p) {
            openPlaytimeDialog(idx, p.days());
        }
    }

    private void openPickerFor(TriggerType type) {
        switch (type) {
            case BIOME -> showAbstract(new SearchableBiomeList(id -> placeTrigger(new BiomeTrigger(id))), null, false);
            case STRUCTURE -> showAbstract(new SearchableStructureList(id -> placeTrigger(new StructureTrigger(id))), TriggerType.STRUCTURE, true);
            case DIMENSION -> showAbstract(new SearchableDimensionList(id -> placeTrigger(new DimensionTrigger(id))), TriggerType.DIMENSION, true);
            case ITEM -> showItem(new SearchableItemList(id -> placeTrigger(new ItemTrigger(id))));
            case ENTITY -> showEntity(new SearchableEntityList(id -> pendingEntityId = id));
            case BLOCK_PLACE -> showItem(new SearchableItemList(id -> placeTrigger(new BlockPlaceTrigger(id))));
            case BLOCK_BREAK -> showItem(new SearchableItemList(id -> placeTrigger(new BlockBreakTrigger(id))));
            case ADVANCEMENT -> showAbstract(new SearchableAdvancementList(id -> placeTrigger(new AdvancementTrigger(id))), null, true);
            case PLAYTIME -> openPlaytimeDialog(-1, 1);
        }
    }

    private void showAbstract(AbstractSearchableList<String> list, TriggerType type, boolean multi) {
        list.setMultiSelect(multi);
        // Wire the "Hide stage-locked" filter for trigger types that have a meaningful
        // lock source on the stage (dimension / structure). Biome and advancement have
        // no direct lock list, so we don't add a filter there.
        StageEntry stage = lockSnapshot == null ? null : lockSnapshot.get();
        if (stage != null && type != null) {
            String label = Component.translatable("editor.historystages.auto_trigger.filter.hide_locked").getString();
            switch (type) {
                case DIMENSION -> list.addLockedFilter(label, StageLockFilter.forDimensions(stage));
                case STRUCTURE -> list.addLockedFilter(label, StageLockFilter.forStructures(stage));
                default -> {}
            }
        }
        list.show(this.width / 2, this.height / 2, this.width);
        currentList = wrap(list);
    }

    private void showItem(SearchableItemList list) {
        list.setMultiSelect(true);
        StageEntry stage = lockSnapshot == null ? null : lockSnapshot.get();
        if (stage != null) {
            list.setLockedFilter(
                    Component.translatable("editor.historystages.auto_trigger.filter.hide_locked").getString(),
                    StageLockFilter.forItems(stage));
        }
        list.show(this.width / 2, this.height / 2, this.width);
        currentList = wrap(list);
    }

    private void showEntity(SearchableEntityList list) {
        StageEntry stage = lockSnapshot == null ? null : lockSnapshot.get();
        if (stage != null) {
            list.setLockedFilter(
                    Component.translatable("editor.historystages.auto_trigger.filter.hide_locked").getString(),
                    StageLockFilter.forEntities(stage));
        }
        list.show(this.width / 2, this.height / 2, this.width);
        currentList = wrap(list);
    }

    // Adapter interface so the screen can hold any of the Searchable overlays uniformly.
    private interface OverlayHandler {
        void render(GuiGraphics g, Font font, int mx, int my);
        boolean isVisible();
        boolean mouseClicked(double mx, double my);
        boolean mouseScrolled(double mx, double my, double delta);
        boolean mouseDragged(double mx, double my);
        boolean mouseReleased();
        boolean keyPressed(int keyCode);
        boolean charTyped(char c);
    }

    private static OverlayHandler wrap(AbstractSearchableList<?> l) {
        return new OverlayHandler() {
            @Override public void render(GuiGraphics g, Font f, int mx, int my) { l.render(g, f, mx, my); }
            @Override public boolean isVisible() { return l.isVisible(); }
            @Override public boolean mouseClicked(double mx, double my) { return l.mouseClicked(mx, my); }
            @Override public boolean mouseScrolled(double mx, double my, double delta) { return l.mouseScrolled(mx, my, delta); }
            @Override public boolean mouseDragged(double mx, double my) { return l.mouseDragged(mx, my); }
            @Override public boolean mouseReleased() { return l.mouseReleased(); }
            @Override public boolean keyPressed(int k) { return l.keyPressed(k); }
            @Override public boolean charTyped(char c) { return l.charTyped(c); }
        };
    }

    private static OverlayHandler wrap(SearchableItemList l) {
        return new OverlayHandler() {
            @Override public void render(GuiGraphics g, Font f, int mx, int my) { l.render(g, f, mx, my); }
            @Override public boolean isVisible() { return l.isVisible(); }
            @Override public boolean mouseClicked(double mx, double my) { return l.mouseClicked(mx, my); }
            @Override public boolean mouseScrolled(double mx, double my, double delta) { return l.mouseScrolled(mx, my, delta); }
            @Override public boolean mouseDragged(double mx, double my) { return l.mouseDragged(mx, my); }
            @Override public boolean mouseReleased() { return l.mouseReleased(); }
            @Override public boolean keyPressed(int k) { return l.keyPressed(k); }
            @Override public boolean charTyped(char c) { return l.charTyped(c); }
        };
    }

    private static OverlayHandler wrap(SearchableEntityList l) {
        return new OverlayHandler() {
            @Override public void render(GuiGraphics g, Font f, int mx, int my) { l.render(g, f, mx, my); }
            @Override public boolean isVisible() { return l.isVisible(); }
            @Override public boolean mouseClicked(double mx, double my) { return l.mouseClicked(mx, my); }
            @Override public boolean mouseScrolled(double mx, double my, double delta) { return l.mouseScrolled(mx, my, delta); }
            @Override public boolean mouseDragged(double mx, double my) { return l.mouseDragged(mx, my); }
            @Override public boolean mouseReleased() { return l.mouseReleased(); }
            @Override public boolean keyPressed(int k) { return l.keyPressed(k); }
            @Override public boolean charTyped(char c) { return l.charTyped(c); }
        };
    }

    private void openPlaytimeDialog(int idx, int initial) {
        playtimeEditIndex = idx;
        playtimeDialogOpen = true;
        playtimeField.setValue(String.valueOf(Math.max(1, initial)));
        playtimeField.setFocused(true);
        setInitialFocus(playtimeField);
    }

    private void closePlaytimeDialog() {
        playtimeDialogOpen = false;
        playtimeField.visible = false;
        playtimeField.setFocused(false);
    }

    private void confirmPlaytimeDialog() {
        String v = playtimeField.getValue().trim();
        int days;
        try { days = Math.max(1, Integer.parseInt(v)); }
        catch (NumberFormatException nfe) { return; }
        editIndex = playtimeEditIndex;
        placeTrigger(new PlaytimeTrigger(days));
        closePlaytimeDialog();
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

    private void notifyChanged() {
        if (onChanged != null) onChanged.accept(trigger);
        applyTriggerFilter();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /**
     * True when a modal picker / dialog is up (Searchable widget, sub-mode chooser, or
     * playtime dialog). Used to gate the dim layer and suppress chrome that would
     * otherwise show through the overlay.
     */
    private boolean isOverlayActive() {
        return (currentList != null && currentList.isVisible())
                || pendingEntityId != null
                || playtimeDialogOpen;
    }

    /**
     * True when an overlay (add-dropdown, Searchable widget, sub-mode chooser, playtime
     * dialog, context menu, or the list search bar's own filter dropdown) is intercepting
     * input. Used to suppress trigger-row hover lighting that would otherwise bleed
     * through the overlay.
     */
    private boolean isInputBlocked() {
        if (addDropdownOpen) return true;
        if (currentList != null && currentList.isVisible()) return true;
        if (pendingEntityId != null) return true;
        if (playtimeDialogOpen) return true;
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

    /** Trigger types in the order they appear in the [+ Add] dropdown. */
    private enum TriggerType {
        BIOME("biome"),
        STRUCTURE("structure"),
        DIMENSION("dimension"),
        ITEM("item"),
        ENTITY("entity"),
        BLOCK_PLACE("block_place"),
        BLOCK_BREAK("block_break"),
        ADVANCEMENT("advancement"),
        PLAYTIME("playtime");

        final String id;
        TriggerType(String id) { this.id = id; }
    }
}
