package net.bananemdnsa.historystages.client.editor;
import net.bananemdnsa.historystages.api.editor.widget.CountInputScreen;
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
import net.bananemdnsa.historystages.api.editor.DependencyTab;
import net.bananemdnsa.historystages.client.editor.dep.IdCountTab;
import net.bananemdnsa.historystages.client.editor.dep.EntityKillTab;
import net.bananemdnsa.historystages.client.editor.dep.IndividualStageTab;
import net.bananemdnsa.historystages.client.editor.dep.ItemRequirementTab;
import net.bananemdnsa.historystages.api.editor.RequirementEditor;
import net.bananemdnsa.historystages.client.editor.dep.RequirementEditors;
import net.bananemdnsa.historystages.client.editor.dep.ScoreboardTab;
import net.bananemdnsa.historystages.client.editor.dep.StatTab;
import net.bananemdnsa.historystages.client.editor.dep.StringListTab;
import net.bananemdnsa.historystages.client.editor.dep.XpLevelTab;
import net.bananemdnsa.historystages.api.editor.EntryAction;
import net.bananemdnsa.historystages.api.editor.EntryActionContext;
import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.client.editor.widget.*;
import net.bananemdnsa.historystages.client.editor.widget.list.*;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.*;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.RequirementTypes;
import net.bananemdnsa.historystages.api.stage.StageScope;
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
import java.util.function.Function;
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
    /**
     * Whether the active tab offers an Add row.
     *
     * <p>The tab answers now. This used to name XP as a special case and then ask whether the
     * requirement was built in — two questions with one answer between them, and both wrong for a
     * tab that simply has nothing to add to.
     */
    private boolean activeTabHasAddButton() {
        DependencyTab tab = activeAddonTab();
        return tab != null && tab.hasAddButton();
    }

    private String activeRequirementId() {
        List<Requirement> visible = visibleRequirements();
        return activeTab >= 0 && activeTab < visible.size() ? visible.get(activeTab).id() : "";
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
                    DependencyGroup fresh = new DependencyGroup();
                    // An id of its own from the start, so what players deposit into it stays with
                    // it when the group list is later reordered or thinned out.
                    fresh.setId(DependencyGroup.freshId(groups));
                    groups.add(fresh);
                    selectedGroup = groups.size() - 1;
                    // And load from the new one, which is empty and therefore clears the tabs.
                    loadAddonTabs(currentGroup());
                    activeTab = 0;
                    scrollOffset = 0;
                    restartRowAnimation();
                    hasChanges = true;
                }, 10, this.height - 50, LEFT_PANEL_W - 20, 16));
        // The fresh button is enabled and carries the add label, so the cache starts unlimited;
        // without this reset a re-init while capped would leave a stale enabled button.
        addGroupButtonLimited = false;
        updateAddGroupButton();

        // Searchable widgets. Already-added suppliers map the dependency-wrapper
        // lists (DependencyItem/EntityKillDep/etc.) back to plain string IDs so
        // the FilterDropdown's "Hide already added" toggle can match entries.

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

    /**
     * True while any tab's overlay is up.
     *
     * <p>One question to the active tab, where this used to name six searchable lists by field.
     * Every one of them now belongs to the tab that opens it, and a tab answers for whichever of
     * its overlays is showing.
     */
    private boolean isOverlayOpen() {
        return (addonPicker() != null && addonPicker().isVisible()) || actionOverlay() != null;
    }

    /**
     * The overlay a declared action put up, while it is still up.
     *
     * <p>Drops the reference as soon as the popup hides itself. Holding on to a hidden overlay is
     * how an editor stops responding without throwing anything: every click keeps being forwarded
     * to something invisible.
     */
    private PickerOverlay actionOverlay() {
        if (actionOverlay != null && !actionOverlay.isVisible()) actionOverlay = null;
        return actionOverlay;
    }

    // --- Count dialog ---

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

        if (addonPicker() != null)
            addonPicker().render(g, this.font, mouseX, mouseY);
        if (actionOverlay() != null)
            actionOverlay().render(g, this.font, mouseX, mouseY);

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

    private static final int CONTENT_TOP = HEADER_HEIGHT + TAB_HEIGHT + 6;

    private int contentX() {
        return LEFT_PANEL_W + 15;
    }

    /**
     * Width of the content area.
     *
     * <p>One method because the render path and the click path used to compute it separately and
     * disagreed by five pixels, which made the rightmost column of every row unclickable.
     */
    private int contentWidth() {
        return this.width - contentX() - 15;
    }

    private int contentBottom() {
        return this.height - 30;
    }

    /**
     * The rectangle a tab draws in, scroll already applied.
     *
     * <p>Built once and handed to render and to every input hook, so a hit test and the drawing it
     * refers to cannot drift apart.
     */
    private TabRenderContext renderContext(GuiGraphics g, int mouseX, int mouseY) {
        return new TabRenderContext(g, this.font, contentX(),
                CONTENT_TOP - Math.round(smoothScroll.value()), contentWidth(),
                CONTENT_TOP, contentBottom(), mouseX, mouseY,
                isOverlayOpen() || contextMenu.isVisible(),
                (key, text) -> contentTooltip = new String[] { key, text });
    }

    private TabInputContext inputContext(double mouseX, double mouseY) {
        return new TabInputContext(contentX(), CONTENT_TOP - Math.round(smoothScroll.value()),
                contentWidth(), CONTENT_TOP, contentBottom(), mouseX, mouseY);
    }

    private void renderTabContent(GuiGraphics g, int mouseX, int mouseY) {
        DependencyGroup group = currentGroup();
        int rightX = contentX();
        int rightW = contentWidth();
        int contentY = CONTENT_TOP;
        int contentBottom = contentBottom();
        contentTooltip = null;

        g.enableScissor(rightX, contentY, rightX + rightW, contentBottom);
        int y = contentY - Math.round(smoothScroll.value());

        // A tab that draws its own content is asked first. Returning false means it drew nothing,
        // and the host draws its rows — which is what every tab that is only a list does.
        DependencyTab active = activeAddonTab();
        if (active != null) {
            TabRenderContext tabCtx = renderContext(g, mouseX, mouseY);
            if (active.renderContent(tabCtx)) {
                selfDrawing.add(active.requirementId());
            } else {
                selfDrawing.remove(active.requirementId());
                renderHostRows(tabCtx, active);
            }
            y += active.contentHeight(rightW);
        } else {
            int[] res = renderBuiltInEntries(g, mouseX, mouseY, rightX, rightW, y, contentY,
                    contentBottom, group);
            y = res[0];
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

        // One source of height, so a tab that draws itself can state its own instead of the host
        // inferring it from where the drawing happened to stop.
        DependencyTab heightSource = activeAddonTab();
        int contentHeight = (heightSource != null
                ? heightSource.contentHeight(rightW)
                : y - (contentY - Math.round(smoothScroll.value())))
                + CARD_HEIGHT + 10;
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

    }

    /**
     * What is shown for a requirement that has no tab at all.
     *
     * <p>Every built-in requirement is a tab now, so this is reached only when an addon registered
     * a requirement without an editor: it gates but cannot be edited in game, and saying so beats
     * an empty panel.
     */
    private int[] renderBuiltInEntries(GuiGraphics g, int mouseX, int mouseY, int rightX, int rightW,
            int y, int contentY, int contentBottom, DependencyGroup group) {
        return renderAddonEntries(g, mouseX, mouseY, rightX, rightW, y, contentY, contentBottom);
    }


    // --- Card with marquee helper ---

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


    // --- Scoreboard dialog ---

    private void openScoreboardDialog(int editIndex) {
        String objective = "";
        String holder = "";
        int opIndex = 0;
        int value = 0;
        ScoreboardTab tab = scoreboardTab();
        ScoreboardDep sb = tab == null ? null : tab.at(editIndex);
        if (sb != null) {
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
        ScoreboardTab tab = scoreboardTab();
        if (tab == null) return;
        tab.apply(editIndex, obj, hold.isEmpty() ? null : hold,
                ScoreboardDep.OPERATORS[opIndex], val);
    }

    private ScoreboardTab scoreboardTab() {
        return addonTabs.get("scoreboard") instanceof ScoreboardTab tab ? tab : null;
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
        if (actionOverlay() != null)
            return actionOverlay().mouseClicked(mouseX, mouseY);
        if (addonPicker() != null && addonPicker().isVisible())
            return addonPicker().mouseClicked(mouseX, mouseY);

        // The active tab, after the context menu and the overlays and before this screen's own
        // handling. Ask it earlier and a click on an open dropdown lands in the content behind it;
        // ask it later and a focused field never sees ESC, because confirmDiscard() already ran.
        DependencyTab inputTab = activeAddonTab();
        if (inputTab != null && hasGroup()
                && inputTab.mouseClicked(inputContext(mouseX, mouseY), button)) {
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
                            if (atGroupLimit()) return;
                            DependencyGroup duplicate = groups.get(gi).copy();
                            // The one copy that must not keep the id: two groups sharing one
                            // would share every deposit made into either of them.
                            duplicate.setId(DependencyGroup.freshId(groups));
                            groups.add(gi + 1, duplicate);
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
                        restartRowAnimation();
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
        int rightX = contentX();
        int rightW = contentWidth();
        int contentY = CONTENT_TOP;
        int contentBottom = contentBottom();
        int y = contentY - Math.round(smoothScroll.value());
        int cx = this.width / 2, cy = this.height / 2;

        // No dispatch on the requirement id any more: every requirement is a tab, built in or
        // not, and one path serves them all. DependencyTabSeamGuardTest keeps it that way.
        handleAddonClick(mx, my, button, rightX, rightW, contentY, contentBottom, y, cx, cy);
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

    /**
     * The overlay a declared entry action put up, or null.
     *
     * <p>Cleared as soon as it hides itself, so a stale reference cannot keep swallowing input
     * after the popup is gone — which is the way an editor locks up without an exception.
     */
    private PickerOverlay actionOverlay;

    /** One row list per requirement id. Created lazily, so a tab that draws itself never gets one. */
    private final Map<String, EditorRowList> rowLists = new HashMap<>();

    /**
     * Which tabs drew their own content on the last frame.
     *
     * <p>Recorded rather than asked, because {@code renderContent} is what answers it and only the
     * render pass calls that. A click needs the answer to know whose hit test to trust.
     */
    private final Set<String> selfDrawing = new HashSet<>();

    /** The active tab, or null when the active requirement is built in or has no editor. */
    private DependencyTab activeAddonTab() {
        return addonTabs.get(activeRequirementId());
    }

    /** The active addon tab's picker, or null when there is none to forward input to. */
    private PickerOverlay addonPicker() {
        DependencyTab tab = activeAddonTab();
        return tab == null ? null : tab.activeOverlay();
    }

    private void buildAddonTabs() {
        // Every built-in requirement is a tab, found the same way an addon's is. That is the point
        // of the migration: one lookup path, not a shortcut beside it.
        buildBuiltInTab("item", requirement -> {
            ItemRequirementTab tab = new ItemRequirementTab(requirement,
                    (onSelect, alreadyAdded) -> {
                        SearchableItemList picker = new SearchableItemList(onSelect, alreadyAdded);
                        picker.setMultiSelect(false); // every pick opens the count dialog
                        return picker;
                    },
                    () -> hasChanges = true);
            tab.setOnEditNbt(this::openNbtEditScreen);
            tab.setOnCountNeeded(id -> openItemCountDialog(tab, id, -1));
            return tab;
        });

        buildBuiltInTab("stage", requirement -> new StringListTab(requirement,
                (onSelect, alreadyAdded) -> {
                    SearchableStageList picker = new SearchableStageList(onSelect, false, alreadyAdded);
                    picker.setExcludeStageId(currentStageId);
                    picker.setMultiSelect(true);
                    return picker;
                },
                () -> hasChanges = true,
                DependencyGroup::getStages,
                id -> {
                    StageEntry entry = StageManager.getStages().get(id);
                    return (entry != null ? entry.getDisplayName() : id) + " §7(" + id + ")";
                }));

        buildBuiltInTab("individual_stage", requirement -> new IndividualStageTab(requirement,
                (onSelect, alreadyAdded) -> {
                    SearchableStageList picker = new SearchableStageList(onSelect, true, alreadyAdded);
                    picker.setMultiSelect(true);
                    return picker;
                },
                () -> hasChanges = true));

        buildBuiltInTab("advancement", requirement -> new StringListTab(requirement,
                (onSelect, alreadyAdded) -> {
                    SearchableAdvancementList picker = new SearchableAdvancementList(onSelect, alreadyAdded);
                    picker.setMultiSelect(true);
                    return picker;
                },
                () -> hasChanges = true,
                DependencyGroup::getAdvancements,
                id -> id));

        buildBuiltInTab("xp_level", requirement -> {
            XpLevelTab tab = new XpLevelTab(requirement, () -> hasChanges = true);
            tab.setOnLevelNeeded(() -> openXpLevelDialog(tab));
            return tab;
        });

        buildBuiltInTab("entity_kill", requirement -> {
            EntityKillTab tab = new EntityKillTab(requirement,
                    (onSelect, alreadyAdded) -> {
                        SearchableEntityList picker = new SearchableEntityList(onSelect, alreadyAdded);
                        picker.setMultiSelect(true);
                        return picker;
                    },
                    () -> hasChanges = true);
            tab.setOnCountNeeded(id -> openKillCountDialog(tab, id, -1));
            return tab;
        });

        buildBuiltInTab("stat", requirement -> new StatTab(requirement,
                (onSelect, alreadyAdded) -> {
                    SearchableStatList picker = new SearchableStatList(onSelect, alreadyAdded);
                    picker.setMultiSelect(true);
                    return picker;
                },
                () -> hasChanges = true));

        buildBuiltInTab("scoreboard", requirement -> {
            ScoreboardTab tab = new ScoreboardTab(requirement, () -> hasChanges = true);
            tab.setOnEditRequested(this::openScoreboardDialog);
            return tab;
        });

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

    /**
     * Builds one built-in tab, unless it exists already.
     *
     * <p>Not in {@code init()} for the same reason the addon tabs are not: Minecraft re-runs init
     * on every window resize, and a tab rebuilt there would throw away whatever was entered.
     */
    private void buildBuiltInTab(String requirementId, Function<Requirement, DependencyTab> factory) {
        if (addonTabs.containsKey(requirementId)) return;
        Requirement requirement = RequirementTypes.byId(requirementId);
        if (requirement == null) return;
        DependencyTab tab = factory.apply(requirement);
        if (hasGroup()) tab.load(currentGroup());
        addonTabs.put(requirementId, tab);
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
     * What is shown for a requirement that has no tab at all.
     *
     * <p>Reached only when {@code activeAddonTab()} is null, because a tab is drawn before this
     * path is considered. A requirement can be registered without an editor, which means it gates
     * but cannot be edited in game — and saying so is better than an empty panel.
     */
    private int[] renderAddonEntries(GuiGraphics g, int mouseX, int mouseY, int rightX, int rightW, int y,
            int contentY, int contentBottom) {
        g.drawString(this.font, t("editor.historystages.dep.no_editor"), rightX, y + 4,
                0xFF888888, false);
        return new int[] { y, -1 };
    }

    /**
     * Draws one tab's rows: the text it supplies, plus the optional icon and badge it declares.
     *
     * <p>The path every tab that is only a list goes down, built-in or addon. Chrome, hover, the
     * slide-in, the marquee and hit testing belong to {@link EditorRowList}, which is the same
     * widget the stage editor draws with — that is what makes an addon's tab look like the rest.
     */
    private void renderHostRows(TabRenderContext ctx, DependencyTab tab) {
        List<String> rows = tab.entries();
        rowList(tab).render(ctx, rows.size(), (row, i) -> {
            String iconId = tab.iconItemId(i);
            if (iconId != null) {
                ResourceLocation rl = ResourceLocation.tryParse(iconId);
                Item icon = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
                if (icon != null) {
                    ItemStack stack = new ItemStack(icon);
                    row.leading(16, (g, x, y, w, h) -> {
                        g.pose().pushPose();
                        g.pose().translate(x, y, 0);
                        g.pose().scale(SMALL_SCALE, SMALL_SCALE, 1);
                        g.renderItem(stack, 0, 0);
                        g.pose().popPose();
                    });
                }
            }
            row.text(rows.get(i));
            String badge = tab.badgeText(i);
            if (badge != null) row.badge(badge);
        });
    }

    /**
     * Restarts the staggered entrance on whichever tab is now showing.
     *
     * <p>Every row list has to be told: they are per requirement, and the one being switched to is
     * a different object from the one being left. Without this the entrance never plays at all,
     * because a list that is never told starts life with its clock at zero and reads as long
     * finished.
     */
    private void restartRowAnimation() {
        DependencyTab tab = activeAddonTab();
        if (tab == null) return;
        rowList(tab).resetSlideIn();
        // And the tab's own list, which a tab that draws itself keeps out of the host's reach.
        tab.onShown();
    }

    /** One row list per requirement, so two tabs cannot share a hover animation. */
    private EditorRowList rowList(DependencyTab tab) {
        return rowLists.computeIfAbsent(tab.requirementId(), k -> new EditorRowList());
    }

    /**
     * The click half of {@link #renderHostRows}, and of a tab that drew itself.
     *
     * <p>The tab is asked first — it may have drawn a button of its own — then the row list's
     * declared buttons, then the row under the cursor.
     */
    private void handleAddonClick(int mx, int my, int button, int rightX, int rightW, int contentY,
            int contentBottom, int y, int cx, int cy) {
        DependencyTab tab = activeAddonTab();
        if (tab == null || !hasGroup()) return;

        TabInputContext ctx = inputContext(mx, my);
        if (tab.mouseClicked(ctx, button)) return;
        if (button == 0 && rowList(tab).mouseClicked(ctx)) return;

        // A tab that drew itself is the only one that knows where its rows ended up.
        int row = selfDrawing.contains(tab.requirementId())
                ? tab.rowAt(ctx)
                : rowList(tab).rowAt(ctx, tab.entries().size());
        if (row >= 0) {
            if (button == 1) {
                showAddonContextMenu(mx, my, row, tab);
                return;
            }
            if (button == 0 && tab instanceof IdCountTab counted && counted.hasAmount()) {
                openAddonCountDialog(counted, counted.idAt(row), row);
                return;
            }
            if (button == 0 && tab instanceof ScoreboardTab scoreboard) {
                scoreboard.setOnEditRequested(this::openScoreboardDialog);
                openScoreboardDialog(row);
                return;
            }
            return;
        }

        // Below the last row: the Add button lives there.
        int addTop = contentY - Math.round(smoothScroll.value())
                + tab.contentHeight(rightW) + 3;
        if (button == 0 && tab.hasAddButton() && my >= addTop && my < addTop + CARD_HEIGHT
                && mx >= rightX && mx < rightX + rightW) {
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

        if (tab instanceof StatTab statTab) {
            String statId = statTab.idAt(idx);
            contextMenu.addEntry(t("editor.historystages.dep.context.min_value"),
                    () -> openStatValueDialog(statTab, statId, idx));
            addCopyEntry(statId);
            contextMenu.addEntry(t("editor.historystages.duplicate"), () -> statTab.duplicateAt(idx));
            contextMenu.addEntry(t("editor.historystages.remove"), () -> statTab.removeAt(idx));
            contextMenu.show(mx, my, this.font);
            return;
        }

        if (tab instanceof ScoreboardTab scoreboardTab) {
            ScoreboardDep dep = scoreboardTab.at(idx);
            contextMenu.addEntry(t("editor.historystages.dep.context.edit"),
                    () -> openScoreboardDialog(idx));
            addCopyEntry(dep == null ? "" : dep.getObjective());
            contextMenu.addEntry(t("editor.historystages.duplicate"), () -> scoreboardTab.duplicateAt(idx));
            contextMenu.addEntry(t("editor.historystages.remove"), () -> scoreboardTab.removeAt(idx));
            contextMenu.show(mx, my, this.font);
            return;
        }

        if (tab instanceof XpLevelTab xpTab) {
            contextMenu.addEntry(t("editor.historystages.dep.context.change_level"),
                    () -> openXpLevelDialog(xpTab));
            contextMenu.addEntry(t("editor.historystages.dep.context.toggle_consume"),
                    xpTab::toggleConsume);
            contextMenu.addEntry(t("editor.historystages.remove"), xpTab::clear);
            contextMenu.show(mx, my, this.font);
            return;
        }

        if (tab instanceof EntityKillTab killTab) {
            String entityId = killTab.idAt(idx);
            contextMenu.addEntry(t("editor.historystages.dep.context.count"),
                    () -> openKillCountDialog(killTab, entityId, idx));
            addCopyEntry(entityId);
            contextMenu.addEntry(t("editor.historystages.duplicate"), () -> killTab.duplicateAt(idx));
            contextMenu.addEntry(t("editor.historystages.remove"), () -> killTab.removeAt(idx));
            contextMenu.show(mx, my, this.font);
            return;
        }

        if (tab instanceof IndividualStageTab individualTab) {
            String stageId = individualTab.idAt(idx);
            contextMenu.addEntry(t("editor.historystages.dep.context.toggle_mode"),
                    () -> individualTab.toggleMode(idx));
            addCopyEntry(stageId);
            contextMenu.addEntry(t("editor.historystages.duplicate"),
                    () -> individualTab.duplicateAt(idx));
            contextMenu.addEntry(t("editor.historystages.remove"), () -> individualTab.removeAt(idx));
            contextMenu.show(mx, my, this.font);
            return;
        }

        if (tab instanceof StringListTab stringTab) {
            String entryId = stringTab.idAt(idx);
            addCopyEntry(entryId);
            contextMenu.addEntry(t("editor.historystages.duplicate"), () -> stringTab.duplicateAt(idx));
            contextMenu.addEntry(t("editor.historystages.remove"), () -> stringTab.removeAt(idx));
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

    /** Copy-to-clipboard, the one menu entry every tab offers. */
    private void addCopyEntry(String value) {
        contextMenu.addEntry(t("editor.historystages.copy_id"), () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
            EditorToastHandler.copiedToClipboard(value);
        });
    }

    /** The level dialog for the XP requirement, reading its value out of the tab. */
    private void openXpLevelDialog(XpLevelTab tab) {
        int initial = tab.level() > 0 ? tab.level() : 30;
        this.minecraft.setScreen(new CountInputScreen(this,
                Component.translatable("editor.historystages.dep.dialog.xp_level"), null,
                initial, 0, 999999, tab::setLevel));
    }

    /** The count dialog for an entity-kill entry, reading its value out of the tab. */
    private void openKillCountDialog(EntityKillTab tab, String entityId, int editIndex) {
        int initial = editIndex >= 0 ? tab.countAt(editIndex) : 1;
        this.minecraft.setScreen(new CountInputScreen(this,
                Component.translatable("editor.historystages.dep.dialog.kill_count"), entityId,
                initial, 1, 999999,
                num -> {
                    if (editIndex >= 0) tab.setCountAt(editIndex, num);
                    else tab.addKill(entityId, num);
                }));
    }

    /**
     * The minimum dialog for a stat entry.
     *
     * <p>Apart from {@link #openCountDialog}, which reads its current value out of the group. The
     * stat entries live in the tab now, so the lookup has to go there.
     */
    private void openStatValueDialog(StatTab tab, String statId, int editIndex) {
        int initial = editIndex >= 0 ? tab.minimumAt(editIndex) : 1;
        this.minecraft.setScreen(new CountInputScreen(this,
                Component.translatable("editor.historystages.dep.dialog.min_value"), statId,
                initial, 0, 999999,
                num -> {
                    if (editIndex >= 0) tab.setMinimumAt(editIndex, num);
                    else tab.addStat(statId, num);
                }));
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
            contextMenu.addEntry(t(action.langKey()), () -> action.run(entryActionContext(idx)));
        }
    }

    /**
     * What a declared action is handed when it runs.
     *
     * <p>The overlay sink is what lets an action put up one of the mod's filter popups: this
     * screen renders {@code actionOverlay} above everything and forwards input to it, exactly the
     * way it does for a tab's picker.
     */
    private EntryActionContext entryActionContext(int idx) {
        return new EntryActionContext(idx, () -> hasChanges = true,
                screen -> this.minecraft.setScreen(screen),
                overlay -> {
                    // Shown here rather than by the factory: only the screen knows its own centre,
                    // and a popup told to appear at (0, 0) lands in the top-left corner.
                    overlay.show(this.width / 2, this.height / 2, this.width);
                    this.actionOverlay = overlay;
                });
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

    // --- Context menus ---

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
        if (actionOverlay() != null)
            return actionOverlay().mouseDragged(mouseX, mouseY);
        if (addonPicker() != null && addonPicker().isVisible())
            return addonPicker().mouseDragged(mouseX, mouseY);
        DependencyTab draggedTab = activeAddonTab();
        if (draggedTab != null && draggedTab.mouseDragged(inputContext(mouseX, mouseY), button))
            return true;
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
        if (actionOverlay() != null && actionOverlay().mouseReleased())
            return true;
        if (addonPicker() != null && addonPicker().mouseReleased())
            return true;
        DependencyTab releasedTab = activeAddonTab();
        if (releasedTab != null && releasedTab.mouseReleased(inputContext(mouseX, mouseY), button))
            return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (actionOverlay() != null)
            return actionOverlay().keyPressed(keyCode);
        if (addonPicker() != null && addonPicker().isVisible())
            return addonPicker().keyPressed(keyCode);
        DependencyTab keyTab = activeAddonTab();
        if (keyTab != null && keyTab.keyPressed(keyCode, scanCode, modifiers))
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (actionOverlay() != null)
            return actionOverlay().charTyped(codePoint);
        if (addonPicker() != null && addonPicker().isVisible())
            return addonPicker().charTyped(codePoint);
        DependencyTab charTab = activeAddonTab();
        if (charTab != null && charTab.charTyped(codePoint, modifiers))
            return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double delta = scrollY;
        if (actionOverlay() != null)
            return actionOverlay().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (addonPicker() != null && addonPicker().isVisible())
            return addonPicker().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        DependencyTab scrollTab = activeAddonTab();
        if (scrollTab != null && scrollTab.mouseScrolled(inputContext(mouseX, mouseY), scrollX, scrollY))
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

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
