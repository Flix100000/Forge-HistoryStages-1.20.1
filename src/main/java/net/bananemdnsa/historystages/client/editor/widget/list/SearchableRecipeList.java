package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.bananemdnsa.historystages.client.editor.widget.SearchPanelChrome;
import net.bananemdnsa.historystages.api.editor.widget.SearchBar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.RegistryAccess;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;

import net.bananemdnsa.historystages.data.lock.IndividualRecipeSupport;
import net.bananemdnsa.historystages.util.AllRecipesCache;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * JEI-like recipe browser overlay.
 * Phase 1: Item grid showing all items that have recipes.
 * Phase 2: Recipe list for the selected item with ingredient icons.
 */
public class SearchableRecipeList implements PickerOverlay {
    // Grid phase constants
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 5;
    private static final int PADDING = 6;

    // Recipe list phase constants
    private static final int RECIPE_ROW_HEIGHT = 22;
    private static final int RECIPE_VISIBLE_ROWS = 5;
    private static final int RECIPE_PANEL_WIDTH = 300;

    // State
    private boolean visible = false;
    private boolean inRecipePhase = false;
    private boolean keepVisibleOnSelect = false;

    // Which recipe row the cursor is over, so the tooltip knows whose id to show.
    private int hoveredRecipeIndex = -1;

    // Enough ingredient icons to tell recipes apart without crowding the namespace label off
    // the right edge; anything beyond is summarised as "+n".
    private static final int MAX_INGREDIENT_ICONS = 5;
    private static final int INGREDIENT_ICON_SPACING = 15;

    // Room for the one line explaining why an individual stage sees fewer recipes.
    private static final int SCOPE_HINT_HEIGHT = 11;

    // Phase 1: Item grid
    private final List<ItemEntry> allRecipeItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();
    private int panelX, panelY, panelW, panelH;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private boolean draggingScrollbar = false;

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
    // On an individual stage only recipes from stations that know the player can be gated, so the
    // picker shows only those. Read by buildRecipeIndex(), which the constructor runs.
    private final boolean individualScope;
    private final Consumer<String> onSelect;
    private final Supplier<Collection<String>> alreadyAddedSupplier;
    private final SearchBar searchBar;

    public SearchableRecipeList(Consumer<String> onSelect) {
        this(onSelect, null, false);
    }

    public SearchableRecipeList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        this(onSelect, alreadyAddedSupplier, false);
    }

    /**
     * @param individualScope true on an individual stage, where only stations that know who is
     *                        crafting can gate anything — the picker then offers only those
     *                        recipes rather than showing entries that would do nothing
     */
    public SearchableRecipeList(Consumer<String> onSelect,
                                Supplier<Collection<String>> alreadyAddedSupplier,
                                boolean individualScope) {
        this.onSelect = onSelect;
        this.alreadyAddedSupplier = alreadyAddedSupplier;
        this.individualScope = individualScope;
        this.searchBar = SearchPanelChrome.createSearchBar(Component.translatable("editor.historystages.search.placeholder.recipes").getString(), this::applyFilter, alreadyAddedSupplier);
        buildRecipeIndex();
    }

    private void buildRecipeIndex() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        RegistryAccess registryAccess = mc.level.registryAccess();
        Collection<RecipeHolder<?>> allCached = AllRecipesCache.get();
        Collection<RecipeHolder<?>> recipes = allCached.isEmpty()
                ? mc.level.getRecipeManager().getRecipes()
                : allCached;

        for (RecipeHolder<?> holder : recipes) {
            try {
                Recipe<?> recipe = holder.value();
                if (individualScope && !isIndividuallyGateable(recipe.getType()))
                    continue;

                ItemStack result = recipe.getResultItem(registryAccess);
                if (result.isEmpty())
                    continue;

                ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(result.getItem());
                if (itemKey == null)
                    continue;

                String outputId = itemKey.toString();
                ResourceLocation recipeId = holder.id();

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
            }
        }

        Map<String, Integer> registryOrder = new HashMap<>();
        int idx = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
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

    /**
     * Whether an individual stage could gate this recipe at all. The answer comes from
     * {@link IndividualRecipeSupport}, which the load-time audit reads too — one list, two readers.
     */
    private static boolean isIndividuallyGateable(RecipeType<?> type) {
        ResourceLocation typeKey = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return typeKey != null && IndividualRecipeSupport.supports(typeKey.toString());
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
        panelH = SearchBar.HEIGHT + PADDING * 2 + GRID_ROWS * SLOT_SIZE + PADDING + 4
                + (individualScope ? SCOPE_HINT_HEIGHT : 0);
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        if (panelX < 4)
            panelX = 4;
        if (panelY < 4)
            panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        searchBar.setFocused(true);
        searchBar.setText("");
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
        searchBar.setText(f);
    }

    private void applyFilter(String filter) {
        this.scrollRow = 0;
        filteredItems.clear();
        boolean modSearch = filter.startsWith("@");
        String modFilter = modSearch ? filter.substring(1) : "";
        for (ItemEntry entry : allRecipeItems) {
            if (!matchesDropdownFilters(entry))
                continue;
            if (filter.isEmpty()) {
                filteredItems.add(entry);
            } else if (modSearch) {
                String modId = entry.id.contains(":") ? entry.id.substring(0, entry.id.indexOf(':')) : "";
                if (modId.contains(modFilter))
                    filteredItems.add(entry);
            } else if (entry.id.contains(filter) || entry.searchName.contains(filter)) {
                filteredItems.add(entry);
            }
        }
        updateMaxScroll();
    }

    private boolean matchesDropdownFilters(ItemEntry entry) {
        // Custom "hide already added" semantics: check recipe ids by output, not the entry id.
        if (searchBar.filters().isActive("hide_added") && alreadyAddedSupplier != null) {
            Collection<String> added = alreadyAddedSupplier.get();
            if (added != null) {
                List<RecipeInfo> recipes = recipesByOutput.get(entry.id);
                if (recipes != null && !recipes.isEmpty()) {
                    boolean anyUnadded = false;
                    for (RecipeInfo r : recipes) {
                        if (!added.contains(r.recipeId)) {
                            anyUnadded = true;
                            break;
                        }
                    }
                    if (!anyUnadded) return false;
                }
            }
        }
        return SearchPanelChrome.passesNamespaceFilters(searchBar, entry.id);
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
        SearchPanelChrome.renderFrame(guiGraphics, panelX, panelY, panelW, panelH);

        int searchX = panelX + PADDING;
        int searchY = panelY + PADDING;
        searchBar.setPosition(searchX, searchY, panelW - PADDING * 2);
        searchBar.render(guiGraphics, font, mouseX, mouseY);

        int gridX = panelX + PADDING + 4;
        int gridY = searchY + SearchBar.HEIGHT + PADDING;
        int startIndex = scrollRow * GRID_COLS;

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;

                boolean slotHovered = !filterUiHovered && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
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

        // Says why there is less here than on a global stage, rather than leaving the author to
        // wonder whether their recipe is missing.
        if (individualScope) {
            String hint = Component.translatable("editor.historystages.recipes.individual_only").getString();
            guiGraphics.drawString(font, hint, panelX + (panelW - font.width(hint)) / 2,
                    gridY + GRID_ROWS * SLOT_SIZE + 3, 0xFF888888, false);
        }

        // Tooltip for hovered item — suppressed when filter UI overlaps the cursor.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        if (!filterUiHovered) {
            for (int row = 0; row < GRID_ROWS; row++) {
                for (int col = 0; col < GRID_COLS; col++) {
                    int index = startIndex + row * GRID_COLS + col;
                    int slotX = gridX + col * SLOT_SIZE;
                    int slotY = gridY + row * SLOT_SIZE;
                    if (index < filteredItems.size() && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                            && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                        ItemEntry entry = filteredItems.get(index);
                        String name = entry.stack.getHoverName().getString();
                        String tooltipText = name + " §7(" + entry.recipeCount + " recipes)";
                        int tooltipW = font.width(tooltipText) + 8;
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
                        guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH,
                                0xFF3D3D3D);
                        guiGraphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipW + 1, tooltipY + tooltipH - 1,
                                0xFF0D0D0D);
                        guiGraphics.drawString(font, tooltipText, tooltipX + 2, tooltipY + 2, 0xFFFFFF, false);
                    }
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderRecipePhase(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        SearchPanelChrome.renderFrame(guiGraphics, recipePanelX, recipePanelY, recipePanelW, recipePanelH);

        int headerY = recipePanelY + PADDING;
        guiGraphics.renderItem(selectedItemStack, recipePanelX + PADDING, headerY);
        guiGraphics.drawString(font, selectedItemName, recipePanelX + PADDING + 20, headerY + 4, 0xFFFFFF, false);

        String backHint = "§7[ESC] Back";
        int backW = font.width(backHint);
        guiGraphics.drawString(font, backHint, recipePanelX + recipePanelW - PADDING - backW, headerY + 4, 0x888888,
                false);

        int listY = headerY + 20 + PADDING;
        int listX = recipePanelX + PADDING;
        int listW = recipePanelW - PADDING * 2 - 8;

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

                int typeColor = getRecipeTypeColor(recipe.type);
                guiGraphics.fill(listX, rowY + 1, listX + 2, rowY + RECIPE_ROW_HEIGHT, typeColor);

                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(listX + 5, rowY + 3, 0);
                guiGraphics.pose().scale(0.85f, 0.85f, 1.0f);
                guiGraphics.renderItem(recipe.result, 0, 0);
                guiGraphics.pose().popPose();

                // Ingredients, not the raw id. By this phase the output item is already chosen,
                // so what tells two recipes apart is what goes into them — while the id was the
                // least useful thing in the row and took the whole width to say nothing. It is
                // worst for script-generated recipes, where "kubejs:crafting_shaped_7" is all
                // there was to read.
                int iconX = listX + 22;
                int shown = Math.min(MAX_INGREDIENT_ICONS, recipe.ingredients.size());
                for (int ing = 0; ing < shown; ing++) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(iconX + ing * INGREDIENT_ICON_SPACING, rowY + 3, 0);
                    guiGraphics.pose().scale(0.8f, 0.8f, 1.0f);
                    guiGraphics.renderItem(recipe.ingredients.get(ing), 0, 0);
                    guiGraphics.pose().popPose();
                }
                if (recipe.ingredients.size() > shown) {
                    guiGraphics.drawString(font, "+" + (recipe.ingredients.size() - shown),
                            iconX + shown * INGREDIENT_ICON_SPACING, rowY + 7, 0x888888, false);
                }

                // Namespace on the right: the orientation that used to be buried in the id,
                // without the rest of it. "kubejs" and "crafttweaker" show up here as themselves.
                String namespace = recipe.recipeId.contains(":")
                        ? recipe.recipeId.substring(0, recipe.recipeId.indexOf(':'))
                        : "";
                if (!namespace.isEmpty()) {
                    int nsW = font.width(namespace);
                    guiGraphics.drawString(font, namespace, listX + listW - nsW - 2, rowY + 7,
                            rowHovered ? 0xAAAAAA : 0x666666, false);
                }
            }
        }

        hoveredRecipeIndex = currentHoveredIndex;

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

        // The id still matters to whoever is debugging a lock, so it moves into the tooltip
        // rather than off the screen. Drawn last and lifted, or the rows below paint over it.
        if (hoveredRecipeIndex >= 0 && hoveredRecipeIndex < currentRecipes.size()) {
            RecipeInfo hovered = currentRecipes.get(hoveredRecipeIndex);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 300);
            guiGraphics.renderTooltip(font,
                    List.of(Component.literal(hovered.recipeId),
                            Component.literal(hovered.type).withStyle(ChatFormatting.DARK_GRAY)),
                    Optional.empty(), mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
    }

    private static int getRecipeTypeColor(String type) {
        if (type.contains("crafting"))
            return 0xFFFFCC00;
        if (type.contains("smelting"))
            return 0xFFFF6600;
        if (type.contains("blasting"))
            return 0xFFFF3300;
        if (type.contains("smoking"))
            return 0xFF996633;
        if (type.contains("campfire"))
            return 0xFFFF9900;
        if (type.contains("stonecutting"))
            return 0xFF999999;
        if (type.contains("smithing"))
            return 0xFF4499CC;
        return 0xFF66CC66;
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
        if (searchBar.mouseClicked(mouseX, mouseY))
            return true;
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        if (maxScrollRow > 0) {
            int searchY = panelY + PADDING;
            int gridX = panelX + PADDING + 4;
            int gridY = searchY + SearchBar.HEIGHT + PADDING;
            int scrollBarX = gridX + GRID_COLS * SLOT_SIZE + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= gridY && mouseY < gridY + GRID_ROWS * SLOT_SIZE) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, gridY);
                return true;
            }
        }

        int searchY = panelY + PADDING;
        int gridX = panelX + PADDING + 4;
        int gridY = searchY + SearchBar.HEIGHT + PADDING;
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
        searchBar.setFocused(true);
        return true;
    }

    private boolean mouseClickedRecipePhase(double mouseX, double mouseY) {
        if (mouseX < recipePanelX || mouseX > recipePanelX + recipePanelW
                || mouseY < recipePanelY || mouseY > recipePanelY + recipePanelH) {
            hide();
            return true;
        }

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
            int gridY = searchY + SearchBar.HEIGHT + PADDING;
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

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double delta = scrollY;
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
            // In recipe phase, ESC backs out to item grid. In item phase, defer
            // to searchBar (so an open filter popup closes first) then hide().
            if (inRecipePhase) {
                inRecipePhase = false;
                return true;
            }
            if (searchBar.keyPressed(keyCode))
                return true;
            hide();
            return true;
        }

        if (inRecipePhase)
            return false;

        return searchBar.keyPressed(keyCode);
    }

    public boolean charTyped(char c) {
        if (!visible || inRecipePhase)
            return false;
        return searchBar.charTyped(c);
    }

    private record ItemEntry(String id, ItemStack stack, int recipeCount, String searchName) {
    }

    private record RecipeInfo(String recipeId, ItemStack result, List<ItemStack> ingredients, String type,
            ItemStack workstation) {
    }
}
