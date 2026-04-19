package net.bananemdnsa.historystages.client.editor;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bananemdnsa.historystages.client.editor.widget.*;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DependencyEditorScreen extends Screen {
    private final Screen parent;
    private final List<DependencyGroup> groups;
    private final boolean isIndividual;
    private final Consumer<List<DependencyGroup>> onSave;
    private final String currentStageId;

    private int selectedGroup = 0;
    private int activeTab = 0;
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private boolean hasChanges = false;

    // Tab definitions (translation keys)
    private static final String[] GLOBAL_TAB_KEYS = {
            "editor.historystages.dep.tab.items", "editor.historystages.dep.tab.global_stages",
            "editor.historystages.dep.tab.individual_stages"
    };
    private static final String[] INDIVIDUAL_TAB_KEYS = {
            "editor.historystages.dep.tab.items", "editor.historystages.dep.tab.global_stages",
            "editor.historystages.dep.tab.individual_stages",
            "editor.historystages.dep.tab.advancements", "editor.historystages.dep.tab.xp_level",
            "editor.historystages.dep.tab.entity_kills", "editor.historystages.dep.tab.stats"
    };

    private static final String[] GLOBAL_TOOLTIP_KEYS = {
            "editor.historystages.dep.tooltip.items", "editor.historystages.dep.tooltip.global_stages",
            "editor.historystages.dep.tooltip.individual_stages"
    };
    private static final String[] INDIVIDUAL_TOOLTIP_KEYS = {
            "editor.historystages.dep.tooltip.items", "editor.historystages.dep.tooltip.global_stages",
            "editor.historystages.dep.tooltip.individual_stages",
            "editor.historystages.dep.tooltip.advancements", "editor.historystages.dep.tooltip.xp_level",
            "editor.historystages.dep.tooltip.entity_kills", "editor.historystages.dep.tooltip.stats"
    };

    // Layout
    private static final int LEFT_PANEL_W = 130;
    private static final int CARD_HEIGHT = 22;
    private static final int CARD_GAP = 3;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_PAD = 8;
    private static final int TAB_ARROW_WIDTH = 12;
    private static final int HEADER_HEIGHT = 30;
    private static final float SMALL_SCALE = 0.85f;

    // Marquee
    private static final long CARD_MARQUEE_DELAY_MS = 800;
    private static final float CARD_MARQUEE_SPEED = 25.0f;
    private int hoveredCardIndex = -1;
    private long cardHoverStartTime = 0;

    // Card hover animation
    private final Map<Integer, Float> cardHoverProgress = new HashMap<>();

    // Animated tab indicator
    private float tabIndicatorX = 0;
    private float tabIndicatorW = 0;
    private boolean tabIndicatorInit = false;
    private long tabSwitchTime = 0;

    // Tab scrolling
    private int tabScrollOffset = 0;
    private int maxTabScroll = 0;

    // Searchable widget overlays
    private SearchableItemList itemSearch;
    private SearchableEntityList entitySearch;
    private SearchableStageList globalStageSearch;
    private SearchableStageList individualStageSearch;
    private SearchableAdvancementList advancementSearch;
    private SearchableStatList statSearch;

    // Context menu
    private ContextMenu contextMenu;

    // Count/value input dialog (custom text input, no EditBox)
    private String countDialogType = null;
    private String pendingId = null;
    private int editingEntryIndex = -1;
    private String countInputText = "";
    private boolean countCursorBlink = true;

    // Tab layout arrays
    private int[] tabX;
    private int[] tabW;
    private int tabY;

    // Tooltip hover tracking
    private String hoveredTooltipKey = null;
    private long tooltipHoverStart = 0;
    private static final long TOOLTIP_DELAY_MS = 400;

    // NBT editing for item dependencies
    private final Map<Integer, JsonObject> itemNbtMap = new HashMap<>();

    // Content scrollbar
    private boolean draggingContentScrollbar = false;
    private static final int SCROLLBAR_WIDTH = 4;

    // Entity 3D model cache
    private final Map<String, LivingEntity> entityCache = new HashMap<>();

    public DependencyEditorScreen(Screen parent, List<DependencyGroup> dependencies, boolean isIndividual,
            String currentStageId, Consumer<List<DependencyGroup>> onSave) {
        super(Component.translatable("editor.historystages.dep.title"));
        this.parent = parent;
        this.groups = dependencies != null
                ? dependencies.stream().map(DependencyGroup::copy).collect(Collectors.toList())
                : new ArrayList<>();
        this.isIndividual = isIndividual;
        this.currentStageId = currentStageId;
        this.onSave = onSave;
    }

    private String[] getTabKeys() {
        return isIndividual ? INDIVIDUAL_TAB_KEYS : GLOBAL_TAB_KEYS;
    }

    private String[] getTabTooltipKeys() {
        return isIndividual ? INDIVIDUAL_TOOLTIP_KEYS : GLOBAL_TOOLTIP_KEYS;
    }

    private String t(String key) {
        return Component.translatable(key).getString();
    }

    private String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    protected void init() {
        // Save button (no checkmark, like StageDetailScreen)
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> save(), this.width - 60, this.height - 25, 50, 18));

        // Back button
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> confirmDiscard(), 10, this.height - 25, 50, 18));

        // Add Group button
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.dep.add_group"),
                btn -> {
                    groups.add(new DependencyGroup());
                    selectedGroup = groups.size() - 1;
                    activeTab = 0;
                    scrollOffset = 0;
                    hasChanges = true;
                }, 10, this.height - 50, LEFT_PANEL_W - 20, 16));

        // Searchable widgets
        itemSearch = new SearchableItemList(id -> {
            if (hasGroup()) {
                currentGroup().getItems().add(new DependencyItem(id, 1));
                hasChanges = true;
            }
        });
        entitySearch = new SearchableEntityList(id -> {
            if (hasGroup()) {
                currentGroup().getEntityKills().add(new EntityKillDep(id, 1));
                hasChanges = true;
            }
        });
        globalStageSearch = new SearchableStageList(id -> {
            if (hasGroup()) {
                currentGroup().getStages().add(id);
                hasChanges = true;
            }
        }, false);
        globalStageSearch.setExcludeStageId(currentStageId);
        individualStageSearch = new SearchableStageList(id -> {
            if (hasGroup()) {
                currentGroup().getIndividualStages().add(new IndividualStageDep(id, "all_online"));
                hasChanges = true;
            }
        }, true);
        advancementSearch = new SearchableAdvancementList(id -> {
            if (hasGroup()) {
                currentGroup().getAdvancements().add(id);
                hasChanges = true;
            }
        });
        statSearch = new SearchableStatList(id -> {
            if (hasGroup()) {
                currentGroup().getStats().add(new StatDep(id, 1));
                hasChanges = true;
            }
        });

        contextMenu = new ContextMenu();
        computeTabLayout();
        rebuildItemNbtMap();
    }

    private boolean hasGroup() {
        return !groups.isEmpty() && selectedGroup < groups.size();
    }

    private DependencyGroup currentGroup() {
        return groups.get(selectedGroup);
    }

    private void computeTabLayout() {
        String[] tabKeys = getTabKeys();
        int tabMargin = LEFT_PANEL_W + 15;
        int totalAvail = this.width - tabMargin - 10;
        tabY = HEADER_HEIGHT;
        tabX = new int[tabKeys.length];
        tabW = new int[tabKeys.length];
        int gap = 2;
        int[] naturalW = new int[tabKeys.length];
        int totalNaturalW = 0;
        for (int i = 0; i < tabKeys.length; i++) {
            naturalW[i] = (int) (this.font.width(t(tabKeys[i])) * SMALL_SCALE) + TAB_PAD * 2;
            totalNaturalW += naturalW[i];
        }
        int totalGaps = (tabKeys.length - 1) * gap;
        if (totalNaturalW + totalGaps <= totalAvail) {
            int x = tabMargin;
            for (int i = 0; i < tabKeys.length; i++) {
                tabX[i] = x;
                tabW[i] = naturalW[i];
                x += tabW[i] + gap;
            }
            maxTabScroll = 0;
        } else {
            int x = tabMargin + TAB_ARROW_WIDTH;
            for (int i = 0; i < tabKeys.length; i++) {
                tabX[i] = x;
                tabW[i] = naturalW[i];
                x += naturalW[i] + gap;
            }
            int totalTabsWidth = x - gap - (tabMargin + TAB_ARROW_WIDTH);
            int scrollAreaAvail = totalAvail - TAB_ARROW_WIDTH * 2;
            maxTabScroll = Math.max(0, totalTabsWidth - scrollAreaAvail);
            tabScrollOffset = Math.min(tabScrollOffset, maxTabScroll);
        }
    }

    private void rebuildItemNbtMap() {
        itemNbtMap.clear();
        if (hasGroup()) {
            for (int i = 0; i < currentGroup().getItems().size(); i++) {
                DependencyItem item = currentGroup().getItems().get(i);
                if (item.hasNbt())
                    itemNbtMap.put(i, item.getNbt().deepCopy());
            }
        }
    }

    private void save() {
        syncNbtToItems();
        groups.removeIf(DependencyGroup::isEmpty);
        onSave.accept(groups.stream().map(DependencyGroup::copy).collect(Collectors.toList()));
        hasChanges = false;
    }

    private void syncNbtToItems() {
        if (hasGroup()) {
            for (var entry : itemNbtMap.entrySet()) {
                int idx = entry.getKey();
                if (idx < currentGroup().getItems().size()) {
                    currentGroup().getItems().get(idx).setNbt(entry.getValue());
                }
            }
        }
    }

    @Override
    public void onClose() {
        confirmDiscard();
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

    private boolean isOverlayOpen() {
        return (itemSearch != null && itemSearch.isVisible())
                || (entitySearch != null && entitySearch.isVisible())
                || (globalStageSearch != null && globalStageSearch.isVisible())
                || (individualStageSearch != null && individualStageSearch.isVisible())
                || (advancementSearch != null && advancementSearch.isVisible())
                || (statSearch != null && statSearch.isVisible())
                || countDialogType != null;
    }

    // --- Count dialog ---

    private void openCountDialog(String type, String id, int editIndex) {
        this.countDialogType = type;
        this.pendingId = id;
        this.editingEntryIndex = editIndex;

        // Pre-fill with existing value if editing
        String defaultVal = "1";
        if (editIndex >= 0 && hasGroup()) {
            DependencyGroup g = currentGroup();
            switch (type) {
                case "item_count" -> {
                    if (editIndex < g.getItems().size())
                        defaultVal = String.valueOf(g.getItems().get(editIndex).getCount());
                }
                case "kill_count" -> {
                    if (editIndex < g.getEntityKills().size())
                        defaultVal = String.valueOf(g.getEntityKills().get(editIndex).getCount());
                }
                case "stat_value" -> {
                    if (editIndex < g.getStats().size())
                        defaultVal = String.valueOf(g.getStats().get(editIndex).getMinValue());
                }
                case "xp_level" -> {
                    if (g.getXpLevel() != null)
                        defaultVal = String.valueOf(g.getXpLevel().getLevel());
                }
            }
        }
        if (type.equals("xp_level") && editIndex < 0)
            defaultVal = "30";
        this.countInputText = defaultVal;
    }

    private void confirmCountDialog() {
        if (countDialogType == null)
            return;
        int num;
        try {
            num = Integer.parseInt(countInputText.trim());
        } catch (NumberFormatException e) {
            num = 1;
        }
        num = Math.max(1, num);

        if (hasGroup()) {
            DependencyGroup group = currentGroup();
            if (editingEntryIndex >= 0) {
                switch (countDialogType) {
                    case "item_count" -> {
                        if (editingEntryIndex < group.getItems().size())
                            group.getItems().get(editingEntryIndex).setCount(num);
                    }
                    case "kill_count" -> {
                        if (editingEntryIndex < group.getEntityKills().size())
                            group.getEntityKills().get(editingEntryIndex).setCount(num);
                    }
                    case "stat_value" -> {
                        if (editingEntryIndex < group.getStats().size())
                            group.getStats().get(editingEntryIndex).setMinValue(num);
                    }
                    case "xp_level" -> {
                        boolean consume = group.getXpLevel() != null && group.getXpLevel().isConsume();
                        group.setXpLevel(new XpLevelDep(num, consume));
                    }
                }
            } else {
                switch (countDialogType) {
                    case "item_count" -> group.getItems().add(new DependencyItem(pendingId, num));
                    case "kill_count" -> group.getEntityKills().add(new EntityKillDep(pendingId, num));
                    case "stat_value" -> group.getStats().add(new StatDep(pendingId, Math.max(0, num)));
                    case "xp_level" -> {
                        boolean consume = group.getXpLevel() != null && group.getXpLevel().isConsume();
                        group.setXpLevel(new XpLevelDep(num, consume));
                    }
                }
            }
            hasChanges = true;
        }
        closeCountDialog();
    }

    private void closeCountDialog() {
        countDialogType = null;
        pendingId = null;
        editingEntryIndex = -1;
        countInputText = "";
    }

    private void drawSmallText(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
        g.drawString(this.font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);

        String currentTooltipKey = null;
        String currentTooltipText = null;

        g.drawCenteredString(this.font, t("editor.historystages.dep.title"), this.width / 2, 8, 0xFFCC00);
        g.fill(10, HEADER_HEIGHT - 4, this.width - 10, HEADER_HEIGHT - 3, 0xFF555555);

        // Left panel
        g.fill(5, HEADER_HEIGHT, LEFT_PANEL_W + 5, this.height - 55, 0x20FFFFFF);
        String[] groupTooltip = renderGroupList(g, mouseX, mouseY);
        if (groupTooltip != null) {
            currentTooltipKey = groupTooltip[0];
            currentTooltipText = groupTooltip[1];
        }

        // Right panel
        if (hasGroup()) {
            g.fill(LEFT_PANEL_W + 10, HEADER_HEIGHT, this.width - 5, HEADER_HEIGHT + TAB_HEIGHT + 4, 0x10FFFFFF);
            String[] tabTooltip = renderTabs(g, mouseX, mouseY);
            if (tabTooltip != null) {
                currentTooltipKey = tabTooltip[0];
                currentTooltipText = tabTooltip[1];
            }
            int contentTop = HEADER_HEIGHT + TAB_HEIGHT + 4;
            g.fill(LEFT_PANEL_W + 10, contentTop, this.width - 5, contentTop + 1, 0xFF555555);
            renderTabContent(g, mouseX, mouseY);
        } else {
            g.drawCenteredString(this.font, t("editor.historystages.dep.no_groups"), this.width / 2, this.height / 2,
                    0x888888);
        }

        // Unsaved indicator (pulsing dot like StageDetailScreen)
        if (hasChanges) {
            float pulse = (System.currentTimeMillis() % 1000) / 1000.0f;
            pulse = 0.4f + (float) Math.sin(pulse * 3.14159f * 2) * 0.3f;
            int dotAlpha = (int) (pulse * 255);
            int dotX = this.width / 2 - 45;
            g.fill(dotX, this.height - 12, dotX + 6, this.height - 6, (dotAlpha << 24) | 0xFFCC00);
            drawSmallText(g, t("editor.historystages.unsaved"), dotX + 9, this.height - 12, 0xFFCC00);
        }

        super.render(g, mouseX, mouseY, partialTick);

        // Overlays (z-ordered on top)
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);

        if (countDialogType != null)
            renderCountDialog(g, mouseX, mouseY);

        if (itemSearch != null)
            itemSearch.render(g, this.font, mouseX, mouseY);
        if (entitySearch != null)
            entitySearch.render(g, this.font, mouseX, mouseY);
        if (globalStageSearch != null)
            globalStageSearch.render(g, this.font, mouseX, mouseY);
        if (individualStageSearch != null)
            individualStageSearch.render(g, this.font, mouseX, mouseY);
        if (advancementSearch != null)
            advancementSearch.render(g, this.font, mouseX, mouseY);
        if (statSearch != null)
            statSearch.render(g, this.font, mouseX, mouseY);

        // Context menu on top of everything
        contextMenu.render(g, this.font, mouseX, mouseY);

        g.pose().popPose();

        // Content tooltips (from tab content area)
        if (contentTooltip != null && currentTooltipKey == null) {
            currentTooltipKey = contentTooltip[0];
            currentTooltipText = contentTooltip[1];
        }

        // Tooltip rendering
        if (!isOverlayOpen() && !contextMenu.isVisible() && currentTooltipKey != null && currentTooltipText != null) {
            if (!currentTooltipKey.equals(hoveredTooltipKey)) {
                hoveredTooltipKey = currentTooltipKey;
                tooltipHoverStart = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - tooltipHoverStart >= TOOLTIP_DELAY_MS) {
                renderTooltip(g, currentTooltipText, mouseX, mouseY);
            }
        } else {
            hoveredTooltipKey = null;
        }
    }

    private String[] renderGroupList(GuiGraphics g, int mouseX, int mouseY) {
        String[] tooltip = null;
        int y = HEADER_HEIGHT + 4;
        for (int i = 0; i < groups.size(); i++) {
            DependencyGroup group = groups.get(i);
            boolean selected = (i == selectedGroup);
            boolean hovered = !isOverlayOpen() && !contextMenu.isVisible() && mouseX >= 8 && mouseX <= LEFT_PANEL_W + 2
                    && mouseY >= y && mouseY < y + 28;

            // Card style
            float cardProgress = cardHoverProgress.getOrDefault(-1000 - i, 0.0f);
            if (hovered)
                cardProgress = Math.min(1.0f, cardProgress + 0.1f);
            else
                cardProgress = Math.max(0.0f, cardProgress - 0.07f);
            if (cardProgress > 0.001f)
                cardHoverProgress.put(-1000 - i, cardProgress);
            else
                cardHoverProgress.remove(-1000 - i);

            int borderColor = selected ? 0x60FFFFFF : (int) (0x25 + cardProgress * 0x15) << 24 | 0xFFFFFF;
            int bgColor = selected ? 0xFF2A2A2A : 0xFF1E1E1E;
            if (hovered && !selected)
                bgColor = 0xFF252525;
            g.fill(8, y, LEFT_PANEL_W + 2, y + 28, borderColor);
            g.fill(9, y + 1, LEFT_PANEL_W + 1, y + 27, bgColor);

            // Left accent
            if (selected)
                g.fill(8, y, 10, y + 28, 0xFFFFCC00);
            else if (cardProgress > 0.01f) {
                int accentAlpha = (int) (cardProgress * 0xCC);
                g.fill(8, y, 10, y + 28, (accentAlpha << 24) | 0xFFCC00);
            }

            g.drawString(this.font, t("editor.historystages.dep.group", i + 1), 14, y + 3,
                    selected ? 0xFFFFFF : 0xCCCCCC, false);

            // AND/OR toggle button
            String logic = group.getLogic();
            int badgeX = LEFT_PANEL_W - 28;
            boolean badgeHovered = !isOverlayOpen() && !contextMenu.isVisible() && mouseX >= badgeX
                    && mouseX <= badgeX + 25 && mouseY >= y + 2 && mouseY < y + 14;
            int badgeBg = badgeHovered ? 0xFF3D3520 : 0xFF2A2A2A;
            int badgeColor = group.isOr() ? (badgeHovered ? 0xFF77CCFF : 0xFF55AAFF)
                    : (badgeHovered ? 0xFF77FF77 : 0xFF55FF55);
            g.fill(badgeX, y + 2, badgeX + 25, y + 14, badgeBg);
            g.fill(badgeX, y + 12, badgeX + 25, y + 14, badgeHovered ? 0xAAFFCC00 : 0x40FFCC00);
            g.drawString(this.font, logic, badgeX + 3, y + 3, badgeColor, false);

            if (badgeHovered) {
                tooltip = new String[] { "logic." + i,
                        "Click to toggle.\nAND: All conditions must be met.\nOR: Any one condition is enough." };
            }

            int entryCount = countGroupEntries(group);
            drawSmallText(g, entryCount + " entries", 14, y + 16, 0x888888);

            y += 31;
        }
        return tooltip;
    }

    private String[] renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        String[] tabKeys = getTabKeys();
        String[] tooltipKeys = getTabTooltipKeys();
        String[] result = null;

        boolean hasTabScroll = maxTabScroll > 0;
        int tabAreaLeft = LEFT_PANEL_W + 10;
        int tabAreaRight = this.width - 5;
        int tabClipLeft = hasTabScroll ? tabAreaLeft + TAB_ARROW_WIDTH : 0;
        int tabClipRight = hasTabScroll ? tabAreaRight - TAB_ARROW_WIDTH : this.width;

        // Scroll arrows
        if (hasTabScroll) {
            if (tabScrollOffset > 0) {
                boolean lh = !isOverlayOpen() && mouseX >= tabAreaLeft && mouseX < tabAreaLeft + TAB_ARROW_WIDTH
                        && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
                g.fill(tabAreaLeft, tabY, tabAreaLeft + TAB_ARROW_WIDTH, tabY + TAB_HEIGHT,
                        lh ? 0x40FFFFFF : 0x20FFFFFF);
                drawSmallText(g, "\u25C0", tabAreaLeft + 2, tabY + 4, lh ? 0xFFFFFF : 0x999999);
            }
            if (tabScrollOffset < maxTabScroll) {
                boolean rh = !isOverlayOpen() && mouseX >= tabAreaRight - TAB_ARROW_WIDTH && mouseX < tabAreaRight
                        && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
                g.fill(tabAreaRight - TAB_ARROW_WIDTH, tabY, tabAreaRight, tabY + TAB_HEIGHT,
                        rh ? 0x40FFFFFF : 0x20FFFFFF);
                drawSmallText(g, "\u25B6", tabAreaRight - TAB_ARROW_WIDTH + 2, tabY + 4, rh ? 0xFFFFFF : 0x999999);
            }
        }

        // Animated indicator
        if (!tabIndicatorInit && tabX != null && tabX.length > 0) {
            tabIndicatorX = tabX[activeTab] - tabScrollOffset;
            tabIndicatorW = tabW[activeTab];
            tabIndicatorInit = true;
        }
        if (tabX != null && activeTab < tabX.length) {
            float targetX = tabX[activeTab] - tabScrollOffset;
            float targetW = tabW[activeTab];
            tabIndicatorX += (targetX - tabIndicatorX) * 0.18f;
            tabIndicatorW += (targetW - tabIndicatorW) * 0.18f;
            if (Math.abs(tabIndicatorX - targetX) < 0.5f)
                tabIndicatorX = targetX;
            if (Math.abs(tabIndicatorW - targetW) < 0.5f)
                tabIndicatorW = targetW;
        }

        if (hasTabScroll)
            g.enableScissor(tabClipLeft, tabY, tabClipRight, tabY + TAB_HEIGHT);

        for (int i = 0; i < tabKeys.length; i++) {
            int sx = tabX[i] - tabScrollOffset;
            boolean active = (i == activeTab);
            boolean hovered = !isOverlayOpen() && !contextMenu.isVisible()
                    && mouseX >= Math.max(sx, tabClipLeft) && mouseX < Math.min(sx + tabW[i], tabClipRight)
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
            g.fill(sx, tabY, sx + tabW[i], tabY + TAB_HEIGHT,
                    active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF));
            drawSmallText(g, t(tabKeys[i]), sx + TAB_PAD, tabY + 4,
                    active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999));
            if (hovered && i < tooltipKeys.length)
                result = new String[] { "tab." + i, t(tooltipKeys[i]) };
        }

        g.fill((int) tabIndicatorX, tabY + TAB_HEIGHT - 2, (int) (tabIndicatorX + tabIndicatorW), tabY + TAB_HEIGHT,
                0xFFFFCC00);
        if (hasTabScroll)
            g.disableScissor();

        return result;
    }

    // Content tooltip (set during renderTabContent, displayed after scissor)
    private String[] contentTooltip = null;

    private void renderTabContent(GuiGraphics g, int mouseX, int mouseY) {
        DependencyGroup group = currentGroup();
        int rightX = LEFT_PANEL_W + 15;
        int rightW = this.width - rightX - 15;
        int contentY = HEADER_HEIGHT + TAB_HEIGHT + 6;
        int contentBottom = this.height - 30;
        contentTooltip = null;

        g.enableScissor(rightX, contentY, rightX + rightW, contentBottom);
        int y = contentY - (int) scrollOffset;
        int currentHoveredCard = -1;

        switch (activeTab) {
            case 0 -> {
                int[] res = renderItemEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom, group);
                y = res[0];
                currentHoveredCard = res[1];
            }
            case 1 -> {
                int[] res = renderStringCardEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom,
                        group.getStages(), false);
                y = res[0];
                currentHoveredCard = res[1];
            }
            case 2 -> {
                int[] res = renderIndividualStageEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom,
                        group);
                y = res[0];
                currentHoveredCard = res[1];
            }
            case 3 -> {
                if (isIndividual) {
                    int[] res = renderStringCardEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom,
                            group.getAdvancements(), true);
                    y = res[0];
                    currentHoveredCard = res[1];
                }
            }
            case 4 -> {
                if (isIndividual)
                    y = renderXpLevelEntry(g, mouseX, mouseY, rightX, rightW, y, group);
            }
            case 5 -> {
                if (isIndividual) {
                    int[] res = renderEntityKillEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom,
                            group);
                    y = res[0];
                    currentHoveredCard = res[1];
                }
            }
            case 6 -> {
                if (isIndividual) {
                    int[] res = renderStatEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom, group);
                    y = res[0];
                    currentHoveredCard = res[1];
                }
            }
        }

        // Add button (matching StageDetailScreen style)
        if (activeTab != 4) {
            int addY = y + 3;
            String addText = t("editor.historystages.dep.add");
            int addTextW = this.font.width(addText);
            int addBoxRight = rightX + addTextW + 20;
            boolean addH = !isOverlayOpen() && !contextMenu.isVisible() && mouseX >= rightX && mouseX < addBoxRight
                    && mouseY >= addY && mouseY < addY + CARD_HEIGHT && mouseY >= contentY && mouseY < contentBottom;

            float addProgress = cardHoverProgress.getOrDefault(-2, 0.0f);
            if (addH)
                addProgress = Math.min(1.0f, addProgress + 0.1f);
            else
                addProgress = Math.max(0.0f, addProgress - 0.07f);
            if (addProgress > 0.001f)
                cardHoverProgress.put(-2, addProgress);
            else
                cardHoverProgress.remove(-2);

            int addBorderAlpha = (int) (0x25 + addProgress * 0x1B);
            int addBgAlpha = (int) (0x18 + addProgress * 0x18);
            g.fill(rightX, addY, addBoxRight, addY + CARD_HEIGHT, (addBorderAlpha << 24) | 0xFFFFFF);
            g.fill(rightX + 1, addY + 1, addBoxRight - 1, addY + CARD_HEIGHT - 1, (addBgAlpha << 24) | 0xFFFFFF);

            if (addProgress > 0.01f) {
                int greenAlpha = (int) (addProgress * 0xAA);
                g.fill(rightX, addY, rightX + 2, addY + CARD_HEIGHT, (greenAlpha << 24) | 0x55FF55);
            }

            int addG = (int) (0x88 + addProgress * 0x77);
            int addRB = (int) (0x33 + addProgress * 0x22);
            g.drawString(this.font, addText, rightX + 6, addY + 7, (0xFF << 24) | (addRB << 16) | (addG << 8) | addRB,
                    false);
        }

        g.disableScissor();

        int contentHeight = y + CARD_HEIGHT + 10 - contentY + (int) scrollOffset;
        maxScroll = Math.max(0, contentHeight - (contentBottom - contentY));

        // Scrollbar
        if (maxScroll > 0) {
            int scrollTrackX = rightX + rightW + 3;
            int scrollTrackTop = contentY;
            int scrollTrackBottom = contentBottom;
            int scrollTrackH = scrollTrackBottom - scrollTrackTop;

            // Track background
            g.fill(scrollTrackX, scrollTrackTop, scrollTrackX + SCROLLBAR_WIDTH, scrollTrackBottom, 0x20FFFFFF);

            // Thumb: size proportional to visible / total
            int totalContentH = contentHeight;
            int visibleH = contentBottom - contentY;
            int thumbH = Math.max(12, (int) ((float) visibleH / totalContentH * scrollTrackH));
            int thumbY = scrollTrackTop + (int) ((float) scrollOffset / maxScroll * (scrollTrackH - thumbH));

            boolean thumbHovered = !isOverlayOpen() && !contextMenu.isVisible()
                    && mouseX >= scrollTrackX - 2 && mouseX <= scrollTrackX + SCROLLBAR_WIDTH + 2
                    && mouseY >= thumbY && mouseY <= thumbY + thumbH;
            int thumbColor = draggingContentScrollbar ? 0xCCFFCC00 : (thumbHovered ? 0xBBCCCCCC : 0x80888888);
            g.fill(scrollTrackX, thumbY, scrollTrackX + SCROLLBAR_WIDTH, thumbY + thumbH, thumbColor);
        }

        // Update marquee state
        if (currentHoveredCard != hoveredCardIndex) {
            hoveredCardIndex = currentHoveredCard;
            cardHoverStartTime = System.currentTimeMillis();
        }
    }

    // --- Card with marquee helper ---

    private void renderCardWithText(GuiGraphics g, int rx, int rw, int cardY, boolean hovered, float cardProgress,
            String text, int textOffsetX, int badgeW, int cardIndex, int contentY, int contentBottom) {
        // Card background (matching StageDetailScreen)
        int borderAlpha = (int) (0x30 + cardProgress * 0x20);
        int bgAlpha = (int) (0x20 + cardProgress * 0x18);
        g.fill(rx, cardY, rx + rw, cardY + CARD_HEIGHT, (borderAlpha << 24) | 0xFFFFFF);
        g.fill(rx + 1, cardY + 1, rx + rw - 1, cardY + CARD_HEIGHT - 1, (bgAlpha << 24) | 0xFFFFFF);

        // Hover accent
        if (cardProgress > 0.01f) {
            int accentAlpha = (int) (cardProgress * 0xCC);
            g.fill(rx, cardY, rx + 2, cardY + CARD_HEIGHT, (accentAlpha << 24) | 0xFFCC00);
        }

        // Marquee text
        int textStartX = rx + textOffsetX;
        int textAvailW = rw - textOffsetX - 4 - badgeW;
        int textW = this.font.width(text);
        int textColor = hovered ? 0xFFFFFF : 0xBBBBBB;

        if (textW > textAvailW && hovered && cardIndex == hoveredCardIndex) {
            long elapsed = System.currentTimeMillis() - cardHoverStartTime;
            if (elapsed > CARD_MARQUEE_DELAY_MS) {
                float scrollProg = (elapsed - CARD_MARQUEE_DELAY_MS) / 1000.0f * CARD_MARQUEE_SPEED;
                int maxMarquee = textW - textAvailW + 10;
                float cycle = (float) maxMarquee * 2;
                float pos = scrollProg % cycle;
                int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                g.enableScissor(textStartX, cardY, textStartX + textAvailW, cardY + CARD_HEIGHT);
                g.drawString(this.font, text, textStartX - scrollOff, cardY + 7, textColor, false);
                g.disableScissor();
            } else {
                String truncated = this.font.plainSubstrByWidth(text, textAvailW - 8) + "...";
                g.drawString(this.font, truncated, textStartX, cardY + 7, textColor, false);
            }
        } else if (textW > textAvailW) {
            String truncated = this.font.plainSubstrByWidth(text, textAvailW - 8) + "...";
            g.drawString(this.font, truncated, textStartX, cardY + 7, textColor, false);
        } else {
            g.drawString(this.font, text, textStartX, cardY + 7, textColor, false);
        }
    }

    private float updateCardHover(int cardIndex, boolean hovered) {
        float p = cardHoverProgress.getOrDefault(cardIndex, 0.0f);
        if (hovered)
            p = Math.min(1.0f, p + 0.1f);
        else
            p = Math.max(0.0f, p - 0.07f);
        if (p > 0.001f)
            cardHoverProgress.put(cardIndex, p);
        else
            cardHoverProgress.remove(cardIndex);
        return p;
    }

    // --- Entry renderers ---

    private int[] renderItemEntries(GuiGraphics g, int mx, int my, int rx, int rw, int y, int cTop, int cBot,
            DependencyGroup group) {
        int hovered = -1;
        for (int i = 0; i < group.getItems().size(); i++) {
            DependencyItem item = group.getItems().get(i);
            boolean isHovered = !isOverlayOpen() && !contextMenu.isVisible() && mx >= rx && mx < rx + rw && my >= y
                    && my < y + CARD_HEIGHT && my >= cTop && my < cBot;
            float cp = updateCardHover(i, isHovered);
            if (isHovered)
                hovered = i;

            // Item icon
            int badgeW = 0;
            if (itemNbtMap.containsKey(i)) {
                badgeW = this.font.width("\u00A76[NBT]") + 6;
            }

            String displayName = item.getId();
            try {
                ResourceLocation rl = ResourceLocation.tryParse(item.getId());
                if (rl != null) {
                    var mcItem = ForgeRegistries.ITEMS.getValue(rl);
                    if (mcItem != null)
                        displayName = mcItem.getDescription().getString();
                }
            } catch (Exception ignored) {
            }

            renderCardWithText(g, rx, rw, y, isHovered, cp, item.getCount() + "x " + displayName, 22, badgeW, i, cTop,
                    cBot);

            // Item icon on card
            try {
                ResourceLocation rl = ResourceLocation.tryParse(item.getId());
                if (rl != null) {
                    var mcItem = ForgeRegistries.ITEMS.getValue(rl);
                    if (mcItem != null) {
                        g.pose().pushPose();
                        g.pose().scale(SMALL_SCALE, SMALL_SCALE, 1);
                        g.renderItem(new ItemStack(mcItem), (int) ((rx + 3) / SMALL_SCALE),
                                (int) ((y + 3) / SMALL_SCALE));
                        g.pose().popPose();
                    }
                }
            } catch (Exception ignored) {
            }

            // NBT badge
            if (itemNbtMap.containsKey(i)) {
                g.drawString(this.font, "\u00A76[NBT]", rx + rw - badgeW, y + 7, 0xFFCC00, false);
            }

            y += CARD_HEIGHT + CARD_GAP;
        }
        return new int[] { y, hovered };
    }

    private int[] renderStringCardEntries(GuiGraphics g, int mx, int my, int rx, int rw, int y, int cTop, int cBot,
            List<String> entries, boolean isAdvancement) {
        int hovered = -1;
        var stageMap = isAdvancement ? null : StageManager.getStages();
        for (int i = 0; i < entries.size(); i++) {
            String id = entries.get(i);
            boolean isHovered = !isOverlayOpen() && !contextMenu.isVisible() && mx >= rx && mx < rx + rw && my >= y
                    && my < y + CARD_HEIGHT && my >= cTop && my < cBot;
            float cp = updateCardHover(100 + i, isHovered);
            if (isHovered)
                hovered = i;

            String text;
            if (isAdvancement) {
                text = id;
            } else {
                var entry = stageMap != null ? stageMap.get(id) : null;
                String name = entry != null ? entry.getDisplayName() : id;
                text = name + " \u00A77(" + id + ")";
            }

            renderCardWithText(g, rx, rw, y, isHovered, cp, text, 6, 0, 100 + i, cTop, cBot);
            y += CARD_HEIGHT + CARD_GAP;
        }
        return new int[] { y, hovered };
    }

    private int[] renderIndividualStageEntries(GuiGraphics g, int mx, int my, int rx, int rw, int y, int cTop, int cBot,
            DependencyGroup group) {
        int hovered = -1;
        for (int i = 0; i < group.getIndividualStages().size(); i++) {
            IndividualStageDep dep = group.getIndividualStages().get(i);
            boolean isHovered = !isOverlayOpen() && !contextMenu.isVisible() && mx >= rx && mx < rx + rw && my >= y
                    && my < y + CARD_HEIGHT && my >= cTop && my < cBot;
            float cp = updateCardHover(200 + i, isHovered);
            if (isHovered)
                hovered = i;

            var entry = StageManager.getIndividualStages().get(dep.getStageId());
            String name = entry != null ? entry.getDisplayName() : dep.getStageId();

            // Toggle badge width
            int toggleW = this.font.width(
                    dep.isAllEver() ? t("editor.historystages.dep.ever") : t("editor.historystages.dep.online")) + 10;
            String text = name + " \u00A77(" + dep.getStageId() + ")";

            renderCardWithText(g, rx, rw, y, isHovered, cp, text, 6, toggleW + 4, 200 + i, cTop, cBot);

            // Mode toggle button
            int toggleX = rx + rw - toggleW - 2;
            boolean toggleH = !isOverlayOpen() && !contextMenu.isVisible() && mx >= toggleX && mx < toggleX + toggleW
                    && my >= y + 3 && my < y + CARD_HEIGHT - 3;
            g.fill(toggleX, y + 3, toggleX + toggleW, y + CARD_HEIGHT - 3, toggleH ? 0xFF3D3520 : 0xFF2A2A2A);
            drawSmallText(g,
                    dep.isAllEver() ? t("editor.historystages.dep.ever") : t("editor.historystages.dep.online"),
                    toggleX + 3, y + 7, toggleH ? 0xFFCC00 : 0xCCCCCC);

            if (toggleH) {
                contentTooltip = new String[] { "toggle.indiv." + i,
                        dep.isAllEver()
                                ? t("editor.historystages.dep.tooltip.mode_ever")
                                : t("editor.historystages.dep.tooltip.mode_online") };
            }

            y += CARD_HEIGHT + CARD_GAP;
        }
        return new int[] { y, hovered };
    }

    private int renderXpLevelEntry(GuiGraphics g, int mx, int my, int rx, int rw, int y, DependencyGroup group) {
        XpLevelDep xp = group.getXpLevel();
        g.drawString(this.font, t("editor.historystages.dep.required_xp"), rx + 6, y + 4, 0xAAAAAA, false);
        y += 18;

        if (xp != null && xp.getLevel() > 0) {
            boolean hovered = !isOverlayOpen() && !contextMenu.isVisible() && mx >= rx && mx < rx + rw && my >= y
                    && my < y + CARD_HEIGHT;
            float cp = updateCardHover(900, hovered);

            int borderAlpha2 = (int) (0x30 + cp * 0x20);
            int bgAlpha2 = (int) (0x20 + cp * 0x18);
            g.fill(rx, y, rx + rw, y + CARD_HEIGHT, (borderAlpha2 << 24) | 0xFFFFFF);
            g.fill(rx + 1, y + 1, rx + rw - 1, y + CARD_HEIGHT - 1, (bgAlpha2 << 24) | 0xFFFFFF);
            if (cp > 0.01f)
                g.fill(rx, y, rx + 2, y + CARD_HEIGHT, ((int) (cp * 0xCC) << 24) | 0xFFCC00);

            String consumeStr = xp.isConsume() ? t("editor.historystages.dep.consumed")
                    : t("editor.historystages.dep.checked_only");
            g.drawString(this.font, t("editor.historystages.dep.level", xp.getLevel(), consumeStr), rx + 6, y + 7,
                    0xDDDDDD, false);

            String consumeLabel = xp.isConsume() ? t("editor.historystages.dep.consume")
                    : t("editor.historystages.dep.check");
            int toggleW = this.font.width(consumeLabel) + 8;
            int toggleX = rx + rw - toggleW - 2;
            boolean toggleH = !isOverlayOpen() && !contextMenu.isVisible() && mx >= toggleX && mx < toggleX + toggleW
                    && my >= y + 3 && my < y + CARD_HEIGHT - 3;
            g.fill(toggleX, y + 3, toggleX + toggleW, y + CARD_HEIGHT - 3, toggleH ? 0xFF3D3520 : 0xFF2A2A2A);
            drawSmallText(g, consumeLabel, toggleX + 3, y + 7, toggleH ? 0xFFCC00 : 0xCCCCCC);

            if (toggleH) {
                contentTooltip = new String[] { "toggle.xp",
                        xp.isConsume()
                                ? t("editor.historystages.dep.tooltip.consume")
                                : t("editor.historystages.dep.tooltip.check_only") };
            }

            y += CARD_HEIGHT + CARD_GAP;
        } else {
            boolean addH = !isOverlayOpen() && !contextMenu.isVisible() && mx >= rx && mx < rx + rw && my >= y
                    && my < y + CARD_HEIGHT;
            g.fill(rx, y, rx + rw, y + CARD_HEIGHT, addH ? 0x40FFCC00 : 0x20FFFFFF);
            g.drawCenteredString(this.font, t("editor.historystages.dep.set_xp_level"), rx + rw / 2, y + 7,
                    addH ? 0xFFCC00 : 0x888888);
            y += CARD_HEIGHT + CARD_GAP;
        }
        return y;
    }

    private static final int ENTITY_CARD_HEIGHT = 32;

    private int[] renderEntityKillEntries(GuiGraphics g, int mx, int my, int rx, int rw, int y, int cTop, int cBot,
            DependencyGroup group) {
        int hovered = -1;
        for (int i = 0; i < group.getEntityKills().size(); i++) {
            EntityKillDep kill = group.getEntityKills().get(i);
            boolean isHovered = !isOverlayOpen() && !contextMenu.isVisible() && mx >= rx && mx < rx + rw && my >= y
                    && my < y + ENTITY_CARD_HEIGHT && my >= cTop && my < cBot;
            float cp = updateCardHover(300 + i, isHovered);
            if (isHovered)
                hovered = i;

            String entityName = kill.getEntityId();
            try {
                ResourceLocation rl = ResourceLocation.tryParse(kill.getEntityId());
                if (rl != null) {
                    EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
                    if (type != null)
                        entityName = type.getDescription().getString();
                }
            } catch (Exception ignored) {
            }

            // Card background (matching StageDetailScreen)
            int borderAlpha = (int) (0x30 + cp * 0x20);
            int bgAlpha = (int) (0x20 + cp * 0x18);
            g.fill(rx, y, rx + rw, y + ENTITY_CARD_HEIGHT, (borderAlpha << 24) | 0xFFFFFF);
            g.fill(rx + 1, y + 1, rx + rw - 1, y + ENTITY_CARD_HEIGHT - 1, (bgAlpha << 24) | 0xFFFFFF);

            // Hover accent
            if (cp > 0.01f) {
                int accentAlpha = (int) (cp * 0xCC);
                g.fill(rx, y, rx + 2, y + ENTITY_CARD_HEIGHT, (accentAlpha << 24) | 0xFFCC00);
            }

            // 3D entity model (skip when overlay is open to prevent bleed-through)
            if (!isOverlayOpen() && !contextMenu.isVisible()) {
                LivingEntity entityModel = getOrCreateEntity(kill.getEntityId());
                if (entityModel != null) {
                    g.enableScissor(rx + 3, Math.max(y + 1, cTop), rx + 28, Math.min(y + ENTITY_CARD_HEIGHT - 1, cBot));
                    float angle = (System.currentTimeMillis() % 4000) / 4000.0f * 360.0f;
                    renderSpinningEntity(g, rx + 15, y + ENTITY_CARD_HEIGHT - 3, 10, angle, entityModel);
                    g.disableScissor();
                }
            }

            // Text
            String text = kill.getCount() + "x " + entityName;
            int textStartX = rx + 30;
            int textAvailW = rw - 34;
            int textW = this.font.width(text);
            int textColor = isHovered ? 0xFFFFFF : 0xBBBBBB;

            if (textW > textAvailW && isHovered && 300 + i == hoveredCardIndex) {
                long elapsed = System.currentTimeMillis() - cardHoverStartTime;
                if (elapsed > CARD_MARQUEE_DELAY_MS) {
                    float scrollProg = (elapsed - CARD_MARQUEE_DELAY_MS) / 1000.0f * CARD_MARQUEE_SPEED;
                    int maxMarquee = textW - textAvailW + 10;
                    float cycle = (float) maxMarquee * 2;
                    float pos = scrollProg % cycle;
                    int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                    g.enableScissor(textStartX, y, textStartX + textAvailW, y + ENTITY_CARD_HEIGHT);
                    g.drawString(this.font, text, textStartX - scrollOff, y + 12, textColor, false);
                    g.disableScissor();
                } else {
                    g.drawString(this.font, this.font.plainSubstrByWidth(text, textAvailW - 8) + "...", textStartX,
                            y + 12, textColor, false);
                }
            } else if (textW > textAvailW) {
                g.drawString(this.font, this.font.plainSubstrByWidth(text, textAvailW - 8) + "...", textStartX, y + 12,
                        textColor, false);
            } else {
                g.drawString(this.font, text, textStartX, y + 12, textColor, false);
            }

            y += ENTITY_CARD_HEIGHT + CARD_GAP;
        }
        return new int[] { y, hovered };
    }

    private int[] renderStatEntries(GuiGraphics g, int mx, int my, int rx, int rw, int y, int cTop, int cBot,
            DependencyGroup group) {
        int hovered = -1;
        for (int i = 0; i < group.getStats().size(); i++) {
            StatDep stat = group.getStats().get(i);
            boolean isHovered = !isOverlayOpen() && !contextMenu.isVisible() && mx >= rx && mx < rx + rw && my >= y
                    && my < y + CARD_HEIGHT && my >= cTop && my < cBot;
            float cp = updateCardHover(400 + i, isHovered);
            if (isHovered)
                hovered = i;

            renderCardWithText(g, rx, rw, y, isHovered, cp, stat.getStatId() + " >= " + stat.getMinValue(), 6, 0,
                    400 + i, cTop, cBot);
            y += CARD_HEIGHT + CARD_GAP;
        }
        return new int[] { y, hovered };
    }

    // --- Count dialog ---

    private void renderCountDialog(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0xA0000000);
        int dw = 220, dh = 100;
        int dx = this.width / 2 - dw / 2, dy = this.height / 2 - dh / 2;

        // Dialog frame
        g.fill(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, 0xFF3D3D3D);
        g.fill(dx, dy, dx + dw, dy + dh, 0xFF1A1A1A);
        g.fill(dx, dy, dx + dw, dy + 2, 0xFFFFCC00);

        // Title
        String title = switch (countDialogType) {
            case "item_count" -> t("editor.historystages.dep.dialog.item_count");
            case "kill_count" -> t("editor.historystages.dep.dialog.kill_count");
            case "stat_value" -> t("editor.historystages.dep.dialog.min_value");
            case "xp_level" -> t("editor.historystages.dep.dialog.xp_level");
            default -> t("editor.historystages.dep.dialog.value");
        };
        g.drawCenteredString(this.font, title, dx + dw / 2, dy + 8, 0xFFCC00);

        // Subtitle (item/entity/stat ID)
        String subtitle = pendingId != null ? pendingId : "";
        if (this.font.width(subtitle) > dw - 10)
            subtitle = this.font.plainSubstrByWidth(subtitle, dw - 16) + "...";
        if (!subtitle.isEmpty())
            drawSmallText(g, subtitle, dx + dw / 2 - (int) (this.font.width(subtitle) * SMALL_SCALE / 2), dy + 22,
                    0x888888);

        // Input field background
        int fieldX = dx + 20, fieldY = dy + 36, fieldW = dw - 40, fieldH = 20;
        g.fill(fieldX - 1, fieldY - 1, fieldX + fieldW + 1, fieldY + fieldH + 1, 0xFF4A4A4A);
        g.fill(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH, 0xFF0D0D0D);

        // Input text + cursor
        String displayText = countInputText;
        int textW = this.font.width(displayText);
        int textX = fieldX + fieldW / 2 - textW / 2;
        g.drawString(this.font, displayText, textX, fieldY + 6, 0xFFFFFF, false);

        // Blinking cursor
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = textX + textW + 1;
            g.fill(cursorX, fieldY + 4, cursorX + 1, fieldY + fieldH - 4, 0xFFFFCC00);
        }

        // Hint
        String hint = t("editor.historystages.dep.enter_number");
        drawSmallText(g, hint, dx + dw / 2 - (int) (this.font.width(hint) * SMALL_SCALE / 2), fieldY + fieldH + 3,
                0x555555);

        // Buttons: Cancel + OK
        int btnW = 60, btnH = 18, btnGap = 10;
        int totalBtnW = btnW * 2 + btnGap;
        int cancelX = dx + dw / 2 - totalBtnW / 2;
        int okX = cancelX + btnW + btnGap;
        int btnY = dy + dh - btnH - 8;

        boolean cancelH = mx >= cancelX && mx < cancelX + btnW && my >= btnY && my < btnY + btnH;
        boolean okH = mx >= okX && mx < okX + btnW && my >= btnY && my < btnY + btnH;

        // Cancel button
        g.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, cancelH ? 0xFF3A2A2A : 0xFF2A2A2A);
        g.fill(cancelX, btnY + btnH - 2, cancelX + btnW, btnY + btnH, cancelH ? 0xAAFF5555 : 0x60FF5555);
        g.drawCenteredString(this.font, t("editor.historystages.cancel"), cancelX + btnW / 2, btnY + 5,
                cancelH ? 0xFFFFFF : 0xAAAAAA);

        // OK button
        g.fill(okX, btnY, okX + btnW, btnY + btnH, okH ? 0xFF2A3A20 : 0xFF2A2A2A);
        g.fill(okX, btnY + btnH - 2, okX + btnW, btnY + btnH, okH ? 0xAAFFCC00 : 0x60FFCC00);
        g.drawCenteredString(this.font, t("editor.historystages.confirm"), okX + btnW / 2, btnY + 5,
                okH ? 0xFFFFFF : 0xAAAAAA);
    }

    // --- Tooltip ---

    private void renderTooltip(GuiGraphics g, String text, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        List<String> lines = new ArrayList<>();
        for (String segment : text.split("\n")) {
            int maxWidth = 200;
            String[] words = segment.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (line.length() > 0 && this.font.width(line + " " + word) > maxWidth) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    if (line.length() > 0)
                        line.append(" ");
                    line.append(word);
                }
            }
            if (line.length() > 0)
                lines.add(line.toString());
        }

        int tooltipW = 0;
        for (String l : lines)
            tooltipW = Math.max(tooltipW, this.font.width(l));
        tooltipW += 8;
        int tooltipH = lines.size() * 10 + 6;

        int tx = mouseX + 12, ty2 = mouseY - 4;
        if (tx + tooltipW + 2 > this.width - 4)
            tx = mouseX - tooltipW - 4;
        if (ty2 + tooltipH + 2 > this.height - 4)
            ty2 = this.height - tooltipH - 6;
        if (tx < 4)
            tx = 4;
        if (ty2 < 4)
            ty2 = 4;

        g.fill(tx - 2, ty2 - 2, tx + tooltipW + 2, ty2 + tooltipH + 2, 0xFF3D3D3D);
        g.fill(tx, ty2, tx + tooltipW, ty2 + tooltipH, 0xFF0D0D0D);

        int tyy = ty2 + 3;
        for (String l : lines) {
            g.drawString(this.font, l, tx + 4, tyy, 0xCCCCCC, false);
            tyy += 10;
        }
        g.pose().popPose();
    }

    // --- Click handling ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Context menu first
        if (contextMenu.isVisible()) {
            contextMenu.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        // Widget overlays
        if (itemSearch != null && itemSearch.isVisible())
            return itemSearch.mouseClicked(mouseX, mouseY);
        if (entitySearch != null && entitySearch.isVisible())
            return entitySearch.mouseClicked(mouseX, mouseY);
        if (globalStageSearch != null && globalStageSearch.isVisible())
            return globalStageSearch.mouseClicked(mouseX, mouseY);
        if (individualStageSearch != null && individualStageSearch.isVisible())
            return individualStageSearch.mouseClicked(mouseX, mouseY);
        if (advancementSearch != null && advancementSearch.isVisible())
            return advancementSearch.mouseClicked(mouseX, mouseY);
        if (statSearch != null && statSearch.isVisible())
            return statSearch.mouseClicked(mouseX, mouseY);

        // Count dialog
        if (countDialogType != null) {
            int dw = 220, dh = 100, dx = this.width / 2 - dw / 2, dy = this.height / 2 - dh / 2;
            int btnW = 60, btnH = 18, btnGap = 10;
            int totalBtnW = btnW * 2 + btnGap;
            int cancelX = dx + dw / 2 - totalBtnW / 2;
            int okX = cancelX + btnW + btnGap;
            int btnY = dy + dh - btnH - 8;
            if (mouseX >= cancelX && mouseX < cancelX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                closeCountDialog();
                return true;
            }
            if (mouseX >= okX && mouseX < okX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                confirmCountDialog();
                return true;
            }
            return true;
        }

        int mx = (int) mouseX, my = (int) mouseY;

        // Content scrollbar drag
        if (maxScroll > 0 && hasGroup()) {
            int rightX = LEFT_PANEL_W + 15;
            int rightW = this.width - rightX - 10;
            int scrollTrackX = rightX + rightW - SCROLLBAR_WIDTH - 1;
            int contentY = HEADER_HEIGHT + TAB_HEIGHT + 6;
            int contentBottom = this.height - 30;
            if (mx >= scrollTrackX - 2 && mx <= scrollTrackX + SCROLLBAR_WIDTH + 2
                    && my >= contentY && my < contentBottom) {
                draggingContentScrollbar = true;
                updateContentScrollFromMouse(my, contentY, contentBottom);
                return true;
            }
        }

        // Group list
        if (mx >= 8 && mx <= LEFT_PANEL_W + 2) {
            int y = HEADER_HEIGHT + 4;
            for (int i = 0; i < groups.size(); i++) {
                if (my >= y && my < y + 28) {
                    if (button == 1) {
                        // Right-click context menu on group
                        final int gi = i;
                        contextMenu = new ContextMenu();
                        contextMenu.addEntry(t("editor.historystages.dep.context.toggle_and_or"), () -> {
                            DependencyGroup grp = groups.get(gi);
                            grp.setLogic(grp.isOr() ? "AND" : "OR");
                            hasChanges = true;
                        });
                        contextMenu.addEntry(t("editor.historystages.duplicate"), () -> {
                            groups.add(gi + 1, groups.get(gi).copy());
                            hasChanges = true;
                        });
                        contextMenu.addEntry(t("editor.historystages.remove"), () -> {
                            syncNbtToItems();
                            groups.remove(gi);
                            if (selectedGroup >= groups.size())
                                selectedGroup = Math.max(0, groups.size() - 1);
                            scrollOffset = 0;
                            hasChanges = true;
                            rebuildItemNbtMap();
                        });
                        contextMenu.show(mx, my, this.font);
                        return true;
                    }
                    // Left-click on AND/OR badge: toggle logic
                    int badgeX2 = LEFT_PANEL_W - 28;
                    if (button == 0 && mx >= badgeX2 && mx <= badgeX2 + 25 && my >= y + 2 && my < y + 14) {
                        DependencyGroup grp = groups.get(i);
                        grp.setLogic(grp.isOr() ? "AND" : "OR");
                        hasChanges = true;
                        Minecraft.getInstance().getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                    // Left-click: select
                    if (selectedGroup != i) {
                        syncNbtToItems();
                        selectedGroup = i;
                        activeTab = 0;
                        scrollOffset = 0;
                        rebuildItemNbtMap();
                        Minecraft.getInstance().getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
                y += 31;
            }
        }

        // Tab area
        if (hasGroup() && tabX != null) {
            if (maxTabScroll > 0 && my >= tabY && my < tabY + TAB_HEIGHT) {
                int tabAreaLeft = LEFT_PANEL_W + 10, tabAreaRight = this.width - 5;
                if (tabScrollOffset > 0 && mx >= tabAreaLeft && mx < tabAreaLeft + TAB_ARROW_WIDTH) {
                    tabScrollOffset = Math.max(0, tabScrollOffset - 40);
                    return true;
                }
                if (tabScrollOffset < maxTabScroll && mx >= tabAreaRight - TAB_ARROW_WIDTH && mx < tabAreaRight) {
                    tabScrollOffset = Math.min(maxTabScroll, tabScrollOffset + 40);
                    return true;
                }
            }
            String[] tabKeys = getTabKeys();
            for (int i = 0; i < tabKeys.length; i++) {
                int sx = tabX[i] - tabScrollOffset;
                if (mx >= sx && mx < sx + tabW[i] && my >= tabY && my < tabY + TAB_HEIGHT) {
                    if (activeTab != i) {
                        activeTab = i;
                        scrollOffset = 0;
                        tabSwitchTime = System.currentTimeMillis();
                        cardHoverProgress.clear();
                        Minecraft.getInstance().getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
            }

            // Content area
            handleContentClick(mx, my, button);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleContentClick(int mx, int my, int button) {
        if (!hasGroup())
            return;
        DependencyGroup group = currentGroup();
        int rightX = LEFT_PANEL_W + 15;
        int rightW = this.width - rightX - 10;
        int contentY = HEADER_HEIGHT + TAB_HEIGHT + 6;
        int contentBottom = this.height - 30;
        int y = contentY - (int) scrollOffset;
        int cx = this.width / 2, cy = this.height / 2;

        switch (activeTab) {
            case 0 -> { // Items
                for (int i = 0; i < group.getItems().size(); i++) {
                    if (mx >= rightX && mx < rightX + rightW && my >= y && my < y + CARD_HEIGHT && my >= contentY
                            && my < contentBottom) {
                        if (button == 1) {
                            showItemContextMenu(mx, my, i, group);
                            return;
                        }
                    }
                    y += CARD_HEIGHT + CARD_GAP;
                }
                if (button == 0 && my >= y + 3 && my < y + 3 + CARD_HEIGHT && mx >= rightX && mx < rightX + rightW) {
                    itemSearch.show(cx, cy, this.width);
                }
            }
            case 1 -> { // Global Stages
                for (int i = 0; i < group.getStages().size(); i++) {
                    if (mx >= rightX && mx < rightX + rightW && my >= y && my < y + CARD_HEIGHT && my >= contentY
                            && my < contentBottom) {
                        if (button == 1) {
                            showSimpleContextMenu(mx, my, i, group.getStages(), "stage");
                            return;
                        }
                    }
                    y += CARD_HEIGHT + CARD_GAP;
                }
                if (button == 0 && my >= y + 3 && my < y + 3 + CARD_HEIGHT && mx >= rightX && mx < rightX + rightW) {
                    globalStageSearch.show(cx, cy, this.width);
                }
            }
            case 2 -> { // Individual Stages
                for (int i = 0; i < group.getIndividualStages().size(); i++) {
                    if (mx >= rightX && mx < rightX + rightW && my >= y && my < y + CARD_HEIGHT && my >= contentY
                            && my < contentBottom) {
                        // Toggle button area
                        IndividualStageDep dep = group.getIndividualStages().get(i);
                        int toggleW = this.font.width(dep.isAllEver() ? t("editor.historystages.dep.ever")
                                : t("editor.historystages.dep.online")) + 10;
                        int toggleX = rightX + rightW - toggleW - 2;
                        if (button == 0 && mx >= toggleX && mx < toggleX + toggleW && my >= y + 3
                                && my < y + CARD_HEIGHT - 3) {
                            dep.setMode(dep.isAllEver() ? "all_online" : "all_ever");
                            hasChanges = true;
                            return;
                        }
                        if (button == 1) {
                            showIndividualStageContextMenu(mx, my, i, group);
                            return;
                        }
                    }
                    y += CARD_HEIGHT + CARD_GAP;
                }
                if (button == 0 && my >= y + 3 && my < y + 3 + CARD_HEIGHT && mx >= rightX && mx < rightX + rightW) {
                    individualStageSearch.show(cx, cy, this.width);
                }
            }
            case 3 -> { // Advancements
                if (!isIndividual)
                    return;
                for (int i = 0; i < group.getAdvancements().size(); i++) {
                    if (mx >= rightX && mx < rightX + rightW && my >= y && my < y + CARD_HEIGHT && my >= contentY
                            && my < contentBottom) {
                        if (button == 1) {
                            showSimpleContextMenu(mx, my, i, group.getAdvancements(), "advancement");
                            return;
                        }
                    }
                    y += CARD_HEIGHT + CARD_GAP;
                }
                if (button == 0 && my >= y + 3 && my < y + 3 + CARD_HEIGHT && mx >= rightX && mx < rightX + rightW) {
                    advancementSearch.show(cx, cy, this.width);
                }
            }
            case 4 -> { // XP Level
                if (!isIndividual)
                    return;
                XpLevelDep xp = group.getXpLevel();
                y += 18;
                if (xp != null && xp.getLevel() > 0) {
                    if (mx >= rightX && mx < rightX + rightW && my >= y && my < y + CARD_HEIGHT) {
                        int toggleW = this.font.width(xp.isConsume() ? t("editor.historystages.dep.consume")
                                : t("editor.historystages.dep.check")) + 8;
                        int toggleX = rightX + rightW - toggleW - 2;
                        if (button == 0 && mx >= toggleX && mx < toggleX + toggleW && my >= y + 3
                                && my < y + CARD_HEIGHT - 3) {
                            xp.setConsume(!xp.isConsume());
                            hasChanges = true;
                            return;
                        }
                        if (button == 1) {
                            showXpContextMenu(mx, my, group);
                            return;
                        }
                    }
                } else {
                    if (button == 0 && my >= y && my < y + CARD_HEIGHT && mx >= rightX && mx < rightX + rightW) {
                        openCountDialog("xp_level", null, -1);
                    }
                }
            }
            case 5 -> { // Entity Kills
                if (!isIndividual)
                    return;
                for (int i = 0; i < group.getEntityKills().size(); i++) {
                    if (mx >= rightX && mx < rightX + rightW && my >= y && my < y + ENTITY_CARD_HEIGHT && my >= contentY
                            && my < contentBottom) {
                        if (button == 1) {
                            showEntityKillContextMenu(mx, my, i, group);
                            return;
                        }
                    }
                    y += ENTITY_CARD_HEIGHT + CARD_GAP;
                }
                if (button == 0 && my >= y + 3 && my < y + 3 + CARD_HEIGHT && mx >= rightX && mx < rightX + rightW) {
                    entitySearch.show(cx, cy, this.width);
                }
            }
            case 6 -> { // Stats
                if (!isIndividual)
                    return;
                for (int i = 0; i < group.getStats().size(); i++) {
                    if (mx >= rightX && mx < rightX + rightW && my >= y && my < y + CARD_HEIGHT && my >= contentY
                            && my < contentBottom) {
                        if (button == 1) {
                            showStatContextMenu(mx, my, i, group);
                            return;
                        }
                    }
                    y += CARD_HEIGHT + CARD_GAP;
                }
                if (button == 0 && my >= y + 3 && my < y + 3 + CARD_HEIGHT && mx >= rightX && mx < rightX + rightW) {
                    statSearch.show(cx, cy, this.width);
                }
            }
        }
    }

    // --- Context menus ---

    private void showItemContextMenu(int mx, int my, int idx, DependencyGroup group) {
        contextMenu = new ContextMenu();
        contextMenu.addEntry(t("editor.historystages.dep.context.edit_nbt"),
                () -> openNbtEditScreen(idx, group.getItems().get(idx).getId()));
        contextMenu.addEntry(t("editor.historystages.dep.context.count"),
                () -> openCountDialog("item_count", group.getItems().get(idx).getId(), idx));
        contextMenu.addEntry(t("editor.historystages.copy_id"),
                () -> Minecraft.getInstance().keyboardHandler.setClipboard(group.getItems().get(idx).getId()));
        contextMenu.addEntry(t("editor.historystages.duplicate"), () -> {
            DependencyItem orig = group.getItems().get(idx);
            group.getItems().add(idx + 1, orig.copy());
            if (itemNbtMap.containsKey(idx)) {
                // Shift indices and duplicate NBT
                Map<Integer, JsonObject> shifted = new HashMap<>();
                for (var e : itemNbtMap.entrySet())
                    shifted.put(e.getKey() > idx ? e.getKey() + 1 : e.getKey(), e.getValue());
                shifted.put(idx + 1, itemNbtMap.get(idx).deepCopy());
                itemNbtMap.clear();
                itemNbtMap.putAll(shifted);
            }
            hasChanges = true;
        });
        contextMenu.addEntry(t("editor.historystages.remove"), () -> {
            group.getItems().remove(idx);
            itemNbtMap.remove(idx);
            Map<Integer, JsonObject> shifted = new HashMap<>();
            for (var e : itemNbtMap.entrySet())
                shifted.put(e.getKey() > idx ? e.getKey() - 1 : e.getKey(), e.getValue());
            itemNbtMap.clear();
            itemNbtMap.putAll(shifted);
            hasChanges = true;
        });
        contextMenu.show(mx, my, this.font);
    }

    private void showSimpleContextMenu(int mx, int my, int idx, List<String> list, String type) {
        contextMenu = new ContextMenu();
        contextMenu.addEntry(t("editor.historystages.copy_id"),
                () -> Minecraft.getInstance().keyboardHandler.setClipboard(list.get(idx)));
        contextMenu.addEntry(t("editor.historystages.duplicate"), () -> {
            list.add(idx + 1, list.get(idx));
            hasChanges = true;
        });
        contextMenu.addEntry(t("editor.historystages.remove"), () -> {
            list.remove(idx);
            hasChanges = true;
        });
        contextMenu.show(mx, my, this.font);
    }

    private void showIndividualStageContextMenu(int mx, int my, int idx, DependencyGroup group) {
        contextMenu = new ContextMenu();
        IndividualStageDep dep = group.getIndividualStages().get(idx);
        contextMenu.addEntry(t("editor.historystages.dep.context.toggle_mode"), () -> {
            dep.setMode(dep.isAllEver() ? "all_online" : "all_ever");
            hasChanges = true;
        });
        contextMenu.addEntry(t("editor.historystages.copy_id"),
                () -> Minecraft.getInstance().keyboardHandler.setClipboard(dep.getStageId()));
        contextMenu.addEntry(t("editor.historystages.duplicate"), () -> {
            group.getIndividualStages().add(idx + 1, dep.copy());
            hasChanges = true;
        });
        contextMenu.addEntry(t("editor.historystages.remove"), () -> {
            group.getIndividualStages().remove(idx);
            hasChanges = true;
        });
        contextMenu.show(mx, my, this.font);
    }

    private void showXpContextMenu(int mx, int my, DependencyGroup group) {
        contextMenu = new ContextMenu();
        contextMenu.addEntry(t("editor.historystages.dep.context.change_level"),
                () -> openCountDialog("xp_level", null, 0));
        contextMenu.addEntry(t("editor.historystages.dep.context.toggle_consume"), () -> {
            group.getXpLevel().setConsume(!group.getXpLevel().isConsume());
            hasChanges = true;
        });
        contextMenu.addEntry(t("editor.historystages.remove"), () -> {
            group.setXpLevel(null);
            hasChanges = true;
        });
        contextMenu.show(mx, my, this.font);
    }

    private void showEntityKillContextMenu(int mx, int my, int idx, DependencyGroup group) {
        contextMenu = new ContextMenu();
        EntityKillDep kill = group.getEntityKills().get(idx);
        contextMenu.addEntry(t("editor.historystages.dep.context.count"),
                () -> openCountDialog("kill_count", kill.getEntityId(), idx));
        contextMenu.addEntry(t("editor.historystages.copy_id"),
                () -> Minecraft.getInstance().keyboardHandler.setClipboard(kill.getEntityId()));
        contextMenu.addEntry(t("editor.historystages.duplicate"), () -> {
            group.getEntityKills().add(idx + 1, kill.copy());
            hasChanges = true;
        });
        contextMenu.addEntry(t("editor.historystages.remove"), () -> {
            group.getEntityKills().remove(idx);
            hasChanges = true;
        });
        contextMenu.show(mx, my, this.font);
    }

    private void showStatContextMenu(int mx, int my, int idx, DependencyGroup group) {
        contextMenu = new ContextMenu();
        StatDep stat = group.getStats().get(idx);
        contextMenu.addEntry(t("editor.historystages.dep.context.min_value"),
                () -> openCountDialog("stat_value", stat.getStatId(), idx));
        contextMenu.addEntry(t("editor.historystages.copy_id"),
                () -> Minecraft.getInstance().keyboardHandler.setClipboard(stat.getStatId()));
        contextMenu.addEntry(t("editor.historystages.duplicate"), () -> {
            group.getStats().add(idx + 1, stat.copy());
            hasChanges = true;
        });
        contextMenu.addEntry(t("editor.historystages.remove"), () -> {
            group.getStats().remove(idx);
            hasChanges = true;
        });
        contextMenu.show(mx, my, this.font);
    }

    private void openNbtEditScreen(int entryIdx, String itemId) {
        syncNbtToItems();
        DependencyItem item = currentGroup().getItems().get(entryIdx);
        JsonObject currentNbt = item.hasNbt() ? item.getNbt().deepCopy() : null;
        this.minecraft.setScreen(new NbtItemEditScreen(this, itemId, currentNbt, nbt -> {
            if (nbt != null) {
                itemNbtMap.put(entryIdx, nbt);
                currentGroup().getItems().get(entryIdx).setNbt(nbt.deepCopy());
            } else {
                itemNbtMap.remove(entryIdx);
                currentGroup().getItems().get(entryIdx).setNbt(null);
            }
            hasChanges = true;
        }));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (itemSearch != null && itemSearch.isVisible())
            return itemSearch.mouseDragged(mouseX, mouseY);
        if (entitySearch != null && entitySearch.isVisible())
            return entitySearch.mouseDragged(mouseX, mouseY);
        if (globalStageSearch != null && globalStageSearch.isVisible())
            return globalStageSearch.mouseDragged(mouseX, mouseY);
        if (individualStageSearch != null && individualStageSearch.isVisible())
            return individualStageSearch.mouseDragged(mouseX, mouseY);
        if (advancementSearch != null && advancementSearch.isVisible())
            return advancementSearch.mouseDragged(mouseX, mouseY);
        if (statSearch != null && statSearch.isVisible())
            return statSearch.mouseDragged(mouseX, mouseY);
        if (draggingContentScrollbar) {
            int contentY = HEADER_HEIGHT + TAB_HEIGHT + 6;
            int contentBottom = this.height - 30;
            updateContentScrollFromMouse(mouseY, contentY, contentBottom);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingContentScrollbar) {
            draggingContentScrollbar = false;
            return true;
        }
        if (itemSearch != null && itemSearch.mouseReleased())
            return true;
        if (entitySearch != null && entitySearch.mouseReleased())
            return true;
        if (globalStageSearch != null && globalStageSearch.mouseReleased())
            return true;
        if (individualStageSearch != null && individualStageSearch.mouseReleased())
            return true;
        if (advancementSearch != null && advancementSearch.mouseReleased())
            return true;
        if (statSearch != null && statSearch.mouseReleased())
            return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (itemSearch != null && itemSearch.isVisible())
            return itemSearch.keyPressed(keyCode);
        if (entitySearch != null && entitySearch.isVisible())
            return entitySearch.keyPressed(keyCode);
        if (globalStageSearch != null && globalStageSearch.isVisible())
            return globalStageSearch.keyPressed(keyCode);
        if (individualStageSearch != null && individualStageSearch.isVisible())
            return individualStageSearch.keyPressed(keyCode);
        if (advancementSearch != null && advancementSearch.isVisible())
            return advancementSearch.keyPressed(keyCode);
        if (statSearch != null && statSearch.isVisible())
            return statSearch.keyPressed(keyCode);
        if (countDialogType != null) {
            if (keyCode == 256) {
                closeCountDialog();
                return true;
            }
            if (keyCode == 257) {
                confirmCountDialog();
                return true;
            }
            if (keyCode == 259 && !countInputText.isEmpty()) { // Backspace
                countInputText = countInputText.substring(0, countInputText.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (itemSearch != null && itemSearch.isVisible())
            return itemSearch.charTyped(codePoint);
        if (entitySearch != null && entitySearch.isVisible())
            return entitySearch.charTyped(codePoint);
        if (globalStageSearch != null && globalStageSearch.isVisible())
            return globalStageSearch.charTyped(codePoint);
        if (individualStageSearch != null && individualStageSearch.isVisible())
            return individualStageSearch.charTyped(codePoint);
        if (advancementSearch != null && advancementSearch.isVisible())
            return advancementSearch.charTyped(codePoint);
        if (statSearch != null && statSearch.isVisible())
            return statSearch.charTyped(codePoint);
        if (countDialogType != null) {
            if (Character.isDigit(codePoint) && countInputText.length() < 10) {
                countInputText += codePoint;
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (itemSearch != null && itemSearch.isVisible())
            return itemSearch.mouseScrolled(mouseX, mouseY, delta);
        if (entitySearch != null && entitySearch.isVisible())
            return entitySearch.mouseScrolled(mouseX, mouseY, delta);
        if (globalStageSearch != null && globalStageSearch.isVisible())
            return globalStageSearch.mouseScrolled(mouseX, mouseY, delta);
        if (individualStageSearch != null && individualStageSearch.isVisible())
            return individualStageSearch.mouseScrolled(mouseX, mouseY, delta);
        if (advancementSearch != null && advancementSearch.isVisible())
            return advancementSearch.mouseScrolled(mouseX, mouseY, delta);
        if (statSearch != null && statSearch.isVisible())
            return statSearch.mouseScrolled(mouseX, mouseY, delta);
        if (countDialogType != null)
            return true;
        if (maxTabScroll > 0 && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
            tabScrollOffset = Math.max(0, Math.min(maxTabScroll, tabScrollOffset - (int) (delta * 30)));
            return true;
        }
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
        return true;
    }

    private void updateContentScrollFromMouse(double mouseY, int contentY, int contentBottom) {
        int scrollTrackH = contentBottom - contentY;
        int contentHeight = maxScroll + scrollTrackH;
        int thumbH = Math.max(12, (int) ((float) scrollTrackH / contentHeight * scrollTrackH));
        float usableH = scrollTrackH - thumbH;
        if (usableH > 0) {
            float ratio = (float) (mouseY - contentY - thumbH / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollOffset = Math.round(ratio * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        }
    }

    private int countGroupEntries(DependencyGroup group) {
        int count = group.getItems().size() + group.getStages().size()
                + group.getIndividualStages().size() + group.getAdvancements().size()
                + group.getEntityKills().size() + group.getStats().size();
        if (group.getXpLevel() != null && group.getXpLevel().getLevel() > 0)
            count++;
        return count;
    }

    // --- Entity rendering ---

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

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
