package net.bananemdnsa.historystages.client.editor.widget;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable list of all registered LivingEntity types with 3D preview.
 * Supports toggling to an inventory view for selecting entities via spawn eggs.
 * <p>
 * When multi-select is enabled (see {@link #setMultiSelect}) the Registry and
 * Inventory tabs toggle selections instead of adding immediately, a "Selected"
 * tab lists every chosen entity for review/deselection, and {@link #onSelect}
 * fires once per entity on confirm. Mirrors {@link SearchableItemList}.
 */
public class SearchableEntityList {
    private static final int SLOT_SIZE = 18;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 8;
    private static final int PADDING = 6;
    private static final int PANEL_WIDTH = 260;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_PAD = 4;
    private static final int PREVIEW_SIZE = 80;
    private static final int ADD_BTN_H = 20;

    private static final int TAB_REGISTRY = 0;
    private static final int TAB_INVENTORY = 1;
    private static final int TAB_SELECTED = 2;

    private final List<EntityEntry> allEntities = new ArrayList<>();
    private final List<EntityEntry> filteredEntities = new ArrayList<>();
    /**
     * Snapshot of selected entities taken when the Selected tab is entered.
     * Stays stable while on the tab — toggling deselect/reselect modifies the
     * underlying sets but leaves the snapshot intact, so the list doesn't shift
     * under the cursor and the user can undo a misclick. Cleared on tab exit.
     */
    private final List<SelectedRef> selectedSnapshot = new ArrayList<>();
    /** Filtered view of {@link #selectedSnapshot} for the current search filter. */
    private final List<SelectedRef> selectedView = new ArrayList<>();
    private final Consumer<String> onSelect;
    private final Supplier<Collection<String>> alreadyAddedSupplier;
    private final SearchBar searchBar;

    /** Optional "is locked by stage" predicate enabled via {@link #setLockedFilter}. */
    private java.util.function.Predicate<String> lockedFilterFn = null;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();

    private int panelX, panelY, panelW, panelH;
    private int centerX, centerY;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private boolean draggingScrollbar = false;

    private int currentTab = TAB_REGISTRY;
    private boolean multiSelect = false;
    private final Set<String> selectedRegistryIds = new LinkedHashSet<>();
    private final Set<Integer> selectedInventorySlots = new LinkedHashSet<>();

    // Tab indicator animation
    private float tabIndicatorX = 0;
    private float tabIndicatorW = 0;
    private boolean tabIndicatorInit = false;

    // Add button hover animation
    private float addHoverProgress = 0.0f;

    public SearchableEntityList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableEntityList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        this.onSelect = onSelect;
        this.alreadyAddedSupplier = alreadyAddedSupplier;
        this.searchBar = SearchPanelChrome.createSearchBar("Search entities...", this::applyFilter, alreadyAddedSupplier);

        for (EntityType<?> entityType : ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
            if (key != null && isLivingEntityType(entityType)) {
                String displayName = entityType.getDescription().getString();
                allEntities.add(new EntityEntry(key.toString(), displayName, displayName.toLowerCase()));
            }
        }
        allEntities.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        filteredEntities.addAll(allEntities);
    }

    /** Enables multi-select: toggle entries across tabs, review them on the
     *  "Selected" tab, and emit one {@link #onSelect} per entity on confirm. */
    public void setMultiSelect(boolean multi) {
        this.multiSelect = multi;
    }

    @SuppressWarnings("unchecked")
    private boolean isLivingEntityType(EntityType<?> type) {
        try {
            if (Minecraft.getInstance().level != null) {
                Entity entity = type.create(Minecraft.getInstance().level);
                boolean isLiving = entity instanceof LivingEntity;
                if (entity != null)
                    entity.discard();
                return isLiving;
            }
            return type.getCategory() != net.minecraft.world.entity.MobCategory.MISC;
        } catch (Exception e) {
            return false;
        }
    }

    private LivingEntity getOrCreateEntity(String entityId) {
        if (entityCache.containsKey(entityId))
            return entityCache.get(entityId);
        if (Minecraft.getInstance().level == null)
            return null;

        try {
            ResourceLocation rl = ResourceLocation.tryParse(entityId);
            if (rl == null)
                return null;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            if (type == null)
                return null;
            Entity entity = type.create(Minecraft.getInstance().level);
            if (entity instanceof LivingEntity living) {
                entityCache.put(entityId, living);
                return living;
            }
            if (entity != null)
                entity.discard();
        } catch (Exception ignored) {
        }
        entityCache.put(entityId, null);
        return null;
    }

    /**
     * Returns the entity type ID for a spawn egg item, or null if not a spawn egg.
     */
    private String getEntityIdFromSpawnEgg(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof SpawnEggItem spawnEgg))
            return null;
        EntityType<?> type = spawnEgg.getType(stack.getTag());
        if (type == null)
            return null;
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return key != null ? key.toString() : null;
    }

    public void show(int centerX, int centerY, int parentWidth) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.visible = true;
        this.scrollRow = 0;
        searchBar.setFocused(true);
        this.currentTab = TAB_REGISTRY;
        this.selectedRegistryIds.clear();
        this.selectedInventorySlots.clear();
        this.selectedSnapshot.clear();
        this.selectedView.clear();
        this.tabIndicatorInit = false;
        searchBar.setPlaceholder("Search entities...");
        searchBar.setText("");
        recalcPanelSize();
    }

    private boolean isInventoryTab() {
        return currentTab == TAB_INVENTORY;
    }

    private boolean isSelectedTab() {
        return currentTab == TAB_SELECTED;
    }

    private int totalSelectionCount() {
        return selectedRegistryIds.size() + selectedInventorySlots.size();
    }

    private boolean showSelectedTab() {
        return multiSelect && (totalSelectionCount() > 0 || !selectedSnapshot.isEmpty());
    }

    private boolean isStillSelected(SelectedRef ref) {
        return ref.fromInventory
                ? selectedInventorySlots.contains(ref.inventorySlot)
                : selectedRegistryIds.contains(ref.id);
    }

    private int calcMinTabWidth() {
        Font font = Minecraft.getInstance().font;
        int total = PADDING * 2;
        List<String> labels = tabLabels();
        for (int i = 0; i < labels.size(); i++) {
            total += font.width(labels.get(i)) + TAB_PAD * 2;
            if (i < labels.size() - 1)
                total += 2;
        }
        return total + PADDING;
    }

    private void recalcPanelSize() {
        if (isInventoryTab()) {
            int gridW = SLOT_SIZE * 9;
            int invPanelW = PADDING + gridW + PADDING + 8;
            int topAreaH = 4 * SLOT_SIZE + 4;
            int invPanelH = PADDING + TAB_HEIGHT + 4
                    + topAreaH + 4
                    + 3 * SLOT_SIZE + 6
                    + SLOT_SIZE + 6
                    + ADD_BTN_H + PADDING;
            panelW = invPanelW + 4 + PREVIEW_SIZE + PADDING;
            panelH = invPanelH;
        } else {
            panelW = PANEL_WIDTH;
            panelH = TAB_HEIGHT + 4 + SearchBar.HEIGHT + PADDING * 2 + VISIBLE_ROWS * ROW_HEIGHT + PADDING + 4;
            if (multiSelect)
                panelH += ADD_BTN_H + PADDING;
        }

        int minW = calcMinTabWidth();
        if (panelW < minW)
            panelW = minW;

        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        clampToScreen();
    }

    private void clampToScreen() {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (panelX < 4)
            panelX = 4;
        if (panelY < 4)
            panelY = 4;
        if (panelX + panelW > screenW - 4)
            panelX = screenW - panelW - 4;
        if (panelY + panelH > screenH - 4)
            panelY = screenH - panelH - 4;
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setFilter(String filter) {
        searchBar.setText(filter);
    }

    private void applyFilter(String filter) {
        this.scrollRow = 0;
        filteredEntities.clear();
        for (EntityEntry entry : allEntities) {
            if (matchesFilter(entry, filter))
                filteredEntities.add(entry);
        }
        if (isSelectedTab())
            applySelectedFilter();
        updateMaxScroll();
    }

    private boolean matchesDropdownFilters(String id) {
        if (!SearchPanelChrome.passesDefaultFilters(searchBar, id, alreadyAddedSupplier)) return false;
        if (lockedFilterFn != null && searchBar.filters().isActive("hide_locked")
                && id != null && lockedFilterFn.test(id)) return false;
        return true;
    }

    /** See {@link SearchableItemList#setLockedFilter}. */
    public void setLockedFilter(String label, java.util.function.Predicate<String> isLocked) {
        this.lockedFilterFn = isLocked;
        searchBar.filters().addOption("hide_locked", label, null, true);
        applyFilter(searchBar.getText() == null ? "" : searchBar.getText());
    }

    private boolean matchesFilter(EntityEntry entry, String filter) {
        if (!matchesDropdownFilters(entry.id))
            return false;
        return filter.isEmpty() || entry.id.contains(filter) || entry.searchName.contains(filter);
    }

    /** Captures the current selection sets as the frozen Selected-tab snapshot. */
    private void rebuildSelectedSnapshot() {
        selectedSnapshot.clear();
        for (String id : selectedRegistryIds) {
            String displayName = displayNameForId(id);
            selectedSnapshot.add(new SelectedRef(id, displayName, displayName.toLowerCase(), false, -1));
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            for (Integer slot : selectedInventorySlots) {
                ItemStack stack = player.getInventory().getItem(slot);
                String id = getEntityIdFromSpawnEgg(stack);
                if (id == null)
                    continue;
                String displayName = displayNameForId(id);
                selectedSnapshot.add(new SelectedRef(id, displayName, displayName.toLowerCase(), true, slot));
            }
        }
        applySelectedFilter();
    }

    private String displayNameForId(String id) {
        for (EntityEntry entry : allEntities) {
            if (entry.id.equals(id))
                return entry.displayName;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        EntityType<?> type = rl != null ? ForgeRegistries.ENTITY_TYPES.getValue(rl) : null;
        return type != null ? type.getDescription().getString() : id;
    }

    /** Filters the snapshot by the current search filter into the rendered view. */
    private void applySelectedFilter() {
        selectedView.clear();
        String f = searchBar.getText();
        for (SelectedRef ref : selectedSnapshot) {
            if (f.isEmpty() || ref.id.contains(f) || ref.searchName.contains(f))
                selectedView.add(ref);
        }
    }

    private void updateMaxScroll() {
        if (isInventoryTab()) {
            maxScrollRow = 0;
        } else if (isSelectedTab()) {
            maxScrollRow = Math.max(0, selectedView.size() - VISIBLE_ROWS);
        } else {
            maxScrollRow = Math.max(0, filteredEntities.size() - VISIBLE_ROWS);
        }
    }

    private List<String> tabLabels() {
        List<String> labels = new ArrayList<>(3);
        labels.add("Registry");
        labels.add("Inventory");
        if (showSelectedTab()) {
            labels.add("Selected (" + totalSelectionCount() + ")");
        }
        return labels;
    }

    private String addButtonLabel() {
        return multiSelect ? "Add Entities (" + totalSelectionCount() + ")" : "Add Entity";
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible)
            return;

        SearchPanelChrome.renderFrame(guiGraphics, panelX, panelY, panelW, panelH);

        renderTabs(guiGraphics, font, mouseX, mouseY);

        if (isInventoryTab()) {
            renderInventoryMode(guiGraphics, font, mouseX, mouseY);
        } else if (isSelectedTab()) {
            renderSelectedMode(guiGraphics, font, mouseX, mouseY);
        } else {
            renderRegistryMode(guiGraphics, font, mouseX, mouseY);
        }
    }

    private void renderTabs(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int tabY = panelY + PADDING;
        List<String> labels = tabLabels();
        int n = labels.size();
        int[] tabXs = new int[n];
        int[] tabWs = new int[n];

        int x = panelX + PADDING;
        for (int i = 0; i < n; i++) {
            tabWs[i] = font.width(labels.get(i)) + TAB_PAD * 2;
            tabXs[i] = x;
            x += tabWs[i] + 2;
        }

        int activeIdx = Math.min(currentTab, n - 1);
        if (!tabIndicatorInit) {
            tabIndicatorX = tabXs[activeIdx];
            tabIndicatorW = tabWs[activeIdx];
            tabIndicatorInit = true;
        }

        float targetX = tabXs[activeIdx];
        float targetW = tabWs[activeIdx];
        tabIndicatorX += (targetX - tabIndicatorX) * 0.18f;
        tabIndicatorW += (targetW - tabIndicatorW) * 0.18f;
        if (Math.abs(tabIndicatorX - targetX) < 0.5f)
            tabIndicatorX = targetX;
        if (Math.abs(tabIndicatorW - targetW) < 0.5f)
            tabIndicatorW = targetW;

        for (int i = 0; i < n; i++) {
            boolean active = (i == activeIdx);
            boolean hovered = mouseX >= tabXs[i] && mouseX < tabXs[i] + tabWs[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            guiGraphics.fill(tabXs[i], tabY, tabXs[i] + tabWs[i], tabY + TAB_HEIGHT, bg);

            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            guiGraphics.drawString(font, labels.get(i), tabXs[i] + TAB_PAD, tabY + 3, textColor, false);
        }

        guiGraphics.fill((int) tabIndicatorX, tabY + TAB_HEIGHT - 2,
                (int) (tabIndicatorX + tabIndicatorW), tabY + TAB_HEIGHT, 0xFFFFCC00);

        guiGraphics.fill(panelX + PADDING, tabY + TAB_HEIGHT, panelX + panelW - PADDING, tabY + TAB_HEIGHT + 1,
                0xFF555555);
    }

    private void renderRegistryMode(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchX = panelX + PADDING;
        int searchY = panelY + topOffset;
        searchBar.setPosition(searchX, searchY, panelW - PADDING * 2);
        searchBar.render(guiGraphics, font, mouseX, mouseY);

        int listX = panelX + PADDING;
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;

            boolean rowHovered = !filterUiHovered && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (index < filteredEntities.size()) {
                EntityEntry entry = filteredEntities.get(index);
                boolean selected = multiSelect && selectedRegistryIds.contains(entry.id);
                renderEntityRow(guiGraphics, font, listX, rowY, listW, entry.id, entry.displayName,
                        rowHovered, selected ? RowState.SELECTED : RowState.NORMAL);
            } else {
                guiGraphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT,
                        rowHovered ? 0xFF353535 : 0xFF252525);
            }
        }

        if (maxScrollRow > 0) {
            renderScrollbar(guiGraphics, listX, listW, listY);
        }

        if (multiSelect) {
            renderAddButton(guiGraphics, font, mouseX, mouseY);
        }
    }

    private void renderSelectedMode(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchX = panelX + PADDING;
        int searchY = panelY + topOffset;
        searchBar.setPosition(searchX, searchY, panelW - PADDING * 2);
        searchBar.render(guiGraphics, font, mouseX, mouseY);

        int listX = panelX + PADDING;
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;

            boolean rowHovered = !filterUiHovered && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (index < selectedView.size()) {
                SelectedRef ref = selectedView.get(index);
                RowState state = isStillSelected(ref) ? RowState.SELECTED : RowState.DESELECTED;
                renderEntityRow(guiGraphics, font, listX, rowY, listW, ref.id, ref.displayName, rowHovered, state);
            } else {
                guiGraphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT,
                        rowHovered ? 0xFF353535 : 0xFF252525);
            }
        }

        if (maxScrollRow > 0) {
            renderScrollbar(guiGraphics, listX, listW, listY);
        }

        renderAddButton(guiGraphics, font, mouseX, mouseY);
    }

    private enum RowState { NORMAL, SELECTED, DESELECTED }

    private void renderEntityRow(GuiGraphics guiGraphics, Font font, int listX, int rowY, int listW,
            String id, String displayName, boolean rowHovered, RowState state) {
        int bg;
        switch (state) {
            case SELECTED -> bg = rowHovered ? 0xFF553A10 : 0xFF2A2510;
            case DESELECTED -> bg = rowHovered ? 0xFF3A1A1A : 0xFF1A0D0D;
            default -> bg = rowHovered ? 0xFF353535 : 0xFF252525;
        }
        guiGraphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT, bg);
        if (state == RowState.SELECTED) {
            guiGraphics.fill(listX, rowY, listX + 2, rowY + ROW_HEIGHT, 0xFFFFCC00);
        } else if (state == RowState.DESELECTED) {
            guiGraphics.fill(listX, rowY, listX + 2, rowY + ROW_HEIGHT, 0xFFCC4444);
        }

        LivingEntity living = getOrCreateEntity(id);
        if (living != null) {
            try {
                float angle = (System.currentTimeMillis() % 3600) / 10.0f;
                guiGraphics.enableScissor(listX, rowY, listX + 18, rowY + ROW_HEIGHT);
                int entityScale = (int) Math.max(3, 9.0f / Math.max(living.getBbWidth(), living.getBbHeight()));
                renderSpinningEntity(guiGraphics, listX + 9, rowY + ROW_HEIGHT - 2, entityScale, angle, living);
                guiGraphics.disableScissor();
            } catch (Exception ignored) {
            }
        }

        String text = displayName + " §7(" + id + ")";
        if (font.width(text) > listW - 22) {
            text = font.plainSubstrByWidth(text, listW - 28) + "...";
        }
        int textColor = state == RowState.DESELECTED
                ? (rowHovered ? 0xFFCC8888 : 0xFF885555)
                : (rowHovered ? 0xFFFFFF : 0xBBBBBB);
        guiGraphics.drawString(font, text, listX + 20, rowY + 6, textColor, false);
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int listX, int listW, int listY) {
        int scrollBarX = listX + listW + 2;
        int scrollBarTop = listY;
        int scrollBarBottom = listY + VISIBLE_ROWS * ROW_HEIGHT;
        int scrollBarHeight = scrollBarBottom - scrollBarTop;

        guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);

        int thumbHeight = Math.max(10,
                (int) ((float) VISIBLE_ROWS / (maxScrollRow + VISIBLE_ROWS) * scrollBarHeight));
        int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarHeight - thumbHeight));
        guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
    }

    private void renderAddButton(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int addBtnW = panelW - PADDING * 2;
        int addBtnX = panelX + PADDING;
        int addBtnY = panelY + panelH - PADDING - ADD_BTN_H;

        boolean canAdd = canConfirm();
        boolean addHovered = canAdd && mouseX >= addBtnX && mouseX < addBtnX + addBtnW
                && mouseY >= addBtnY && mouseY < addBtnY + ADD_BTN_H;
        addHoverProgress = addHovered ? Math.min(1.0f, addHoverProgress + 0.1f)
                : Math.max(0.0f, addHoverProgress - 0.08f);

        if (canAdd) {
            renderStyledButton(guiGraphics, font, addBtnX, addBtnY, addBtnW, ADD_BTN_H, addButtonLabel(),
                    addHoverProgress);
        } else {
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + ADD_BTN_H, 0x20FFFFFF);
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + 1, 0x10FFFFFF);
            String addText = "Select an Entity";
            guiGraphics.drawString(font, addText, addBtnX + (addBtnW - font.width(addText)) / 2,
                    addBtnY + (ADD_BTN_H - 8) / 2, 0xFF666666, false);
        }
    }

    private boolean canConfirm() {
        if (multiSelect)
            return totalSelectionCount() > 0;
        // Single-select: only the Inventory tab routes through the Add button;
        // the Registry tab adds immediately on click.
        if (selectedInventorySlots.isEmpty())
            return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return false;
        int slot = selectedInventorySlots.iterator().next();
        return getEntityIdFromSpawnEgg(player.getInventory().getItem(slot)) != null;
    }

    private void renderInventoryMode(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;

        int[] layout = getInvLayout();
        int gridX = layout[0];
        int topY = layout[1];
        int mainY = layout[2];
        int hotbarY = layout[3];
        int previewX = layout[4];

        int armorX = gridX;
        int entityAreaX = gridX + SLOT_SIZE + 4;
        int entityAreaW = 9 * SLOT_SIZE - 2 * (SLOT_SIZE + 4);
        int entityAreaH = 4 * SLOT_SIZE;
        int offhandX = gridX + 9 * SLOT_SIZE - SLOT_SIZE;

        int[] armorSlots = { 39, 38, 37, 36 };
        String[] armorLabels = { "H", "C", "L", "F" };
        for (int i = 0; i < 4; i++) {
            renderInventorySlot(guiGraphics, font, armorX, topY + i * SLOT_SIZE,
                    player.getInventory().getItem(armorSlots[i]), armorSlots[i], mouseX, mouseY, armorLabels[i]);
        }

        guiGraphics.fill(entityAreaX, topY, entityAreaX + entityAreaW, topY + entityAreaH, 0xFF0D0D0D);
        int entityCenterX = entityAreaX + entityAreaW / 2;
        int entityBottomY = topY + entityAreaH - 3;
        InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics,
                entityCenterX, entityBottomY, 25,
                (float) (entityCenterX - mouseX), (float) (entityBottomY - 50 - mouseY), player);

        renderInventorySlot(guiGraphics, font, offhandX, topY + 3 * SLOT_SIZE,
                player.getInventory().getItem(40), 40, mouseX, mouseY, "O");

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                renderInventorySlot(guiGraphics, font, gridX + col * SLOT_SIZE, mainY + row * SLOT_SIZE,
                        player.getInventory().getItem(slotIndex), slotIndex, mouseX, mouseY, null);
            }
        }

        guiGraphics.fill(gridX, hotbarY - 3, gridX + 9 * SLOT_SIZE, hotbarY - 2, 0xFF333333);

        for (int col = 0; col < 9; col++) {
            renderInventorySlot(guiGraphics, font, gridX + col * SLOT_SIZE, hotbarY,
                    player.getInventory().getItem(col), col, mouseX, mouseY, null);
        }

        int previewY = topY;
        int previewH = panelY + panelH - PADDING - 20 - 6 - previewY;
        guiGraphics.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + previewH, 0xFF0D0D0D);
        guiGraphics.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + 1, 0xFF333333);
        guiGraphics.fill(previewX, previewY, previewX + 1, previewY + previewH, 0xFF333333);
        guiGraphics.fill(previewX + PREVIEW_SIZE - 1, previewY, previewX + PREVIEW_SIZE, previewY + previewH,
                0xFF333333);
        guiGraphics.fill(previewX, previewY + previewH - 1, previewX + PREVIEW_SIZE, previewY + previewH, 0xFF333333);

        int previewSlot = lastSelectedSlot();
        String selectedEntityId = null;
        if (previewSlot >= 0) {
            ItemStack selectedStack = player.getInventory().getItem(previewSlot);
            selectedEntityId = getEntityIdFromSpawnEgg(selectedStack);
        }

        if (selectedEntityId != null) {
            LivingEntity previewEntity = getOrCreateEntity(selectedEntityId);
            if (previewEntity != null) {
                try {
                    float angle = (System.currentTimeMillis() % 7200) / 20.0f;
                    int entityScale = (int) Math.max(8,
                            30.0f / Math.max(previewEntity.getBbWidth(), previewEntity.getBbHeight()));
                    int prevCenterX = previewX + PREVIEW_SIZE / 2;
                    int prevCenterY = previewY + previewH / 2 + entityScale;
                    guiGraphics.enableScissor(previewX + 1, previewY + 1, previewX + PREVIEW_SIZE - 1,
                            previewY + previewH - 1);
                    renderSpinningEntity(guiGraphics, prevCenterX, prevCenterY, entityScale, angle, previewEntity);
                    guiGraphics.disableScissor();
                } catch (Exception ignored) {
                }
            }

            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(selectedEntityId));
            if (type != null) {
                String entityName = type.getDescription().getString();
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 300);
                int nameW = font.width(entityName);
                int nameX = previewX + (PREVIEW_SIZE - nameW) / 2;
                if (nameW > PREVIEW_SIZE - 4) {
                    entityName = font.plainSubstrByWidth(entityName, PREVIEW_SIZE - 10) + "..";
                    nameX = previewX + 2;
                }
                guiGraphics.drawString(font, entityName, nameX, previewY + previewH - 11, 0xFFCC00, false);
                guiGraphics.pose().popPose();
            }
        } else {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 300);
            String hint1 = "Select a";
            String hint2 = "Spawn Egg";
            guiGraphics.drawString(font, hint1, previewX + (PREVIEW_SIZE - font.width(hint1)) / 2,
                    previewY + previewH / 2 - 8, 0xFF555555, false);
            guiGraphics.drawString(font, hint2, previewX + (PREVIEW_SIZE - font.width(hint2)) / 2,
                    previewY + previewH / 2 + 2, 0xFF555555, false);
            guiGraphics.pose().popPose();
        }

        int addBtnW = panelW - PADDING * 2;
        int addBtnX = panelX + PADDING;
        int addBtnY = panelY + panelH - PADDING - ADD_BTN_H;

        boolean canAdd = canConfirm();

        boolean addHovered = canAdd && mouseX >= addBtnX && mouseX < addBtnX + addBtnW
                && mouseY >= addBtnY && mouseY < addBtnY + ADD_BTN_H;
        addHoverProgress = addHovered ? Math.min(1.0f, addHoverProgress + 0.1f)
                : Math.max(0.0f, addHoverProgress - 0.08f);

        if (canAdd) {
            renderStyledButton(guiGraphics, font, addBtnX, addBtnY, addBtnW, ADD_BTN_H, addButtonLabel(),
                    addHoverProgress);
        } else {
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + ADD_BTN_H, 0x20FFFFFF);
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + 1, 0x10FFFFFF);
            boolean hasNonEggSelection = previewSlot >= 0 && selectedEntityId == null
                    && !player.getInventory().getItem(previewSlot).isEmpty();
            String addText = hasNonEggSelection ? "Not a Spawn Egg!" : "Select a Spawn Egg";
            int textColor = hasNonEggSelection ? 0xFFFF6666 : 0xFF666666;
            guiGraphics.drawString(font, addText, addBtnX + (addBtnW - font.width(addText)) / 2,
                    addBtnY + (ADD_BTN_H - 8) / 2, textColor, false);
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        int hoveredSlot = getInventorySlotAt(mouseX, mouseY);
        if (hoveredSlot >= 0) {
            ItemStack stack = player.getInventory().getItem(hoveredSlot);
            if (!stack.isEmpty()) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (key != null) {
                    String eggEntityId = getEntityIdFromSpawnEgg(stack);
                    String tooltip = stack.getHoverName().getString() + " §7(" + key + ")";
                    if (eggEntityId != null) {
                        tooltip += " §a✔";
                    }
                    renderTooltip(guiGraphics, font, mouseX, mouseY, tooltip);
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    /** Most recently selected inventory slot (insertion order), or -1. */
    private int lastSelectedSlot() {
        int last = -1;
        for (Integer slot : selectedInventorySlots) {
            last = slot;
        }
        return last;
    }

    private void renderInventorySlot(GuiGraphics guiGraphics, Font font, int x, int y,
            ItemStack stack, int slotIndex, int mouseX, int mouseY, String placeholder) {
        boolean isEmpty = stack.isEmpty();
        boolean isSpawnEgg = !isEmpty && stack.getItem() instanceof SpawnEggItem;
        boolean isSelected = selectedInventorySlots.contains(slotIndex);
        boolean isHovered = !isEmpty && mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;

        int borderColor = isSelected ? 0xFFFFCC00 : 0xFF252525;
        int bgColor = isSelected ? 0xFF2A2510 : (isHovered ? 0xFF353535 : 0xFF1A1A1A);

        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, borderColor);
        guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, bgColor);

        if (!isEmpty) {
            guiGraphics.renderItem(stack, x + 1, y + 1);
            if (stack.getCount() > 1) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 200);
                String count = String.valueOf(stack.getCount());
                guiGraphics.drawString(font, count, x + SLOT_SIZE - 1 - font.width(count), y + SLOT_SIZE - 9, 0xFFFFFF,
                        true);
                guiGraphics.pose().popPose();
            }
            if (!isSpawnEgg) {
                guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x80000000);
            }
            if (isSelected) {
                guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x40FFCC00);
            }
        } else if (placeholder != null) {
            guiGraphics.drawString(font, placeholder, x + (SLOT_SIZE - font.width(placeholder)) / 2, y + 5, 0xFF444444,
                    false);
        }
    }

    private void renderStyledButton(GuiGraphics guiGraphics, Font font, int x, int y, int w, int h,
            String text, float hoverProgress) {
        int bgAlpha = (int) (0x30 + hoverProgress * 0x20);
        int bgR = 0xFF;
        int bgG = (int) (0xFF - hoverProgress * 0x33);
        int bgB = (int) (0xFF - hoverProgress * 0xFF);
        guiGraphics.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgR << 16) | (bgG << 8) | bgB);

        int accentAlpha = (int) (0x60 + hoverProgress * 0x9F);
        guiGraphics.fill(x, y + h - 2, x + w, y + h, (accentAlpha << 24) | 0xFFCC00);

        guiGraphics.fill(x, y, x + w, y + 1, 0x20FFFFFF);
        guiGraphics.fill(x, y, x + 1, y + h, 0x15FFFFFF);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0x15FFFFFF);

        int textGray = (int) (0xCC + hoverProgress * 0x33);
        int textColor = (0xFF << 24) | (textGray << 16) | (textGray << 8) | textGray;
        guiGraphics.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, textColor, false);
    }

    private void renderTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, String text) {
        int tooltipW = font.width(text) + 8;
        int tooltipH = 16;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 12;
        if (tooltipX + tooltipW + 2 > screenW - 4)
            tooltipX = mouseX - tooltipW - 4;
        if (tooltipY + tooltipH + 2 > screenH - 4)
            tooltipY = screenH - tooltipH - 6;
        if (tooltipX < 4)
            tooltipX = 4;
        if (tooltipY < 4)
            tooltipY = 4;
        guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH, 0xFF1A1A1A);
        guiGraphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipW + 1, tooltipY + tooltipH - 1, 0xFF0D0D1A);
        guiGraphics.drawString(font, text, tooltipX + 2, tooltipY + 2, 0xFFFFFF, false);
    }

    private static void renderSpinningEntity(GuiGraphics guiGraphics, int x, int y, int scale, float angleDegrees,
            LivingEntity entity) {
        float origBodyRot = entity.yBodyRot;
        float origYRot = entity.getYRot();
        float origXRot = entity.getXRot();
        float origHeadRotO = entity.yHeadRotO;
        float origHeadRot = entity.yHeadRot;

        entity.yBodyRot = 180.0F;
        entity.setYRot(180.0F);
        entity.setXRot(0.0F);
        entity.yHeadRot = 180.0F;
        entity.yHeadRotO = 180.0F;

        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        try {
            modelViewStack.translate(0.0F, 0.0F, 1500.0F);
            RenderSystem.applyModelViewMatrix();

            PoseStack poseStack = new PoseStack();
            poseStack.translate((double) x, (double) y, -950.0D);
            poseStack.scale((float) scale, (float) scale, (float) scale);

            Quaternionf flipAndSpin = new Quaternionf().rotateZ((float) Math.PI);
            flipAndSpin.mul(new Quaternionf().rotateY(angleDegrees * ((float) Math.PI / 180.0F)));
            poseStack.mulPose(flipAndSpin);

            Lighting.setupForEntityInInventory();
            RenderSystem.disableDepthTest();

            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            dispatcher.overrideCameraOrientation(new Quaternionf());
            dispatcher.setRenderShadow(false);

            MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            RenderSystem.runAsFancy(() -> {
                dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, poseStack, bufferSource, 15728880);
            });
            bufferSource.endBatch();
            dispatcher.setRenderShadow(true);
            RenderSystem.enableDepthTest();
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();

            entity.yBodyRot = origBodyRot;
            entity.setYRot(origYRot);
            entity.setXRot(origXRot);
            entity.yHeadRotO = origHeadRotO;
            entity.yHeadRot = origHeadRot;
        }
    }

    private int[] getInvLayout() {
        int topOffset = PADDING + TAB_HEIGHT + 4;
        int gridX = panelX + PADDING + 4;
        int topY = panelY + topOffset + 2;
        int topAreaH = 4 * SLOT_SIZE + 4;
        int mainY = topY + topAreaH + 4;
        int hotbarY = mainY + 3 * SLOT_SIZE + 6;
        int previewX = gridX + 9 * SLOT_SIZE + 8;
        return new int[] { gridX, topY, mainY, hotbarY, previewX };
    }

    private int getInventorySlotAt(double mouseX, double mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return -1;

        int[] layout = getInvLayout();
        int gridX = layout[0];
        int topY = layout[1];
        int mainY = layout[2];
        int hotbarY = layout[3];

        int armorX = gridX;
        int offhandX = gridX + 9 * SLOT_SIZE - SLOT_SIZE;

        int[] armorSlots = { 39, 38, 37, 36 };
        for (int i = 0; i < 4; i++) {
            int slotY = topY + i * SLOT_SIZE;
            if (mouseX >= armorX && mouseX < armorX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                return armorSlots[i];
            }
        }

        int offhandY = topY + 3 * SLOT_SIZE;
        if (mouseX >= offhandX && mouseX < offhandX + SLOT_SIZE && mouseY >= offhandY
                && mouseY < offhandY + SLOT_SIZE) {
            return 40;
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = mainY + row * SLOT_SIZE;
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    return slotIndex;
                }
            }
        }

        for (int col = 0; col < 9; col++) {
            int slotX = gridX + col * SLOT_SIZE;
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= hotbarY && mouseY < hotbarY + SLOT_SIZE) {
                return col;
            }
        }

        return -1;
    }

    private int getTabAt(double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int tabY = panelY + PADDING;
        List<String> labels = tabLabels();
        int x = panelX + PADDING;
        for (int i = 0; i < labels.size(); i++) {
            int w = font.width(labels.get(i)) + TAB_PAD * 2;
            if (mouseX >= x && mouseX < x + w && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                return i;
            }
            x += w + 2;
        }
        return -1;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible)
            return false;

        if (!isInventoryTab() && searchBar.mouseClicked(mouseX, mouseY))
            return true;

        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        int clickedTab = getTabAt(mouseX, mouseY);
        if (clickedTab >= 0 && clickedTab != currentTab) {
            int maxTab = showSelectedTab() ? TAB_SELECTED : TAB_INVENTORY;
            if (clickedTab <= maxTab) {
                switchTab(clickedTab);
                return true;
            }
        }

        if (isSelectedTab()) {
            return handleSelectedClick(mouseX, mouseY);
        }
        if (isInventoryTab()) {
            return handleInventoryClick(mouseX, mouseY);
        }
        return handleRegistryClick(mouseX, mouseY);
    }

    private void switchTab(int newTab) {
        int oldTab = currentTab;
        currentTab = newTab;
        searchBar.filters().close();
        searchBar.setFocused(currentTab != TAB_INVENTORY);
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        scrollRow = 0;
        if (oldTab == TAB_SELECTED && newTab != TAB_SELECTED) {
            selectedSnapshot.clear();
            selectedView.clear();
        }
        if (newTab == TAB_SELECTED) {
            searchBar.setPlaceholder("Search selected (" + totalSelectionCount() + ")...");
        } else {
            searchBar.setPlaceholder("Search entities...");
        }
        searchBar.setText("");
        if (currentTab == TAB_SELECTED) {
            rebuildSelectedSnapshot();
        }
        updateMaxScroll();
        recalcPanelSize();
    }

    private boolean isAddButtonAt(double mouseX, double mouseY) {
        int addBtnW = panelW - PADDING * 2;
        int addBtnX = panelX + PADDING;
        int addBtnY = panelY + panelH - PADDING - ADD_BTN_H;
        return mouseX >= addBtnX && mouseX < addBtnX + addBtnW && mouseY >= addBtnY && mouseY < addBtnY + ADD_BTN_H;
    }

    private void toggleRegistrySelection(String id) {
        if (selectedRegistryIds.contains(id)) {
            selectedRegistryIds.remove(id);
        } else {
            selectedRegistryIds.add(id);
        }
    }

    private void toggleInventorySelection(int slot) {
        if (selectedInventorySlots.contains(slot)) {
            selectedInventorySlots.remove(slot);
        } else {
            if (!multiSelect) {
                selectedInventorySlots.clear();
            }
            selectedInventorySlots.add(slot);
        }
    }

    private boolean confirmAndAdd() {
        if (!canConfirm())
            return false;
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

        LocalPlayer player = Minecraft.getInstance().player;

        if (multiSelect) {
            // Dedupe so an entity selected via both a registry row and an
            // inventory spawn egg only fires onSelect once.
            Set<String> emitted = new LinkedHashSet<>();
            for (String id : selectedRegistryIds) {
                if (emitted.add(id))
                    onSelect.accept(id);
            }
            if (player != null) {
                for (Integer slot : selectedInventorySlots) {
                    String id = getEntityIdFromSpawnEgg(player.getInventory().getItem(slot));
                    if (id != null && emitted.add(id))
                        onSelect.accept(id);
                }
            }
        } else if (player != null && !selectedInventorySlots.isEmpty()) {
            int slot = selectedInventorySlots.iterator().next();
            String id = getEntityIdFromSpawnEgg(player.getInventory().getItem(slot));
            if (id != null)
                onSelect.accept(id);
        }
        hide();
        return true;
    }

    private boolean handleInventoryClick(double mouseX, double mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return true;

        if (isAddButtonAt(mouseX, mouseY)) {
            confirmAndAdd();
            return true;
        }

        int clickedSlot = getInventorySlotAt(mouseX, mouseY);
        if (clickedSlot >= 0) {
            ItemStack stack = player.getInventory().getItem(clickedSlot);
            if (!stack.isEmpty()) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                toggleInventorySelection(clickedSlot);
            }
            return true;
        }

        return true;
    }

    private boolean handleRegistryClick(double mouseX, double mouseY) {
        if (multiSelect && isAddButtonAt(mouseX, mouseY)) {
            confirmAndAdd();
            return true;
        }

        int topOffset = PADDING + TAB_HEIGHT + 4;

        int searchY = panelY + topOffset;
        int listX = panelX + PADDING;
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        if (maxScrollRow > 0) {
            int scrollBarX = listX + listW + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, listY);
                return true;
            }
        }

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;
            if (index < filteredEntities.size() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (multiSelect) {
                    toggleRegistrySelection(filteredEntities.get(index).id);
                } else {
                    onSelect.accept(filteredEntities.get(index).id);
                    hide();
                }
                return true;
            }
        }

        searchBar.setFocused(true);
        return true;
    }

    private boolean handleSelectedClick(double mouseX, double mouseY) {
        if (isAddButtonAt(mouseX, mouseY)) {
            confirmAndAdd();
            return true;
        }

        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchY = panelY + topOffset;
        int listX = panelX + PADDING;
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        if (maxScrollRow > 0) {
            int scrollBarX = listX + listW + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, listY);
                return true;
            }
        }

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;
            if (index < selectedView.size() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                toggleSnapshotEntry(selectedView.get(index));
                return true;
            }
        }

        searchBar.setFocused(true);
        return true;
    }

    /**
     * Toggles whether the snapshot entry is in the active selection sets, but
     * leaves it in the snapshot/view so the list doesn't shift under the
     * cursor. Snapshot is purged on tab exit.
     */
    private void toggleSnapshotEntry(SelectedRef ref) {
        if (ref.fromInventory) {
            if (selectedInventorySlots.contains(ref.inventorySlot)) {
                selectedInventorySlots.remove(ref.inventorySlot);
            } else {
                selectedInventorySlots.add(ref.inventorySlot);
            }
        } else {
            if (selectedRegistryIds.contains(ref.id)) {
                selectedRegistryIds.remove(ref.id);
            } else {
                selectedRegistryIds.add(ref.id);
            }
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar || isInventoryTab())
            return false;
        int topOffset = PADDING + TAB_HEIGHT + 4;
        int searchY = panelY + topOffset;
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        updateScrollFromMouse(mouseY, listY);
        return true;
    }

    public boolean mouseReleased() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double mouseY, int listY) {
        int listH = VISIBLE_ROWS * ROW_HEIGHT;
        int totalRows = maxScrollRow + VISIBLE_ROWS;
        int thumbHeight = Math.max(10, (int) ((float) VISIBLE_ROWS / totalRows * listH));
        float usableH = listH - thumbHeight;
        if (usableH > 0) {
            float ratio = (float) (mouseY - listY - thumbHeight / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollRow = Math.round(ratio * maxScrollRow);
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow));
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || isInventoryTab())
            return false;
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) delta));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible)
            return false;

        if (keyCode == 256) { // ESC
            if (!isInventoryTab() && searchBar.keyPressed(keyCode))
                return true;
            hide();
            return true;
        }

        if (keyCode == 257 && canConfirm()) { // Enter
            confirmAndAdd();
            return true;
        }

        if (isInventoryTab())
            return true;

        return searchBar.keyPressed(keyCode);
    }

    public boolean charTyped(char c) {
        if (!visible || isInventoryTab())
            return false;
        return searchBar.charTyped(c);
    }

    private record EntityEntry(String id, String displayName, String searchName) {
    }

    private record SelectedRef(String id, String displayName, String searchName, boolean fromInventory,
            int inventorySlot) {
    }
}
