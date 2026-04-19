package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.util.AllRecipesCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;

/**
 * JEI-like recipe browser overlay.
 * Phase 1: Item grid showing all items that have recipes.
 * Phase 2: Recipe list for the selected item with ingredient icons.
 */
public class SearchableRecipeList {
    // Grid phase constants
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 5;
    private static final int SEARCH_HEIGHT = 20;
    private static final int PADDING = 6;

    // Recipe list phase constants
    private static final int RECIPE_ROW_HEIGHT = 22;
    private static final int RECIPE_VISIBLE_ROWS = 5;
    private static final int RECIPE_PANEL_WIDTH = 300;

    // State
    private boolean visible = false;
    private boolean inRecipePhase = false;
    private boolean keepVisibleOnSelect = false;

    // Marquee scroll state for truncated recipe IDs
    private int hoveredRecipeIndex = -1;
    private long hoverStartTime = 0;
    private static final long MARQUEE_DELAY_MS = 600;
    private static final float MARQUEE_SPEED = 30.0f; // pixels per second

    // Phase 1: Item grid
    private final List<ItemEntry> allRecipeItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();
    private int panelX, panelY, panelW, panelH;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private String filter = "";
    private boolean searchFocused = true;
    private boolean draggingScrollbar = false;
    private boolean allSelected = false;

    // Phase 2: Recipe list
    private final List<RecipeInfo> currentRecipes = new ArrayList<>();
    private int recipePanelX, recipePanelY, recipePanelW, recipePanelH;
    private int recipeScrollRow = 0;
    private int recipeMaxScrollRow = 0;
    private boolean recipeDraggingScrollbar = false;
    private ItemStack selectedItemStack = ItemStack.EMPTY;
    private String selectedItemName = "";

    // Recipe data: maps output item ID -> list of recipe IDs
    private final Map<String, List<RecipeInfo>> recipesByOutput = new LinkedHashMap<>();
    private final Consumer<String> onSelect;

    public SearchableRecipeList(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        buildRecipeIndex();
    }

    private void buildRecipeIndex() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        RegistryAccess registryAccess = mc.level.registryAccess();
        // Use unfiltered recipes so locked recipes are still visible in the editor
        Collection<Recipe<?>> allCached = AllRecipesCache.get();
        Collection<Recipe<?>> recipes = allCached.isEmpty()
                ? mc.level.getRecipeManager().getRecipes()
                : allCached;

        for (Recipe<?> recipe : recipes) {
            try {
                ItemStack result = recipe.getResultItem(registryAccess);
                if (result.isEmpty())
                    continue;

                ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(result.getItem());
                if (itemKey == null)
                    continue;

                String outputId = itemKey.toString();
                ResourceLocation recipeId = recipe.getId();

                // Collect ingredient stacks (first item of each ingredient)
                List<ItemStack> ingredientStacks = new ArrayList<>();
                for (Ingredient ingredient : recipe.getIngredients()) {
                    ItemStack[] items = ingredient.getItems();
                    if (items.length > 0) {
                        ingredientStacks.add(items[0]);
                    }
                }

                ItemStack workstation = getWorkstationForType(recipe.getType());
                RecipeInfo info = new RecipeInfo(recipeId.toString(), result, ingredientStacks,
                        recipe.getType().toString(), workstation);
                recipesByOutput.computeIfAbsent(outputId, k -> new ArrayList<>()).add(info);
            } catch (Exception ignored) {
                // Skip problematic recipes
            }
        }

        // Build item list from recipe outputs, sorted by registry order (like creative
        // tabs)
        Map<String, Integer> registryOrder = new HashMap<>();
        int idx = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null)
                registryOrder.put(key.toString(), idx++);
        }

        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, List<RecipeInfo>> entry : recipesByOutput.entrySet()) {
            if (seen.add(entry.getKey())) {
                ItemStack stack = entry.getValue().get(0).result;
                allRecipeItems.add(new ItemEntry(entry.getKey(), stack, entry.getValue().size(),
                        stack.getHoverName().getString().toLowerCase()));
            }
        }
        allRecipeItems.sort((a, b) -> {
            int orderA = registryOrder.getOrDefault(a.id, Integer.MAX_VALUE);
            int orderB = registryOrder.getOrDefault(b.id, Integer.MAX_VALUE);
            return Integer.compare(orderA, orderB);
        });
        filteredItems.addAll(allRecipeItems);
    }

    private static ItemStack getWorkstationForType(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING)
            return new ItemStack(Blocks.CRAFTING_TABLE);
        if (type == RecipeType.SMELTING)
            return new ItemStack(Blocks.FURNACE);
        if (type == RecipeType.BLASTING)
            return new ItemStack(Blocks.BLAST_FURNACE);
        if (type == RecipeType.SMOKING)
            return new ItemStack(Blocks.SMOKER);
        if (type == RecipeType.CAMPFIRE_COOKING)
            return new ItemStack(Blocks.CAMPFIRE);
        if (type == RecipeType.STONECUTTING)
            return new ItemStack(Blocks.STONECUTTER);
        if (type == RecipeType.SMITHING)
            return new ItemStack(Blocks.SMITHING_TABLE);
        return ItemStack.EMPTY;
    }

    public void show(int centerX, int centerY, int parentWidth) {
        inRecipePhase = false;
        panelW = GRID_COLS * SLOT_SIZE + PADDING * 2 + 8;
        panelH = SEARCH_HEIGHT + PADDING * 2 + GRID_ROWS * SLOT_SIZE + PADDING + 4;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        if (panelX < 4)
            panelX = 4;
        if (panelY < 4)
            panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        this.filter = "";
        this.searchFocused = true;
        this.allSelected = false;
        filteredItems.clear();
        filteredItems.addAll(allRecipeItems);
        updateMaxScroll();
    }

    public void hide() {
        this.visible = false;
        this.inRecipePhase = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setKeepVisibleOnSelect(boolean keep) {
        this.keepVisibleOnSelect = keep;
    }

    public void setFilter(String f) {
        this.filter = f.toLowerCase();
        this.scrollRow = 0;
        filteredItems.clear();
        if (this.filter.isEmpty()) {
            filteredItems.addAll(allRecipeItems);
        } else if (this.filter.startsWith("@")) {
            String modFilter = this.filter.substring(1);
            for (ItemEntry entry : allRecipeItems) {
                String modId = entry.id.contains(":") ? entry.id.substring(0, entry.id.indexOf(':')) : "";
                if (modId.contains(modFilter))
                    filteredItems.add(entry);
            }
        } else {
            for (ItemEntry entry : allRecipeItems) {
                if (entry.id.contains(this.filter) || entry.searchName.contains(this.filter)) {
                    filteredItems.add(entry);
                }
            }
        }
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        int totalRows = (filteredItems.size() + GRID_COLS - 1) / GRID_COLS;
        maxScrollRow = Math.max(0, totalRows - GRID_ROWS);
    }

    private void showRecipesForItem(String itemId) {
        List<RecipeInfo> recipes = recipesByOutput.get(itemId);
        if (recipes == null || recipes.isEmpty())
            return;

        currentRecipes.clear();
        currentRecipes.addAll(recipes);
        selectedItemStack = recipes.get(0).result;
        selectedItemName = selectedItemStack.getHoverName().getString();

        inRecipePhase = true;
        recipeScrollRow = 0;
        recipePanelW = RECIPE_PANEL_WIDTH;
        recipePanelH = 20 + PADDING * 2 + RECIPE_VISIBLE_ROWS * RECIPE_ROW_HEIGHT + PADDING + 4;
        recipePanelX = panelX + (panelW - recipePanelW) / 2;
        recipePanelY = panelY;
        if (recipePanelX < 4)
            recipePanelX = 4;
        if (recipePanelY < 4)
            recipePanelY = 4;

        recipeMaxScrollRow = Math.max(0, currentRecipes.size() - RECIPE_VISIBLE_ROWS);
    }

    // ========== RENDERING ==========

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible)
            return;
        if (inRecipePhase) {
            renderRecipePhase(guiGraphics, font, mouseX, mouseY);
        } else {
            renderItemPhase(guiGraphics, font, mouseX, mouseY);
        }
    }

    private void renderItemPhase(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        // Panel background
        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        // Search bar
        int searchX = panelX + PADDING;
        int searchY = panelY + PADDING;
        int searchW = panelW - PADDING * 2;
        guiGraphics.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + SEARCH_HEIGHT + 1, 0xFF4A4A4A);
        guiGraphics.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, 0xFF0D0D0D);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        String displayFilter = filter.isEmpty() ? "\u00A77Search recipes..." : filter;
        if (allSelected && !filter.isEmpty()) {
            int textW = font.width(filter);
            guiGraphics.fill(searchX + 3, searchY + 3, searchX + 5 + textW, searchY + SEARCH_HEIGHT - 3, 0xFF4A6A9A);
        }
        guiGraphics.drawString(font, displayFilter, searchX + 4, searchY + 6, filter.isEmpty() ? 0x666666 : 0xFFFFFF,
                false);
        if (searchFocused && !allSelected && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = searchX + 4 + (filter.isEmpty() ? 0 : font.width(filter));
            guiGraphics.fill(cursorX, searchY + 4, cursorX + 1, searchY + SEARCH_HEIGHT - 4, 0xFFFFFFFF);
        }
        guiGraphics.pose().popPose();

        // Item grid
        int gridX = panelX + PADDING + 4;
        int gridY = searchY + SEARCH_HEIGHT + PADDING;
        int startIndex = scrollRow * GRID_COLS;

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;

                boolean slotHovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE,
                        slotHovered ? 0xFF4A4A4A : 0xFF252525);
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                        slotHovered ? 0xFF353535 : 0xFF1A1A1A);

                if (index < filteredItems.size()) {
                    guiGraphics.renderItem(filteredItems.get(index).stack, slotX + 1, slotY + 1);
                }
            }
        }

        // Scrollbar
        if (maxScrollRow > 0) {
            int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
            int scrollBarTop = gridY;
            int scrollBarBottom = gridY + GRID_ROWS * SLOT_SIZE;
            int scrollBarHeight = scrollBarBottom - scrollBarTop;
            guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);
            int thumbHeight = Math.max(10, (int) ((float) GRID_ROWS / (maxScrollRow + GRID_ROWS) * scrollBarHeight));
            int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarHeight - thumbHeight));
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
        }

        // Tooltip for hovered item
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;
                if (index < filteredItems.size() && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    ItemEntry entry = filteredItems.get(index);
                    String name = entry.stack.getHoverName().getString();
                    String tooltipText = name + " \u00A77(" + entry.recipeCount + " recipes)";
                    int tooltipW = font.width(tooltipText) + 8;
                    int tooltipH = 16;
                    int screenW = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
                    int screenH = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
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
                    guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH,
                            0xFF3D3D3D);
                    guiGraphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipW + 1, tooltipY + tooltipH - 1,
                            0xFF0D0D0D);
                    guiGraphics.drawString(font, tooltipText, tooltipX + 2, tooltipY + 2, 0xFFFFFF, false);
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderRecipePhase(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        // Panel background
        guiGraphics.fill(recipePanelX - 2, recipePanelY - 2, recipePanelX + recipePanelW + 2,
                recipePanelY + recipePanelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(recipePanelX, recipePanelY, recipePanelX + recipePanelW, recipePanelY + recipePanelH,
                0xFF1A1A1A);

        // Header with item icon and name + back hint
        int headerY = recipePanelY + PADDING;
        guiGraphics.renderItem(selectedItemStack, recipePanelX + PADDING, headerY);
        guiGraphics.drawString(font, selectedItemName, recipePanelX + PADDING + 20, headerY + 4, 0xFFFFFF, false);

        // Back hint (top right)
        String backHint = "\u00A77[ESC] Back";
        int backW = font.width(backHint);
        guiGraphics.drawString(font, backHint, recipePanelX + recipePanelW - PADDING - backW, headerY + 4, 0x888888,
                false);

        // Recipe list
        int listY = headerY + 20 + PADDING;
        int listX = recipePanelX + PADDING;
        int listW = recipePanelW - PADDING * 2 - 8;

        // Track which recipe row is hovered for marquee
        int currentHoveredIndex = -1;

        for (int i = 0; i < RECIPE_VISIBLE_ROWS; i++) {
            int index = recipeScrollRow + i;
            int rowY = listY + i * RECIPE_ROW_HEIGHT;

            boolean rowHovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + RECIPE_ROW_HEIGHT;

            if (rowHovered && index < currentRecipes.size())
                currentHoveredIndex = index;

            guiGraphics.fill(listX, rowY, listX + listW, rowY + RECIPE_ROW_HEIGHT,
                    rowHovered ? 0xFF353535 : 0xFF252525);

            if (index < currentRecipes.size()) {
                RecipeInfo recipe = currentRecipes.get(index);

                // Recipe type color bar (left edge, 2px wide)
                int typeColor = getRecipeTypeColor(recipe.type);
                guiGraphics.fill(listX, rowY + 1, listX + 2, rowY + RECIPE_ROW_HEIGHT, typeColor);

                // Result icon
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(listX + 5, rowY + 3, 0);
                guiGraphics.pose().scale(0.85f, 0.85f, 1.0f);
                guiGraphics.renderItem(recipe.result, 0, 0);
                guiGraphics.pose().popPose();

                // Recipe ID with marquee scroll for truncated text
                String fullText = recipe.recipeId;
                int textX = listX + 22;
                int availW = listX + listW - textX - 2;
                int fullTextW = font.width(fullText);

                if (fullTextW > availW && rowHovered && index == hoveredRecipeIndex) {
                    // Marquee scroll: clip text area and scroll
                    long elapsed = System.currentTimeMillis() - hoverStartTime;
                    if (elapsed > MARQUEE_DELAY_MS) {
                        float scrollProgress = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                        int maxScroll = fullTextW - availW + 10;
                        // Ping-pong: scroll right then back left
                        float cycle = (float) maxScroll * 2;
                        float pos = scrollProgress % cycle;
                        int scrollOff = pos <= maxScroll ? (int) pos : (int) (cycle - pos);

                        guiGraphics.enableScissor(textX, rowY, textX + availW, rowY + RECIPE_ROW_HEIGHT);
                        guiGraphics.drawString(font, fullText, textX - scrollOff, rowY + 7, 0xFFFFFF, false);
                        guiGraphics.disableScissor();
                    } else {
                        // Still in delay, show truncated
                        String truncated = font.plainSubstrByWidth(fullText, availW - 8) + "...";
                        guiGraphics.drawString(font, truncated, textX, rowY + 7, 0xFFFFFF, false);
                    }
                } else if (fullTextW > availW) {
                    String truncated = font.plainSubstrByWidth(fullText, availW - 8) + "...";
                    guiGraphics.drawString(font, truncated, textX, rowY + 7, rowHovered ? 0xFFFFFF : 0xBBBBBB, false);
                } else {
                    guiGraphics.drawString(font, fullText, textX, rowY + 7, rowHovered ? 0xFFFFFF : 0xBBBBBB, false);
                }
            }
        }

        // Update marquee hover tracking
        if (currentHoveredIndex != hoveredRecipeIndex) {
            hoveredRecipeIndex = currentHoveredIndex;
            hoverStartTime = System.currentTimeMillis();
        }

        // Scrollbar
        if (recipeMaxScrollRow > 0) {
            int scrollBarX = listX + listW + 2;
            int scrollBarTop = listY;
            int scrollBarBottom = listY + RECIPE_VISIBLE_ROWS * RECIPE_ROW_HEIGHT;
            int scrollBarHeight = scrollBarBottom - scrollBarTop;
            guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);
            int thumbHeight = Math.max(10,
                    (int) ((float) RECIPE_VISIBLE_ROWS / (recipeMaxScrollRow + RECIPE_VISIBLE_ROWS) * scrollBarHeight));
            int thumbY = scrollBarTop
                    + (int) ((float) recipeScrollRow / recipeMaxScrollRow * (scrollBarHeight - thumbHeight));
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
        }
    }

    private static int getRecipeTypeColor(String type) {
        if (type.contains("crafting"))
            return 0xFFFFCC00; // Gold - crafting
        if (type.contains("smelting"))
            return 0xFFFF6600; // Orange - smelting
        if (type.contains("blasting"))
            return 0xFFFF3300; // Red-orange - blasting
        if (type.contains("smoking"))
            return 0xFF996633; // Brown - smoking
        if (type.contains("campfire"))
            return 0xFFFF9900; // Warm orange - campfire
        if (type.contains("stonecutting"))
            return 0xFF999999; // Gray - stonecutting
        if (type.contains("smithing"))
            return 0xFF4499CC; // Blue - smithing
        return 0xFF66CC66; // Green - modded/unknown
    }

    // ========== INPUT ==========

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible)
            return false;

        if (inRecipePhase) {
            return mouseClickedRecipePhase(mouseX, mouseY);
        }
        return mouseClickedItemPhase(mouseX, mouseY);
    }

    private boolean mouseClickedItemPhase(double mouseX, double mouseY) {
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        // Scrollbar
        if (maxScrollRow > 0) {
            int searchY = panelY + PADDING;
            int gridX = panelX + PADDING + 4;
            int gridY = searchY + SEARCH_HEIGHT + PADDING;
            int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= gridY && mouseY < gridY + GRID_ROWS * SLOT_SIZE) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, gridY);
                return true;
            }
        }

        // Grid clicks
        int searchY = panelY + PADDING;
        int gridX = panelX + PADDING + 4;
        int gridY = searchY + SEARCH_HEIGHT + PADDING;
        int startIndex = scrollRow * GRID_COLS;

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;
                if (index < filteredItems.size() && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    showRecipesForItem(filteredItems.get(index).id);
                    return true;
                }
            }
        }
        searchFocused = true;
        return true;
    }

    private boolean mouseClickedRecipePhase(double mouseX, double mouseY) {
        if (mouseX < recipePanelX || mouseX > recipePanelX + recipePanelW
                || mouseY < recipePanelY || mouseY > recipePanelY + recipePanelH) {
            hide();
            return true;
        }

        // Scrollbar
        if (recipeMaxScrollRow > 0) {
            int headerY = recipePanelY + PADDING;
            int listY = headerY + 20 + PADDING;
            int listW = recipePanelW - PADDING * 2 - 8;
            int scrollBarX = recipePanelX + PADDING + listW + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= listY && mouseY < listY + RECIPE_VISIBLE_ROWS * RECIPE_ROW_HEIGHT) {
                recipeDraggingScrollbar = true;
                updateRecipeScrollFromMouse(mouseY, listY);
                return true;
            }
        }

        // Recipe row clicks
        int headerY = recipePanelY + PADDING;
        int listY = headerY + 20 + PADDING;
        int listX = recipePanelX + PADDING;
        int listW = recipePanelW - PADDING * 2 - 8;

        for (int i = 0; i < RECIPE_VISIBLE_ROWS; i++) {
            int index = recipeScrollRow + i;
            int rowY = listY + i * RECIPE_ROW_HEIGHT;
            if (index < currentRecipes.size() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + RECIPE_ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onSelect.accept(currentRecipes.get(index).recipeId);
                if (!keepVisibleOnSelect)
                    hide();
                return true;
            }
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible)
            return false;
        if (inRecipePhase && recipeDraggingScrollbar) {
            int headerY = recipePanelY + PADDING;
            int listY = headerY + 20 + PADDING;
            updateRecipeScrollFromMouse(mouseY, listY);
            return true;
        }
        if (!inRecipePhase && draggingScrollbar) {
            int searchY = panelY + PADDING;
            int gridY = searchY + SEARCH_HEIGHT + PADDING;
            updateScrollFromMouse(mouseY, gridY);
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (recipeDraggingScrollbar) {
            recipeDraggingScrollbar = false;
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double mouseY, int gridY) {
        int gridH = GRID_ROWS * SLOT_SIZE;
        int totalRows = maxScrollRow + GRID_ROWS;
        int thumbHeight = Math.max(10, (int) ((float) GRID_ROWS / totalRows * gridH));
        float usableH = gridH - thumbHeight;
        if (usableH > 0) {
            float ratio = (float) (mouseY - gridY - thumbHeight / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollRow = Math.round(ratio * maxScrollRow);
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow));
        }
    }

    private void updateRecipeScrollFromMouse(double mouseY, int listY) {
        int listH = RECIPE_VISIBLE_ROWS * RECIPE_ROW_HEIGHT;
        float ratio = (float) Math.max(0, Math.min(1, (mouseY - listY) / (double) listH));
        recipeScrollRow = Math.round(ratio * recipeMaxScrollRow);
        recipeScrollRow = Math.max(0, Math.min(recipeMaxScrollRow, recipeScrollRow));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible)
            return false;
        if (inRecipePhase) {
            if (mouseX >= recipePanelX && mouseX <= recipePanelX + recipePanelW
                    && mouseY >= recipePanelY && mouseY <= recipePanelY + recipePanelH) {
                recipeScrollRow = Math.max(0, Math.min(recipeMaxScrollRow, recipeScrollRow - (int) delta));
                return true;
            }
            return false;
        }
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
            if (inRecipePhase) {
                inRecipePhase = false; // Go back to item grid
                return true;
            }
            hide();
            return true;
        }

        if (inRecipePhase)
            return false; // No typing in recipe phase

        if (!searchFocused)
            return false;
        if (keyCode == 259) {
            if (allSelected) {
                allSelected = false;
                setFilter("");
            } else if (!filter.isEmpty()) {
                setFilter(filter.substring(0, filter.length() - 1));
            }
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 65) {
            if (!filter.isEmpty())
                allSelected = true;
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 67) {
            if (!filter.isEmpty())
                Minecraft.getInstance().keyboardHandler.setClipboard(filter);
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 86) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                setFilter(allSelected ? clipboard : filter + clipboard);
                allSelected = false;
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (!visible || inRecipePhase || !searchFocused)
            return false;
        if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '.' || c == ' ' || c == '-' || c == '@') {
            setFilter(allSelected ? String.valueOf(c) : filter + c);
            allSelected = false;
            return true;
        }
        return false;
    }

    // ========== Data types ==========
    private record ItemEntry(String id, ItemStack stack, int recipeCount, String searchName) {
    }

    private record RecipeInfo(String recipeId, ItemStack result, List<ItemStack> ingredients, String type,
            ItemStack workstation) {
    }
}
