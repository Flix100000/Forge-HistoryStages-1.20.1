package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.editor.widget.SearchBar;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.recipe.CardStackGeometry;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeCardLayout;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeCardRenderer;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeFluids;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeShape;
import net.bananemdnsa.historystages.client.editor.widget.FluidIcon;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.GridGeometry;
import net.bananemdnsa.historystages.client.editor.widget.ItemSlotGrid;
import net.bananemdnsa.historystages.client.editor.widget.SearchPanelChrome;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import net.bananemdnsa.historystages.client.ClientFluidRecipeIndex;
import net.bananemdnsa.historystages.data.lock.FluidRecipeIndex;
import net.bananemdnsa.historystages.data.lock.FluidRecipeScanner;
import net.bananemdnsa.historystages.data.lock.IndividualRecipeSupport;
import net.bananemdnsa.historystages.util.AllRecipesCache;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The recipe picker: an item grid on the left, that item's recipes as real cards on the right.
 *
 * <p>It used to be two phases — pick an item, then a separate panel of one-line rows. A row could
 * not show a pattern, so two shaped recipes with the same ingredients were indistinguishable, and
 * getting back to the grid meant leaving the recipes. Both columns are on screen at once now, and
 * every layout question is answered by {@link GridGeometry} and {@link CardStackGeometry}, which
 * are unit tested.
 */
public class SearchableRecipeList implements PickerOverlay {

    private static final int PADDING = 6;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_PAD = 6;
    private static final int DETAIL_GAP = 8;
    private static final int MASTER_COLS = 9;
    /** Seven grid rows. Also the height of the detail column, so the two columns line up. */
    private static final int CONTENT_HEIGHT = 126;
    /** Matches the item picker, so the two panels read as the same family. */
    private static final int ADD_BTN_H = 20;
    private static final int ADD_BTN_W = 120;
    private static final int SELECTALL_ROW_H = 18;
    private static final int SELECTALL_BTN_H = 14;
    private static final int SELECTALL_BTN_GAP = 4;
    /** Item icon and name above the detail column's cards. */
    private static final int DETAIL_HEADER_H = 20;
    private static final int HINT_LINE_H = 10;
    private static final int NOTE_H = 10;
    private static final int DETAIL_SCROLLBAR_W = 4;

    private static final int TAB_RECIPES = 0;
    private static final int TAB_SELECTED = 1;

    // State
    private boolean visible = false;
    private int panelX, panelY, panelW, panelH;
    private int currentTab = TAB_RECIPES;

    // Master column
    private final List<ItemEntry> allRecipeItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private boolean draggingScrollbar = false;
    private int scrollbarGrabOffset = 0;

    /** Which item the detail column is showing, or null before anything is clicked. */
    private String selectedItemId = null;
    /** Detail column scroll, in pixels — cards differ in height, so rows are no use here. */
    private int detailScroll = 0;
    /** The visible recipes for {@link #selectedItemId}, after the active filters. */
    private final List<RecipeInfo> detailRecipes = new ArrayList<>();
    /** Their heights, in the same order, so CardStackGeometry can do the maths. */
    private final List<Integer> detailHeights = new ArrayList<>();
    /** How many of this item's recipes the active filters dropped, for the note under the list. */
    private int hiddenByFilter = 0;
    private boolean draggingDetailScrollbar = false;

    /**
     * Chosen recipes, keyed by recipe id, in click order — the Add button fires the callback once
     * per entry in that order. The RecipeInfo is kept alongside so the Selected tab can draw a
     * card for a recipe whose item is no longer in the filtered grid.
     */
    private final LinkedHashMap<String, RecipeInfo> selected = new LinkedHashMap<>();

    /**
     * Frozen copy of {@link #selected}, taken when the Selected tab is entered. Deselecting
     * mutates {@code selected} but leaves this alone, so cards don't shift under the cursor and a
     * misclick can be undone. Cleared on tab exit, rebuilt on next entry.
     */
    private final List<RecipeInfo> selectedSnapshot = new ArrayList<>();

    private final Anim addHoverProgress = new Anim();
    private final Anim tabIndicatorX = new Anim();
    private final Anim tabIndicatorW = new Anim();
    private boolean tabIndicatorInit = false;

    // Recipe data: maps output item ID -> list of recipes producing it
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

        // The picker cannot see a fluid-producing recipe without this: the index is built only
        // for packs that already gate a fluid unless somebody asks for it.
        FluidRecipeIndex.requestForEditor();
        ClientFluidRecipeIndex.refresh();

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
                ResourceLocation recipeId = holder.id();
                Map<String, Set<FluidRecipeScanner.Position>> sides =
                        FluidRecipeIndex.fluidsIn(recipeId.toString());
                List<String> fluidOutputs = RecipeFluids.definiteOutputs(sides);
                List<RecipeFluids.Ref> fluidInputs = RecipeFluids.ingredientRow(sides);

                // A recipe with neither an item nor a certain fluid result can be filed nowhere,
                // and stays out exactly as it did before.
                if (result.isEmpty() && fluidOutputs.isEmpty())
                    continue;

                ResourceLocation typeKey = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                RecipeInfo info = new RecipeInfo(recipeId.toString(), result,
                        typeKey == null ? "" : typeKey.toString(),
                        RecipeShape.of(recipe, fluidInputs.size()),
                        fluidOutputs.isEmpty() ? "" : fluidOutputs.get(0),
                        fluidInputs);

                if (!result.isEmpty()) {
                    ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(result.getItem());
                    if (itemKey != null) {
                        recipesByOutput.computeIfAbsent(itemKey.toString(), k -> new ArrayList<>())
                                .add(info);
                    }
                }
                // Filed under every fluid it certainly produces, so a machine making two of them
                // is reachable from either.
                for (String fluidId : fluidOutputs) {
                    recipesByOutput.computeIfAbsent(fluidId, k -> new ArrayList<>()).add(info);
                }
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
            if (!seen.add(entry.getKey())) continue;
            ResourceLocation key = ResourceLocation.tryParse(entry.getKey());
            if (key != null && !BuiltInRegistries.ITEM.containsKey(key)) {
                allRecipeItems.add(new ItemEntry(entry.getKey(), ItemStack.EMPTY,
                        entry.getValue().size(),
                        FluidIcon.nameOf(entry.getKey()).toLowerCase(), true));
            } else {
                ItemStack stack = entry.getValue().get(0).result();
                allRecipeItems.add(new ItemEntry(entry.getKey(), stack, entry.getValue().size(),
                        stack.getHoverName().getString().toLowerCase(), false));
            }
        }
        // Fluids land after the items without being told to: the order below is the item
        // registry position, and a fluid id has none.
        allRecipeItems.sort((a, b) -> {
            int orderA = registryOrder.getOrDefault(a.id(), Integer.MAX_VALUE);
            int orderB = registryOrder.getOrDefault(b.id(), Integer.MAX_VALUE);
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

    // ========== LAYOUT ==========

    @Override
    public void show(int centerX, int centerY, int parentWidth) {
        int masterW = MASTER_COLS * ItemSlotGrid.SLOT_SIZE + ItemSlotGrid.SCROLLBAR_GAP;
        // Sized for the widest card there is, not the commonest. A modded machine with four or
        // more inputs lays out five columns wide; at a 3x3 column that card's arrow and result
        // slot fall outside the clip rectangle and simply are not drawn.
        int detailW = RecipeCardRenderer.cardWidth(RecipeCardLayout.sequence(RecipeCardLayout.MAX_COLS))
                + ItemSlotGrid.SCROLLBAR_GAP;
        panelW = PADDING + masterW + DETAIL_GAP + detailW + PADDING;
        panelH = PADDING + TAB_HEIGHT + 4 + SearchBar.HEIGHT + PADDING + SELECTALL_ROW_H
                + CONTENT_HEIGHT + PADDING + ADD_BTN_H + PADDING;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        clampToScreen();
        this.visible = true;
        this.currentTab = TAB_RECIPES;
        this.scrollRow = 0;
        this.detailScroll = 0;
        this.selectedItemId = null;
        this.tabIndicatorInit = false;
        selected.clear();
        selectedSnapshot.clear();
        detailRecipes.clear();
        detailHeights.clear();
        hiddenByFilter = 0;
        searchBar.setFocused(true);
        searchBar.setText("");
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

    /** Top of the content area — below the tab bar, search bar and select-all row. */
    private int contentTopY() {
        return panelY + PADDING + TAB_HEIGHT + 4 + SearchBar.HEIGHT + PADDING + SELECTALL_ROW_H;
    }

    /**
     * Left edge of the detail column, just right of the master grid and its scrollbar. On the
     * Selected tab there is no master grid, so the cards take the whole panel.
     */
    private int detailX() {
        if (currentTab == TAB_SELECTED)
            return panelX + PADDING;
        return panelX + PADDING + MASTER_COLS * ItemSlotGrid.SLOT_SIZE
                + ItemSlotGrid.SCROLLBAR_GAP + DETAIL_GAP;
    }

    /** Width of the detail column, leaving room for its own scrollbar. */
    private int detailWidth() {
        return panelX + panelW - PADDING - detailX() - ItemSlotGrid.SCROLLBAR_GAP;
    }

    /**
     * How much of the detail column the header eats before the first card. The scope hint is
     * wrapped rather than drawn on one line: the column is 126px wide and the sentence is not.
     */
    private int detailHeaderH() {
        if (selectedItemId == null && currentTab != TAB_SELECTED)
            return 0;
        return DETAIL_HEADER_H + (individualScope ? scopeHintLines().size() * HINT_LINE_H : 0);
    }

    private List<FormattedText> scopeHintLines() {
        return Minecraft.getInstance().font.getSplitter().splitLines(
                Component.translatable("editor.historystages.recipes.individual_only"),
                detailWidth(), net.minecraft.network.chat.Style.EMPTY);
    }

    private int selectAllRowY() {
        return panelY + PADDING + TAB_HEIGHT + 4 + SearchBar.HEIGHT + PADDING
                + (SELECTALL_ROW_H - SELECTALL_BTN_H) / 2;
    }

    private int gridRows() {
        return GridGeometry.rowsThatFit(CONTENT_HEIGHT, ItemSlotGrid.SLOT_SIZE);
    }

    @Override
    public void hide() {
        this.visible = false;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setFilter(String f) {
        searchBar.setText(f);
    }

    // ========== FILTERING ==========

    private void applyFilter(String filter) {
        this.scrollRow = 0;
        String query = filter == null ? "" : filter;
        filteredItems.clear();
        for (ItemEntry entry : allRecipeItems) {
            if (!itemHasAnySurvivingRecipe(entry))
                continue;
            // A "@namespace" query is answered by recipePassesFilters, which reads the recipe id;
            // free text is matched against the item and its recipe types.
            if (query.isEmpty() || query.startsWith("@") || itemMatchesQuery(entry, query))
                filteredItems.add(entry);
        }
        updateMaxScroll();
        rebuildDetail();
    }

    /**
     * An item stays in the grid if its name or id matches, or if any of its recipes is of a
     * matching type. Type search is nearly free — the id is already on every RecipeInfo.
     */
    private boolean itemMatchesQuery(ItemEntry entry, String query) {
        if (entry.id().contains(query) || entry.searchName().contains(query)) return true;
        List<RecipeInfo> recipes = recipesByOutput.get(entry.id());
        if (recipes == null) return false;
        for (RecipeInfo info : recipes) {
            if (info.typeId().toLowerCase().contains(query)) return true;
        }
        return false;
    }

    /**
     * Whether one recipe survives the namespace filters. The id checked is the recipe's, not its
     * output item's — the recipe id is what gets locked, and a KubeJS recipe almost always
     * outputs a vanilla item, so filtering by the item hid precisely the pack's own recipes.
     */
    private boolean recipePassesFilters(RecipeInfo info) {
        String namespace = info.recipeId().contains(":")
                ? info.recipeId().substring(0, info.recipeId().indexOf(':'))
                : "";
        boolean vanilla = "minecraft".equals(namespace);
        if (searchBar.filters().isActive("only_vanilla") && !vanilla) return false;
        if (searchBar.filters().isActive("only_modded") && vanilla) return false;
        if (searchBar.filters().isActive("hide_added") && alreadyAddedSupplier != null) {
            Collection<String> added = alreadyAddedSupplier.get();
            if (added != null && added.contains(info.recipeId())) return false;
        }
        String query = searchBar.getText();
        if (query != null && query.startsWith("@")) {
            return namespace.contains(query.substring(1));
        }
        return true;
    }

    /** An item stays in the grid while at least one of its recipes survives the filters. */
    private boolean itemHasAnySurvivingRecipe(ItemEntry entry) {
        List<RecipeInfo> recipes = recipesByOutput.get(entry.id());
        if (recipes == null) return false;
        for (RecipeInfo info : recipes) {
            if (recipePassesFilters(info)) return true;
        }
        return false;
    }

    private void updateMaxScroll() {
        maxScrollRow = GridGeometry.maxScrollRow(filteredItems.size(), MASTER_COLS, gridRows());
        scrollRow = GridGeometry.clampScroll(scrollRow, maxScrollRow);
    }

    /**
     * Refills the detail column. Called on selecting an item and on every filter change — a
     * namespace filter narrows what the column shows, or you would search for a namespace, click
     * an item, and not find what you searched for.
     */
    private void rebuildDetail() {
        if (currentTab == TAB_SELECTED) return;
        detailRecipes.clear();
        detailHeights.clear();
        hiddenByFilter = 0;
        if (selectedItemId == null) return;
        List<RecipeInfo> all = recipesByOutput.get(selectedItemId);
        if (all == null) return;
        for (RecipeInfo info : all) {
            if (recipePassesFilters(info)) {
                detailRecipes.add(info);
                detailHeights.add(info.shape().layout().cardHeight());
            } else {
                hiddenByFilter++;
            }
        }
        detailScroll = CardStackGeometry.clampScroll(detailScroll, detailHeights, detailListH());
    }

    private int detailListH() {
        return CONTENT_HEIGHT - detailHeaderH() - (hiddenByFilter > 0 ? NOTE_H : 0);
    }

    // ========== SELECTION ==========

    private void toggleSelection(String recipeId) {
        if (selected.remove(recipeId) == null) {
            for (RecipeInfo info : detailRecipes) {
                if (info.recipeId().equals(recipeId)) {
                    selected.put(recipeId, info);
                    break;
                }
            }
        }
    }

    private int selectableShownCount() {
        int n = 0;
        for (RecipeInfo info : detailRecipes) {
            if (!selected.containsKey(info.recipeId())) n++;
        }
        return n;
    }

    private int deselectableShownCount() {
        int n = 0;
        for (RecipeInfo info : detailRecipes) {
            if (selected.containsKey(info.recipeId())) n++;
        }
        return n;
    }

    private void confirmSelection() {
        // One call per selected id, in click order. StringListCategoryTab tolerates n calls —
        // that is how every other multi-select picker in the editor already reports.
        for (String recipeId : selected.keySet()) {
            onSelect.accept(recipeId);
        }
        selected.clear();
        hide();
    }

    private List<String> tabLabels() {
        return List.of(
                Component.translatable("editor.historystages.search.tab.recipes").getString(),
                Component.translatable("editor.historystages.search.tab.selected",
                        selected.size()).getString());
    }

    /**
     * Enters the Selected tab on a snapshot of the current selection, so deselecting a card does
     * not make the ones below it jump up under the cursor.
     */
    private void enterSelectedTab() {
        selectedSnapshot.clear();
        selectedSnapshot.addAll(selected.values());
        detailHeights.clear();
        for (RecipeInfo info : selectedSnapshot) {
            detailHeights.add(info.shape().layout().cardHeight());
        }
        detailRecipes.clear();
        detailRecipes.addAll(selectedSnapshot);
        hiddenByFilter = 0;
        detailScroll = 0;
    }

    private void switchTab(int newTab) {
        if (newTab == currentTab) return;
        currentTab = newTab;
        if (newTab == TAB_SELECTED) {
            enterSelectedTab();
        } else {
            selectedSnapshot.clear();
            detailScroll = 0;
            rebuildDetail();
        }
    }

    // ========== RENDERING ==========

    @Override
    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible)
            return;

        SearchPanelChrome.renderFrame(guiGraphics, panelX, panelY, panelW, panelH);
        renderTabs(guiGraphics, font, mouseX, mouseY);

        int searchX = panelX + PADDING;
        int searchY = panelY + PADDING + TAB_HEIGHT + 4;
        searchBar.setPosition(searchX, searchY, panelW - PADDING * 2);
        searchBar.render(guiGraphics, font, mouseX, mouseY);

        renderSelectAllRow(guiGraphics, font, mouseX, mouseY);

        boolean filterUiHovered = searchBar.isMouseOverFilterUi(mouseX, mouseY);
        int gridX = panelX + PADDING;
        int gridY = contentTopY();
        int rows = gridRows();

        if (currentTab != TAB_SELECTED) {
            ItemSlotGrid.render(guiGraphics, gridX, gridY, MASTER_COLS, rows, scrollRow,
                    filteredItems.size(),
                    (gg, index, sx, sy) -> {
                        ItemEntry entry = filteredItems.get(index);
                        if (entry.isFluid()) {
                            FluidIcon.draw(gg, entry.id(), sx + 1, sy + 1, 16);
                        } else {
                            gg.renderItem(entry.stack(), sx + 1, sy + 1);
                        }
                    },
                    i -> filteredItems.get(i).id().equals(selectedItemId),
                    mouseX, mouseY, filterUiHovered);
            if (maxScrollRow > 0) {
                ItemSlotGrid.renderScrollbar(guiGraphics, gridX, gridY, MASTER_COLS, rows,
                        scrollRow, maxScrollRow);
            }
        }

        renderDetail(guiGraphics, font, mouseX, mouseY);
        renderAddButton(guiGraphics, font, mouseX, mouseY);

        // Tooltips last and lifted, or the panel below paints over them.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        if (!filterUiHovered) {
            renderMasterTooltip(guiGraphics, font, gridX, gridY, rows, mouseX, mouseY);
            renderCardTooltip(guiGraphics, font, mouseX, mouseY);
        }
        guiGraphics.pose().popPose();
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

        if (!tabIndicatorInit) {
            tabIndicatorX.set(tabXs[currentTab]);
            tabIndicatorW.set(tabWs[currentTab]);
            tabIndicatorInit = true;
        }
        tabIndicatorX.approach(tabXs[currentTab], Timing.SCROLL_HALF_LIFE_MS);
        tabIndicatorW.approach(tabWs[currentTab], Timing.SCROLL_HALF_LIFE_MS);
        tabIndicatorX.settle(tabXs[currentTab], 0.5f);
        tabIndicatorW.settle(tabWs[currentTab], 0.5f);

        for (int i = 0; i < n; i++) {
            boolean active = i == currentTab;
            boolean hovered = mouseX >= tabXs[i] && mouseX < tabXs[i] + tabWs[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
            int bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            guiGraphics.fill(tabXs[i], tabY, tabXs[i] + tabWs[i], tabY + TAB_HEIGHT, bg);
            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            guiGraphics.drawString(font, labels.get(i), tabXs[i] + TAB_PAD, tabY + 3, textColor, false);
        }

        guiGraphics.fill(Math.round(tabIndicatorX.value()), tabY + TAB_HEIGHT - 2,
                Math.round(tabIndicatorX.value() + tabIndicatorW.value()), tabY + TAB_HEIGHT, 0xFFFFCC00);
        guiGraphics.fill(panelX + PADDING, tabY + TAB_HEIGHT, panelX + panelW - PADDING,
                tabY + TAB_HEIGHT + 1, 0xFF555555);
    }

    private void renderSelectAllRow(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int rowX = panelX + PADDING;
        int rowW = panelW - PADDING * 2;
        int btnW = (rowW - SELECTALL_BTN_GAP) / 2;
        int rightX = rowX + btnW + SELECTALL_BTN_GAP;
        int y = selectAllRowY();

        int selCount = selectableShownCount();
        int deselCount = deselectableShownCount();
        boolean canSelect = selCount > 0;
        boolean canDeselect = deselCount > 0;

        String selLabel = Component.translatable("editor.historystages.search.selectall", selCount).getString();
        String deselLabel = Component.translatable("editor.historystages.search.deselectall", deselCount).getString();
        drawSmallButton(g, font, rowX, y, btnW, selLabel, true, canSelect,
                canSelect && isInBtn(mouseX, mouseY, rowX, y, btnW));
        drawSmallButton(g, font, rightX, y, btnW, deselLabel, false, canDeselect,
                canDeselect && isInBtn(mouseX, mouseY, rightX, y, btnW));
    }

    private boolean isInBtn(double mouseX, double mouseY, int x, int y, int w) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SELECTALL_BTN_H;
    }

    private void drawSmallButton(GuiGraphics g, Font font, int x, int y, int w,
                                 String label, boolean accentYellow, boolean enabled, boolean hovered) {
        int border;
        int bg;
        int text;
        if (!enabled) {
            border = 0xFF333333; bg = 0xFF1E1E1E; text = 0xFF555555;
        } else if (accentYellow) {
            border = hovered ? 0xFFFFCC00 : 0xFFB38F00;
            bg = hovered ? 0xFF553A10 : 0xFF2A2510;
            text = hovered ? 0xFFFFFFFF : 0xFFDDCC88;
        } else {
            border = hovered ? 0xFF884444 : 0xFF663030;
            bg = hovered ? 0xFF3A1A1A : 0xFF2A1010;
            text = hovered ? 0xFFFFFFFF : 0xFFDDAAAA;
        }
        g.fill(x, y, x + w, y + SELECTALL_BTN_H, border);
        g.fill(x + 1, y + 1, x + w - 1, y + SELECTALL_BTN_H - 1, bg);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (SELECTALL_BTN_H - 8) / 2, text, false);
    }

    private void renderDetail(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = detailX();
        int y = contentTopY();
        int w = detailWidth();

        if (currentTab != TAB_SELECTED && selectedItemId == null) {
            String hint = Component.translatable(
                    "editor.historystages.recipes.pick_an_item").getString();
            drawWrapped(g, font, hint, x, y + CONTENT_HEIGHT / 2 - 8, w, 0xFF777777);
            return;
        }

        int headerY = y;
        if (currentTab != TAB_SELECTED) {
            ItemStack header = detailRecipes.isEmpty() ? ItemStack.EMPTY : detailRecipes.get(0).result();
            if (!header.isEmpty()) {
                g.renderItem(header, x, headerY);
                g.drawString(font, trimToWidth(font, header.getHoverName().getString(), w - 22),
                        x + 20, headerY + 5, 0xFFFFFFFF, false);
            } else if (selectedItemId != null) {
                // A fluid entry: its recipes have no item result to take the header from.
                FluidIcon.draw(g, selectedItemId, x, headerY, 16);
                g.drawString(font, trimToWidth(font, FluidIcon.nameOf(selectedItemId), w - 22),
                        x + 20, headerY + 5, 0xFFFFFFFF, false);
            }
        } else {
            String label = Component.translatable("editor.historystages.search.tab.selected",
                    selected.size()).getString();
            g.drawString(font, label, x, headerY + 5, 0xFFFFFFFF, false);
        }

        // Says why there is less here than on a global stage, rather than leaving the author to
        // wonder whether their recipe is missing. Wrapped: the column is narrower than the line.
        if (individualScope) {
            int hintY = headerY + DETAIL_HEADER_H;
            for (FormattedText line : scopeHintLines()) {
                g.drawString(font, line.getString(), x, hintY, 0xFF888888, false);
                hintY += HINT_LINE_H;
            }
        }

        int listY = y + detailHeaderH();
        int listH = detailListH();

        // Cards are clipped to the column rather than to whole cards: a card poking half into
        // the window is normal, and cutting it out would leave a visible gap at the edge.
        g.enableScissor(x, listY, x + w, listY + listH);
        int first = CardStackGeometry.firstVisible(detailHeights, detailScroll);
        int end = CardStackGeometry.endVisible(detailHeights, detailScroll, listH);
        for (int i = first; i < end && i < detailRecipes.size(); i++) {
            RecipeInfo info = detailRecipes.get(i);
            int cardY = listY + CardStackGeometry.offsetOf(detailHeights, i) - detailScroll;
            boolean hovered = CardStackGeometry.indexAt(detailHeights, detailScroll,
                    mouseY - listY) == i && mouseX >= x && mouseX < x + w;
            RecipeCardRenderer.render(g, font, info.shape(), info.result(), info.fluidResult(),
                    info.fluids(), info.typeId(), info.recipeId(), x, cardY, w, hovered,
                    selected.containsKey(info.recipeId()));
        }
        g.disableScissor();

        if (hiddenByFilter > 0) {
            String note = Component.translatable(
                    "editor.historystages.recipes.hidden_by_filter", hiddenByFilter).getString();
            g.drawString(font, trimToWidth(font, note, w), x, listY + listH + 1, 0xFF777777, false);
        }

        int maxScroll = CardStackGeometry.maxScroll(detailHeights, listH);
        if (maxScroll > 0) {
            renderDetailScrollbar(g, x + w + 2, listY, listH, maxScroll);
        }
    }

    /**
     * The detail column's scrollbar. Its own, not {@link ItemSlotGrid}'s: that one measures in
     * rows of equal height, and these cards are not.
     */
    private void renderDetailScrollbar(GuiGraphics g, int x, int listY, int listH, int maxScroll) {
        int total = CardStackGeometry.totalHeight(detailHeights);
        g.fill(x, listY, x + DETAIL_SCROLLBAR_W, listY + listH, 0xFF252525);
        int thumbH = Math.max(10, (int) ((float) listH / total * listH));
        int thumbY = listY + (int) ((float) detailScroll / maxScroll * (listH - thumbH));
        g.fill(x, thumbY, x + DETAIL_SCROLLBAR_W, thumbY + thumbH, 0xFF888888);
    }

    private void renderAddButton(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int btnX = panelX + (panelW - ADD_BTN_W) / 2;
        int btnY = panelY + panelH - PADDING - ADD_BTN_H;
        boolean canAdd = !selected.isEmpty();
        boolean hovered = canAdd && mouseX >= btnX && mouseX < btnX + ADD_BTN_W
                && mouseY >= btnY && mouseY < btnY + ADD_BTN_H;
        float progress = Ease.outCubic(
                addHoverProgress.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        if (canAdd) {
            String label = Component.translatable("editor.historystages.search.add",
                    selected.size()).getString();
            SearchPanelChrome.renderStyledButton(g, font, btnX, btnY, ADD_BTN_W, ADD_BTN_H, label, progress);
        } else {
            g.fill(btnX, btnY, btnX + ADD_BTN_W, btnY + ADD_BTN_H, 0x20FFFFFF);
            g.fill(btnX, btnY, btnX + ADD_BTN_W, btnY + 1, 0x10FFFFFF);
            String label = Component.translatable("editor.historystages.search.add.empty").getString();
            g.drawString(font, label, btnX + (ADD_BTN_W - font.width(label)) / 2,
                    btnY + (ADD_BTN_H - 8) / 2, 0xFF666666, false);
        }
    }

    private void renderMasterTooltip(GuiGraphics g, Font font, int gridX, int gridY, int rows,
                                     int mouseX, int mouseY) {
        if (currentTab == TAB_SELECTED) return;
        int index = GridGeometry.indexAt(gridX, gridY, ItemSlotGrid.SLOT_SIZE, MASTER_COLS, rows,
                scrollRow, mouseX, mouseY);
        if (index < 0 || index >= filteredItems.size()) return;
        ItemEntry entry = filteredItems.get(index);
        String name = entry.isFluid()
                ? FluidIcon.nameOf(entry.id())
                : entry.stack().getHoverName().getString();
        drawTooltip(g, font, name + " §7(" + entry.recipeCount() + ")\n§8" + entry.id(),
                mouseX, mouseY);
    }

    /**
     * A slot under the cursor names its item; anywhere else on the card names the recipe. The
     * card draws only the namespace, so the full id — the thing that actually gets locked —
     * belongs here, where someone debugging a lock can read it.
     */
    private void renderCardTooltip(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int i = cardIndexAt(mouseX, mouseY);
        if (i < 0) return;
        RecipeInfo info = detailRecipes.get(i);

        int listY = contentTopY() + detailHeaderH();
        int cardY = listY + CardStackGeometry.offsetOf(detailHeights, i) - detailScroll;
        ItemStack slot = RecipeCardRenderer.stackAt(info.shape(), info.result(),
                detailX(), cardY, detailWidth(), mouseX, mouseY);
        if (!slot.isEmpty()) {
            String itemId = String.valueOf(
                    BuiltInRegistries.ITEM.getKey(slot.getItem()));
            drawTooltip(g, font, slot.getHoverName().getString() + "\n§8" + itemId,
                    mouseX, mouseY);
            return;
        }

        String fluid = RecipeCardRenderer.fluidAt(info.shape(), info.fluidResult(), info.fluids(),
                detailX(), cardY, detailWidth(), mouseX, mouseY);
        if (!fluid.isEmpty()) {
            StringBuilder text = new StringBuilder(FluidIcon.nameOf(fluid))
                    .append("\n§8").append(fluid);
            boolean placed = fluid.equals(info.fluidResult()) || info.fluids().stream()
                    .anyMatch(ref -> ref.fluidId().equals(fluid) && ref.sideKnown());
            if (!placed) {
                text.append("\n§7").append(Component.translatable(
                        "editor.historystages.recipes.fluid_side_unknown").getString());
            }
            drawTooltip(g, font, text.toString(), mouseX, mouseY);
            return;
        }

        drawTooltip(g, font, info.recipeId() + "\n§8" + info.typeId(), mouseX, mouseY);
    }

    /**
     * Every tooltip in this panel, in the editor's own look.
     *
     * <p>The card ones used to come from {@code GuiGraphics.renderTooltip}, which brings vanilla's
     * border and gradient — beside the panel's own chrome that reads as a second application
     * sitting on top of the first. No hover delay here on purpose: the grid never had one, and
     * adding it in one picker but not its twin would only move the inconsistency.
     */
    private void drawTooltip(GuiGraphics g, Font font, String text, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        EditorTooltip.draw(g, font, text, mouseX, mouseY,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private int cardIndexAt(double mouseX, double mouseY) {
        int x = detailX();
        int w = detailWidth();
        int listY = contentTopY() + detailHeaderH();
        int listH = detailListH();
        if (mouseX < x || mouseX >= x + w || mouseY < listY || mouseY >= listY + listH) return -1;
        int i = CardStackGeometry.indexAt(detailHeights, detailScroll, mouseY - listY);
        return i >= 0 && i < detailRecipes.size() ? i : -1;
    }

    private String trimToWidth(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private void drawWrapped(GuiGraphics g, Font font, String text, int x, int y, int w, int color) {
        int lineY = y;
        for (FormattedText line : font.getSplitter().splitLines(
                Component.literal(text), w, net.minecraft.network.chat.Style.EMPTY)) {
            g.drawString(font, line.getString(), x, lineY, color, false);
            lineY += HINT_LINE_H;
        }
    }

    // ========== INPUT ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible)
            return false;
        if (searchBar.mouseClicked(mouseX, mouseY))
            return true;
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        if (tabClicked(mouseX, mouseY)) return true;
        if (selectAllClicked(mouseX, mouseY)) return true;

        int btnX = panelX + (panelW - ADD_BTN_W) / 2;
        int btnY = panelY + panelH - PADDING - ADD_BTN_H;
        if (!selected.isEmpty() && mouseX >= btnX && mouseX < btnX + ADD_BTN_W
                && mouseY >= btnY && mouseY < btnY + ADD_BTN_H) {
            playClick();
            confirmSelection();
            return true;
        }

        if (detailClicked(mouseX, mouseY)) return true;
        if (currentTab != TAB_SELECTED && masterClicked(mouseX, mouseY)) return true;

        searchBar.setFocused(true);
        return true;
    }

    private boolean tabClicked(double mouseX, double mouseY) {
        int tabY = panelY + PADDING;
        if (mouseY < tabY || mouseY >= tabY + TAB_HEIGHT) return false;
        Font font = Minecraft.getInstance().font;
        List<String> labels = tabLabels();
        int x = panelX + PADDING;
        for (int i = 0; i < labels.size(); i++) {
            int w = font.width(labels.get(i)) + TAB_PAD * 2;
            if (mouseX >= x && mouseX < x + w) {
                if (i != currentTab) {
                    playClick();
                    switchTab(i);
                }
                return true;
            }
            x += w + 2;
        }
        return false;
    }

    private boolean selectAllClicked(double mouseX, double mouseY) {
        int rowX = panelX + PADDING;
        int rowW = panelW - PADDING * 2;
        int btnW = (rowW - SELECTALL_BTN_GAP) / 2;
        int rightX = rowX + btnW + SELECTALL_BTN_GAP;
        int y = selectAllRowY();

        if (isInBtn(mouseX, mouseY, rowX, y, btnW)) {
            if (selectableShownCount() > 0) {
                playClick();
                for (RecipeInfo info : detailRecipes) {
                    selected.putIfAbsent(info.recipeId(), info);
                }
            }
            return true;
        }
        if (isInBtn(mouseX, mouseY, rightX, y, btnW)) {
            if (deselectableShownCount() > 0) {
                playClick();
                for (RecipeInfo info : detailRecipes) {
                    selected.remove(info.recipeId());
                }
            }
            return true;
        }
        return false;
    }

    private boolean masterClicked(double mouseX, double mouseY) {
        int gridX = panelX + PADDING;
        int gridY = contentTopY();
        int rows = gridRows();

        if (maxScrollRow > 0
                && ItemSlotGrid.isOverScrollbar(gridX, gridY, MASTER_COLS, rows, mouseX, mouseY)) {
            int trackH = rows * ItemSlotGrid.SLOT_SIZE;
            int thumbH = GridGeometry.thumbHeight(trackH, rows, maxScrollRow);
            int thumbTop = gridY + GridGeometry.thumbOffset(trackH, thumbH, scrollRow, maxScrollRow);
            // Grab offset, so the thumb does not jump to the cursor on the first drag pixel.
            scrollbarGrabOffset = (mouseY >= thumbTop && mouseY < thumbTop + thumbH)
                    ? (int) (mouseY - thumbTop)
                    : thumbH / 2;
            draggingScrollbar = true;
            scrollRow = GridGeometry.scrollFromThumbDrag(gridY, trackH, thumbH,
                    scrollbarGrabOffset, maxScrollRow, mouseY);
            return true;
        }

        int index = GridGeometry.indexAt(gridX, gridY, ItemSlotGrid.SLOT_SIZE, MASTER_COLS,
                rows, scrollRow, mouseX, mouseY);
        if (index >= 0 && index < filteredItems.size()) {
            selectedItemId = filteredItems.get(index).id;
            detailScroll = 0;
            rebuildDetail();
            playClick();
            return true;
        }
        return false;
    }

    private boolean detailClicked(double mouseX, double mouseY) {
        int x = detailX();
        int w = detailWidth();
        int listY = contentTopY() + detailHeaderH();
        int listH = detailListH();

        if (CardStackGeometry.maxScroll(detailHeights, listH) > 0
                && mouseX >= x + w && mouseX <= x + w + 2 + DETAIL_SCROLLBAR_W + 2
                && mouseY >= listY && mouseY < listY + listH) {
            draggingDetailScrollbar = true;
            dragDetailScrollbar(mouseY, listY, listH);
            return true;
        }

        int i = cardIndexAt(mouseX, mouseY);
        if (i < 0) return false;
        toggleSelection(detailRecipes.get(i).recipeId());
        playClick();
        return true;
    }

    private void dragDetailScrollbar(double mouseY, int listY, int listH) {
        float ratio = (float) Math.max(0, Math.min(1, (mouseY - listY) / (double) listH));
        int maxScroll = CardStackGeometry.maxScroll(detailHeights, listH);
        detailScroll = CardStackGeometry.clampScroll(Math.round(ratio * maxScroll),
                detailHeights, listH);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible)
            return false;
        if (draggingScrollbar) {
            int gridY = contentTopY();
            int rows = gridRows();
            int trackH = rows * ItemSlotGrid.SLOT_SIZE;
            int thumbH = GridGeometry.thumbHeight(trackH, rows, maxScrollRow);
            scrollRow = GridGeometry.scrollFromThumbDrag(gridY, trackH, thumbH,
                    scrollbarGrabOffset, maxScrollRow, mouseY);
            return true;
        }
        if (draggingDetailScrollbar) {
            dragDetailScrollbar(mouseY, contentTopY() + detailHeaderH(), detailListH());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased() {
        boolean was = draggingScrollbar || draggingDetailScrollbar;
        draggingScrollbar = false;
        draggingDetailScrollbar = false;
        return was;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible)
            return false;
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH)
            return false;

        if (mouseX >= detailX()) {
            detailScroll = CardStackGeometry.clampScroll(
                    detailScroll - (int) (scrollY * ItemSlotGrid.SLOT_SIZE),
                    detailHeights, detailListH());
            return true;
        }
        scrollRow = GridGeometry.clampScroll(scrollRow - (int) scrollY, maxScrollRow);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (!visible)
            return false;
        if (keyCode == 256) { // ESC — let an open filter popup close first
            if (searchBar.keyPressed(keyCode))
                return true;
            hide();
            return true;
        }
        return searchBar.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(char c) {
        if (!visible)
            return false;
        return searchBar.charTyped(c);
    }

    /**
     * One row of the master grid. A fluid entry carries an empty stack and is painted from its
     * own id instead — a fluid has no {@code ItemStack}, and substituting a bucket would name a
     * different thing, often one that does not exist.
     */
    private record ItemEntry(String id, ItemStack stack, int recipeCount, String searchName,
                             boolean isFluid) {
    }

    /**
     * One recipe as the detail column needs it. The ingredient stacks the old row drew are gone —
     * {@link RecipeShape} reads them from the recipe itself now, holes and all, which is what
     * tells two shaped recipes apart.
     */
    private record RecipeInfo(String recipeId, ItemStack result, String typeId,
                              RecipeShape shape, String fluidResult,
                              List<RecipeFluids.Ref> fluids) {
    }
}
