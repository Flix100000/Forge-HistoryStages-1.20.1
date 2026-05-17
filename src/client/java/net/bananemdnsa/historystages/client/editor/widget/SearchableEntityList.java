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
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable list of all registered LivingEntity types with 3D preview.
 * Supports toggling to an inventory view for selecting entities via spawn eggs.
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

    private final List<EntityEntry> allEntities = new ArrayList<>();
    private final List<EntityEntry> filteredEntities = new ArrayList<>();
    private final Consumer<String> onSelect;
    private final Supplier<Collection<String>> alreadyAddedSupplier;
    private final SearchBar searchBar;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();

    private int panelX, panelY, panelW, panelH;
    private int centerX, centerY;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private boolean draggingScrollbar = false;

    private boolean inventoryMode = false;
    private int selectedInventorySlot = -1;

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
        this.searchBar = new SearchBar("Search entities...").onChange(this::applyFilter);
        if (alreadyAddedSupplier != null) {
            searchBar.filters().addOption("hide_added", "Hide already added", null);
        }
        searchBar.filters().addOption("only_vanilla", "Only vanilla", "source");
        searchBar.filters().addOption("only_modded", "Only modded", "source");

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
        EntityType<?> type = spawnEgg.getType(stack);
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
        this.inventoryMode = false;
        this.selectedInventorySlot = -1;
        this.tabIndicatorInit = false;
        searchBar.setText("");
        recalcPanelSize();
    }

    private void recalcPanelSize() {
        if (inventoryMode) {
            int gridW = SLOT_SIZE * 9;
            int invPanelW = PADDING + gridW + PADDING + 8;
            int topAreaH = 4 * SLOT_SIZE + 4;
            int addButtonH = 20;
            int invPanelH = PADDING + TAB_HEIGHT + 4
                    + topAreaH + 4
                    + 3 * SLOT_SIZE + 6
                    + SLOT_SIZE + 6
                    + addButtonH + PADDING;
            panelW = invPanelW + 4 + PREVIEW_SIZE + PADDING;
            panelH = invPanelH;
        } else {
            panelW = PANEL_WIDTH;
            panelH = TAB_HEIGHT + 4 + SearchBar.HEIGHT + PADDING * 2 + VISIBLE_ROWS * ROW_HEIGHT + PADDING + 4;
        }

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
            if (!matchesDropdownFilters(entry.id))
                continue;
            if (filter.isEmpty()
                    || entry.id.contains(filter)
                    || entry.searchName.contains(filter)) {
                filteredEntities.add(entry);
            }
        }
        updateMaxScroll();
    }

    private boolean matchesDropdownFilters(String id) {
        if (searchBar.filters().isActive("hide_added") && alreadyAddedSupplier != null) {
            Collection<String> added = alreadyAddedSupplier.get();
            if (added != null && added.contains(id))
                return false;
        }
        String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "";
        boolean isVanilla = "minecraft".equals(namespace);
        if (searchBar.filters().isActive("only_vanilla") && !isVanilla)
            return false;
        if (searchBar.filters().isActive("only_modded") && isVanilla)
            return false;
        return true;
    }

    private void updateMaxScroll() {
        if (inventoryMode) {
            maxScrollRow = 0;
        } else {
            maxScrollRow = Math.max(0, filteredEntities.size() - VISIBLE_ROWS);
        }
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible)
            return;

        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        renderTabs(guiGraphics, font, mouseX, mouseY);

        if (inventoryMode) {
            renderInventoryMode(guiGraphics, font, mouseX, mouseY);
        } else {
            renderRegistryMode(guiGraphics, font, mouseX, mouseY);
        }
    }

    private void renderTabs(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        int tabY = panelY + PADDING;
        String[] labels = { "Registry", "Inventory" };
        int[] tabXs = new int[2];
        int[] tabWs = new int[2];

        int x = panelX + PADDING;
        for (int i = 0; i < 2; i++) {
            tabWs[i] = font.width(labels[i]) + TAB_PAD * 2;
            tabXs[i] = x;
            x += tabWs[i] + 2;
        }

        int activeIdx = inventoryMode ? 1 : 0;
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

        for (int i = 0; i < 2; i++) {
            boolean active = (i == activeIdx);
            boolean hovered = mouseX >= tabXs[i] && mouseX < tabXs[i] + tabWs[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            guiGraphics.fill(tabXs[i], tabY, tabXs[i] + tabWs[i], tabY + TAB_HEIGHT, bg);

            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            guiGraphics.drawString(font, labels[i], tabXs[i] + TAB_PAD, tabY + 3, textColor, false);
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
            guiGraphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT,
                    rowHovered ? 0xFF353535 : 0xFF252525);

            if (index < filteredEntities.size()) {
                EntityEntry entry = filteredEntities.get(index);

                LivingEntity living = getOrCreateEntity(entry.id);
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

                String text = entry.displayName + " §7(" + entry.id + ")";
                if (font.width(text) > listW - 22) {
                    text = font.plainSubstrByWidth(text, listW - 28) + "...";
                }
                guiGraphics.drawString(font, text, listX + 20, rowY + 6, rowHovered ? 0xFFFFFF : 0xBBBBBB, false);
            }
        }

        if (maxScrollRow > 0) {
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
                entityAreaX, topY, entityAreaX + entityAreaW, topY + entityAreaH,
                25,
                0.0f,
                (float) mouseX,
                (float) mouseY,
                player);

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

        String selectedEntityId = null;
        if (selectedInventorySlot >= 0) {
            ItemStack selectedStack = player.getInventory().getItem(selectedInventorySlot);
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
        int addBtnH = 20;
        int addBtnX = panelX + PADDING;
        int addBtnY = panelY + panelH - PADDING - addBtnH;

        boolean canAdd = selectedEntityId != null;

        boolean addHovered = canAdd && mouseX >= addBtnX && mouseX < addBtnX + addBtnW
                && mouseY >= addBtnY && mouseY < addBtnY + addBtnH;
        addHoverProgress = addHovered ? Math.min(1.0f, addHoverProgress + 0.1f)
                : Math.max(0.0f, addHoverProgress - 0.08f);

        if (canAdd) {
            renderStyledButton(guiGraphics, font, addBtnX, addBtnY, addBtnW, addBtnH, "Add Entity", addHoverProgress);
        } else {
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + addBtnH, 0x20FFFFFF);
            guiGraphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + 1, 0x10FFFFFF);
            boolean hasNonEggSelection = selectedInventorySlot >= 0 && selectedEntityId == null
                    && !player.getInventory().getItem(selectedInventorySlot).isEmpty();
            String addText = hasNonEggSelection ? "Not a Spawn Egg!" : "Select a Spawn Egg";
            int textColor = hasNonEggSelection ? 0xFFFF6666 : 0xFF666666;
            guiGraphics.drawString(font, addText, addBtnX + (addBtnW - font.width(addText)) / 2,
                    addBtnY + (addBtnH - 8) / 2, textColor, false);
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

    private void renderInventorySlot(GuiGraphics guiGraphics, Font font, int x, int y,
            ItemStack stack, int slotIndex, int mouseX, int mouseY, String placeholder) {
        boolean isEmpty = stack.isEmpty();
        boolean isSpawnEgg = !isEmpty && stack.getItem() instanceof SpawnEggItem;
        boolean isSelected = selectedInventorySlot == slotIndex;
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

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
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
            modelViewStack.popMatrix();
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
        String[] labels = { "Registry", "Inventory" };
        int x = panelX + PADDING;
        for (int i = 0; i < 2; i++) {
            int w = font.width(labels[i]) + TAB_PAD * 2;
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

        if (!inventoryMode && searchBar.mouseClicked(mouseX, mouseY))
            return true;

        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        int clickedTab = getTabAt(mouseX, mouseY);
        if (clickedTab == 0 && inventoryMode) {
            inventoryMode = false;
            selectedInventorySlot = -1;
            searchBar.setFocused(true);
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            recalcPanelSize();
            return true;
        }
        if (clickedTab == 1 && !inventoryMode) {
            inventoryMode = true;
            selectedInventorySlot = -1;
            searchBar.setFocused(false);
            searchBar.filters().close();
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            recalcPanelSize();
            return true;
        }

        if (inventoryMode) {
            return handleInventoryClick(mouseX, mouseY);
        } else {
            return handleRegistryClick(mouseX, mouseY);
        }
    }

    private boolean handleInventoryClick(double mouseX, double mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return true;

        int addBtnW = panelW - PADDING * 2;
        int addBtnH = 20;
        int addBtnX = panelX + PADDING;
        int addBtnY = panelY + panelH - PADDING - addBtnH;

        if (mouseX >= addBtnX && mouseX < addBtnX + addBtnW && mouseY >= addBtnY && mouseY < addBtnY + addBtnH) {
            if (selectedInventorySlot >= 0) {
                ItemStack stack = player.getInventory().getItem(selectedInventorySlot);
                String entityId = getEntityIdFromSpawnEgg(stack);
                if (entityId != null) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    onSelect.accept(entityId);
                    hide();
                    return true;
                }
            }
            return true;
        }

        int clickedSlot = getInventorySlotAt(mouseX, mouseY);
        if (clickedSlot >= 0) {
            ItemStack stack = player.getInventory().getItem(clickedSlot);
            if (!stack.isEmpty()) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                selectedInventorySlot = (selectedInventorySlot == clickedSlot) ? -1 : clickedSlot;
            }
            return true;
        }

        return true;
    }

    private boolean handleRegistryClick(double mouseX, double mouseY) {
        int topOffset = PADDING + TAB_HEIGHT + 4;

        if (maxScrollRow > 0) {
            int searchY = panelY + topOffset;
            int listY = searchY + SearchBar.HEIGHT + PADDING;
            int listW = panelW - PADDING * 2 - 8;
            int scrollBarX = panelX + PADDING + listW + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, listY);
                return true;
            }
        }

        int searchY = panelY + topOffset;
        int listX = panelX + PADDING;
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;
            if (index < filteredEntities.size() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onSelect.accept(filteredEntities.get(index).id);
                hide();
                return true;
            }
        }

        searchBar.setFocused(true);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar || inventoryMode)
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
        if (!visible || inventoryMode)
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

        if (inventoryMode) {
            if (keyCode == 256) {
                hide();
                return true;
            }
            if (keyCode == 257 && selectedInventorySlot >= 0) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    ItemStack stack = player.getInventory().getItem(selectedInventorySlot);
                    String entityId = getEntityIdFromSpawnEgg(stack);
                    if (entityId != null) {
                        Minecraft.getInstance().getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        onSelect.accept(entityId);
                        hide();
                        return true;
                    }
                }
            }
            return true;
        }

        if (searchBar.keyPressed(keyCode))
            return true;
        if (keyCode == 256) {
            hide();
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (!visible || inventoryMode)
            return false;
        return searchBar.charTyped(c);
    }

    private record EntityEntry(String id, String displayName, String searchName) {
    }
}
