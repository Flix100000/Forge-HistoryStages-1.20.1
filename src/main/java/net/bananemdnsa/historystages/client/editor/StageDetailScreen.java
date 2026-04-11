package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.client.editor.widget.ModEntitySelectionPopup;
import net.bananemdnsa.historystages.client.editor.widget.SearchableEntityList;
import net.bananemdnsa.historystages.client.editor.widget.SearchableItemList;
import net.bananemdnsa.historystages.client.editor.widget.SearchableDimensionList;
import net.bananemdnsa.historystages.client.editor.widget.SearchableModList;
import net.bananemdnsa.historystages.client.editor.widget.SearchableRecipeList;
import net.bananemdnsa.historystages.client.editor.widget.SearchableTagList;
import net.bananemdnsa.historystages.data.EntityLocks;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SaveStagePacket;
import net.bananemdnsa.historystages.util.ClientStageCache;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.bananemdnsa.historystages.util.AllRecipesCache;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Quaternionf;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

public class StageDetailScreen extends Screen {
    private final Screen parent;
    private final String originalStageId;
    private final boolean isNewStage;

    // Editable data
    private String editStageId;
    private String editDisplayName;
    private int editResearchTime;
    private final List<String> editItems;
    private final Map<Integer, com.google.gson.JsonObject> editItemNbt;
    private final List<String> editTags;
    private final List<String> editMods;
    private final List<String> editModExceptions;
    private final Map<Integer, com.google.gson.JsonObject> editModExceptionNbt;
    private final List<String> editRecipes;
    private final List<String> editDimensions;
    private final List<String> editAttacklock;
    private final List<String> editSpawnlock;
    private final List<String> editModLinked;

    // UI state
    private EditBox stageIdField;
    private EditBox displayNameField;
    private EditBox researchTimeField;
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private boolean hasChanges = false;
    private String saveError = "";

    // Original values for change detection
    private String origStageId;
    private String origDisplayName;
    private String origResearchTime;

    // Tab state: 0-6, one per section
    private int activeTab = 0;

    // Widgets
    private SearchableItemList itemSearch;
    private SearchableItemList modExceptionSearch;
    private SearchableModList modSearch;
    private SearchableEntityList entitySearch;
    private SearchableTagList tagSearch;
    private SearchableDimensionList dimensionSearch;
    private SearchableRecipeList recipeSearch;
    private ContextMenu contextMenu;
    private ModEntitySelectionPopup modEntityPopup;

    // Tooltip hover tracking
    private String hoveredTooltipKey = null;
    private long tooltipHoverStart = 0;
    private static final long TOOLTIP_DELAY_MS = 400;

    // Animation state
    private final Map<Integer, Float> cardHoverProgress = new HashMap<>();
    private float tabIndicatorX = 0;
    private float tabIndicatorW = 0;
    private boolean tabIndicatorInit = false;
    private long tabSwitchTime = 0;
    private float smoothScrollOffset = 0;

    // Marquee state for card entries
    private int hoveredCardIndex = -1;
    private long cardHoverStartTime = 0;
    private static final long CARD_MARQUEE_DELAY_MS = 800;
    private static final float CARD_MARQUEE_SPEED = 25.0f;

    // Recipe detail popup state
    private boolean recipePopupVisible = false;
    private String recipePopupId = null;
    private int recipePopupIngredientScroll = 0;
    private boolean recipePopupAddMode = false;
    private Runnable recipePopupAddAction = null;
    // Popup layout cache for click detection
    private int cachedPopupX, cachedPopupY, cachedPopupW, cachedPopupH;
    // Popup recipe ID marquee state
    private long popupMarqueeStartTime = 0;
    private String popupMarqueeLastId = null;
    private boolean popupIdHovered = false;

    // Entity preview cache and hover state
    private final Map<String, LivingEntity> entityCache = new HashMap<>();

    // Recipe info cache: recipeId -> [workstation, result]
    private final Map<String, ItemStack[]> recipeInfoCache = new HashMap<>();
    private boolean recipeInfoBuilt = false;

    // Section definitions
    static final String[] SECTION_KEYS = {
            "editor.historystages.section.items",
            "editor.historystages.section.tags",
            "editor.historystages.section.mods",
            "editor.historystages.section.mod_exceptions",
            "editor.historystages.section.recipes",
            "editor.historystages.section.dimensions",
            "editor.historystages.section.entities_attack",
            "editor.historystages.section.entities_spawn"
    };

    // Short tab label keys
    private static final String[] TAB_KEYS = {
            "editor.historystages.tab.items",
            "editor.historystages.tab.tags",
            "editor.historystages.tab.mods",
            "editor.historystages.tab.exceptions",
            "editor.historystages.tab.recipes",
            "editor.historystages.tab.dimensions",
            "editor.historystages.tab.attack",
            "editor.historystages.tab.spawn"
    };

    // Tooltip descriptions for tabs
    private static final String[] TAB_TOOLTIPS = {
            "editor.historystages.tooltip.items",
            "editor.historystages.tooltip.tags",
            "editor.historystages.tooltip.mods",
            "editor.historystages.tooltip.exceptions",
            "editor.historystages.tooltip.recipes",
            "editor.historystages.tooltip.dimensions",
            "editor.historystages.tooltip.attack",
            "editor.historystages.tooltip.spawn"
    };

    // Tab layout (computed in init)
    private int[] tabX;
    private int[] tabW;
    private int tabY;
    private int tabScrollOffset = 0;
    private int maxTabScroll = 0;
    private static final int TAB_ARROW_WIDTH = 12;

    // Layout constants
    private static final int HEADER_HEIGHT = 104;
    private static final int CARD_HEIGHT = 22;
    private static final int CARD_GAP = 3;
    private static final int ADD_ROW_HEIGHT = 22;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_PAD = 8;
    private static final float SMALL_SCALE = 0.85f;
    private static final int FIELD_HEIGHT = 18;

    private final boolean isIndividual;

    // Tabs that are disabled for individual stages (Recipes=4, Spawnlock=7)
    private boolean isTabDisabled(int tab) {
        return isIndividual && (tab == 4 || tab == 7);
    }

    public StageDetailScreen(Screen parent, String stageId, StageEntry entry, boolean isIndividual) {
        super(Component.translatable("editor.historystages.detail_title"));
        this.parent = parent;
        this.originalStageId = stageId;
        this.isIndividual = isIndividual;
        this.isNewStage = (stageId == null
                || (!StageManager.getStages().containsKey(stageId)
                    && !StageManager.getIndividualStages().containsKey(stageId)));

        StageEntry e = entry != null ? entry : new StageEntry();
        this.editStageId = stageId != null ? stageId : "";
        this.editDisplayName = (e.getDisplayName().equals("Unknown Stage") && entry == null) ? "" : e.getDisplayName();
        this.editResearchTime = (entry == null && e.getResearchTime() == 0) ? Config.COMMON.researchTimeInSeconds.get() : e.getResearchTime();
        this.editItems = new ArrayList<>(e.getAllItemIds());
        this.editItemNbt = new HashMap<>();
        List<net.bananemdnsa.historystages.data.ItemEntry> itemEntries = e.getItemEntries();
        for (int idx = 0; idx < itemEntries.size(); idx++) {
            net.bananemdnsa.historystages.data.ItemEntry ie = itemEntries.get(idx);
            if (ie.hasNbt()) {
                editItemNbt.put(idx, ie.getNbt().deepCopy());
            }
        }
        this.editTags = new ArrayList<>(e.getTags());
        this.editMods = new ArrayList<>(e.getMods());
        this.editModExceptions = new ArrayList<>(e.getAllModExceptionIds());
        this.editModExceptionNbt = new HashMap<>();
        List<net.bananemdnsa.historystages.data.ItemEntry> modExEntries = e.getModExceptionEntries();
        for (int idx = 0; idx < modExEntries.size(); idx++) {
            net.bananemdnsa.historystages.data.ItemEntry me = modExEntries.get(idx);
            if (me.hasNbt()) {
                editModExceptionNbt.put(idx, me.getNbt().deepCopy());
            }
        }
        this.editRecipes = new ArrayList<>(e.getRecipes());
        this.editDimensions = new ArrayList<>(e.getDimensions());
        this.editAttacklock = new ArrayList<>(e.getEntities().getAttacklock());
        this.editSpawnlock = new ArrayList<>(e.getEntities().getSpawnlock());
        this.editModLinked = new ArrayList<>(e.getEntities().getModLinked());
    }

    @Override
    protected void init() {
        int labelX = 30;
        String labelId = Component.translatable("editor.historystages.field.stage_id").getString();
        String labelName = Component.translatable("editor.historystages.field.display_name").getString();
        String labelTime = Component.translatable("editor.historystages.field.research_time").getString();
        int maxLabelW = Math.max(this.font.width(labelId),
                Math.max(this.font.width(labelName), this.font.width(labelTime)));
        int fieldX = labelX + maxLabelW + 10;
        int fieldWidth = Math.min(200, this.width - fieldX - 40);

        origStageId = editStageId;
        origDisplayName = editDisplayName;
        origResearchTime = String.valueOf(editResearchTime);

        stageIdField = new EditBox(this.font, fieldX, 22, fieldWidth, FIELD_HEIGHT,
                Component.translatable("editor.historystages.field.stage_id"));
        stageIdField.setMaxLength(64);
        stageIdField.setValue(editStageId);
        stageIdField.setEditable(isNewStage);
        stageIdField.setResponder(val -> {
            editStageId = val;
            if (!val.equals(origStageId)) hasChanges = true;
        });
        this.addRenderableWidget(stageIdField);

        displayNameField = new EditBox(this.font, fieldX, 44, fieldWidth, FIELD_HEIGHT,
                Component.translatable("editor.historystages.field.display_name"));
        displayNameField.setMaxLength(128);
        displayNameField.setValue(editDisplayName);
        displayNameField.setResponder(val -> {
            editDisplayName = val;
            if (!val.equals(origDisplayName)) hasChanges = true;
        });
        this.addRenderableWidget(displayNameField);

        researchTimeField = new EditBox(this.font, fieldX, 66, 80, FIELD_HEIGHT,
                Component.translatable("editor.historystages.field.research_time"));
        researchTimeField.setMaxLength(5);
        researchTimeField.setValue(String.valueOf(editResearchTime));
        researchTimeField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        researchTimeField.setResponder(val -> {
            try { editResearchTime = val.isEmpty() ? 0 : Integer.parseInt(val); } catch (NumberFormatException ignored) {}
            if (!val.equals(origResearchTime)) hasChanges = true;
        });
        this.addRenderableWidget(researchTimeField);

        tabY = 88;
        tabX = new int[TAB_KEYS.length];
        tabW = new int[TAB_KEYS.length];
        int tabMargin = 20;
        int totalAvail = this.width - tabMargin * 2;
        int gap = 2;

        // Compute natural width for each tab based on its text content (label + count)
        int[] naturalW = new int[TAB_KEYS.length];
        int totalNaturalW = 0;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            String label = Component.translatable(TAB_KEYS[i]).getString();
            int count = getListForSection(i).size();
            String tabText = label + " (" + count + ")";
            naturalW[i] = (int)(this.font.width(tabText) * SMALL_SCALE) + TAB_PAD * 2;
            totalNaturalW += naturalW[i];
        }
        int totalGaps = (TAB_KEYS.length - 1) * gap;

        if (totalNaturalW + totalGaps <= totalAvail) {
            // All tabs fit without scrolling - use natural widths
            int x = tabMargin;
            for (int i = 0; i < TAB_KEYS.length; i++) {
                tabX[i] = x;
                tabW[i] = naturalW[i];
                x += tabW[i] + gap;
            }
            maxTabScroll = 0;
        } else {
            // Tabs need scrolling - use natural widths, offset by arrow width
            int scrollAreaAvail = totalAvail - TAB_ARROW_WIDTH * 2;
            int x = tabMargin + TAB_ARROW_WIDTH;
            for (int i = 0; i < TAB_KEYS.length; i++) {
                tabX[i] = x;
                tabW[i] = naturalW[i];
                x += naturalW[i] + gap;
            }
            int totalTabsWidth = x - gap - (tabMargin + TAB_ARROW_WIDTH);
            maxTabScroll = Math.max(0, totalTabsWidth - scrollAreaAvail);
            tabScrollOffset = Math.min(tabScrollOffset, maxTabScroll);
        }

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> tryClose(), 10, this.height - 30, 60, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> saveStage(), this.width / 2 - 50, this.height - 30, 100, 20));

        itemSearch = new SearchableItemList(itemId -> {
            getActiveList().add(itemId);
            hasChanges = true;
            updateMaxScroll();
        });

        modExceptionSearch = createModExceptionSearch();

        modEntityPopup = new ModEntitySelectionPopup((spawnlockIds, attacklockIds) -> {
            for (String id : spawnlockIds) {
                if (!editSpawnlock.contains(id)) editSpawnlock.add(id);
                if (!editModLinked.contains(id)) editModLinked.add(id);
            }
            for (String id : attacklockIds) {
                if (!editAttacklock.contains(id)) editAttacklock.add(id);
                if (!editModLinked.contains(id)) editModLinked.add(id);
            }
            if (!spawnlockIds.isEmpty() || !attacklockIds.isEmpty()) hasChanges = true;
            updateMaxScroll();
        });

        modSearch = new SearchableModList(modId -> {
            editMods.add(modId);
            hasChanges = true;
            updateMaxScroll();
            // Show entity selection popup if mod has entities
            String displayName = modSearch.getDisplayName(modId);
            modEntityPopup.showForMod(modId, displayName, this.width / 2, this.height / 2);
        });

        entitySearch = new SearchableEntityList(entityId -> {
            getActiveList().add(entityId);
            hasChanges = true;
            updateMaxScroll();
        });

        tagSearch = new SearchableTagList(tagId -> {
            editTags.add(tagId);
            hasChanges = true;
            updateMaxScroll();
        });

        dimensionSearch = new SearchableDimensionList(dimId -> {
            editDimensions.add(dimId);
            hasChanges = true;
            updateMaxScroll();
        });

        recipeSearch = new SearchableRecipeList(recipeId -> {
            showRecipePreview(recipeId, () -> {
                editRecipes.add(recipeId);
                hasChanges = true;
                updateMaxScroll();
            });
        });
        recipeSearch.setKeepVisibleOnSelect(true);

        contextMenu = new ContextMenu();
        updateMaxScroll();
    }

    private boolean isAnyOverlayVisible() {
        return itemSearch.isVisible() || modExceptionSearch.isVisible() || modSearch.isVisible() || entitySearch.isVisible()
                || tagSearch.isVisible() || dimensionSearch.isVisible() || recipeSearch.isVisible()
                || contextMenu.isVisible() || recipePopupVisible || modEntityPopup.isVisible();
    }

    private void switchTab(int tab) {
        if (isTabDisabled(tab)) return;
        if (activeTab != tab) {
            activeTab = tab;
            scrollOffset = 0;
            smoothScrollOffset = 0;
            tabSwitchTime = System.currentTimeMillis();
            cardHoverProgress.clear();
            updateMaxScroll();
        }
    }

    private List<String> getActiveList() {
        return getListForSection(activeTab);
    }

    List<String> getListForSection(int sectionIndex) {
        return switch (sectionIndex) {
            case 0 -> editItems;
            case 1 -> editTags;
            case 2 -> editMods;
            case 3 -> editModExceptions;
            case 4 -> editRecipes;
            case 5 -> editDimensions;
            case 6 -> editAttacklock;
            case 7 -> editSpawnlock;
            default -> new ArrayList<>();
        };
    }

    void updateMaxScroll() {
        int contentHeight = getActiveList().size() * (CARD_HEIGHT + CARD_GAP) + ADD_ROW_HEIGHT + CARD_GAP;
        int visibleHeight = this.height - HEADER_HEIGHT - 50;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);

        String titleText = isNewStage
                ? Component.translatable("editor.historystages.new_stage").getString()
                : editDisplayName + " (" + originalStageId + ")";
        guiGraphics.drawCenteredString(this.font, titleText, this.width / 2, 6, 0xFFFFFF);

        // Individual badge
        if (isIndividual) {
            guiGraphics.drawString(this.font, "\u00A77[Individual]", 10, 8, 0xBBBBBB, false);
        }

        // Lock status indicator (top right) - display only
        if (!isNewStage) {
            boolean unlocked = ClientStageCache.isStageUnlocked(originalStageId);
            String statusIcon = unlocked ? "\u2714" : "\uD83D\uDD12";
            int statusColor = unlocked ? 0x55FF55 : 0xFF5555;
            String statusText = Component.translatable(unlocked ? "editor.historystages.unlocked" : "editor.historystages.locked").getString();
            drawSmallText(guiGraphics, statusIcon + " " + statusText, this.width - 90, 8, statusColor);
        }

        int labelX = 30;
        guiGraphics.drawString(this.font, Component.translatable("editor.historystages.field.stage_id").getString(), labelX, 27, 0xAAAAAA, false);
        guiGraphics.drawString(this.font, Component.translatable("editor.historystages.field.display_name").getString(), labelX, 49, 0xAAAAAA, false);
        guiGraphics.drawString(this.font, Component.translatable("editor.historystages.field.research_time").getString(), labelX, 71, 0xAAAAAA, false);

        guiGraphics.fill(10, tabY - 2, this.width - 10, tabY - 1, 0xFF555555);

        // Track tooltip
        String currentTooltipKey = null;
        String currentTooltipText = null;

        // Check field label hovers for tooltips
        int maxLabelW = Math.max(this.font.width(Component.translatable("editor.historystages.field.stage_id").getString()),
                Math.max(this.font.width(Component.translatable("editor.historystages.field.display_name").getString()),
                        this.font.width(Component.translatable("editor.historystages.field.research_time").getString())));

        if (mouseX >= labelX && mouseX <= labelX + maxLabelW + 5) {
            if (mouseY >= 22 && mouseY <= 40) {
                currentTooltipKey = "field.stage_id";
                currentTooltipText = Component.translatable("editor.historystages.tooltip.stage_id").getString();
            } else if (mouseY >= 42 && mouseY <= 62) {
                currentTooltipKey = "field.display_name";
                currentTooltipText = Component.translatable("editor.historystages.tooltip.display_name").getString();
            } else if (mouseY >= 64 && mouseY <= 84) {
                currentTooltipKey = "field.research_time";
                currentTooltipText = Component.translatable("editor.historystages.tooltip.research_time").getString();
            }
        }

        // Animated tab indicator - smoothly slide to active tab
        if (!tabIndicatorInit) {
            tabIndicatorX = tabX[activeTab] - tabScrollOffset;
            tabIndicatorW = tabW[activeTab];
            tabIndicatorInit = true;
        }
        float targetX = tabX[activeTab] - tabScrollOffset;
        float targetW = tabW[activeTab];
        tabIndicatorX += (targetX - tabIndicatorX) * 0.18f;
        tabIndicatorW += (targetW - tabIndicatorW) * 0.18f;
        if (Math.abs(tabIndicatorX - targetX) < 0.5f) tabIndicatorX = targetX;
        if (Math.abs(tabIndicatorW - targetW) < 0.5f) tabIndicatorW = targetW;

        // Suppress hover when overlays are open
        boolean overlayOpen = isAnyOverlayVisible();
        int effectiveMouseX = overlayOpen ? -1 : mouseX;
        int effectiveMouseY = overlayOpen ? -1 : mouseY;

        // Tab scroll arrows
        int tabAreaLeft = 20;
        int tabAreaRight = this.width - 20;
        boolean hasTabScroll = maxTabScroll > 0;
        if (hasTabScroll) {
            // Left arrow
            if (tabScrollOffset > 0) {
                boolean leftHovered = !overlayOpen && mouseX >= tabAreaLeft && mouseX < tabAreaLeft + TAB_ARROW_WIDTH
                        && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
                guiGraphics.fill(tabAreaLeft, tabY, tabAreaLeft + TAB_ARROW_WIDTH, tabY + TAB_HEIGHT,
                        leftHovered ? 0x40FFFFFF : 0x20FFFFFF);
                drawSmallText(guiGraphics, "\u25C0", tabAreaLeft + 2, tabY + 4, leftHovered ? 0xFFFFFF : 0x999999);
            }
            // Right arrow
            if (tabScrollOffset < maxTabScroll) {
                boolean rightHovered = !overlayOpen && mouseX >= tabAreaRight - TAB_ARROW_WIDTH && mouseX < tabAreaRight
                        && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
                guiGraphics.fill(tabAreaRight - TAB_ARROW_WIDTH, tabY, tabAreaRight, tabY + TAB_HEIGHT,
                        rightHovered ? 0x40FFFFFF : 0x20FFFFFF);
                drawSmallText(guiGraphics, "\u25B6", tabAreaRight - TAB_ARROW_WIDTH + 2, tabY + 4, rightHovered ? 0xFFFFFF : 0x999999);
            }
        }

        // Clip tab area for scrolling (only when scroll is active)
        int tabClipLeft = hasTabScroll ? tabAreaLeft + TAB_ARROW_WIDTH : 0;
        int tabClipRight = hasTabScroll ? tabAreaRight - TAB_ARROW_WIDTH : this.width;
        if (hasTabScroll) {
            guiGraphics.enableScissor(tabClipLeft, tabY, tabClipRight, tabY + TAB_HEIGHT);
        }

        // Render tabs
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int scrolledTabX = tabX[i] - tabScrollOffset;
            boolean disabled = isTabDisabled(i);
            boolean active = (i == activeTab);
            boolean hovered = !overlayOpen && !disabled && mouseX >= Math.max(scrolledTabX, tabClipLeft)
                    && mouseX < Math.min(scrolledTabX + tabW[i], tabClipRight)
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bg;
            if (disabled) {
                bg = 0x10FFFFFF;
            } else {
                bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            }
            guiGraphics.fill(scrolledTabX, tabY, scrolledTabX + tabW[i], tabY + TAB_HEIGHT, bg);

            String label = Component.translatable(TAB_KEYS[i]).getString();
            int entryCount = getListForSection(i).size();
            String tabText = label + " (" + entryCount + ")";
            int textColor;
            if (disabled) {
                textColor = 0x555555;
            } else {
                textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            }
            drawSmallText(guiGraphics, tabText, scrolledTabX + TAB_PAD, tabY + 4, textColor);

            if (hovered) {
                currentTooltipKey = "tab." + i;
                currentTooltipText = Component.translatable(TAB_TOOLTIPS[i]).getString();
            } else if (disabled && !overlayOpen && mouseX >= Math.max(scrolledTabX, tabClipLeft)
                    && mouseX < Math.min(scrolledTabX + tabW[i], tabClipRight)
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                currentTooltipKey = "tab.disabled." + i;
                currentTooltipText = "Not available for individual stages";
            }
        }

        // Sliding gold underline indicator
        guiGraphics.fill((int) tabIndicatorX, tabY + TAB_HEIGHT - 2, (int) (tabIndicatorX + tabIndicatorW), tabY + TAB_HEIGHT, 0xFFFFCC00);

        if (hasTabScroll) {
            guiGraphics.disableScissor();
        }

        guiGraphics.fill(10, HEADER_HEIGHT - 2, this.width - 10, HEADER_HEIGHT - 1, 0xFF555555);

        int listTop = HEADER_HEIGHT;
        int listBottom = this.height - 40;
        int contentLeft = 30;
        int contentRight = this.width - 30;

        guiGraphics.enableScissor(contentLeft - 10, listTop, contentRight + 10, listBottom);

        // Smooth scroll interpolation
        smoothScrollOffset += ((float) scrollOffset - smoothScrollOffset) * 0.25f;
        if (Math.abs(smoothScrollOffset - (float) scrollOffset) < 0.5f) smoothScrollOffset = (float) scrollOffset;

        List<String> list = getActiveList();
        int y = listTop - (int) smoothScrollOffset + CARD_GAP;
        boolean isItemsTab = (activeTab == 0);
        boolean isExceptionsTab = (activeTab == 3);

        // Overlap warning for individual stages (items/tags/mods tabs)
        if (isIndividual && (activeTab == 0 || activeTab == 1 || activeTab == 2)) {
            java.util.Set<String> globalLocked = new java.util.HashSet<>();
            for (StageEntry gEntry : StageManager.getStages().values()) {
                switch (activeTab) {
                    case 0 -> globalLocked.addAll(gEntry.getAllItemIds());
                    case 1 -> globalLocked.addAll(gEntry.getTags());
                    case 2 -> globalLocked.addAll(gEntry.getMods());
                }
            }
            boolean hasOverlap = list.stream().anyMatch(globalLocked::contains);
            if (hasOverlap) {
                String warnText = Component.translatable("editor.historystages.overlap_warning").getString();
                int warnH = 14;
                guiGraphics.fill(contentLeft, y, contentRight, y + warnH, 0x40FFAA00);
                guiGraphics.drawString(this.font, "\u26A0 " + warnText, contentLeft + 4, y + 3, 0xFFAA00, false);
                y += warnH + CARD_GAP;
            }
        }

        // Slide-in timing for tab switch
        long slideElapsed = System.currentTimeMillis() - tabSwitchTime;

        // Track marquee hover
        int currentHoveredCard = -1;

        // Entries — Card style with smooth hover animation + slide-in + marquee
        for (int i = 0; i < list.size(); i++) {
            // Per-card slide-in: staggered delay based on index
            float slideProgress = 1.0f;
            if (slideElapsed < 400) {
                float cardDelay = Math.min(i * 25.0f, 200.0f);
                float cardElapsed = Math.max(0, slideElapsed - cardDelay);
                slideProgress = Math.min(1.0f, cardElapsed / 200.0f);
                // Ease-out curve
                slideProgress = 1.0f - (1.0f - slideProgress) * (1.0f - slideProgress);
            }

            if (y + CARD_HEIGHT > listTop - 20 && y < listBottom + 20) {
                boolean entryHovered = effectiveMouseX >= contentLeft && effectiveMouseX <= contentRight
                        && effectiveMouseY >= Math.max(y, listTop) && effectiveMouseY < Math.min(y + CARD_HEIGHT, listBottom);

                if (entryHovered) currentHoveredCard = i;

                // Smooth card hover progress
                float cardProgress = cardHoverProgress.getOrDefault(i, 0.0f);
                if (entryHovered) {
                    cardProgress = Math.min(1.0f, cardProgress + 0.1f);
                } else {
                    cardProgress = Math.max(0.0f, cardProgress - 0.07f);
                }
                if (cardProgress > 0.001f) cardHoverProgress.put(i, cardProgress);
                else cardHoverProgress.remove(i);

                // Hover lift: card moves up slightly
                int liftY = (int) (cardProgress * -1.5f);
                int cardY = y + liftY;

                // Slide-in offset from left
                int slideOffsetX = (int) ((1.0f - slideProgress) * 15);
                float slideAlpha = slideProgress;

                int borderAlpha = (int) ((0x30 + cardProgress * 0x20) * slideAlpha);
                int bgAlpha = (int) ((0x20 + cardProgress * 0x18) * slideAlpha);
                int cardBorder = (borderAlpha << 24) | 0xFFFFFF;
                int cardBg = (bgAlpha << 24) | 0xFFFFFF;
                guiGraphics.fill(contentLeft + slideOffsetX, cardY, contentRight, cardY + CARD_HEIGHT, cardBorder);
                guiGraphics.fill(contentLeft + 1 + slideOffsetX, cardY + 1, contentRight - 1, cardY + CARD_HEIGHT - 1, cardBg);

                // Check if this entry overlaps with a global stage (individual mode only)
                boolean isGlobalOverlap = false;
                if (isIndividual && (activeTab == 0 || activeTab == 1 || activeTab == 2)) {
                    String entry = list.get(i);
                    for (StageEntry gEntry : StageManager.getStages().values()) {
                        boolean found = switch (activeTab) {
                            case 0 -> gEntry.getAllItemIds().contains(entry);
                            case 1 -> gEntry.getTags().contains(entry);
                            case 2 -> gEntry.getMods().contains(entry);
                            default -> false;
                        };
                        if (found) { isGlobalOverlap = true; break; }
                    }
                }

                // Left accent on hover (orange for overlap, gold otherwise)
                if (cardProgress > 0.01f) {
                    int accentAlpha = (int) (cardProgress * 0xCC);
                    int accentColor = isGlobalOverlap ? 0xFFAA00 : 0xFFCC00;
                    guiGraphics.fill(contentLeft + slideOffsetX, cardY, contentLeft + 2 + slideOffsetX, cardY + CARD_HEIGHT, (accentAlpha << 24) | accentColor);
                }

                // Permanent orange left accent for overlapping entries
                if (isGlobalOverlap && cardProgress <= 0.01f) {
                    guiGraphics.fill(contentLeft + slideOffsetX, cardY, contentLeft + 2 + slideOffsetX, cardY + CARD_HEIGHT, 0x80FFAA00);
                }

                int textOffsetX = 8;
                boolean isEntityTab = (activeTab == 6 || activeTab == 7);
                int renderLeft = contentLeft + slideOffsetX;
                if (isItemsTab || isExceptionsTab) {
                    ItemStack stack = getItemStack(list.get(i));
                    if (!stack.isEmpty()) {
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate(renderLeft + 3, cardY + 3, 0);
                        guiGraphics.pose().scale(0.85f, 0.85f, 1.0f);
                        guiGraphics.renderItem(stack, 0, 0);
                        guiGraphics.pose().popPose();
                    }
                    textOffsetX = 20;
                } else if (activeTab == 4) {
                    ItemStack[] info = getRecipeInfo(list.get(i));
                    if (info != null && info.length > 1 && !info[1].isEmpty()) {
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate(renderLeft + 3, cardY + 3, 0);
                        guiGraphics.pose().scale(0.85f, 0.85f, 1.0f);
                        guiGraphics.renderItem(info[1], 0, 0);
                        guiGraphics.pose().popPose();
                    }
                    textOffsetX = 20;
                } else if (isEntityTab) {
                    LivingEntity living = getOrCreateEntity(list.get(i));
                    if (living != null) {
                        try {
                            float angle = (System.currentTimeMillis() % 3600) / 10.0f;
                            guiGraphics.enableScissor(renderLeft + 1, cardY + 1, renderLeft + 20, cardY + CARD_HEIGHT - 1);
                            int entityScale = (int) Math.max(3, 9.0f / Math.max(living.getBbWidth(), living.getBbHeight()));
                            renderSpinningEntity(guiGraphics, renderLeft + 10, cardY + CARD_HEIGHT - 2, entityScale, angle, living);
                            guiGraphics.disableScissor();
                        } catch (Exception ignored) {}
                    }
                    textOffsetX = 22;
                }

                // NBT badge for items tab and exceptions tab
                int badgeW = 0;
                if (isItemsTab && editItemNbt.containsKey(i) || isExceptionsTab && editModExceptionNbt.containsKey(i)) {
                    String badge = "\u00A76[NBT]";
                    badgeW = this.font.width(badge) + 4;
                    guiGraphics.drawString(this.font, badge, contentRight - badgeW, cardY + 7, 0xFFCC00, false);
                }

                // Mod badge for entity tabs: show tag if entity was added via mod popup
                if (isEntityTab && editModLinked.contains(list.get(i))) {
                    String badge = "\u00A77[mod]";
                    badgeW = this.font.width(badge) + 4;
                    guiGraphics.drawString(this.font, badge, contentRight - badgeW, cardY + 7, 0x999999, false);
                }

                // Text with marquee for truncated entries
                String entryText = list.get(i) + (isGlobalOverlap ? " *" : "");
                int textStartX = renderLeft + textOffsetX;
                int textAvailW = contentRight - textStartX - 4 - badgeW;
                int entryTextW = this.font.width(entryText);
                int textColor = isGlobalOverlap ? 0xFFAA00 : (entryHovered ? 0xFFFFFF : 0xBBBBBB);

                if (entryTextW > textAvailW && entryHovered && i == hoveredCardIndex) {
                    long elapsed = System.currentTimeMillis() - cardHoverStartTime;
                    if (elapsed > CARD_MARQUEE_DELAY_MS) {
                        float scrollProg = (elapsed - CARD_MARQUEE_DELAY_MS) / 1000.0f * CARD_MARQUEE_SPEED;
                        int maxMarquee = entryTextW - textAvailW + 10;
                        float cycle = (float) maxMarquee * 2;
                        float pos = scrollProg % cycle;
                        int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                        guiGraphics.enableScissor(textStartX, cardY, textStartX + textAvailW, cardY + CARD_HEIGHT);
                        guiGraphics.drawString(this.font, entryText, textStartX - scrollOff, cardY + 7, textColor, false);
                        guiGraphics.disableScissor();
                    } else {
                        String truncated = this.font.plainSubstrByWidth(entryText, textAvailW - 8) + "...";
                        guiGraphics.drawString(this.font, truncated, textStartX, cardY + 7, textColor, false);
                    }
                } else if (entryTextW > textAvailW) {
                    String truncated = this.font.plainSubstrByWidth(entryText, textAvailW - 8) + "...";
                    guiGraphics.drawString(this.font, truncated, textStartX, cardY + 7, textColor, false);
                } else {
                    guiGraphics.drawString(this.font, entryText, textStartX, cardY + 7, textColor, false);
                }
            }
            y += CARD_HEIGHT + CARD_GAP;
        }

        // Update marquee hover tracking for cards
        if (currentHoveredCard != hoveredCardIndex) {
            hoveredCardIndex = currentHoveredCard;
            cardHoverStartTime = System.currentTimeMillis();
        }

        // Add button with card styling and subtle hover glow
        if (y + ADD_ROW_HEIGHT > listTop - 20 && y < listBottom + 20) {
            String addText = "+ " + Component.translatable("editor.historystages.add").getString();
            int addTextW = this.font.width(addText);
            int addBoxRight = contentLeft + addTextW + 20;
            boolean addHovered = effectiveMouseX >= contentLeft && effectiveMouseX <= addBoxRight
                    && effectiveMouseY >= y && effectiveMouseY < y + ADD_ROW_HEIGHT
                    && effectiveMouseY >= listTop && effectiveMouseY <= listBottom;

            // Use index -1 for add button hover progress
            float addProgress = cardHoverProgress.getOrDefault(-1, 0.0f);
            if (addHovered) addProgress = Math.min(1.0f, addProgress + 0.1f);
            else addProgress = Math.max(0.0f, addProgress - 0.07f);
            if (addProgress > 0.001f) cardHoverProgress.put(-1, addProgress);
            else cardHoverProgress.remove(-1);

            int addBorderAlpha = (int) (0x25 + addProgress * 0x1B);
            int addBgAlpha = (int) (0x18 + addProgress * 0x18);
            guiGraphics.fill(contentLeft, y, addBoxRight, y + ADD_ROW_HEIGHT, (addBorderAlpha << 24) | 0xFFFFFF);
            guiGraphics.fill(contentLeft + 1, y + 1, addBoxRight - 1, y + ADD_ROW_HEIGHT - 1, (addBgAlpha << 24) | 0xFFFFFF);

            if (addProgress > 0.01f) {
                int greenAlpha = (int) (addProgress * 0xAA);
                guiGraphics.fill(contentLeft, y, contentLeft + 2, y + ADD_ROW_HEIGHT, (greenAlpha << 24) | 0x55FF55);
            }

            int addG = (int) (0x88 + addProgress * 0x77);
            int addRB = (int) (0x33 + addProgress * 0x22);
            guiGraphics.drawString(this.font, addText, contentLeft + 6, y + 7,
                    (0xFF << 24) | (addRB << 16) | (addG << 8) | addRB, false);
        }

        guiGraphics.disableScissor();

        if (maxScroll > 0) {
            int scrollAreaHeight = listBottom - listTop;
            int barHeight = Math.max(20, (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
            int barY = listTop + (int) ((float) scrollOffset / maxScroll * (scrollAreaHeight - barHeight));
            guiGraphics.fill(contentRight + 2, barY, contentRight + 5, barY + barHeight, 0x80FFFFFF);
        }

        if (hasChanges) {
            String unsavedText = Component.translatable("editor.historystages.unsaved").getString();
            int dotX = this.width / 2 + 55;
            // Pulsing dot: gentle opacity oscillation
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 400.0) * 0.3 + 0.7);
            int dotAlpha = (int) (pulse * 255);
            guiGraphics.fill(dotX, this.height - 25, dotX + 6, this.height - 19, (dotAlpha << 24) | 0xFFCC00);
            drawSmallText(guiGraphics, unsavedText, dotX + 9, this.height - 24, 0xFFCC00);
        }

        if (!saveError.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, saveError, this.width / 2, this.height - 42, 0xFF5555);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        itemSearch.render(guiGraphics, this.font, mouseX, mouseY);
        modExceptionSearch.render(guiGraphics, this.font, mouseX, mouseY);
        modSearch.render(guiGraphics, this.font, mouseX, mouseY);
        entitySearch.render(guiGraphics, this.font, mouseX, mouseY);
        tagSearch.render(guiGraphics, this.font, mouseX, mouseY);
        dimensionSearch.render(guiGraphics, this.font, mouseX, mouseY);
        recipeSearch.render(guiGraphics, this.font, mouseX, mouseY);
        contextMenu.render(guiGraphics, this.font, mouseX, mouseY);
        modEntityPopup.render(guiGraphics, this.font, mouseX, mouseY);
        if (recipePopupVisible) renderRecipePopup(guiGraphics, mouseX, mouseY);
        guiGraphics.pose().popPose();

        // Tooltip rendering
        if (currentTooltipKey != null && currentTooltipText != null && !currentTooltipText.isEmpty()) {
            if (!currentTooltipKey.equals(hoveredTooltipKey)) {
                hoveredTooltipKey = currentTooltipKey;
                tooltipHoverStart = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - tooltipHoverStart >= TOOLTIP_DELAY_MS) {
                renderTooltip(guiGraphics, currentTooltipText, mouseX, mouseY);
            }
        } else {
            hoveredTooltipKey = null;
        }

    }

    private void renderTooltip(GuiGraphics guiGraphics, String text, int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);

        List<String> lines = new ArrayList<>();
        int maxWidth = 200;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() > 0 && this.font.width(line + " " + word) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());

        int tooltipW = 0;
        for (String l : lines) tooltipW = Math.max(tooltipW, this.font.width(l));
        tooltipW += 8;
        int tooltipH = lines.size() * 10 + 6;

        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 4;
        if (tooltipX + tooltipW + 2 > this.width - 4) tooltipX = mouseX - tooltipW - 4;
        if (tooltipY + tooltipH + 2 > this.height - 4) tooltipY = this.height - tooltipH - 6;
        if (tooltipX < 4) tooltipX = 4;
        if (tooltipY < 4) tooltipY = 4;

        guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH + 2, 0xFF3D3D3D);
        guiGraphics.fill(tooltipX, tooltipY, tooltipX + tooltipW, tooltipY + tooltipH, 0xFF0D0D0D);

        int ty = tooltipY + 3;
        for (String l : lines) {
            guiGraphics.drawString(this.font, l, tooltipX + 4, ty, 0xCCCCCC, false);
            ty += 10;
        }
        guiGraphics.pose().popPose();
    }

    private LivingEntity getOrCreateEntity(String entityId) {
        if (entityCache.containsKey(entityId)) return entityCache.get(entityId);
        if (Minecraft.getInstance().level == null) return null;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(entityId);
            if (rl == null) return null;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
            if (type == null) return null;
            Entity entity = type.create(Minecraft.getInstance().level);
            if (entity instanceof LivingEntity living) {
                entityCache.put(entityId, living);
                return living;
            }
            if (entity != null) entity.discard();
        } catch (Exception ignored) {}
        entityCache.put(entityId, null);
        return null;
    }

    /**
     * Renders a LivingEntity spinning around its Y axis. Uses direct entity rendering
     * instead of InventoryScreen helper to allow full 360° rotation.
     * Uses Z=1500 model view offset (final Z=550) to render above GUI elements at Z=400.
     */
    private static void renderSpinningEntity(GuiGraphics guiGraphics, int x, int y, int scale, float angleDegrees, LivingEntity entity) {
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

        org.joml.Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
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

    private static ItemStack getItemStack(String itemId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(loc);
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Returns [workstation, result] for a recipe ID, cached for performance.
     */
    private ItemStack[] getRecipeInfo(String recipeId) {
        if (!recipeInfoBuilt) {
            recipeInfoBuilt = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Collection<RecipeHolder<?>> allCachedRecipes = AllRecipesCache.get();
                Collection<RecipeHolder<?>> recipes = allCachedRecipes.isEmpty()
                        ? mc.level.getRecipeManager().getRecipes()
                        : allCachedRecipes;
                for (RecipeHolder<?> holder : recipes) {
                    try {
                        Recipe<?> recipe = holder.value();
                        String id = holder.id().toString();
                        ItemStack result = recipe.getResultItem(mc.level.registryAccess());
                        ItemStack workstation = getWorkstationForType(recipe.getType());
                        recipeInfoCache.put(id, new ItemStack[]{workstation, result});
                    } catch (Exception ignored) {}
                }
            }
        }
        return recipeInfoCache.get(recipeId);
    }

    private static ItemStack getWorkstationForType(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) return new ItemStack(Blocks.CRAFTING_TABLE);
        if (type == RecipeType.SMELTING) return new ItemStack(Blocks.FURNACE);
        if (type == RecipeType.BLASTING) return new ItemStack(Blocks.BLAST_FURNACE);
        if (type == RecipeType.SMOKING) return new ItemStack(Blocks.SMOKER);
        if (type == RecipeType.CAMPFIRE_COOKING) return new ItemStack(Blocks.CAMPFIRE);
        if (type == RecipeType.STONECUTTING) return new ItemStack(Blocks.STONECUTTER);
        if (type == RecipeType.SMITHING) return new ItemStack(Blocks.SMITHING_TABLE);
        return ItemStack.EMPTY;
    }

    private static String getRecipeTypeName(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) return "Crafting";
        if (type == RecipeType.SMELTING) return "Smelting";
        if (type == RecipeType.BLASTING) return "Blasting";
        if (type == RecipeType.SMOKING) return "Smoking";
        if (type == RecipeType.CAMPFIRE_COOKING) return "Campfire";
        if (type == RecipeType.STONECUTTING) return "Stonecutting";
        if (type == RecipeType.SMITHING) return "Smithing";
        return "Recipe";
    }

    private static int getRecipeTypeAccentColor(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) return 0xFFFFCC00;
        if (type == RecipeType.SMELTING) return 0xFFFF8800;
        if (type == RecipeType.BLASTING) return 0xFFFF4400;
        if (type == RecipeType.SMOKING) return 0xFF996633;
        if (type == RecipeType.CAMPFIRE_COOKING) return 0xFFFF6600;
        if (type == RecipeType.STONECUTTING) return 0xFF888888;
        if (type == RecipeType.SMITHING) return 0xFF6688AA;
        return 0xFF55CC55;
    }

    private void showRecipePreview(String recipeId, Runnable onAdd) {
        recipePopupId = recipeId;
        recipePopupVisible = true;
        recipePopupAddMode = true;
        recipePopupIngredientScroll = 0;
        recipePopupAddAction = onAdd;
    }

    private void closeRecipePopup() {
        recipePopupVisible = false;
        recipePopupId = null;
        recipePopupAddMode = false;
        recipePopupAddAction = null;
        popupMarqueeLastId = null;
    }

    private void renderRecipePopup(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (recipePopupId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Recipe<?> recipe = null;
        Collection<RecipeHolder<?>> allCached = AllRecipesCache.get();
        Collection<RecipeHolder<?>> allRecipes = allCached.isEmpty()
                ? mc.level.getRecipeManager().getRecipes()
                : allCached;
        for (RecipeHolder<?> r : allRecipes) {
            if (r.id().toString().equals(recipePopupId)) { recipe = r.value(); break; }
        }
        if (recipe == null) { recipePopupVisible = false; return; }

        ItemStack result = recipe.getResultItem(mc.level.registryAccess());
        ItemStack workstation = getWorkstationForType(recipe.getType());
        String typeName = getRecipeTypeName(recipe.getType());
        int typeColor = getRecipeTypeAccentColor(recipe.getType());

        // Check if this is a crafting recipe (shaped or shapeless)
        boolean isCrafting = recipe.getType() == RecipeType.CRAFTING;
        boolean isShaped = recipe instanceof ShapedRecipe;
        int craftW = isShaped ? ((ShapedRecipe) recipe).getWidth() : 0;
        int craftH = isShaped ? ((ShapedRecipe) recipe).getHeight() : 0;

        // Get raw ingredient list (preserving positions for shaped recipes)
        List<net.minecraft.world.item.crafting.Ingredient> rawIngredients = recipe.getIngredients();

        // Collect unique ingredients with counts (for non-crafting recipes)
        List<ItemStack> ingredients = new ArrayList<>();
        Map<String, Integer> ingredientCounts = new HashMap<>();
        if (!isCrafting) {
            for (net.minecraft.world.item.crafting.Ingredient ing : rawIngredients) {
                ItemStack[] items = ing.getItems();
                if (items.length > 0) {
                    ItemStack stack = items[0];
                    String key = BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + stack.getDamageValue();
                    int count = ingredientCounts.getOrDefault(key, 0);
                    if (count == 0) ingredients.add(stack.copy());
                    ingredientCounts.put(key, count + 1);
                }
            }
        }

        // Layout
        int pad = 14;
        int slotSize = 24;
        int resultSlotSize = 32;
        int rightColW = 84;
        int arrowGap = 28;

        int gridW, gridH;
        boolean hasScroll;
        if (isCrafting) {
            int gridCols = isShaped ? craftW : 3;
            int gridRows = isShaped ? craftH : (int) Math.ceil(rawIngredients.size() / 3.0);
            if (!isShaped) gridRows = Math.max(gridRows, 1);
            gridW = gridCols * slotSize;
            gridH = gridRows * slotSize;
            hasScroll = false;
        } else {
            int slotsPerRow = 3;
            int totalIngredients = ingredients.size();
            int ingredientRows = Math.max(1, (totalIngredients + slotsPerRow - 1) / slotsPerRow);
            int visibleRows = Math.min(ingredientRows, 3);
            int maxIngScroll = Math.max(0, ingredientRows - 3);
            recipePopupIngredientScroll = Math.min(recipePopupIngredientScroll, maxIngScroll);
            gridW = slotsPerRow * slotSize;
            gridH = visibleRows * slotSize;
            hasScroll = maxIngScroll > 0;
        }

        int innerW = gridW + (hasScroll ? 10 : 0) + arrowGap + rightColW;
        int popupW = Math.max(innerW + pad * 2, 240);
        int headerH = 40;
        int contentH = Math.max(gridH, resultSlotSize + 20);
        int btnAreaH = recipePopupAddMode ? 36 : 0;
        int popupH = headerH + contentH + btnAreaH + pad + 6;

        int popupX = this.width / 2 - popupW / 2;
        int popupY = this.height / 2 - popupH / 2;

        cachedPopupX = popupX;
        cachedPopupY = popupY;
        cachedPopupW = popupW;
        cachedPopupH = popupH;

        // Dim background
        guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);

        // Shadow + border + background
        guiGraphics.fill(popupX + 3, popupY + 3, popupX + popupW + 3, popupY + popupH + 3, 0x50000000);
        guiGraphics.fill(popupX - 1, popupY - 1, popupX + popupW + 1, popupY + popupH + 1, 0xFF333333);
        guiGraphics.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xFF1A1A1A);

        // Recipe type accent bar
        guiGraphics.fill(popupX, popupY, popupX + popupW, popupY + 3, typeColor);

        // Header: workstation icon + type name
        int hdrY = popupY + 8;
        int hdrX = popupX + pad;
        if (!workstation.isEmpty()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(hdrX, hdrY - 1, 0);
            guiGraphics.pose().scale(0.75f, 0.75f, 1.0f);
            guiGraphics.renderItem(workstation, 0, 0);
            guiGraphics.pose().popPose();
            hdrX += 14;
        }
        guiGraphics.drawString(this.font, typeName, hdrX, hdrY + 1, 0xFFFFFF, false);

        // ESC hint
        String escText = "[ESC]";
        guiGraphics.drawString(this.font, escText, popupX + popupW - pad - this.font.width(escText), hdrY + 1, 0x444444, false);

        // Recipe ID (with marquee scroll on hover if too wide)
        int idMaxW = popupW - pad * 2;
        int idTextW = (int)(this.font.width(recipePopupId) * SMALL_SCALE);
        int idX = popupX + pad;
        int idY = hdrY + 15;
        int idH = (int)(this.font.lineHeight * SMALL_SCALE);
        boolean isIdHovered = mouseX >= idX && mouseX < idX + idMaxW && mouseY >= idY && mouseY < idY + idH + 2;
        if (idTextW <= idMaxW) {
            drawSmallText(guiGraphics, recipePopupId, idX, idY, 0x666666);
        } else {
            // Track hover state for marquee
            if (isIdHovered && !popupIdHovered) {
                popupIdHovered = true;
                popupMarqueeStartTime = System.currentTimeMillis();
                popupMarqueeLastId = recipePopupId;
            } else if (!isIdHovered) {
                popupIdHovered = false;
            }
            float scrollOff = 0;
            int overflow = idTextW - idMaxW;
            if (isIdHovered) {
                long elapsed = System.currentTimeMillis() - popupMarqueeStartTime;
                if (elapsed > CARD_MARQUEE_DELAY_MS) {
                    float t = (elapsed - CARD_MARQUEE_DELAY_MS) / 1000.0f;
                    float cycle = overflow / CARD_MARQUEE_SPEED;
                    float phase = t % (cycle * 2);
                    scrollOff = phase <= cycle ? (phase / cycle) * overflow : (2 - phase / cycle) * overflow;
                }
            }
            guiGraphics.enableScissor(idX, idY, idX + idMaxW, idY + idH + 2);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(idX - scrollOff, idY, 0);
            guiGraphics.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
            guiGraphics.drawString(this.font, recipePopupId, 0, 0, 0x666666, false);
            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
        }

        // Separator
        int sepY = popupY + headerH - 1;
        guiGraphics.fill(popupX + pad - 2, sepY, popupX + popupW - pad + 2, sepY + 1, 0xFF333333);

        // Content area
        int contentY = popupY + headerH + 6;
        int gridX = popupX + pad;
        int gridY = contentY;

        // Ingredient grid
        ItemStack hoveredIngredient = ItemStack.EMPTY;
        if (isCrafting) {
            // Crafting grid: render all slots in grid pattern
            int gridCols = isShaped ? craftW : 3;
            int gridRows = isShaped ? craftH : (int) Math.max(1, Math.ceil(rawIngredients.size() / 3.0));
            for (int row = 0; row < gridRows; row++) {
                for (int col = 0; col < gridCols; col++) {
                    int sx = gridX + col * slotSize;
                    int sy = gridY + row * slotSize;
                    int idx = row * gridCols + col;
                    guiGraphics.fill(sx, sy, sx + slotSize - 1, sy + slotSize - 1, 0xFF2A2A2A);
                    guiGraphics.fill(sx + 1, sy + 1, sx + slotSize - 2, sy + slotSize - 2, 0xFF1E1E1E);
                    if (idx < rawIngredients.size()) {
                        ItemStack[] items = rawIngredients.get(idx).getItems();
                        if (items.length > 0) {
                            guiGraphics.renderItem(items[0], sx + 4, sy + 4);
                            if (mouseX >= sx && mouseX < sx + slotSize - 1 && mouseY >= sy && mouseY < sy + slotSize - 1) {
                                hoveredIngredient = items[0];
                            }
                        }
                    }
                }
            }
        } else {
            // Generic ingredient grid with scroll
            int slotsPerRow = 3;
            int totalIngredients = ingredients.size();
            int ingredientRows = Math.max(1, (totalIngredients + slotsPerRow - 1) / slotsPerRow);
            int maxIngScroll = Math.max(0, ingredientRows - 3);
            recipePopupIngredientScroll = Math.min(recipePopupIngredientScroll, maxIngScroll);

            guiGraphics.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
            int startIdx = recipePopupIngredientScroll * slotsPerRow;
            for (int idx = 0; idx < totalIngredients; idx++) {
                int displayIdx = idx - startIdx;
                if (displayIdx < 0) continue;
                int row = displayIdx / slotsPerRow;
                int col = displayIdx % slotsPerRow;
                int sx = gridX + col * slotSize;
                int sy = gridY + row * slotSize;
                if (sy >= gridY + gridH) break;

                ItemStack stack = ingredients.get(idx);
                String key = BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + stack.getDamageValue();
                int count = ingredientCounts.getOrDefault(key, 1);

                guiGraphics.fill(sx, sy, sx + slotSize - 1, sy + slotSize - 1, 0xFF2A2A2A);
                guiGraphics.fill(sx + 1, sy + 1, sx + slotSize - 2, sy + slotSize - 2, 0xFF1E1E1E);
                guiGraphics.renderItem(stack, sx + 4, sy + 4);
                if (mouseX >= sx && mouseX < sx + slotSize - 1 && mouseY >= sy && mouseY < sy + slotSize - 1
                        && sy >= gridY && sy + slotSize - 1 <= gridY + gridH) {
                    hoveredIngredient = stack;
                }

                if (count > 1) {
                    String cs = count + "x";
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(sx + slotSize - this.font.width(cs) * 0.65f - 1, sy + slotSize - 9, 200);
                    guiGraphics.pose().scale(0.65f, 0.65f, 1.0f);
                    guiGraphics.drawString(this.font, cs, 0, 0, 0xFFFFFF, true);
                    guiGraphics.pose().popPose();
                }
            }
            guiGraphics.disableScissor();

            // Scroll bar
            if (hasScroll) {
                int sbX = gridX + gridW + 3;
                int thumbH = Math.max(8, gridH * 3 / ingredientRows);
                int thumbY = gridY + (int)((float) recipePopupIngredientScroll / maxIngScroll * (gridH - thumbH));
                guiGraphics.fill(sbX, gridY, sbX + 2, gridY + gridH, 0xFF2A2A2A);
                guiGraphics.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFF666666);
            }
        }

        // Arrow
        int arrowX = gridX + gridW + (hasScroll ? 14 : 8);
        int arrowY = contentY + contentH / 2 - 4;
        guiGraphics.drawString(this.font, "\u2192", arrowX, arrowY, (typeColor & 0x00FFFFFF) | 0xFF000000, false);

        // Result area
        int resultAreaX = popupX + popupW - pad - rightColW;
        int rSlotX = resultAreaX + (rightColW - resultSlotSize) / 2;
        int rSlotY = contentY + Math.max(0, (contentH - resultSlotSize - 18) / 2);

        // Result slot with gold border
        guiGraphics.fill(rSlotX - 2, rSlotY - 2, rSlotX + resultSlotSize + 2, rSlotY + resultSlotSize + 2, 0xAAFFCC00);
        guiGraphics.fill(rSlotX - 1, rSlotY - 1, rSlotX + resultSlotSize + 1, rSlotY + resultSlotSize + 1, 0xFF2A2A1A);
        guiGraphics.fill(rSlotX, rSlotY, rSlotX + resultSlotSize, rSlotY + resultSlotSize, 0xFF1A1A14);

        if (!result.isEmpty()) {
            guiGraphics.renderItem(result, rSlotX + 8, rSlotY + 8);
            if (result.getCount() > 1) {
                String cs = String.valueOf(result.getCount());
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(rSlotX + resultSlotSize - this.font.width(cs) * 0.7f, rSlotY + resultSlotSize - 9, 200);
                guiGraphics.pose().scale(0.7f, 0.7f, 1.0f);
                guiGraphics.drawString(this.font, cs, 0, 0, 0xFFFFFF, true);
                guiGraphics.pose().popPose();
            }
            String rName = result.getHoverName().getString();
            int nameW = (int)(this.font.width(rName) * SMALL_SCALE);
            if (nameW > rightColW) {
                rName = this.font.plainSubstrByWidth(rName, (int)(rightColW / SMALL_SCALE) - 6) + "...";
                nameW = (int)(this.font.width(rName) * SMALL_SCALE);
            }
            drawSmallText(guiGraphics, rName, resultAreaX + (rightColW - nameW) / 2, rSlotY + resultSlotSize + 4, 0xFFCC00);
        }

        // Workstation below result
        if (!workstation.isEmpty()) {
            int stationSlot = 22;
            int stationX = resultAreaX + (rightColW - stationSlot) / 2;
            int stationY = rSlotY + resultSlotSize + 18;
            if (stationY + stationSlot < contentY + contentH + 10) {
                guiGraphics.fill(stationX, stationY, stationX + stationSlot - 1, stationY + stationSlot - 1, 0xFF2A2A2A);
                guiGraphics.fill(stationX + 1, stationY + 1, stationX + stationSlot - 2, stationY + stationSlot - 2, 0xFF1E1E1E);
                guiGraphics.renderItem(workstation, stationX + 3, stationY + 3);
                drawSmallText(guiGraphics, "Station", stationX - 2, stationY + stationSlot + 2, 0x555555);
            }
        }

        // Add button (add mode only)
        if (recipePopupAddMode) {
            int btnW = 76;
            int btnH = 18;
            int btnY = popupY + popupH - pad - btnH;
            int addBtnX = popupX + popupW / 2 - btnW / 2;

            boolean aHov = mouseX >= addBtnX && mouseX < addBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            guiGraphics.fill(addBtnX, btnY, addBtnX + btnW, btnY + btnH, aHov ? 0x50FFCC00 : 0x25FFCC00);
            guiGraphics.fill(addBtnX, btnY + btnH - 2, addBtnX + btnW, btnY + btnH, aHov ? 0xD0FFCC00 : 0x70FFCC00);
            String aLabel = Component.translatable("editor.historystages.add").getString();
            guiGraphics.drawCenteredString(this.font, aLabel, addBtnX + btnW / 2, btnY + 5, aHov ? 0xFFFFFF : 0xDDDDDD);
        }

        // Ingredient tooltip
        if (!hoveredIngredient.isEmpty()) {
            guiGraphics.renderTooltip(this.font, hoveredIngredient, mouseX, mouseY);
        }
    }

    private void drawSmallText(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
        guiGraphics.drawString(this.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modEntityPopup.isVisible()) { return modEntityPopup.mouseClicked(mouseX, mouseY); }
        if (recipePopupVisible) {
            int btnW = 76, btnH = 18, btnPad = 14;
            if (recipePopupAddMode) {
                int btnY = cachedPopupY + cachedPopupH - btnPad - btnH;
                int addBtnX = cachedPopupX + cachedPopupW / 2 - btnW / 2;
                if (mouseX >= addBtnX && mouseX < addBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    if (recipePopupAddAction != null) recipePopupAddAction.run();
                    closeRecipePopup();
                    if (recipeSearch.isVisible()) recipeSearch.hide();
                    return true;
                }
            }
            // Click outside popup closes everything
            if (mouseX < cachedPopupX || mouseX > cachedPopupX + cachedPopupW
                    || mouseY < cachedPopupY || mouseY > cachedPopupY + cachedPopupH) {
                closeRecipePopup();
                if (recipeSearch.isVisible()) recipeSearch.hide();
                return true;
            }
            return true; // consume clicks inside popup
        }
        if (contextMenu.isVisible()) {
            contextMenu.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (itemSearch.isVisible()) { if (itemSearch.mouseClicked(mouseX, mouseY)) return true; }
        if (modExceptionSearch.isVisible()) { if (modExceptionSearch.mouseClicked(mouseX, mouseY)) return true; }
        if (modSearch.isVisible()) { if (modSearch.mouseClicked(mouseX, mouseY)) return true; }
        if (entitySearch.isVisible()) { if (entitySearch.mouseClicked(mouseX, mouseY)) return true; }
        if (tagSearch.isVisible()) { if (tagSearch.mouseClicked(mouseX, mouseY)) return true; }
        if (dimensionSearch.isVisible()) { if (dimensionSearch.mouseClicked(mouseX, mouseY)) return true; }
        if (recipeSearch.isVisible()) { if (recipeSearch.mouseClicked(mouseX, mouseY)) return true; }

        if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
            // Tab scroll arrow clicks
            if (maxTabScroll > 0) {
                int tabAreaLeft = 20;
                int tabAreaRight = this.width - 20;
                if (tabScrollOffset > 0 && mouseX >= tabAreaLeft && mouseX < tabAreaLeft + TAB_ARROW_WIDTH) {
                    tabScrollOffset = Math.max(0, tabScrollOffset - 40);
                    return true;
                }
                if (tabScrollOffset < maxTabScroll && mouseX >= tabAreaRight - TAB_ARROW_WIDTH && mouseX < tabAreaRight) {
                    tabScrollOffset = Math.min(maxTabScroll, tabScrollOffset + 40);
                    return true;
                }
            }
            for (int i = 0; i < TAB_KEYS.length; i++) {
                int scrolledTabX = tabX[i] - tabScrollOffset;
                if (mouseX >= scrolledTabX && mouseX < scrolledTabX + tabW[i]) { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)); switchTab(i); return true; }
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int listTop = HEADER_HEIGHT;
        int listBottom = this.height - 40;
        int contentLeft = 30;
        int contentRight = this.width - 30;
        if (mouseX < contentLeft - 10 || mouseX > contentRight + 10 || mouseY < listTop || mouseY > listBottom) return false;

        List<String> list = getActiveList();
        int y = listTop - (int) smoothScrollOffset + CARD_GAP;

        // Account for overlap warning banner offset (same logic as in render)
        if (isIndividual && (activeTab == 0 || activeTab == 1 || activeTab == 2)) {
            java.util.Set<String> globalLocked = new java.util.HashSet<>();
            for (StageEntry gEntry : StageManager.getStages().values()) {
                switch (activeTab) {
                    case 0 -> globalLocked.addAll(gEntry.getAllItemIds());
                    case 1 -> globalLocked.addAll(gEntry.getTags());
                    case 2 -> globalLocked.addAll(gEntry.getMods());
                }
            }
            if (list.stream().anyMatch(globalLocked::contains)) {
                y += 14 + CARD_GAP;
            }
        }

        for (int i = 0; i < list.size(); i++) {
            if (mouseY >= y && mouseY < y + CARD_HEIGHT && mouseY >= listTop && mouseY <= listBottom) {
                if (button == 0 && activeTab == 4) {
                    // Left-click on recipe card: show recipe detail popup (view-only)
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    recipePopupId = list.get(i);
                    recipePopupVisible = true;
                    recipePopupAddMode = false;
                    recipePopupAddAction = null;
                    recipePopupIngredientScroll = 0;
                    return true;
                }
                if (button == 1) {
                    final int entryIdx = i;
                    final String entryValue = list.get(i);
                    final int tabIdx = activeTab;
                    contextMenu = new ContextMenu();
                    if (tabIdx == 0) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(), () -> openNbtEditScreen(entryIdx, entryValue));
                    }
                    if (tabIdx == 3) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(), () -> openModExceptionNbtEditScreen(entryIdx, entryValue));
                    }
                    contextMenu.addEntry(Component.translatable("editor.historystages.copy_id").getString(), () -> Minecraft.getInstance().keyboardHandler.setClipboard(entryValue));
                    contextMenu.addEntry(Component.translatable("editor.historystages.remove").getString(), () -> {
                        String removedValue = getListForSection(tabIdx).remove(entryIdx);
                        // When removing an item, shift NBT indices
                        if (tabIdx == 0) {
                            editItemNbt.remove(entryIdx);
                            Map<Integer, com.google.gson.JsonObject> shifted = new HashMap<>();
                            for (var e : editItemNbt.entrySet()) {
                                int key = e.getKey();
                                shifted.put(key > entryIdx ? key - 1 : key, e.getValue());
                            }
                            editItemNbt.clear();
                            editItemNbt.putAll(shifted);
                        }
                        // When removing a mod exception, shift NBT indices
                        if (tabIdx == 3) {
                            editModExceptionNbt.remove(entryIdx);
                            Map<Integer, com.google.gson.JsonObject> shifted = new HashMap<>();
                            for (var e : editModExceptionNbt.entrySet()) {
                                int key = e.getKey();
                                shifted.put(key > entryIdx ? key - 1 : key, e.getValue());
                            }
                            editModExceptionNbt.clear();
                            editModExceptionNbt.putAll(shifted);
                        }
                        // When removing a mod, also remove mod-linked entities and exceptions from that mod
                        if (tabIdx == 2 && removedValue != null) {
                            String prefix = removedValue + ":";
                            editSpawnlock.removeIf(id -> id.startsWith(prefix) && editModLinked.contains(id));
                            editAttacklock.removeIf(id -> id.startsWith(prefix) && editModLinked.contains(id));
                            editModLinked.removeIf(id -> id.startsWith(prefix));
                            // Remove mod exceptions belonging to this mod
                            for (int j = editModExceptions.size() - 1; j >= 0; j--) {
                                if (editModExceptions.get(j).startsWith(prefix)) {
                                    editModExceptions.remove(j);
                                    editModExceptionNbt.remove(j);
                                    // Shift remaining NBT indices
                                    Map<Integer, com.google.gson.JsonObject> shiftedEx = new HashMap<>();
                                    for (var ex : editModExceptionNbt.entrySet()) {
                                        int key = ex.getKey();
                                        shiftedEx.put(key > j ? key - 1 : key, ex.getValue());
                                    }
                                    editModExceptionNbt.clear();
                                    editModExceptionNbt.putAll(shiftedEx);
                                }
                            }
                        }
                        hasChanges = true; updateMaxScroll();
                    });
                    contextMenu.show((int) mouseX, (int) mouseY, this.font);
                    return true;
                }
            }
            y += CARD_HEIGHT + CARD_GAP;
        }

        if (mouseY >= y && mouseY < y + ADD_ROW_HEIGHT && mouseY >= listTop && mouseY <= listBottom) {
            String addText = "+ " + Component.translatable("editor.historystages.add").getString();
            int addTextW = this.font.width(addText);
            int addBoxRight = contentLeft + addTextW + 20;
            if (mouseX >= contentLeft && mouseX <= addBoxRight) { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)); openAddDialog(); return true; }
        }
        return false;
    }

    private void openAddDialog() {
        int contentLeft = 30;
        int contentRight = this.width - 30;
        int cw = contentRight - contentLeft;
        if (activeTab == 0) { itemSearch.setFilter(""); itemSearch.show(this.width / 2, this.height / 2, cw); }
        else if (activeTab == 1) { tagSearch.setFilter(""); tagSearch.show(this.width / 2, this.height / 2, cw); }
        else if (activeTab == 2) { modSearch.setFilter(""); modSearch.show(this.width / 2, this.height / 2, cw); }
        else if (activeTab == 3) { modExceptionSearch = createModExceptionSearch(); modExceptionSearch.setFilter(""); modExceptionSearch.show(this.width / 2, this.height / 2, cw); }
        else if (activeTab == 4) { recipeSearch.setFilter(""); recipeSearch.show(this.width / 2, this.height / 2, cw); }
        else if (activeTab == 5) { dimensionSearch.setFilter(""); dimensionSearch.show(this.width / 2, this.height / 2, cw); }
        else if (activeTab == 6 || activeTab == 7) { entitySearch.setFilter(""); entitySearch.show(this.width / 2, this.height / 2, cw); }
    }

    private void openEditDialog(int tabIdx, int entryIdx, String currentValue) {
        int contentLeft = 30;
        int contentRight = this.width - 30;
        int cw = contentRight - contentLeft;
        if (tabIdx == 0) {
            itemSearch = new SearchableItemList(itemId -> { getListForSection(tabIdx).set(entryIdx, itemId); hasChanges = true;
                itemSearch = new SearchableItemList(id -> { getActiveList().add(id); hasChanges = true; updateMaxScroll(); }); });
            itemSearch.show(this.width / 2, this.height / 2, cw);
        } else if (tabIdx == 1) {
            tagSearch = new SearchableTagList(tagId -> { getListForSection(tabIdx).set(entryIdx, tagId); hasChanges = true;
                tagSearch = new SearchableTagList(id -> { editTags.add(id); hasChanges = true; updateMaxScroll(); }); });
            tagSearch.show(this.width / 2, this.height / 2, cw);
        } else if (tabIdx == 2) {
            modSearch = new SearchableModList(modId -> {
                getListForSection(tabIdx).set(entryIdx, modId); hasChanges = true;
                String displayName = modSearch.getDisplayName(modId);
                modEntityPopup.showForMod(modId, displayName, this.width / 2, this.height / 2);
                modSearch = new SearchableModList(id -> {
                    editMods.add(id); hasChanges = true; updateMaxScroll();
                    String dn = modSearch.getDisplayName(id);
                    modEntityPopup.showForMod(id, dn, this.width / 2, this.height / 2);
                });
            });
            modSearch.show(this.width / 2, this.height / 2, cw);
        } else if (tabIdx == 3) {
            modExceptionSearch = createModExceptionSearch();
            // Replace callback for edit mode
            modExceptionSearch = new SearchableItemList(itemId -> { getListForSection(tabIdx).set(entryIdx, itemId); hasChanges = true;
                modExceptionSearch = createModExceptionSearch(); });
            modExceptionSearch.setModFilter(new java.util.HashSet<>(editMods));
            modExceptionSearch.show(this.width / 2, this.height / 2, cw);
        } else if (tabIdx == 4) {
            recipeSearch = new SearchableRecipeList(recipeId -> {
                showRecipePreview(recipeId, () -> {
                    getListForSection(tabIdx).set(entryIdx, recipeId);
                    hasChanges = true;
                    recipeSearch = new SearchableRecipeList(id -> {
                        showRecipePreview(id, () -> { editRecipes.add(id); hasChanges = true; updateMaxScroll(); });
                    });
                    recipeSearch.setKeepVisibleOnSelect(true);
                });
            });
            recipeSearch.setKeepVisibleOnSelect(true);
            recipeSearch.show(this.width / 2, this.height / 2, cw);
        } else if (tabIdx == 5) {
            dimensionSearch = new SearchableDimensionList(dimId -> { getListForSection(tabIdx).set(entryIdx, dimId); hasChanges = true;
                dimensionSearch = new SearchableDimensionList(id -> { editDimensions.add(id); hasChanges = true; updateMaxScroll(); }); });
            dimensionSearch.show(this.width / 2, this.height / 2, cw);
        } else if (tabIdx == 6 || tabIdx == 7) {
            entitySearch = new SearchableEntityList(entityId -> { getListForSection(tabIdx).set(entryIdx, entityId); hasChanges = true;
                entitySearch = new SearchableEntityList(id -> { getActiveList().add(id); hasChanges = true; updateMaxScroll(); }); });
            entitySearch.show(this.width / 2, this.height / 2, cw);
        } else {
            this.minecraft.setScreen(new AddEntryScreen(this, tabIdx, entryIdx, currentValue));
        }
    }

    private void openNbtEditScreen(int entryIdx, String itemId) {
        com.google.gson.JsonObject currentNbt = editItemNbt.get(entryIdx);
        this.minecraft.setScreen(new NbtItemEditScreen(this, itemId, currentNbt, nbt -> {
            if (nbt != null) {
                editItemNbt.put(entryIdx, nbt);
            } else {
                editItemNbt.remove(entryIdx);
            }
            hasChanges = true;
        }));
    }

    private void openModExceptionNbtEditScreen(int entryIdx, String itemId) {
        com.google.gson.JsonObject currentNbt = editModExceptionNbt.get(entryIdx);
        this.minecraft.setScreen(new NbtItemEditScreen(this, itemId, currentNbt, nbt -> {
            if (nbt != null) {
                editModExceptionNbt.put(entryIdx, nbt);
            } else {
                editModExceptionNbt.remove(entryIdx);
            }
            hasChanges = true;
        }));
    }

    private SearchableItemList createModExceptionSearch() {
        SearchableItemList search = new SearchableItemList(itemId -> {
            editModExceptions.add(itemId);
            hasChanges = true;
            updateMaxScroll();
        });
        search.setModFilter(new java.util.HashSet<>(editMods));
        return search;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (modEntityPopup.isVisible() && modEntityPopup.mouseDragged(mouseX, mouseY)) return true;
        if (itemSearch.isVisible() && itemSearch.mouseDragged(mouseX, mouseY)) return true;
        if (modExceptionSearch.isVisible() && modExceptionSearch.mouseDragged(mouseX, mouseY)) return true;
        if (modSearch.isVisible() && modSearch.mouseDragged(mouseX, mouseY)) return true;
        if (entitySearch.isVisible() && entitySearch.mouseDragged(mouseX, mouseY)) return true;
        if (tagSearch.isVisible() && tagSearch.mouseDragged(mouseX, mouseY)) return true;
        if (dimensionSearch.isVisible() && dimensionSearch.mouseDragged(mouseX, mouseY)) return true;
        if (recipeSearch.isVisible() && recipeSearch.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (modEntityPopup.isVisible() && modEntityPopup.mouseReleased()) return true;
        if (itemSearch.isVisible() && itemSearch.mouseReleased()) return true;
        if (modExceptionSearch.isVisible() && modExceptionSearch.mouseReleased()) return true;
        if (modSearch.isVisible() && modSearch.mouseReleased()) return true;
        if (entitySearch.isVisible() && entitySearch.mouseReleased()) return true;
        if (tagSearch.isVisible() && tagSearch.mouseReleased()) return true;
        if (dimensionSearch.isVisible() && dimensionSearch.mouseReleased()) return true;
        if (recipeSearch.isVisible() && recipeSearch.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double delta = scrollY;
        if (modEntityPopup.isVisible() && modEntityPopup.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (recipePopupVisible) {
            recipePopupIngredientScroll = Math.max(0, recipePopupIngredientScroll - (int) delta);
            return true;
        }
        if (itemSearch.isVisible() && itemSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (modExceptionSearch.isVisible() && modExceptionSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (modSearch.isVisible() && modSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (entitySearch.isVisible() && entitySearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (tagSearch.isVisible() && tagSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (dimensionSearch.isVisible() && dimensionSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (recipeSearch.isVisible() && recipeSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;

        // Tab area mouse scroll
        if (maxTabScroll > 0 && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
            tabScrollOffset = Math.max(0, Math.min(maxTabScroll, tabScrollOffset - (int)(delta * 30)));
            return true;
        }

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modEntityPopup.isVisible() && modEntityPopup.keyPressed(keyCode)) return true;
        if (recipePopupVisible && keyCode == 256) {
            closeRecipePopup();
            return true;
        }
        if (itemSearch.isVisible() && itemSearch.keyPressed(keyCode)) return true;
        if (modExceptionSearch.isVisible() && modExceptionSearch.keyPressed(keyCode)) return true;
        if (modSearch.isVisible() && modSearch.keyPressed(keyCode)) return true;
        if (entitySearch.isVisible() && entitySearch.keyPressed(keyCode)) return true;
        if (tagSearch.isVisible() && tagSearch.keyPressed(keyCode)) return true;
        if (dimensionSearch.isVisible() && dimensionSearch.keyPressed(keyCode)) return true;
        if (recipeSearch.isVisible() && recipeSearch.keyPressed(keyCode)) return true;

        if (Screen.hasControlDown()) {
            EditBox focused = getFocusedEditBox();
            if (focused != null) {
                if (keyCode == 65) { // Ctrl+A
                    focused.setCursorPosition(focused.getValue().length());
                    focused.setHighlightPos(0);
                    return true;
                }
                if (keyCode == 67) { // Ctrl+C
                    String selected = focused.getHighlighted();
                    if (!selected.isEmpty()) Minecraft.getInstance().keyboardHandler.setClipboard(selected);
                    return true;
                }
            }
        }

        if (stageIdField.isFocused() || displayNameField.isFocused() || researchTimeField.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == 256) { tryClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private EditBox getFocusedEditBox() {
        if (stageIdField != null && stageIdField.isFocused()) return stageIdField;
        if (displayNameField != null && displayNameField.isFocused()) return displayNameField;
        if (researchTimeField != null && researchTimeField.isFocused()) return researchTimeField;
        return null;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (itemSearch.isVisible() && itemSearch.charTyped(c)) return true;
        if (modExceptionSearch.isVisible() && modExceptionSearch.charTyped(c)) return true;
        if (modSearch.isVisible() && modSearch.charTyped(c)) return true;
        if (entitySearch.isVisible() && entitySearch.charTyped(c)) return true;
        if (tagSearch.isVisible() && tagSearch.charTyped(c)) return true;
        if (dimensionSearch.isVisible() && dimensionSearch.charTyped(c)) return true;
        if (recipeSearch.isVisible() && recipeSearch.charTyped(c)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    private void tryClose() {
        if (hasChanges) {
            Screen overview = parent;
            this.minecraft.setScreen(new ConfirmDialog(this, Component.translatable("editor.historystages.unsaved_warning_title"),
                    Component.translatable("editor.historystages.unsaved_warning"), () -> Minecraft.getInstance().setScreen(overview)));
        } else { this.minecraft.setScreen(parent); }
    }

    private void saveStage() {
        String id = editStageId.trim();
        if (id.isEmpty()) { saveError = Component.translatable("editor.historystages.id_empty").getString(); return; }
        if (!id.matches("[a-zA-Z0-9_\\-]+")) { saveError = Component.translatable("editor.historystages.id_invalid").getString(); return; }
        if (editDisplayName.trim().isEmpty()) { saveError = Component.translatable("editor.historystages.display_name_empty").getString(); return; }
        saveError = "";

        StageEntry newEntry = new StageEntry();
        newEntry.setDisplayName(editDisplayName);
        newEntry.setResearchTime(editResearchTime);
        List<net.bananemdnsa.historystages.data.ItemEntry> itemEntries = new ArrayList<>();
        for (int idx = 0; idx < editItems.size(); idx++) {
            com.google.gson.JsonObject nbt = editItemNbt.get(idx);
            itemEntries.add(new net.bananemdnsa.historystages.data.ItemEntry(editItems.get(idx), nbt));
        }
        newEntry.setItemEntries(itemEntries);
        newEntry.setTags(editTags);
        newEntry.setMods(editMods);
        List<net.bananemdnsa.historystages.data.ItemEntry> modExceptionEntries = new ArrayList<>();
        for (int idx = 0; idx < editModExceptions.size(); idx++) {
            com.google.gson.JsonObject nbt = editModExceptionNbt.get(idx);
            modExceptionEntries.add(new net.bananemdnsa.historystages.data.ItemEntry(editModExceptions.get(idx), nbt));
        }
        newEntry.setModExceptionEntries(modExceptionEntries);
        newEntry.setRecipes(editRecipes);
        newEntry.setDimensions(editDimensions);
        EntityLocks locks = new EntityLocks();
        locks.setAttacklock(editAttacklock);
        locks.setSpawnlock(editSpawnlock);
        locks.setModLinked(editModLinked);
        newEntry.setEntities(locks);
        PacketHandler.sendToServer(new SaveStagePacket(id, newEntry, isIndividual));
        hasChanges = false;
    }

    @Override public void onClose() { this.minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }

    /**
     * Inner screen for adding or editing a text entry in a section.
     */
    static class AddEntryScreen extends Screen {
        private final StageDetailScreen parent;
        private final int sectionIndex;
        private final int editIndex;
        private final String initialValue;
        private EditBox inputField;

        protected AddEntryScreen(StageDetailScreen parent, int sectionIndex, int editIndex, String initialValue) {
            super(Component.translatable(editIndex >= 0 ? "editor.historystages.edit" : "editor.historystages.add"));
            this.parent = parent;
            this.sectionIndex = sectionIndex;
            this.editIndex = editIndex;
            this.initialValue = initialValue;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            inputField = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20,
                    Component.translatable(editIndex >= 0 ? "editor.historystages.edit" : "editor.historystages.add"));
            inputField.setMaxLength(256);
            inputField.setValue(initialValue);
            inputField.setFocused(true);
            this.addRenderableWidget(inputField);
            this.setFocused(inputField);

            String hint = switch (sectionIndex) {
                case 1 -> "forge:ingots";
                case 4 -> "minecraft:stone";
                case 5 -> "minecraft:the_nether";
                default -> "";
            };
            if (!hint.isEmpty()) inputField.setHint(Component.literal("\u00A77" + hint));

            String buttonLabel = editIndex >= 0
                    ? Component.translatable("editor.historystages.save").getString()
                    : Component.translatable("editor.historystages.add").getString();

            this.addRenderableWidget(StyledButton.of(Component.literal(buttonLabel),
                    btn -> addAndClose(), centerX - 105, centerY + 20, 100, 20));
            this.addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.cancel"),
                    btn -> this.minecraft.setScreen(parent), centerX + 5, centerY + 20, 100, 20));
        }

        private void addAndClose() {
            String value = inputField.getValue().trim();
            if (!value.isEmpty()) {
                if (editIndex >= 0) parent.getListForSection(sectionIndex).set(editIndex, value);
                else parent.getListForSection(sectionIndex).add(value);
                parent.hasChanges = true;
                parent.updateMaxScroll();
            }
            this.minecraft.setScreen(parent);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 257) { addAndClose(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xC0000000);
            int boxW = 260;
            int boxH = 100;
            int boxX = (this.width - boxW) / 2;
            int boxY = (this.height - boxH) / 2 - 10;
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF2D2D2D);
            guiGraphics.fill(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1, 0xFF1A1A1A);
            String sectionName = Component.translatable(SECTION_KEYS[sectionIndex]).getString();
            String titleLabel = editIndex >= 0 ? Component.translatable("editor.historystages.edit").getString() : Component.translatable("editor.historystages.add").getString();
            guiGraphics.drawCenteredString(this.font, titleLabel + " \u2014 " + sectionName, this.width / 2, boxY + 8, 0xFFFFFF);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override public void onClose() { this.minecraft.setScreen(parent); }
    }
}
