package net.bananemdnsa.historystages.client.editor;
import net.bananemdnsa.historystages.client.editor.dialog.CountInputScreen;
import net.bananemdnsa.historystages.client.editor.dialog.ScoreboardDepScreen;
import net.bananemdnsa.historystages.client.editor.toast.EditorToastHandler;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.dep.DependencyTab;
import net.bananemdnsa.historystages.client.editor.dep.IdCountTab;
import net.bananemdnsa.historystages.client.editor.dep.ItemRequirementTab;
import net.bananemdnsa.historystages.client.editor.dep.RequirementEditor;
import net.bananemdnsa.historystages.client.editor.dep.RequirementEditors;
import net.bananemdnsa.historystages.client.editor.tab.EntryAction;
import net.bananemdnsa.historystages.client.editor.tab.GenericIdPicker;
import net.bananemdnsa.historystages.client.editor.widget.*;
import net.bananemdnsa.historystages.client.editor.widget.list.*;
import net.bananemdnsa.historystages.client.editor.widget.list.PickerOverlay;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.*;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.RequirementTypes;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.joml.Quaternionf;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DependencyEditorScreen extends Screen {
    private final Screen parent;
    private final List<DependencyGroup> groups;
    /** Kept so the label + enabled state can follow the group count at runtime. */
    private StyledButton addGroupButton;
    /** Last applied limit state, so the button is only rewritten when it flips. */
    private boolean addGroupButtonLimited;
    private final boolean isIndividual;
    private final Consumer<List<DependencyGroup>> onSave;
    private final String currentStageId;

    private int selectedGroup = 0;
    private int activeTab = 0;
    private double scrollOffset = 0;
    /**
     * Sub-pixel scroll position chasing {@link #scrollOffset}. Render and the click paths both
     * read this, so what the cursor hits is always what the frame drew.
     */
    private final Anim smoothScroll = new Anim();
    private int maxScroll = 0;
    private boolean hasChanges = false;

    /**
     * The requirement kinds this stage can express, in registry order.
     *
     * <p>Two hardcoded arrays of tab keys used to live here, one per scope. They were the only
     * place the global/individual distinction existed, which meant the editor hid a kind while
     * the checker went on evaluating it — a hand-written advancement on a global stage was
     * checked against whichever player happened to trigger it. The scope now lives on the
     * requirement, and both the tab strip and the checker read it from there.
     */
    private List<Requirement> visibleRequirements() {
        return RequirementTypes.forScope(isIndividual ? StageScope.INDIVIDUAL : StageScope.GLOBAL);
    }

    /**
     * The requirement the active tab belongs to, or {@code ""} when the index is out of range.
     *
     * <p>Dispatching on this rather than on the raw index is the point of the rewrite: tab 3 used
     * to mean advancements on an individual stage and scoreboard on a global one, and every
     * branch had to remember which.
     */
    private String activeRequirementId() {
        List<Requirement> visible = visibleRequirements();
        return activeTab >= 0 && activeTab < visible.size() ? visible.get(activeTab).id() : "";
    }

    /**
     * Whether the active tab offers an Add row.
     *
     * <p>False for XP, which is a single value with nothing to add to, and false for an addon
     * requirement whose mod registered no editor — there the button would open nothing, and a
     * control that silently does nothing is worse than no control at all.
     */
    private boolean activeTabHasAddButton() {
        String requirementId = activeRequirementId();
        if (requirementId.isEmpty() || "xp_level".equals(requirementId)) return false;
        boolean builtIn = RequirementTypes.builtIns().stream()
                .anyMatch(requirement -> requirement.id().equals(requirementId));
        return builtIn || RequirementEditors.byRequirement(requirementId) != null;
    }

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
    private static final long CARD_MARQUEE_DELAY_MS = Timing.MARQUEE_DELAY_MS;
    private static final float CARD_MARQUEE_SPEED = Timing.MARQUEE_SPEED;
    private int hoveredCardIndex = -1;
    private long cardHoverStartTime = 0;

    // Card hover animation
    private final Map<Integer, Anim> cardHoverProgress = new HashMap<>();

    // Animated tab indicator
    private final Anim tabIndicatorXAnim = new Anim();
    private final Anim tabIndicatorWAnim = new Anim();
    /** Per-tab hover progress, indexed by tab position. */
    private final Map<Integer, Anim> tabHover = new HashMap<>();
    private final Anim contentThumbHover = new Anim();
    private boolean tabIndicatorInit = false;
    private long tabSwitchTime = 0;

    // Tab scrolling
    private int tabScrollOffset = 0;
    private int maxTabScroll = 0;

    // Searchable widget overlays
    private SearchableEntityList entitySearch;
    private SearchableStageList globalStageSearch;
    private SearchableStageList individualStageSearch;
    private SearchableAdvancementList advancementSearch;
    private SearchableStatList statSearch;

    // Context menu
    private ContextMenu contextMenu;

    // Tab layout arrays
    private int[] tabX;
    private int[] tabW;
    private int tabY;

    // Tooltip hover tracking
    private String hoveredTooltipKey = null;
    private long tooltipHoverStart = 0;
    private static final long TOOLTIP_DELAY_MS = Timing.TOOLTIP_DELAY_MS;

    // NBT editing for item dependencies

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
        return visibleRequirements().stream().map(Requirement::tabLangKey).toArray(String[]::new);
    }

    private String[] getTabTooltipKeys() {
        return visibleRequirements().stream().map(Requirement::tooltipLangKey).toArray(String[]::new);
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
        addGroupButton = this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.dep.add_group"),
                btn -> {
                    if (atGroupLimit()) return;
                    // Store into the group being left before the selection moves, or its addon
                    // entries never reach it — the tabs hold them until told otherwise.
                    if (hasGroup()) storeAddonTabs(currentGroup());
                    groups.add(new DependencyGroup());
                    selectedGroup = groups.size() - 1;
                    // And load from the new one, which is empty and therefore clears the tabs.
                    loadAddonTabs(currentGroup());
                    activeTab = 0;
                    scrollOffset = 0;
                    hasChanges = true;
                }, 10, this.height - 50, LEFT_PANEL_W - 20, 16));
        // The fresh button is enabled and carries the add label, so the cache starts unlimited;
        // without this reset a re-init while capped would leave a stale enabled button.
        addGroupButtonLimited = false;
        updateAddGroupButton();

        // Searchable widgets. Already-added suppliers map the dependency-wrapper
        // lists (DependencyItem/EntityKillDep/etc.) back to plain string IDs so
        // the FilterDropdown's "Hide already added" toggle can match entries.
        entitySearch = new SearchableEntityList(id -> {
            if (hasGroup()) {
                currentGroup().getEntityKills().add(new EntityKillDep(id, 1));
                hasChanges = true;
            }
        }, () -> hasGroup()
                ? currentGroup().getEntityKills().stream().map(EntityKillDep::getEntityId).toList()
                : java.util.Collections.emptyList());
        entitySearch.setMultiSelect(true);
        globalStageSearch = new SearchableStageList(id -> {
            if (hasGroup()) {
                currentGroup().getStages().add(id);
                hasChanges = true;
            }
        }, false, () -> hasGroup() ? currentGroup().getStages() : java.util.Collections.emptyList());
        globalStageSearch.setExcludeStageId(currentStageId);
        globalStageSearch.setMultiSelect(true);
        individualStageSearch = new SearchableStageList(id -> {
            if (hasGroup()) {
                currentGroup().getIndividualStages().add(new IndividualStageDep(id, "all_online"));
                hasChanges = true;
            }
        }, true, () -> hasGroup()
                ? currentGroup().getIndividualStages().stream().map(IndividualStageDep::getStageId).toList()
                : java.util.Collections.emptyList());
        individualStageSearch.setMultiSelect(true);
        advancementSearch = new SearchableAdvancementList(id -> {
            if (hasGroup()) {
                currentGroup().getAdvancements().add(id);
                hasChanges = true;
            }
        }, () -> hasGroup() ? currentGroup().getAdvancements() : java.util.Collections.emptyList());
        advancementSearch.setMultiSelect(true);
        statSearch = new SearchableStatList(id -> {
            if (hasGroup()) {
                currentGroup().getStats().add(new StatDep(id, 1));
                hasChanges = true;
            }
        }, () -> hasGroup()
                ? currentGroup().getStats().stream().map(StatDep::getStatId).toList()
                : java.util.Collections.emptyList());
        statSearch.setMultiSelect(true);

        contextMenu = new ContextMenu();
        buildAddonTabs();
        computeTabLayout();
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

    private void save() {
        // Addon tabs hold their entries and only write them on store. Without this the last edits
        // made in the selected group never reach it, and removeIf below would then drop a group
        // that only looks empty.
        if (hasGroup()) storeAddonTabs(currentGroup());
        groups.removeIf(DependencyGroup::isEmpty);
        onSave.accept(groups.stream().map(DependencyGroup::copy).collect(Collectors.toList()));
        hasChanges = false;
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
        return (entitySearch != null && entitySearch.isVisible())
                || (globalStageSearch != null && globalStageSearch.isVisible())
                || (individualStageSearch != null && individualStageSearch.isVisible())
                || (advancementSearch != null && advancementSearch.isVisible())
                || (statSearch != null && statSearch.isVisible())
                || (addonPicker() != null && addonPicker().isVisible());
    }

    // --- Count dialog ---

    private void openCountDialog(String type, String id, int editIndex) {
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

        Component title = switch (type) {
            case "item_count" -> Component.translatable("editor.historystages.dep.dialog.item_count");
            case "kill_count" -> Component.translatable("editor.historystages.dep.dialog.kill_count");
            case "stat_value" -> Component.translatable("editor.historystages.dep.dialog.min_value");
            case "xp_level" -> Component.translatable("editor.historystages.dep.dialog.xp_level");
            default -> Component.translatable("editor.historystages.dep.dialog.value");
        };
        int initial;
        try {
            initial = Integer.parseInt(defaultVal);
        } catch (NumberFormatException e) {
            initial = 1;
        }
        // Counts of things must be at least 1 — zero items or zero kills is not a dependency.
        // Thresholds may legitimately be 0, and hand-written packs contain such values; a
        // minimum of 1 there would trap the user in a dialog they cannot confirm.
        int min = switch (type) {
            case "stat_value", "xp_level" -> 0;
            default -> 1;
        };
        this.minecraft.setScreen(new CountInputScreen(this, title, id, initial, min, 999999,
                num -> applyCountDialog(type, id, editIndex, num)));
    }

    /** Applies a confirmed count. The value arrives already parsed and range-checked. */
    private void applyCountDialog(String type, String id, int editIndex, int num) {
        if (hasGroup()) {
            DependencyGroup group = currentGroup();
            if (editIndex >= 0) {
                switch (type) {
                    case "item_count" -> {
                        if (editIndex < group.getItems().size())
                            group.getItems().get(editIndex).setCount(num);
                    }
                    case "kill_count" -> {
                        if (editIndex < group.getEntityKills().size())
                            group.getEntityKills().get(editIndex).setCount(num);
                    }
                    case "stat_value" -> {
                        if (editIndex < group.getStats().size())
                            group.getStats().get(editIndex).setMinValue(num);
                    }
                    case "xp_level" -> {
                        boolean consume = group.getXpLevel() != null && group.getXpLevel().isConsume();
                        group.setXpLevel(new XpLevelDep(num, consume));
                    }
                }
            } else {
                switch (type) {
                    case "item_count" -> group.getItems().add(new DependencyItem(id, num));
                    case "kill_count" -> group.getEntityKills().add(new EntityKillDep(id, num));
                    case "stat_value" -> group.getStats().add(new StatDep(id, num));
                    case "xp_level" -> {
                        boolean consume = group.getXpLevel() != null && group.getXpLevel().isConsume();
                        group.setXpLevel(new XpLevelDep(num, consume));
                    }
                }
            }
            hasChanges = true;
        }
    }

    private boolean atGroupLimit() {
        return groups.size() >= DependencyGroup.MAX_GROUPS;
    }

    /**
     * Swaps the Add-Group button to an inert "limit reached" state once the cap is hit.
     * Runs every frame, so it only touches the button when the state actually flips.
     */
    private void updateAddGroupButton() {
        if (addGroupButton == null) return;
        boolean limited = atGroupLimit();
        if (limited == addGroupButtonLimited) return;
        addGroupButtonLimited = limited;
        addGroupButton.active = !limited;
        addGroupButton.setMessage(limited
                ? Component.translatable("editor.historystages.dep.group_limit", DependencyGroup.MAX_GROUPS)
                : Component.translatable("editor.historystages.dep.add_group"));
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
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op — we draw our own background in render() and want to avoid 1.21's menu blur shader
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);

        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

        // Groups can be added or removed between frames, so the cap is re-checked here.
        updateAddGroupButton();

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

        // Unsaved indicator (pulsing dot), right-aligned against the Save button like StageDetailScreen
        if (hasChanges) {
            float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS)
                    / Timing.BREATHE_PERIOD_MS;
            int dotAlpha = (int) ((0.35f + 0.45f * Ease.breathe(phase)) * 255);
            int dotX = this.width - 60 - 8 - 6;
            String unsavedLabel = t("editor.historystages.unsaved");
            int unsavedW = (int) (this.font.width(unsavedLabel) * SMALL_SCALE);
            g.fill(dotX - unsavedW - 4, this.height - 18, dotX - unsavedW + 2, this.height - 12,
                    (dotAlpha << 24) | 0xFFCC00);
            drawSmallText(g, unsavedLabel, dotX - unsavedW + 5, this.height - 18, 0xFFCC00);
        }

        super.render(g, mouseX, mouseY, partialTick);

        // Overlays (z-ordered on top)
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);

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
        if (addonPicker() != null)
            addonPicker().render(g, this.font, mouseX, mouseY);

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
            float cardProgress = updateCardHover(-1000 - i, hovered);

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
            tabIndicatorXAnim.set(tabX[activeTab] - tabScrollOffset);
            tabIndicatorWAnim.set(tabW[activeTab]);
            tabIndicatorInit = true;
        }
        if (tabX != null && activeTab < tabX.length) {
            float targetX = tabX[activeTab] - tabScrollOffset;
            float targetW = tabW[activeTab];
            tabIndicatorXAnim.approach(targetX, Timing.SCROLL_HALF_LIFE_MS);
            tabIndicatorWAnim.approach(targetW, Timing.SCROLL_HALF_LIFE_MS);
            tabIndicatorXAnim.settle(targetX, 0.5f);
            tabIndicatorWAnim.settle(targetW, 0.5f);
        }

        if (hasTabScroll)
            g.enableScissor(tabClipLeft, tabY, tabClipRight, tabY + TAB_HEIGHT);

        for (int i = 0; i < tabKeys.length; i++) {
            int sx = tabX[i] - tabScrollOffset;
            boolean active = (i == activeTab);
            boolean hovered = !isOverlayOpen() && !contextMenu.isVisible()
                    && mouseX >= Math.max(sx, tabClipLeft) && mouseX < Math.min(sx + tabW[i], tabClipRight)
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
            float th = Ease.outCubic(tabHover.computeIfAbsent(i, k -> new Anim())
                    .ramp(hovered && !active, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            g.fill(sx, tabY, sx + tabW[i], tabY + TAB_HEIGHT,
                    active ? 0x40FFCC00 : Fade.mix(0x15FFFFFF, 0x25FFFFFF, th));
            drawSmallText(g, t(tabKeys[i]), sx + TAB_PAD, tabY + 4,
                    active ? 0xFFFFFF : Fade.mix(0xFF999999, 0xFFDDDDDD, th));
            if (hovered && i < tooltipKeys.length)
                result = new String[] { "tab." + i, t(tooltipKeys[i]) };
        }

        g.fill(Math.round(tabIndicatorXAnim.value()), tabY + TAB_HEIGHT - 2,
                Math.round(tabIndicatorXAnim.value() + tabIndicatorWAnim.value()), tabY + TAB_HEIGHT,
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
        int y = contentY - Math.round(smoothScroll.value());
        int currentHoveredCard = -1;

        // Dispatched on the requirement id, not the tab index. The scope filter has already
        // removed the tabs this stage cannot express, so no branch needs an isIndividual check.
        switch (activeRequirementId()) {
            case "stage" -> {
                int[] res = renderStringCardEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom,
                        group.getStages(), false);
                y = res[0];
                currentHoveredCard = res[1];
            }
            case "individual_stage" -> {
                int[] res = renderIndividualStageEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom,
                        group);
                y = res[0];
                currentHoveredCard = res[1];
            }
            case "advancement" -> {
                int[] res = renderStringCardEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom,
                        group.getAdvancements(), true);
                y = res[0];
                currentHoveredCard = res[1];
            }
            case "xp_level" -> y = renderXpLevelEntry(g, mouseX, mouseY, rightX, rightW, y, group);
            case "entity_kill" -> {
                int[] res = renderEntityKillEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom,
                        group);
                y = res[0];
                currentHoveredCard = res[1];
            }
            case "stat" -> {
                int[] res = renderStatEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom, group);
                y = res[0];
                currentHoveredCard = res[1];
            }
            case "scoreboard" -> {
                int[] res = renderScoreboardEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom,
                        group);
                y = res[0];
                currentHoveredCard = res[1];
            }
            default -> {
                // An addon requirement. It renders through the tab its mod registered; a
                // requirement registered without an editor simply shows an empty tab.
                int[] res = renderAddonEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom);
                y = res[0];
                currentHoveredCard = res[1];
            }
        }

        // Add button (matching StageDetailScreen style)
        if (activeTabHasAddButton()) {
            int addY = y + 3;
            String addText = t("editor.historystages.dep.add");
            int addTextW = this.font.width(addText);
            int addBoxRight = rightX + addTextW + 20;
            boolean addH = !isOverlayOpen() && !contextMenu.isVisible() && mouseX >= rightX && mouseX < addBoxRight
                    && mouseY >= addY && mouseY < addY + CARD_HEIGHT && mouseY >= contentY && mouseY < contentBottom;

            float addProgress = updateCardHover(-2, addH);

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

        int contentHeight = y + CARD_HEIGHT + 10 - contentY + Math.round(smoothScroll.value());
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
            int thumbY = scrollTrackTop + Math.round(smoothScroll.value() / maxScroll * (scrollTrackH - thumbH));

            boolean thumbHovered = !isOverlayOpen() && !contextMenu.isVisible()
                    && mouseX >= scrollTrackX - 2 && mouseX <= scrollTrackX + SCROLLBAR_WIDTH + 2
                    && mouseY >= thumbY && mouseY <= thumbY + thumbH;
            float th = Ease.outCubic(contentThumbHover.ramp(thumbHovered || draggingContentScrollbar,
                    Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            int thumbColor = draggingContentScrollbar
                    ? 0xCCFFCC00
                    : Fade.mix(0x80888888, 0xBBCCCCCC, th);
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

    /**
     * Eased hover progress for one card, keyed so every row in the screen shares one timing.
     * Entries are kept rather than pruned at rest: the key space is bounded by the rows the
     * active tab can show, and dropping an entry mid-hover would restart it from zero.
     */
    private float updateCardHover(int cardIndex, boolean hovered) {
        return Ease.outCubic(cardHoverProgress.computeIfAbsent(cardIndex, k -> new Anim())
                .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
    }

    // --- Entry renderers ---

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
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
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

    private int[] renderScoreboardEntries(GuiGraphics g, int mx, int my, int rx, int rw, int y, int cTop, int cBot,
            DependencyGroup group) {
        int hovered = -1;
        for (int i = 0; i < group.getScoreboard().size(); i++) {
            ScoreboardDep sb = group.getScoreboard().get(i);
            boolean isHovered = !isOverlayOpen() && !contextMenu.isVisible() && mx >= rx && mx < rx + rw && my >= y
                    && my < y + CARD_HEIGHT && my >= cTop && my < cBot;
            float cp = updateCardHover(500 + i, isHovered);
            if (isHovered)
                hovered = i;

            String holderPart = sb.isPlayerSelf() ? "" : " [" + sb.getScoreHolder() + "]";
            String text = sb.getObjective() + " " + sb.getOp() + " " + sb.getValue() + holderPart;
            renderCardWithText(g, rx, rw, y, isHovered, cp, text, 6, 0, 500 + i, cTop, cBot);
            y += CARD_HEIGHT + CARD_GAP;
        }
        return new int[] { y, hovered };
    }

    // --- Scoreboard dialog ---

    private void openScoreboardDialog(int editIndex) {
        String objective = "";
        String holder = "";
        int opIndex = 0;
        int value = 0;
        boolean editing = editIndex >= 0 && hasGroup()
                && editIndex < currentGroup().getScoreboard().size();
        if (editing) {
            ScoreboardDep sb = currentGroup().getScoreboard().get(editIndex);
            objective = sb.getObjective() != null ? sb.getObjective() : "";
            holder = sb.getScoreHolder() != null ? sb.getScoreHolder() : "";
            value = sb.getValue();
            for (int i = 0; i < ScoreboardDep.OPERATORS.length; i++) {
                if (ScoreboardDep.OPERATORS[i].equals(sb.getOp())) { opIndex = i; break; }
            }
        }
        Component title = Component.translatable(editIndex >= 0
                ? "editor.historystages.dep.dialog.scoreboard_edit"
                : "editor.historystages.dep.dialog.scoreboard_add");
        this.minecraft.setScreen(new ScoreboardDepScreen(this, title, objective, holder, opIndex,
                value, ScoreboardDep.OPERATORS,
                (obj, hold, op, val) -> applyScoreboardDialog(editIndex, obj, hold, op, val)));
    }

    /** Applies a confirmed scoreboard dependency. The value arrives already parsed and range-checked. */
    private void applyScoreboardDialog(int editIndex, String obj, String hold, int opIndex, int val) {
        if (!hasGroup()) return;
        String op = ScoreboardDep.OPERATORS[opIndex];
        DependencyGroup group = currentGroup();
        if (editIndex >= 0 && editIndex < group.getScoreboard().size()) {
            ScoreboardDep sb = group.getScoreboard().get(editIndex);
            sb.setObjective(obj);
            sb.setScoreHolder(hold.isEmpty() ? null : hold);
            sb.setOp(op);
            sb.setValue(val);
        } else {
            group.getScoreboard().add(new ScoreboardDep(obj, hold.isEmpty() ? null : hold, op, val));
        }
        hasChanges = true;
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
        if (addonPicker() != null && addonPicker().isVisible())
            return addonPicker().mouseClicked(mouseX, mouseY);

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
                            if (atGroupLimit()) return;
                            groups.add(gi + 1, groups.get(gi).copy());
                            hasChanges = true;
                        });
                        contextMenu.addEntry(t("editor.historystages.remove"), () -> {
                            // Only worth storing when the selected group is not the one going
                            // away; into the doomed one it would be thrown out with it.
                            if (hasGroup() && selectedGroup != gi) storeAddonTabs(currentGroup());
                            groups.remove(gi);
                            if (selectedGroup >= groups.size())
                                selectedGroup = Math.max(0, groups.size() - 1);
                            // Reload whatever is selected now. Note that removing a group before
                            // the selected one shifts the indices without moving the selection —
                            // that jump predates this and is not fixed here, but the tabs must at
                            // least agree with whichever group the selection ended up on.
                            reloadAddonTabsForSelection();
                            scrollOffset = 0;
                            hasChanges = true;
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
                        // Store before, load after — in that order. Reversed, the load overwrites
                        // the tabs before they have been written back, and the edits made in the
                        // group being left are gone with nothing to report it.
                        if (hasGroup()) storeAddonTabs(currentGroup());
                        selectedGroup = i;
                        loadAddonTabs(currentGroup());
                        activeTab = 0;
                        scrollOffset = 0;
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
        int y = contentY - Math.round(smoothScroll.value());
        int cx = this.width / 2, cy = this.height / 2;

        // Dispatched on the requirement id, for the same reason the render path is.
        switch (activeRequirementId()) {
            case "stage" -> { // Global Stages
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
            case "individual_stage" -> { // Individual Stages
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
            case "advancement" -> {
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
            case "xp_level" -> { // XP Level
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
            case "entity_kill" -> { // Entity Kills
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
            case "stat" -> { // Stats
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
            case "scoreboard" ->
                handleScoreboardClick(mx, my, button, rightX, rightW, contentY, contentBottom, y, group);
            default ->
                handleAddonClick(mx, my, button, rightX, rightW, contentY, contentBottom, y, cx, cy);
        }
    }

    // --- Addon requirement tabs ---

    /**
     * One tab per addon requirement that has a registered editor, created once.
     *
     * <p>Created once and deliberately not in {@code init()}: Minecraft re-runs init on every
     * window resize, and a tab rebuilt there would throw away whatever the maintainer had entered.
     * Only the picker is rebuilt on init — the same rule {@code StageDetailScreen} follows.
     */
    private final Map<String, DependencyTab> addonTabs = new LinkedHashMap<>();

    /** The active tab, or null when the active requirement is built in or has no editor. */
    private DependencyTab activeAddonTab() {
        return addonTabs.get(activeRequirementId());
    }

    /** The active addon tab's picker, or null when there is none to forward input to. */
    private PickerOverlay addonPicker() {
        DependencyTab tab = activeAddonTab();
        return tab == null ? null : tab.picker();
    }

    private void buildAddonTabs() {
        // The item requirement is a tab like any other, found the same way an addon's is. That is
        // the point of the migration: one lookup path, not a built-in shortcut beside it.
        if (!addonTabs.containsKey("item")) {
            Requirement itemRequirement = RequirementTypes.byId("item");
            if (itemRequirement != null) {
                ItemRequirementTab itemTab = new ItemRequirementTab(itemRequirement,
                        (onSelect, alreadyAdded) -> {
                            SearchableItemList picker = new SearchableItemList(onSelect, alreadyAdded);
                            picker.setMultiSelect(false); // every pick opens the count dialog
                            return picker;
                        },
                        () -> hasChanges = true);
                itemTab.setOnEditNbt(this::openNbtEditScreen);
                itemTab.setOnCountNeeded(id -> openItemCountDialog(itemTab, id, -1));
                if (hasGroup()) itemTab.load(currentGroup());
                addonTabs.put("item", itemTab);
            }
        }

        for (Requirement requirement : visibleRequirements()) {
            if (addonTabs.containsKey(requirement.id())) continue;
            RequirementEditor editor = RequirementEditors.byRequirement(requirement.id());
            if (editor == null) continue;

            DependencyTab tab = editor.createTab(() -> hasChanges = true);
            if (tab instanceof IdCountTab counted && counted.hasAmount()) {
                // A tab cannot push a screen, so it says it needs an amount and this does it.
                counted.setOnAmountNeeded(id -> openAddonCountDialog(counted, id, -1));
            }
            if (hasGroup()) tab.load(currentGroup());
            addonTabs.put(requirement.id(), tab);
        }
        for (DependencyTab tab : addonTabs.values()) tab.rebuildPicker();
    }

    /** Stores every addon tab's state into the given group. */
    private void storeAddonTabs(DependencyGroup group) {
        for (DependencyTab tab : addonTabs.values()) tab.store(group);
    }

    /** Loads every addon tab's state from the given group. */
    private void loadAddonTabs(DependencyGroup group) {
        for (DependencyTab tab : addonTabs.values()) tab.load(group);
    }

    /**
     * Points the tabs at whichever group is selected now, emptying them when none is.
     *
     * <p>Loading from a throwaway empty group is how they are cleared: a tab that kept the last
     * group's entries would write them into the next group it is stored into.
     */
    private void reloadAddonTabsForSelection() {
        loadAddonTabs(hasGroup() ? currentGroup() : new DependencyGroup());
    }

    /**
     * Renders the tab of a requirement registered by another mod.
     *
     * <p>Row text comes from the tab, the drawing stays here — see {@code EditorTab}. Empty when no
     * addon registered an editor: a requirement can be registered without one, which means it
     * gates but cannot be edited in game.
     */
    private int[] renderAddonEntries(GuiGraphics g, int mouseX, int mouseY, int rightX, int rightW, int y,
            int contentY, int contentBottom) {
        DependencyTab tab = activeAddonTab();
        if (tab == null) {
            g.drawString(this.font, t("editor.historystages.dep.no_editor"), rightX, y + 4,
                    0xFF888888, false);
            return new int[] { y, -1 };
        }
        return renderTabEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom, tab);
    }

    /**
     * Draws one tab's rows: the text it supplies, plus the optional icon and badge it declares.
     *
     * <p>The one renderer every tab goes through, built-in or not. Row height is fixed at
     * {@code CARD_HEIGHT} whatever the tab says, which is what keeps a tab from having to know
     * anything about the scroll arithmetic.
     */
    private int[] renderTabEntries(GuiGraphics g, int mx, int my, int rx, int rw, int y, int cTop, int cBot,
            DependencyTab tab) {
        int hovered = -1;
        List<String> rows = tab.entries();
        for (int i = 0; i < rows.size(); i++) {
            boolean isHovered = !isOverlayOpen() && !contextMenu.isVisible() && mx >= rx && mx < rx + rw
                    && my >= y && my < y + CARD_HEIGHT && my >= cTop && my < cBot;
            float cp = updateCardHover(i, isHovered);
            if (isHovered) hovered = i;

            String badge = tab.badgeText(i);
            int badgeW = badge != null ? this.font.width(badge) + 6 : 0;
            String iconId = tab.iconItemId(i);
            int indent = iconId != null ? 22 : 6;

            renderCardWithText(g, rx, rw, y, isHovered, cp, rows.get(i), indent, badgeW, i, cTop, cBot);

            if (iconId != null) {
                ResourceLocation rl = ResourceLocation.tryParse(iconId);
                Item icon = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
                if (icon != null) {
                    g.pose().pushPose();
                    g.pose().scale(SMALL_SCALE, SMALL_SCALE, 1);
                    g.renderItem(new ItemStack(icon), (int) ((rx + 3) / SMALL_SCALE),
                            (int) ((y + 3) / SMALL_SCALE));
                    g.pose().popPose();
                }
            }
            if (badge != null) {
                g.drawString(this.font, badge, rx + rw - badgeW, y + 7, 0xFFCC00, false);
            }

            y += CARD_HEIGHT + CARD_GAP;
        }
        return new int[] { y, hovered };
    }

    /** The click half of {@link #renderAddonEntries}. */
    private void handleAddonClick(int mx, int my, int button, int rightX, int rightW, int contentY,
            int contentBottom, int y, int cx, int cy) {
        DependencyTab tab = activeAddonTab();
        if (tab == null || !hasGroup()) return;

        for (int i = 0; i < tab.entries().size(); i++) {
            if (mx >= rightX && mx < rightX + rightW && my >= y && my < y + CARD_HEIGHT && my >= contentY
                    && my < contentBottom) {
                if (button == 1) {
                    showAddonContextMenu(mx, my, i, tab);
                    return;
                }
                if (button == 0 && tab instanceof IdCountTab counted && counted.hasAmount()) {
                    openAddonCountDialog(counted, counted.idAt(i), i);
                    return;
                }
            }
            y += CARD_HEIGHT + CARD_GAP;
        }
        if (button == 0 && my >= y + 3 && my < y + 3 + CARD_HEIGHT && mx >= rightX && mx < rightX + rightW) {
            tab.openPicker(cx, cy, this.width);
        }
    }

    private void showAddonContextMenu(int mx, int my, int idx, DependencyTab tab) {
        contextMenu = new ContextMenu();

        if (tab instanceof ItemRequirementTab itemTab) {
            String itemId = itemTab.idAt(idx);
            contextMenu.addEntry(t("editor.historystages.dep.context.edit_nbt"),
                    () -> itemTab.requestNbtEdit(idx));
            contextMenu.addEntry(t("editor.historystages.dep.context.count"),
                    () -> openItemCountDialog(itemTab, itemId, idx));
            contextMenu.addEntry(t("editor.historystages.copy_id"),
                    () -> { Minecraft.getInstance().keyboardHandler.setClipboard(itemId);
                            EditorToastHandler.copiedToClipboard(itemId); });
            contextMenu.addEntry(t("editor.historystages.duplicate"), () -> itemTab.duplicateAt(idx));
            contextMenu.addEntry(t("editor.historystages.remove"), () -> itemTab.removeAt(idx));
            contextMenu.show(mx, my, this.font);
            return;
        }

        IdCountTab counted = tab instanceof IdCountTab c ? c : null;
        String id = counted != null ? counted.idAt(idx) : tab.entries().get(idx);

        if (counted != null && counted.hasAmount()) {
            contextMenu.addEntry(t("editor.historystages.dep.context.count"),
                    () -> openAddonCountDialog(counted, id, idx));
        }
        contextMenu.addEntry(t("editor.historystages.copy_id"),
                () -> { Minecraft.getInstance().keyboardHandler.setClipboard(id);
                        EditorToastHandler.copiedToClipboard(id); });
        if (counted != null) {
            contextMenu.addEntry(t("editor.historystages.duplicate"), () -> counted.duplicateAt(idx));
        }
        addDeclaredActions(tab.requirementId(), idx);
        contextMenu.addEntry(t("editor.historystages.remove"), () -> tab.removeAt(idx));
        contextMenu.show(mx, my, this.font);
    }

    /**
     * Appends whatever extra menu entries the requirement's editor declared.
     *
     * <p>After the built-in ones, not instead of them: copy and remove stay where a maintainer
     * expects them, and an addon adds to the menu rather than replacing it.
     */
    private void addDeclaredActions(String requirementId, int idx) {
        RequirementEditor editor = RequirementEditors.byRequirement(requirementId);
        if (editor == null) return;
        for (EntryAction action : editor.entryActions()) {
            contextMenu.addEntry(t(action.langKey()), () -> action.run(idx, () -> hasChanges = true));
        }
    }

    /**
     * The amount dialog for a free-tier entry.
     *
     * <p>Its own method rather than a fifth case in {@link #openCountDialog}: that one switches on
     * four built-in strings to find both the current value and the title, and neither lookup has an
     * answer here — the value lives in the tab and the title is a lang key the addon supplied.
     */
    /**
     * The count dialog for an item entry.
     *
     * <p>Apart from {@link #openCountDialog}, which reads its current value out of the group. The
     * item entries live in the tab now, so the lookup has to go there.
     */
    private void openItemCountDialog(ItemRequirementTab tab, String itemId, int editIndex) {
        int initial = editIndex >= 0 ? tab.countAt(editIndex) : 1;
        this.minecraft.setScreen(new CountInputScreen(this,
                Component.translatable("editor.historystages.dep.dialog.item_count"), itemId,
                initial, 1, 999999,
                num -> {
                    if (editIndex >= 0) tab.setCountAt(editIndex, num);
                    else tab.addItem(itemId, num);
                }));
    }

    private void openAddonCountDialog(IdCountTab tab, String entryId, int editIndex) {
        int initial = editIndex >= 0 ? tab.amountAt(editIndex) : 1;
        this.minecraft.setScreen(new CountInputScreen(this,
                Component.translatable(tab.amountLangKey()), entryId, initial, 1, 999999,
                num -> {
                    if (editIndex >= 0) tab.setAmountAt(editIndex, num);
                    else tab.addEntry(entryId, num);
                }));
    }

    private void handleScoreboardClick(int mx, int my, int button, int rightX, int rightW, int contentY,
            int contentBottom, int y, DependencyGroup group) {
        for (int i = 0; i < group.getScoreboard().size(); i++) {
            if (mx >= rightX && mx < rightX + rightW && my >= y && my < y + CARD_HEIGHT && my >= contentY
                    && my < contentBottom) {
                if (button == 1) {
                    showScoreboardContextMenu(mx, my, i, group);
                    return;
                }
                if (button == 0) {
                    openScoreboardDialog(i);
                    return;
                }
            }
            y += CARD_HEIGHT + CARD_GAP;
        }
        if (button == 0 && my >= y + 3 && my < y + 3 + CARD_HEIGHT && mx >= rightX && mx < rightX + rightW) {
            openScoreboardDialog(-1);
        }
    }

    private void showScoreboardContextMenu(int mx, int my, int idx, DependencyGroup group) {
        contextMenu = new ContextMenu();
        ScoreboardDep sb = group.getScoreboard().get(idx);
        contextMenu.addEntry(t("editor.historystages.dep.context.edit"),
                () -> openScoreboardDialog(idx));
        contextMenu.addEntry(t("editor.historystages.copy_id"),
                () -> { String v = sb.getObjective(); Minecraft.getInstance().keyboardHandler.setClipboard(v); EditorToastHandler.copiedToClipboard(v); });
        contextMenu.addEntry(t("editor.historystages.duplicate"), () -> {
            group.getScoreboard().add(idx + 1, sb.copy());
            hasChanges = true;
        });
        contextMenu.addEntry(t("editor.historystages.remove"), () -> {
            group.getScoreboard().remove(idx);
            hasChanges = true;
        });
        contextMenu.show(mx, my, this.font);
    }

    // --- Context menus ---

    private void showSimpleContextMenu(int mx, int my, int idx, List<String> list, String type) {
        contextMenu = new ContextMenu();
        contextMenu.addEntry(t("editor.historystages.copy_id"),
                () -> { String v = list.get(idx); Minecraft.getInstance().keyboardHandler.setClipboard(v); EditorToastHandler.copiedToClipboard(v); });
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
                () -> { String v = dep.getStageId(); Minecraft.getInstance().keyboardHandler.setClipboard(v); EditorToastHandler.copiedToClipboard(v); });
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
                () -> { String v = kill.getEntityId(); Minecraft.getInstance().keyboardHandler.setClipboard(v); EditorToastHandler.copiedToClipboard(v); });
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
                () -> { String v = stat.getStatId(); Minecraft.getInstance().keyboardHandler.setClipboard(v); EditorToastHandler.copiedToClipboard(v); });
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
        ItemRequirementTab tab = (ItemRequirementTab) addonTabs.get("item");
        if (tab == null) return;
        this.minecraft.setScreen(new NbtItemEditScreen(this, itemId, tab.nbtAt(entryIdx), nbt -> {
            tab.setNbtAt(entryIdx, nbt == null ? null : nbt.deepCopy());
            // Routes through this screen own save, so the whole stage is persisted.
            save();
        }));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
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
        if (addonPicker() != null && addonPicker().isVisible())
            return addonPicker().mouseDragged(mouseX, mouseY);
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
        if (addonPicker() != null && addonPicker().mouseReleased())
            return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        if (addonPicker() != null && addonPicker().isVisible())
            return addonPicker().keyPressed(keyCode);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
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
        if (addonPicker() != null && addonPicker().isVisible())
            return addonPicker().charTyped(codePoint);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double delta = scrollY;
        if (entitySearch != null && entitySearch.isVisible())
            return entitySearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (globalStageSearch != null && globalStageSearch.isVisible())
            return globalStageSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (individualStageSearch != null && individualStageSearch.isVisible())
            return individualStageSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (advancementSearch != null && advancementSearch.isVisible())
            return advancementSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (statSearch != null && statSearch.isVisible())
            return statSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (addonPicker() != null && addonPicker().isVisible())
            return addonPicker().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
            // Snapped, not eased: while the thumb is held the list must track the
            // cursor exactly, or the thumb drifts from where the pointer is.
            smoothScroll.set((float) scrollOffset);
        }
    }

    private int countGroupEntries(DependencyGroup group) {
        int count = group.getItems().size() + group.getStages().size()
                + group.getIndividualStages().size() + group.getAdvancements().size()
                + group.getEntityKills().size() + group.getStats().size()
                + group.getScoreboard().size();
        if (group.getXpLevel() != null && group.getXpLevel().getLevel() > 0)
            count++;
        // Addon requirements count too, otherwise a group holding nothing else reads as empty in
        // the group list while the checker treats it as a real dependency.
        count += group.addonRequirementIds().size();
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
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
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

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
