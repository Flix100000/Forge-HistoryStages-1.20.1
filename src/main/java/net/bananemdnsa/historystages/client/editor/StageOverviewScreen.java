package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.dialog.CreditsScreen;
import net.bananemdnsa.historystages.client.editor.folder.FolderNameScreen;
import net.bananemdnsa.historystages.client.editor.folder.StageFolderTree;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputField;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputValues;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.PlayerPickerDropdown;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.network.CreateFolderPacket;
import net.bananemdnsa.historystages.network.DeleteFolderPacket;
import net.bananemdnsa.historystages.network.DeleteStagePacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.RenameFolderPacket;
import net.bananemdnsa.historystages.network.ToggleIndividualStageLockPacket;
import net.bananemdnsa.historystages.network.ToggleStageLockPacket;
import net.bananemdnsa.historystages.client.cache.ClientPlayerStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class StageOverviewScreen extends Screen {

    private static final int ENTRY_HEIGHT = 28;
    private static final int LIST_PADDING = 40;
    private static final int HEADER_HEIGHT = 30;
    private static final int SECTION_HEADER_HEIGHT = 22;
    /** Colour code carrying the dependency badge's gold; the raw colour is the fallback. */
    private static final String DEP_BADGE_PREFIX = "§6";
    private static final int DEP_BADGE_COLOR = 0xFFAA55;
    /** Hover-animation key bases; folder rows must not collide with the stage keys i / 10000 + i. */
    private static final int GLOBAL_FOLDER_HOVER_KEY = 20000;
    private static final int INDIVIDUAL_FOLDER_HOVER_KEY = 30000;
    /** Width of the three bars marking a folder row, in pixels. */
    private static final int FOLDER_ICON_WIDTH = 7;
    private static final String BREADCRUMB_SEPARATOR = " / ";

    private List<String> stageOrder;
    private List<String> individualStageOrder;
    private List<String> filteredStageOrder = new ArrayList<>();
    private List<String> filteredIndividualStageOrder = new ArrayList<>();
    private EditBox searchBox;
    private String searchFilter = "";
    private int lastKnownStageCount = -1;
    private int lastKnownIndividualCount = -1;
    private int lastKnownFolderSignature = 0;
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private boolean draggingScrollbar = false;
    private ContextMenu contextMenu;
    /** Target selector for the individual-stage section; null selection means "@a". */
    private PlayerPickerDropdown playerPicker;
    /** Where the picker was last drawn, so hit-testing matches what the user sees. */
    private boolean pickerVisible = false;

    /**
     * Which tree is being browsed: null means the root view with both sections, otherwise
     * false = global, true = individual.
     */
    private Boolean browsingIndividual = null;
    /** Path inside {@link #browsingIndividual}'s tree; {@code ""} is that tree's root. */
    private String currentPath = "";
    /** Folder rows drawn above the stage rows, recomputed by {@link #applyFilter()}. */
    private List<StageFolderTree.Folder> globalFolders = new ArrayList<>();
    private List<StageFolderTree.Folder> individualFolders = new ArrayList<>();
    private StyledButton backButton;

    private record BreadcrumbHit(int x1, int x2, String path) {}
    private final List<BreadcrumbHit> breadcrumbHits = new ArrayList<>();
    /** Screen y of the breadcrumb row, recorded during render for hit-testing. */
    private int breadcrumbY = -1;

    // Animation state
    private final java.util.Map<Integer, Anim> hoverProgress = new java.util.HashMap<>();
    private int lastHoveredIndex = -1;

    // Marquee state
    private int hoveredStageIndex = -1;
    private long stageHoverStartTime = 0;
    private static final long MARQUEE_DELAY_MS = Timing.MARQUEE_DELAY_MS;
    private static final float MARQUEE_SPEED = Timing.MARQUEE_SPEED;

    // Smooth scroll
    private final Anim smoothScroll = new Anim();

    public StageOverviewScreen() {
        super(Component.translatable("editor.historystages.title"));
    }

    /** Re-fetch temporary unlock counts periodically so the display stays current. */
    private int tempCountRefreshTimer = 0;

    /**
     * Picker target the last request asked for, so a change can be noticed here. The
     * dropdown has no selection callback, and it also reassigns itself when the picked
     * player logs off — polling covers both without threading a hook through the widget.
     */
    private UUID lastRequestedTarget;

    @Override
    public void tick() {
        super.tick();
        // A picker change is answered at once; waiting out the refresh interval would
        // leave the previous player's counts on screen for up to a second.
        if (playerPicker != null && !Objects.equals(playerPicker.getSelected(), lastRequestedTarget)) {
            tempCountRefreshTimer = 0;
            requestTemporaryCounts();
            return;
        }
        if (++tempCountRefreshTimer >= 20) { // ~1s
            tempCountRefreshTimer = 0;
            requestTemporaryCounts();
            PacketHandler.sendToServer(new net.bananemdnsa.historystages.network.RequestIndividualStatesPacket());
        }
    }

    /**
     * Asks the server for the live temporary-stage state, for the global tree and for the
     * player the picker is on. Under "@a" — and before the picker exists, on the first
     * request out of {@link #init} — no individual data is requested: the rows have no
     * single player to attribute it to, and the next tick asks again a second later.
     */
    private void requestTemporaryCounts() {
        UUID target = playerPicker == null ? null : playerPicker.getSelected();
        lastRequestedTarget = target;
        PacketHandler.sendToServer(
                new net.bananemdnsa.historystages.network.RequestTemporaryCountsPacket(target));
    }

    @Override
    protected void init() {
        stageOrder = StageManager.getStageOrder();
        individualStageOrder = StageManager.getIndividualStageOrder();

        // Pull the live temporary-stage unlock counts from the server for display.
        requestTemporaryCounts();
        PacketHandler.sendToServer(new net.bananemdnsa.historystages.network.RequestIndividualStatesPacket());

        searchFilter = "";
        int searchW = 120;
        searchBox = new EditBox(this.font, 12, 8, searchW - 4, 14,
                Component.translatable("editor.historystages.search"));
        searchBox.setMaxLength(128);
        searchBox.setBordered(false);
        searchBox.setValue(searchFilter);
        searchBox.setResponder(val -> {
            searchFilter = val;
            applyFilter();
        });
        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.new_stage"),
                btn -> openStageIdInputDialog(null, false),
                10, this.height - 30, 100, 20));

        backButton = StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> navigateUp(),
                115, this.height - 30, 60, 20);
        this.addRenderableWidget(backButton);

        this.addRenderableWidget(StyledButton.of(
                Component.literal("\u2699"),
                btn -> this.minecraft.setScreen(new ConfigEditorScreen(this)),
                this.width - 30, 5, 20, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.depgraph.button"),
                btn -> this.minecraft.setScreen(new DependencyGraphScreen(this)),
                this.width - 110, 5, 75, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.menu.credits"),
                btn -> this.minecraft.setScreen(new CreditsScreen(this)),
                this.width - 175, 5, 60, 20));

        playerPicker = new PlayerPickerDropdown(120);
        contextMenu = new ContextMenu();
        // Another admin may have deleted the folder we were standing in while the screen
        // was open; fall back to the root view instead of showing an empty level.
        if (browsingIndividual != null && !StageFolderTree.exists(browsingIndividual, currentPath)) {
            browsingIndividual = null;
            currentPath = "";
        }
        applyFilter();
    }

    private void applyFilter() {
        String query = searchFilter.toLowerCase().trim();
        Map<String, StageEntry> stages = StageManager.getStages();
        Map<String, StageEntry> individualStages = StageManager.getIndividualStages();

        filteredStageOrder = new ArrayList<>();
        filteredIndividualStageOrder = new ArrayList<>();
        globalFolders = new ArrayList<>();
        individualFolders = new ArrayList<>();

        if (!query.isEmpty()) {
            // Search spans both trees regardless of where the user stands; the current
            // path is kept so clearing the box returns to that exact spot.
            for (String id : stageOrder) {
                if (matchesFilter(id, stages.get(id), query)) filteredStageOrder.add(id);
            }
            for (String id : individualStageOrder) {
                if (matchesFilter(id, individualStages.get(id), query)) filteredIndividualStageOrder.add(id);
            }
        } else if (browsingIndividual == null) {
            globalFolders = StageFolderTree.foldersAt(false, "");
            individualFolders = StageFolderTree.foldersAt(true, "");
            filteredStageOrder = StageFolderTree.stagesAt(false, "", stageOrder);
            filteredIndividualStageOrder = StageFolderTree.stagesAt(true, "", individualStageOrder);
        } else if (browsingIndividual) {
            individualFolders = StageFolderTree.foldersAt(true, currentPath);
            filteredIndividualStageOrder = StageFolderTree.stagesAt(true, currentPath, individualStageOrder);
        } else {
            globalFolders = StageFolderTree.foldersAt(false, currentPath);
            filteredStageOrder = StageFolderTree.stagesAt(false, currentPath, stageOrder);
        }

        updateBackButton();
        updateMaxScroll();
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    /** Visible only while standing inside a tree and not searching — a result list has no "up". */
    private void updateBackButton() {
        if (backButton == null) return;
        backButton.visible = browsingIndividual != null && searchFilter.trim().isEmpty();
    }

    /** Enters a folder, or a tree root when {@code path} is empty. */
    private void navigateInto(boolean individual, String path) {
        browsingIndividual = individual;
        currentPath = path;
        scrollOffset = 0;
        smoothScroll.set(0f);
        hoverProgress.clear();
        applyFilter();
    }

    /** Back to the two-section root view that lists both trees. */
    private void navigateToRoot() {
        browsingIndividual = null;
        currentPath = "";
        scrollOffset = 0;
        smoothScroll.set(0f);
        hoverProgress.clear();
        applyFilter();
    }

    private void navigateUp() {
        if (browsingIndividual == null) return;
        String parent = StagePaths.parent(currentPath);
        // A tree root on its own — "global/" showing only its own section — is not a view
        // anything navigates into: entering a folder always starts from the two-section
        // root. Stopping there on the way back would make one step down cost two steps up,
        // in a state the user never asked for.
        if (currentPath.isEmpty() || parent.isEmpty()) {
            navigateToRoot();
            return;
        }
        currentPath = parent;
        scrollOffset = 0;
        smoothScroll.set(0f);
        hoverProgress.clear();
        applyFilter();
    }

    private boolean matchesFilter(String stageId, StageEntry entry, String query) {
        if (stageId.toLowerCase().contains(query))
            return true;
        if (entry != null && entry.getDisplayName().toLowerCase().contains(query))
            return true;
        return false;
    }

    private void updateMaxScroll() {
        int listHeight = this.height - HEADER_HEIGHT - LIST_PADDING - 40;
        int contentHeight = 0;
        if (showGlobalSection()) {
            contentHeight += SECTION_HEADER_HEIGHT
                    + (globalFolders.size() + filteredStageOrder.size()) * ENTRY_HEIGHT;
        }
        if (showIndividualSection()) {
            contentHeight += SECTION_HEADER_HEIGHT
                    + (individualFolders.size() + filteredIndividualStageOrder.size()) * ENTRY_HEIGHT;
        }
        maxScroll = Math.max(0, contentHeight - listHeight);
    }

    /** In a tree only that tree's section is drawn; in the root view and in search both are. */
    private boolean showGlobalSection() {
        return browsingIndividual == null || !browsingIndividual || !searchFilter.trim().isEmpty();
    }

    private boolean showIndividualSection() {
        if (!searchFilter.trim().isEmpty()) return !filteredIndividualStageOrder.isEmpty();
        if (browsingIndividual == null) {
            return !individualFolders.isEmpty() || !filteredIndividualStageOrder.isEmpty();
        }
        return browsingIndividual;
    }

    /**
     * Row offsets of both sections for one scroll value. {@link #render} and
     * {@link #mouseClicked} build one of these instead of repeating the arithmetic, so a
     * hidden section or a folder row shifts the stage rows by the same amount in both.
     */
    private record ListLayout(int globalHeaderY, int globalRowsY, int individualHeaderY, int individualRowsY) {}

    private ListLayout layout(int listTop, int scroll) {
        int y = listTop - scroll;
        int globalHeaderY = y;
        int globalRowsY = y + SECTION_HEADER_HEIGHT;
        if (showGlobalSection()) {
            y = globalRowsY + (globalFolders.size() + filteredStageOrder.size()) * ENTRY_HEIGHT;
        }
        return new ListLayout(globalHeaderY, globalRowsY, y, y + SECTION_HEADER_HEIGHT);
    }

    /** Screen y of row {@code index} inside a section — folder rows first, then stage rows. */
    private static int rowTop(int rowsY, int index) {
        return rowsY + index * ENTRY_HEIGHT;
    }

    /**
     * Draws one folder row. Same height, hover animation and accent treatment as a stage
     * row so the list reads as one thing; no lock button and no mode badge, because a
     * folder has neither.
     */
    private void drawFolderRow(GuiGraphics g, StageFolderTree.Folder folder, int hoverKey,
                               int entryTop, int listLeft, int listRight, int listTop, int listBottom,
                               int mouseX, int mouseY, int accentColor) {
        int entryBottom = entryTop + ENTRY_HEIGHT - 2;

        boolean hovered = mouseX >= listLeft && mouseX <= listRight
                && mouseY >= Math.max(entryTop, listTop) && mouseY <= Math.min(entryBottom, listBottom);

        float progress = Ease.outCubic(hoverProgress.computeIfAbsent(hoverKey, k -> new Anim())
                .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        // Like the stage rows, the fill starts white and only tints towards the section's
        // accent as the hover animation runs. Using the accent directly at rest painted
        // every global folder row permanently gold.
        int bgAlpha = (int) (0x20 + progress * 0x25);
        g.fill(listLeft, entryTop, listRight, entryBottom,
                (bgAlpha << 24) | tintTowards(accentColor, progress));
        if (progress > 0.01f) {
            g.fill(listLeft, entryTop, listLeft + 2, entryBottom,
                    (((int) (progress * 0xFF)) << 24) | (accentColor & 0xFFFFFF));
        }

        drawFolderIcon(g, listLeft + 5, entryTop + 7, accentColor);
        g.drawString(this.font, folder.name(), listLeft + 16, entryTop + 4,
                progress > 0.01f ? 0xFFFFFF : 0xEEEEEE, false);

        String info = Component.translatable("editor.historystages.folder.stage_count",
                folder.stageCount()).getString();
        int infoColor = (int) (0x88 + progress * 0x33);
        g.drawString(this.font, info, listLeft + 22, entryTop + 15,
                (0xFF << 24) | (infoColor << 16) | (infoColor << 8) | infoColor, false);
    }

    /** Blends white towards {@code accent} by {@code progress}, returning an RGB triple. */
    private static int tintTowards(int accent, float progress) {
        int r = (int) (0xFF + progress * (((accent >> 16) & 0xFF) - 0xFF));
        int g = (int) (0xFF + progress * (((accent >> 8) & 0xFF) - 0xFF));
        int b = (int) (0xFF + progress * ((accent & 0xFF) - 0xFF));
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Three stacked bars marking a folder row, drawn as rectangles rather than a glyph:
     * the row's counterpart on stage rows is an emoji, but a folder symbol outside the
     * font's coverage would degrade to a missing-glyph box, and this cannot.
     */
    private void drawFolderIcon(GuiGraphics g, int x, int y, int accentColor) {
        int color = 0xFF000000 | (accentColor & 0xFFFFFF);
        for (int i = 0; i < 3; i++) {
            int barY = y + i * 3;
            g.fill(x, barY, x + FOLDER_ICON_WIDTH, barY + 1, color);
        }
    }

    /** Label of one breadcrumb segment; the empty path is the tree root. */
    private String breadcrumbLabel(String path) {
        if (!path.isEmpty()) return StagePaths.name(path);
        return Component.translatable(Boolean.TRUE.equals(browsingIndividual)
                ? "editor.historystages.folder.root_individual"
                : "editor.historystages.folder.root_global").getString();
    }

    /** Total rendered width of the breadcrumb, used to size its background box. */
    private int breadcrumbWidth() {
        List<String> parts = StagePaths.breadcrumb(currentPath);
        int w = 0;
        for (int i = 0; i < parts.size(); i++) {
            w += this.font.width(breadcrumbLabel(parts.get(i)));
            if (i < parts.size() - 1) w += this.font.width(BREADCRUMB_SEPARATOR);
        }
        return w;
    }

    /**
     * Draws {@code global / stone / basalt} in place of the section header and records the
     * clickable x-range of each segment in {@link #breadcrumbHits}, so a click can jump
     * straight to an ancestor instead of pressing Back repeatedly.
     */
    private void drawBreadcrumb(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        breadcrumbHits.clear();
        List<String> parts = StagePaths.breadcrumb(currentPath);
        int cx = x;
        for (int i = 0; i < parts.size(); i++) {
            String path = parts.get(i);
            String label = breadcrumbLabel(path);
            int w = this.font.width(label);
            boolean last = i == parts.size() - 1;
            boolean hovered = !last && mouseX >= cx && mouseX <= cx + w && mouseY >= y && mouseY <= y + 10;
            g.drawString(this.font, label, cx, y, last ? 0xFFFFFF : (hovered ? 0xFFCC00 : 0x888888), false);
            if (!last) breadcrumbHits.add(new BreadcrumbHit(cx, cx + w, path));
            cx += w;
            if (!last) {
                g.drawString(this.font, BREADCRUMB_SEPARATOR, cx, y, 0x555555, false);
                cx += this.font.width(BREADCRUMB_SEPARATOR);
            }
        }
    }

    /**
     * Fingerprint of the whole folder layout, both trees. Counting folders is not enough:
     * a rename maps {@code {a, a/x}} to {@code {b, b/x}} — same size, and stage IDs never
     * change with it — so the list would keep drawing the old name and navigate into a folder
     * that is gone. {@code Set.hashCode()} and {@code Map.hashCode()} are element-based and
     * order-independent, so this catches renames and moves alike.
     */
    private int folderSignature() {
        int signature = StageManager.getFolders().hashCode() * 31
                + StageManager.getIndividualFolders().hashCode();
        signature = signature * 31 + StageManager.getStagePaths().hashCode();
        return signature * 31 + StageManager.getIndividualStagePaths().hashCode();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Refresh stage list if another admin changed definitions (broadcast via
        // SyncStageDefinitionsPacket)
        int currentCount = StageManager.getStages().size();
        int currentIndividualCount = StageManager.getIndividualStages().size();
        int currentFolderSignature = folderSignature();
        if (currentCount != lastKnownStageCount
                || currentIndividualCount != lastKnownIndividualCount
                || currentFolderSignature != lastKnownFolderSignature
                || !StageManager.getStages().keySet().containsAll(stageOrder)
                || !stageOrder.containsAll(StageManager.getStages().keySet())) {
            stageOrder = StageManager.getStageOrder();
            individualStageOrder = StageManager.getIndividualStageOrder();
            lastKnownStageCount = currentCount;
            lastKnownIndividualCount = currentIndividualCount;
            lastKnownFolderSignature = currentFolderSignature;
            // The browsed folder can disappear under us when another admin deletes it.
            // Any other change keeps the user where they are — a stage saved elsewhere
            // must not kick them out of the folder they are working in.
            if (browsingIndividual != null && !StageFolderTree.exists(browsingIndividual, currentPath)) {
                browsingIndividual = null;
                currentPath = "";
            }
            applyFilter();
        }

        // Smooth scroll
        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

        guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        // Search bar (left side, same row as title)
        int searchW = 120;
        int searchX = 10;
        guiGraphics.fill(searchX, 5, searchX + searchW, 23, 0x25FFFFFF);
        guiGraphics.fill(searchX, 23, searchX + searchW, 24, searchBox.isFocused() ? 0xFFFFCC00 : 0xFF555555);
        if (searchFilter.isEmpty() && !searchBox.isFocused()) {
            guiGraphics.drawString(this.font, Component.translatable("editor.historystages.search").getString(),
                    searchX + 4, 10, 0x888888, false);
        }

        guiGraphics.fill(10, HEADER_HEIGHT, this.width - 10, HEADER_HEIGHT + 1, 0xFF555555);

        int listTop = HEADER_HEIGHT + 5;
        int listBottom = this.height - 40;
        int listLeft = 20;
        int listRight = this.width - 20;

        guiGraphics.enableScissor(listLeft, listTop, listRight, listBottom);

        boolean overlayOpen = contextMenu != null && contextMenu.isVisible();
        int effectiveMouseX = overlayOpen ? -1 : mouseX;
        int effectiveMouseY = overlayOpen ? -1 : mouseY;

        Map<String, StageEntry> stages = StageManager.getStages();
        Map<String, StageEntry> individualStages = StageManager.getIndividualStages();
        // Single source of the row offsets \u2014 mouseClicked() builds the same record.
        ListLayout layout = layout(listTop, Math.round(smoothScroll.value()));
        boolean searching = !searchFilter.trim().isEmpty();
        boolean showBreadcrumb = browsingIndividual != null && !searching;
        breadcrumbY = -1;

        int currentHovered = -1;
        int currentHoveredStage = -1;

        // --- Global Stages Section Header ---
        // While a tree is browsed only that tree's section is drawn, so at most one of the
        // two headers ever carries the breadcrumb.
        int globalHeaderY = layout.globalHeaderY();
        if (showGlobalSection()
                && globalHeaderY + SECTION_HEADER_HEIGHT > listTop && globalHeaderY < listBottom) {
            guiGraphics.fill(listLeft, globalHeaderY + 8, listRight, globalHeaderY + 9, 0xFF555555);
            if (showBreadcrumb) {
                breadcrumbY = globalHeaderY + 4;
                int crumbX = listLeft + 5;
                guiGraphics.fill(crumbX - 2, globalHeaderY + 3, crumbX + breadcrumbWidth() + 2,
                        globalHeaderY + 15, 0xE0101010);
                drawBreadcrumb(guiGraphics, crumbX, breadcrumbY, effectiveMouseX, effectiveMouseY);
            } else {
                String globalLabel = "\u00A78Global Stages (" + filteredStageOrder.size() + ")";
                int glLabelW = this.font.width(globalLabel);
                int glLabelX = listLeft + 5;
                guiGraphics.fill(glLabelX - 2, globalHeaderY + 3, glLabelX + glLabelW + 2, globalHeaderY + 15, 0xE0101010);
                guiGraphics.drawString(this.font, globalLabel, glLabelX, globalHeaderY + 4, 0x888888, false);
            }
        }

        // --- Global Folders ---
        for (int i = 0; i < globalFolders.size(); i++) {
            int entryTop = rowTop(layout.globalRowsY(), i);
            if (entryTop + ENTRY_HEIGHT - 2 < listTop || entryTop > listBottom) continue;
            drawFolderRow(guiGraphics, globalFolders.get(i), GLOBAL_FOLDER_HOVER_KEY + i, entryTop,
                    listLeft, listRight, listTop, listBottom, effectiveMouseX, effectiveMouseY, 0xFFCC00);
        }

        // --- Global Stages ---
        for (int i = 0; i < filteredStageOrder.size(); i++) {
            String stageId = filteredStageOrder.get(i);
            StageEntry entry = stages.get(stageId);
            if (entry == null)
                continue;

            int entryTop = rowTop(layout.globalRowsY(), globalFolders.size() + i);
            int entryBottom = entryTop + ENTRY_HEIGHT - 2;

            if (entryBottom < listTop || entryTop > listBottom) {
                continue;
            }

            boolean unlocked = ClientStageCache.isStageUnlocked(stageId);
            String lockLabel = Component
                    .translatable(unlocked ? "editor.historystages.lock" : "editor.historystages.unlock").getString();
            int lockBtnW = Math.max(50, this.font.width(lockLabel) + 12);
            int lockBtnX = listRight - lockBtnW - 10;
            int lockBtnY = entryTop + 5;
            int lockBtnH = 16;
            boolean onLockBtn = effectiveMouseX >= lockBtnX && effectiveMouseX <= lockBtnX + lockBtnW
                    && effectiveMouseY >= lockBtnY && effectiveMouseY <= lockBtnY + lockBtnH;

            boolean hovered = effectiveMouseX >= listLeft && effectiveMouseX <= listRight
                    && effectiveMouseY >= Math.max(entryTop, listTop)
                    && effectiveMouseY <= Math.min(entryBottom, listBottom)
                    && !onLockBtn;

            if (hovered) {
                currentHovered = i;
                currentHoveredStage = i;
            }

            // Smooth hover animation (0.0 -> 1.0)
            float progress = Ease.outCubic(hoverProgress.computeIfAbsent(i, k -> new Anim())
                    .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

            // Animated background - subtle gold tint on hover
            int bgAlpha = (int) (0x20 + progress * 0x25);
            int bgG = (int) (0xFF + progress * (0xCC - 0xFF));
            int bgB = (int) (0xFF + progress * (0x00 - 0xFF));
            int bgColor = (bgAlpha << 24) | (0xFF << 16) | (bgG << 8) | bgB;
            guiGraphics.fill(listLeft, entryTop, listRight, entryBottom, bgColor);

            // Gold accent bar on left when hovered
            if (progress > 0.01f) {
                int accentAlpha = (int) (progress * 0xFF);
                guiGraphics.fill(listLeft, entryTop, listLeft + 2, entryBottom, (accentAlpha << 24) | 0xFFCC00);
            }

            // Lock/unlock icon
            String icon = unlocked ? "\u2714" : "\uD83D\uDD12";
            int iconColor = unlocked ? 0xFFCC00 : 0x888888;
            guiGraphics.drawString(this.font, icon, listLeft + 5, entryTop + 6, iconColor, false);

            // Mode badge (pill placed to the LEFT of the lock button, vertically centered)
            long remainingTicks = net.bananemdnsa.historystages.network.EditorDataCache.getTemporaryActiveTicks(stageId);
            int badgeWidth = modeBadgeWidth(entry, remainingTicks);
            int badgeY = entryTop + (ENTRY_HEIGHT - 12) / 2 - 1;
            int badgeX = lockBtnX - badgeWidth - 6;
            if (badgeWidth > 0) drawModeBadge(guiGraphics, entry, remainingTicks, badgeX, badgeY);

            // Temporary-stage unlock count ("used/max"), placed to the LEFT of the badge.
            String countText = temporaryCountText(entry, globalTemporaryCount(stageId));
            int countW = countText.isEmpty() ? 0 : this.font.width(countText) + 6;
            int countX = badgeX - countW;
            if (!countText.isEmpty()) {
                guiGraphics.drawString(this.font, countText, countX + 2, badgeY + 2, 0xFFAAAAAA, false);
            }

            // Stage name with marquee for long names. A search spans both trees, so the
            // folder is appended to make a hit locatable \u2014 before nameW is measured, so a
            // long name plus path still marquees instead of clipping.
            String folder = StageManager.getStageFolder(stageId, false);
            String displayText = entry.getDisplayName() + " \u00A77(" + stageId + ")";
            if (searching && !folder.isEmpty()) {
                displayText += " \u00A78" + folder + "/";
            }
            int nameColor = progress > 0.01f ? 0xFFFFFF : 0xEEEEEE;
            int nameX = listLeft + 16;
            int nameRightLimit = countW > 0 ? countX : ((badgeWidth > 0) ? badgeX : lockBtnX);
            int nameAvailW = nameRightLimit - nameX - 6;
            int nameW = this.font.width(displayText);

            if (nameW > nameAvailW && hovered && i == hoveredStageIndex) {
                long elapsed = System.currentTimeMillis() - stageHoverStartTime;
                if (elapsed > MARQUEE_DELAY_MS) {
                    float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                    int maxMarquee = nameW - nameAvailW + 10;
                    float cycle = (float) maxMarquee * 2;
                    float pos = scrollProg % cycle;
                    int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                    guiGraphics.enableScissor(nameX, entryTop, nameX + nameAvailW, entryBottom);
                    guiGraphics.drawString(this.font, displayText, nameX - scrollOff, entryTop + 4, nameColor, false);
                    guiGraphics.disableScissor();
                } else {
                    guiGraphics.drawString(this.font, displayText, nameX, entryTop + 4, nameColor, false);
                }
            } else {
                guiGraphics.drawString(this.font, displayText, nameX, entryTop + 4, nameColor, false);
            }

            // Item count info + dependency badge
            int itemCount = entry.getItemEntries().size() + entry.getTags().size() + entry.getMods().size()
                    + entry.getRecipes().size() + entry.getDimensions().size()
                    + entry.getEntities().getAttacklock().size() + entry.getEntities().getInteractionlock().size()
                    + entry.getEntities().getSpawnlock().size()
                    + entry.getStructures().size()
                    + entry.getBiomes().size();
            String info = itemCount + " entries";
            int infoColor = (int) (0x88 + progress * 0x33);
            guiGraphics.drawString(this.font, info, listLeft + 22, entryTop + 15,
                    (0xFF << 24) | (infoColor << 16) | (infoColor << 8) | infoColor, false);

            if (entry.hasDependencies()) drawDepBadge(guiGraphics, info, listLeft, entryTop + 15);

            // Lock/Unlock toggle button (right side) - bounds already calculated above
            boolean lockBtnHovered = onLockBtn && mouseY >= listTop && mouseY <= listBottom;

            int lockBg = lockBtnHovered ? 0x50FFCC00 : 0x25FFFFFF;
            guiGraphics.fill(lockBtnX, lockBtnY, lockBtnX + lockBtnW, lockBtnY + lockBtnH, lockBg);
            // Bottom accent (gold, like StyledButton)
            int lockAccent = lockBtnHovered ? 0xFFFFCC00 : 0x60FFCC00;
            guiGraphics.fill(lockBtnX, lockBtnY + lockBtnH - 1, lockBtnX + lockBtnW, lockBtnY + lockBtnH, lockAccent);

            int lockTextColor = lockBtnHovered ? 0xFFFFFF : 0xCCCCCC;
            int textW = this.font.width(lockLabel);
            guiGraphics.drawString(this.font, lockLabel, lockBtnX + (lockBtnW - textW) / 2, lockBtnY + 4, lockTextColor,
                    false);
        }

        // --- Individual Stages Section ---
        if (!showIndividualSection()) {
            pickerVisible = false;
            playerPicker.close();
        } else {
            int sectionY = layout.individualHeaderY();

            // The picker sticks inside the viewport while any part of the individual
            // section is on screen, so scrolling the header away does not take the
            // target selector with it.
            int sectionBottom = sectionY + SECTION_HEADER_HEIGHT
                    + (individualFolders.size() + filteredIndividualStageOrder.size()) * ENTRY_HEIGHT;
            pickerVisible = sectionBottom > listTop && sectionY < listBottom;
            int pickerX = listRight - playerPicker.getWidth();
            int pickerY = Math.max(listTop + 1,
                    Math.min(sectionY + 2, listBottom - PlayerPickerDropdown.BUTTON_HEIGHT - 1));
            if (pickerVisible) {
                playerPicker.setPosition(pickerX, pickerY);
            } else {
                playerPicker.close();
            }

            // Section header
            if (sectionY + SECTION_HEADER_HEIGHT > listTop && sectionY < listBottom) {
                guiGraphics.fill(listLeft, sectionY + 8, pickerX - 5, sectionY + 9, 0xFF555555);
                if (showBreadcrumb) {
                    breadcrumbY = sectionY + 4;
                    int crumbX = listLeft + 5;
                    guiGraphics.fill(crumbX - 2, sectionY + 3, crumbX + breadcrumbWidth() + 2,
                            sectionY + 15, 0xE0101010);
                    drawBreadcrumb(guiGraphics, crumbX, breadcrumbY, effectiveMouseX, effectiveMouseY);
                } else {
                    String sectionLabel = "\u00A78Individual Stages (" + filteredIndividualStageOrder.size() + ")";
                    int labelW = this.font.width(sectionLabel);
                    int labelX = listLeft + 5;
                    guiGraphics.fill(labelX - 2, sectionY + 3, labelX + labelW + 2, sectionY + 15, 0xE0101010);
                    guiGraphics.drawString(this.font, sectionLabel, labelX, sectionY + 4, 0x888888, false);
                }
            }

            int indY = layout.individualRowsY();

            // --- Individual Folders ---
            for (int i = 0; i < individualFolders.size(); i++) {
                int folderTop = rowTop(indY, i);
                if (folderTop + ENTRY_HEIGHT - 2 < listTop || folderTop > listBottom) continue;
                drawFolderRow(guiGraphics, individualFolders.get(i), INDIVIDUAL_FOLDER_HOVER_KEY + i, folderTop,
                        listLeft, listRight, listTop, listBottom, effectiveMouseX, effectiveMouseY, 0xBBBBBB);
            }

            for (int i = 0; i < filteredIndividualStageOrder.size(); i++) {
                String stageId = filteredIndividualStageOrder.get(i);
                StageEntry entry = individualStages.get(stageId);
                if (entry == null)
                    continue;

                int entryTop = rowTop(indY, individualFolders.size() + i);
                int entryBottom = entryTop + ENTRY_HEIGHT - 2;

                if (entryBottom < listTop || entryTop > listBottom)
                    continue;

                // Unique hover key for individual stages (offset to avoid collision with
                // global)
                int hoverKey = 10000 + i;

                // Lock button bounds are needed before the hover test so the row does not
                // light up while the cursor is on the button — same as the global rows.
                int state = individualState(stageId);
                String lockLabel = Component.translatable(
                        state == 2 ? "editor.historystages.lock" : "editor.historystages.unlock").getString();
                int lockBtnW = Math.max(50, this.font.width(lockLabel) + 12);
                int lockBtnX = listRight - lockBtnW - 10;
                int lockBtnH = 16;
                int lockBtnY = entryTop + 5;
                boolean onLockBtn = effectiveMouseX >= lockBtnX && effectiveMouseX <= lockBtnX + lockBtnW
                        && effectiveMouseY >= lockBtnY && effectiveMouseY <= lockBtnY + lockBtnH;

                boolean hovered = effectiveMouseX >= listLeft && effectiveMouseX <= listRight
                        && effectiveMouseY >= Math.max(entryTop, listTop)
                        && effectiveMouseY <= Math.min(entryBottom, listBottom)
                        && !onLockBtn;

                if (hovered) {
                    currentHovered = hoverKey;
                    currentHoveredStage = hoverKey;
                }

                float progress = Ease.outCubic(hoverProgress.computeIfAbsent(hoverKey, k -> new Anim())
                        .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

                // Animated background - subtle silver tint on hover
                int bgAlpha = (int) (0x20 + progress * 0x25);
                int bgG = (int) (0xFF + progress * (0xCC - 0xFF));
                int bgColor = (bgAlpha << 24) | (bgG << 16) | (bgG << 8) | 0xFF;
                guiGraphics.fill(listLeft, entryTop, listRight, entryBottom, bgColor);

                // Silver accent bar on left when hovered
                if (progress > 0.01f) {
                    int accentAlpha = (int) (progress * 0xFF);
                    guiGraphics.fill(listLeft, entryTop, listLeft + 2, entryBottom, (accentAlpha << 24) | 0xBBBBBB);
                }

                // Status icon for the picker target: locked, partially unlocked (@a only),
                // or unlocked.
                String stateIcon = state == 2 ? "\u2714" : (state == 1 ? "\u25C9" : "\uD83D\uDD12");
                int stateColor = state == 2 ? 0xFFCC00 : (state == 1 ? 0xFFAA55 : 0xBBBBBB);
                guiGraphics.drawString(this.font, stateIcon, listLeft + 5, entryTop + 6, stateColor, false);

                // Mode badge sits left of the lock button, same as on global rows. The
                // countdown follows the picked player; under "@a" there is none to show.
                long remainingTicks = individualTemporaryTicks(stageId);
                int badgeWidth = modeBadgeWidth(entry, remainingTicks);
                int badgeY = entryTop + (ENTRY_HEIGHT - 12) / 2 - 1;
                int badgeX = lockBtnX - badgeWidth - 6;
                if (badgeWidth > 0) drawModeBadge(guiGraphics, entry, remainingTicks, badgeX, badgeY);

                // Temporary "used/max" count, left of the mode badge \u2014 same pairing as on
                // the global rows, and likewise empty for every other mode.
                String countText = temporaryCountText(entry, individualTemporaryCount(stageId));
                int modeLeft = (badgeWidth > 0) ? badgeX : lockBtnX;
                int countW = countText.isEmpty() ? 0 : this.font.width(countText) + 6;
                int countX = modeLeft - countW;
                if (!countText.isEmpty()) {
                    guiGraphics.drawString(this.font, countText, countX + 2, badgeY + 2, 0xFFAAAAAA, false);
                }

                // Lose-on-death badge, left of the count. Individual stages only \u2014
                // the flag has no effect on global stages, so their rows never show it.
                int deathWidth = deathBadgeWidth(entry);
                int countLeft = countW > 0 ? countX : modeLeft;
                int deathX = countLeft - deathWidth - 6;
                if (deathWidth > 0) drawDeathBadge(guiGraphics, deathX, badgeY);

                // Stage name with marquee. Folder suffix on search hits, same as the
                // global rows and likewise measured as part of displayText.
                String folder = StageManager.getStageFolder(stageId, true);
                String displayText = entry.getDisplayName() + " \u00A78(" + stageId + ")";
                if (searching && !folder.isEmpty()) {
                    displayText += " \u00A78" + folder + "/";
                }
                int nameColor = progress > 0.01f ? 0xDDDDDD : 0xBBBBBB;
                int nameX = listLeft + 16;
                int nameRightLimit = (deathWidth > 0) ? deathX : countLeft;
                int nameAvailW = nameRightLimit - nameX - 6;
                int nameW = this.font.width(displayText);

                if (nameW > nameAvailW && hovered && hoverKey == hoveredStageIndex) {
                    long elapsed = System.currentTimeMillis() - stageHoverStartTime;
                    if (elapsed > MARQUEE_DELAY_MS) {
                        float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                        int maxMarquee = nameW - nameAvailW + 10;
                        float cycle = (float) maxMarquee * 2;
                        float pos = scrollProg % cycle;
                        int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                        guiGraphics.enableScissor(nameX, entryTop, nameX + nameAvailW, entryBottom);
                        guiGraphics.drawString(this.font, displayText, nameX - scrollOff, entryTop + 4, nameColor,
                                false);
                        guiGraphics.disableScissor();
                    } else {
                        guiGraphics.drawString(this.font, displayText, nameX, entryTop + 4, nameColor, false);
                    }
                } else {
                    guiGraphics.drawString(this.font, displayText, nameX, entryTop + 4, nameColor, false);
                }

                // Item count info + dependency badge
                int itemCount = entry.getItemEntries().size() + entry.getTags().size() + entry.getMods().size()
                        + entry.getDimensions().size()
                        + entry.getEntities().getAttacklock().size() + entry.getEntities().getInteractionlock().size()
                        + entry.getStructures().size()
                        + entry.getBiomes().size();
                String info = itemCount + " entries";
                int infoColor = (int) (0x88 + progress * 0x33);
                guiGraphics.drawString(this.font, info, listLeft + 22, entryTop + 15,
                        (0xFF << 24) | (infoColor << 16) | (infoColor << 8) | infoColor, false);

                // Individual stages carry dependencies just like global ones, so the marker
                // belongs on these rows too.
                if (entry.hasDependencies()) drawDepBadge(guiGraphics, info, listLeft, entryTop + 15);

                // Lock/Unlock toggle button, mirroring the global rows.
                boolean lockBtnHovered = onLockBtn && mouseY >= listTop && mouseY <= listBottom;
                int lockBg = lockBtnHovered ? 0x50FFCC00 : 0x25FFFFFF;
                guiGraphics.fill(lockBtnX, lockBtnY, lockBtnX + lockBtnW, lockBtnY + lockBtnH, lockBg);
                int lockAccent = lockBtnHovered ? 0xFFFFCC00 : 0x60FFCC00;
                guiGraphics.fill(lockBtnX, lockBtnY + lockBtnH - 1, lockBtnX + lockBtnW, lockBtnY + lockBtnH, lockAccent);
                int lockTextColor = lockBtnHovered ? 0xFFFFFF : 0xCCCCCC;
                int lockTextW = this.font.width(lockLabel);
                guiGraphics.drawString(this.font, lockLabel, lockBtnX + (lockBtnW - lockTextW) / 2,
                        lockBtnY + 4, lockTextColor, false);
            }
        }

        lastHoveredIndex = currentHovered;

        // Update marquee tracking
        if (currentHoveredStage != hoveredStageIndex) {
            hoveredStageIndex = currentHoveredStage;
            stageHoverStartTime = System.currentTimeMillis();
        }

        guiGraphics.disableScissor();

        // Drawn after the list scissor so the picker is never clipped; its y is already
        // clamped into the viewport.
        if (pickerVisible) {
            playerPicker.renderButton(guiGraphics, this.font, effectiveMouseX, effectiveMouseY);
        }

        if (maxScroll > 0) {
            int scrollBarHeight = Math.max(20, (int) ((float) (listBottom - listTop)
                    / (maxScroll + (listBottom - listTop)) * (listBottom - listTop)));
            int scrollBarY = listTop
                    + (int) ((float) scrollOffset / maxScroll * (listBottom - listTop - scrollBarHeight));
            guiGraphics.fill(listRight + 2, scrollBarY, listRight + 5, scrollBarY + scrollBarHeight, 0x80FFFFFF);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        contextMenu.render(guiGraphics, this.font, mouseX, mouseY);
        guiGraphics.pose().popPose();

        if (pickerVisible) {
            playerPicker.renderPopup(guiGraphics, this.font, effectiveMouseX, effectiveMouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // The context menu renders above the picker, so it also gets the click first —
        // otherwise the picker would swallow clicks aimed at a menu entry drawn on top of it.
        if (pickerVisible && !contextMenu.isVisible() && playerPicker.mouseClicked(mouseX, mouseY)) {
            searchBox.setFocused(false);
            return true;
        }

        // Unfocus search box when clicking outside it
        if (searchBox.isFocused() && !(mouseX >= 10 && mouseX <= 130 && mouseY >= 5 && mouseY <= 24)) {
            searchBox.setFocused(false);
        }

        if (contextMenu.isVisible()) {
            contextMenu.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (super.mouseClicked(mouseX, mouseY, button))
            return true;

        int listTop = HEADER_HEIGHT + 5;
        int listBottom = this.height - 40;
        int listLeft = 20;
        int listRight = this.width - 20;

        if (maxScroll > 0 && mouseX >= listRight + 1 && mouseX <= listRight + 6
                && mouseY >= listTop && mouseY <= listBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY, listTop, listBottom);
            return true;
        }

        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom)
            return false;

        Map<String, StageEntry> stages = StageManager.getStages();
        Map<String, StageEntry> individualStages = StageManager.getIndividualStages();
        // Same record render() uses, and fed with the same scroll value: smoothScroll lerps
        // towards scrollOffset over several frames, so hit-testing against scrollOffset would
        // aim at rows up to a third of a row away from where they are actually drawn.
        ListLayout layout = layout(listTop, Math.round(smoothScroll.value()));

        // Breadcrumb segments jump straight to an ancestor.
        if (button == 0 && browsingIndividual != null && breadcrumbY >= 0
                && mouseY >= breadcrumbY && mouseY <= breadcrumbY + 10) {
            for (BreadcrumbHit hit : breadcrumbHits) {
                if (mouseX >= hit.x1() && mouseX <= hit.x2()) {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    // The leading segment is the tree itself, and a tree on its own is not
                    // a view we navigate into — same reasoning as navigateUp().
                    if (hit.path().isEmpty()) {
                        navigateToRoot();
                    } else {
                        navigateInto(browsingIndividual, hit.path());
                    }
                    return true;
                }
            }
        }

        // Global folders — drawn above the global stage rows.
        for (int i = 0; i < globalFolders.size(); i++) {
            int folderTop = rowTop(layout.globalRowsY(), i);
            if (mouseY >= folderTop && mouseY <= folderTop + ENTRY_HEIGHT - 2) {
                return folderRowClicked(globalFolders.get(i), false, button, mouseX, mouseY);
            }
        }

        // Global stages
        for (int i = 0; i < filteredStageOrder.size(); i++) {
            String stageId = filteredStageOrder.get(i);
            StageEntry entry = stages.get(stageId);
            if (entry == null)
                continue;

            int entryTop = rowTop(layout.globalRowsY(), globalFolders.size() + i);
            int entryBottom = entryTop + ENTRY_HEIGHT - 2;

            if (mouseY >= entryTop && mouseY <= entryBottom) {
                // Check lock/unlock button click
                boolean unlocked = ClientStageCache.isStageUnlocked(stageId);
                String lockLabel = Component
                        .translatable(unlocked ? "editor.historystages.lock" : "editor.historystages.unlock")
                        .getString();
                int lockBtnW = Math.max(50, this.font.width(lockLabel) + 12);
                int lockBtnX = listRight - lockBtnW - 10;
                int lockBtnY = entryTop + 5;
                if (button == 0 && mouseX >= lockBtnX && mouseX <= lockBtnX + lockBtnW
                        && mouseY >= lockBtnY && mouseY <= lockBtnY + 16) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    PacketHandler.sendToServer(new ToggleStageLockPacket(stageId, !unlocked));
                    return true;
                }

                // Right-click context menu
                if (button == 1) {
                    contextMenu = new ContextMenu();
                    contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(), () -> {
                        this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry, false));
                    });
                    contextMenu.addEntry(Component.translatable("editor.historystages.duplicate").getString(), () -> {
                        openStageIdInputDialog(stageId, false);
                    });
                    contextMenu.addEntry(Component.translatable("editor.historystages.delete").getString(), () -> {
                        Screen self = this;
                        this.minecraft.setScreen(new ConfirmDialog(this,
                                Component.translatable("editor.historystages.confirm_delete_title"),
                                Component.translatable("editor.historystages.confirm_delete", stageId),
                                () -> {
                                    PacketHandler.sendToServer(new DeleteStagePacket(stageId, false));
                                    stageOrder.remove(stageId);
                                    applyFilter();
                                    Minecraft.getInstance().setScreen(self);
                                }));
                    });
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    contextMenu.show((int) mouseX, (int) mouseY, this.font);
                    return true;
                }

                // Left-click on stage entry -> open detail editor
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry, false));
                return true;
            }
        }

        // Individual stages
        if (showIndividualSection()) {
            int indY = layout.individualRowsY();

            // Individual folders — drawn above the individual stage rows.
            for (int i = 0; i < individualFolders.size(); i++) {
                int folderTop = rowTop(indY, i);
                if (mouseY >= folderTop && mouseY <= folderTop + ENTRY_HEIGHT - 2) {
                    return folderRowClicked(individualFolders.get(i), true, button, mouseX, mouseY);
                }
            }

            for (int i = 0; i < filteredIndividualStageOrder.size(); i++) {
                String stageId = filteredIndividualStageOrder.get(i);
                StageEntry entry = individualStages.get(stageId);
                if (entry == null)
                    continue;

                int entryTop = rowTop(indY, individualFolders.size() + i);
                int entryBottom = entryTop + ENTRY_HEIGHT - 2;

                if (mouseY >= entryTop && mouseY <= entryBottom) {
                    int state = individualState(stageId);
                    String lockLabelClick = Component.translatable(
                            state == 2 ? "editor.historystages.lock" : "editor.historystages.unlock").getString();
                    int lockBtnWClick = Math.max(50, this.font.width(lockLabelClick) + 12);
                    int lockBtnX = listRight - lockBtnWClick - 10;
                    int lockBtnY = entryTop + 5;
                    if (button == 0 && mouseX >= lockBtnX && mouseX <= lockBtnX + lockBtnWClick
                            && mouseY >= lockBtnY && mouseY <= lockBtnY + 16) {
                        Minecraft.getInstance().getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        PacketHandler.sendToServer(new ToggleIndividualStageLockPacket(
                                stageId, Optional.ofNullable(playerPicker.getSelected()), state != 2));
                        return true;
                    }

                    if (button == 1) {
                        contextMenu = new ContextMenu();
                        contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(), () -> {
                            this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry, true));
                        });
                        contextMenu.addEntry(Component.translatable("editor.historystages.duplicate").getString(),
                                () -> {
                                    openStageIdInputDialog(stageId, true);
                                });
                        contextMenu.addEntry(Component.translatable("editor.historystages.delete").getString(), () -> {
                            Screen self = this;
                            this.minecraft.setScreen(new ConfirmDialog(this,
                                    Component.translatable("editor.historystages.confirm_delete_title"),
                                    Component.translatable("editor.historystages.confirm_delete", stageId),
                                    () -> {
                                        PacketHandler.sendToServer(new DeleteStagePacket(stageId, true));
                                        individualStageOrder.remove(stageId);
                                        applyFilter();
                                        Minecraft.getInstance().setScreen(self);
                                    }));
                        });
                        Minecraft.getInstance().getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        contextMenu.show((int) mouseX, (int) mouseY, this.font);
                        return true;
                    }

                    // Left-click -> open detail editor (individual mode)
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry, true));
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * A click landed on a folder row: left enters the folder, right opens the rename/delete
     * menu. Middle clicks are swallowed so they cannot fall through to a stage row.
     *
     * <p>Neither action refreshes the list by hand — the server reloads and broadcasts, and
     * the folder-signature check in {@link #render} picks the change up on the next frame.
     */
    private boolean folderRowClicked(StageFolderTree.Folder folder, boolean individual,
                                     int button, double mouseX, double mouseY) {
        if (button == 0) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            navigateInto(individual, folder.path());
            return true;
        }
        if (button == 1) {
            contextMenu = new ContextMenu();
            contextMenu.addEntry(Component.translatable("editor.historystages.folder.rename").getString(), () -> {
                this.minecraft.setScreen(new FolderNameScreen(this,
                        Component.translatable("editor.historystages.folder.rename_title"),
                        individual, StagePaths.parent(folder.path()), folder.name(),
                        newName -> PacketHandler.sendToServer(
                                new RenameFolderPacket(individual, folder.path(), newName))));
            });
            contextMenu.addEntry(Component.translatable("editor.historystages.delete").getString(), () -> {
                Screen self = this;
                this.minecraft.setScreen(new ConfirmDialog(this,
                        Component.translatable("editor.historystages.folder.confirm_delete_title"),
                        Component.translatable("editor.historystages.folder.confirm_delete", folder.name()),
                        () -> {
                            PacketHandler.sendToServer(new DeleteFolderPacket(individual, folder.path()));
                            Minecraft.getInstance().setScreen(self);
                        }));
            });
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            contextMenu.show((int) mouseX, (int) mouseY, this.font);
            return true;
        }
        return true;
    }

    /**
     * Opens the create/duplicate dialog for the position the user is standing in. Inside a
     * tree the tree is decided by that position, so the dialog hides its tree selector.
     *
     * <p>A duplicate is the exception: search spans both trees no matter where the user
     * stands, so the tree has to come from the clicked row — forcing it to the browsed tree
     * would look the source up in the wrong map and silently create nothing. The copy is
     * placed next to its source rather than in the browsed folder for the same reason.
     */
    private void openStageIdInputDialog(String duplicateFromId, boolean individual) {
        if (duplicateFromId != null) {
            // A duplicate inherits its tree from the row that was clicked — search results
            // span both trees, so neither the browsed tree nor a selector may override it.
            // Marking the tree fixed hides that selector; the kind selector is already
            // hidden for duplicates, so the dialog shows none. The copy lands next to
            // its source.
            this.minecraft.setScreen(new StageIdInputScreen(this, duplicateFromId, individual,
                    true, StageManager.getStageFolder(duplicateFromId, individual)));
            return;
        }
        boolean treeFixed = browsingIndividual != null;
        this.minecraft.setScreen(new StageIdInputScreen(this, duplicateFromId,
                treeFixed ? browsingIndividual : individual, treeFixed, currentPath));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (pickerVisible && playerPicker.mouseScrolled(mouseX, mouseY, delta)) return true;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 10));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            int listTop = HEADER_HEIGHT + 5;
            int listBottom = this.height - 40;
            updateScrollFromMouse(mouseY, listTop, listBottom);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
        int listH = listBottom - listTop;
        int totalH = maxScroll + listH;
        int thumbHeight = Math.max(20, (int) ((float) listH / totalH * listH));
        float usableH = listH - thumbHeight;
        if (usableH > 0) {
            float ratio = (float) (mouseY - listTop - thumbHeight / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollOffset = Math.round(ratio * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (playerPicker != null && playerPicker.isExpanded() && playerPicker.keyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (playerPicker != null && playerPicker.charTyped(codePoint)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    /**
     * Lock state of an individual stage for the current picker target:
     * 0 = target does not have it, 1 = some but not all online players have it
     * (only reachable under "@a"), 2 = the target — or every online player — has it.
     */
    private int individualState(String stageId) {
        UUID selected = playerPicker.getSelected();
        if (selected != null) {
            return ClientPlayerStageCache.hasStage(selected, stageId) ? 2 : 0;
        }
        var connection = this.minecraft.getConnection();
        if (connection == null) return 0;
        int total = 0;
        int have = 0;
        for (var info : connection.getOnlinePlayers()) {
            total++;
            if (ClientPlayerStageCache.hasStage(info.getProfile().getId(), stageId)) have++;
        }
        if (total == 0 || have == 0) return 0;
        return have == total ? 2 : 1;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static final String MODE_BADGE_WARN = "⚠";

    /**
     * Returns the label shown inside the pill for {@code entry}'s mode. For an unlocked
     * TEMPORARY stage the live countdown until auto re-lock is appended (e.g.
     * "Temporary (2) · 4m 32s").
     *
     * @param remainingTicks ticks until re-lock, or -1 when the stage is not running or
     *                       its live state is unknown. Resolved by the caller, because
     *                       global and individual rows read it from different places.
     */
    private String modeBadgeLabel(StageEntry entry, long remainingTicks) {
        StageMode mode = entry.getMode();
        String name = switch (mode) {
            case AUTO -> Component.translatable("editor.historystages.mode.auto").getString();
            case TEMPORARY -> Component.translatable("editor.historystages.mode.temporary").getString();
            case EXTERNAL -> Component.translatable("editor.historystages.mode.external").getString();
            default -> Component.translatable("editor.historystages.mode.external").getString();
        };
        // AUTO and TEMPORARY both use auto-triggers — show the configured count.
        if (mode.usesAutoTrigger()) {
            AutoTrigger at = entry.getAutoTrigger();
            int count = at == null ? 0 : at.getTriggers().size();
            String label = name + " (" + count + ")";
            if (mode == StageMode.TEMPORARY && remainingTicks > 0) {
                label += " §6· " + formatTicksShort(remainingTicks);
            }
            return label;
        }
        return name;
    }

    /** Compact tick formatter for the badge countdown: "1d 2h", "4m 32s", "12s". */
    private static String formatTicksShort(long ticks) {
        long totalSec = ticks / 20;
        long d = totalSec / 86400;
        long h = (totalSec % 86400) / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static boolean isAutoEmpty(StageEntry entry) {
        AutoTrigger at = entry.getAutoTrigger();
        return at == null || at.isEmpty();
    }

    /**
     * Returns the "used/max" unlock-count label for a temporary stage (e.g. "§72/5"
     * or "§72/∞"), or an empty string for any other mode.
     *
     * @param used times the stage has been unlocked, or -1 when that is unknown — which
     *             hides the label entirely rather than claiming a count of zero. Resolved
     *             by the caller; see {@link #modeBadgeLabel} for why.
     */
    private String temporaryCountText(StageEntry entry, int used) {
        if (entry.getMode() != StageMode.TEMPORARY || used < 0) return "";
        var cfg = entry.getTemporary();
        String max = (cfg == null || cfg.isUnlimited()) ? "∞" : String.valueOf(cfg.getMaxTriggers());
        return "§7" + used + "/" + max;
    }

    /**
     * Live unlock count for a global temporary stage row.
     *
     * @see #individualTemporaryCount
     */
    private int globalTemporaryCount(String stageId) {
        return net.bananemdnsa.historystages.network.EditorDataCache.getTemporaryCount(stageId);
    }

    /**
     * Live unlock count for an individual temporary stage row, or -1 while the picker is
     * on "@a". Per-player counts differ between players, so there is no honest single
     * number to show for "everyone" — the row drops the label instead of inventing one.
     */
    private int individualTemporaryCount(String stageId) {
        UUID target = playerPicker.getSelected();
        return target == null ? -1 : net.bananemdnsa.historystages.network.EditorDataCache
                .getIndividualTemporaryCount(target, stageId);
    }

    /** Remaining ticks until re-lock for an individual row, or -1 under "@a". */
    private long individualTemporaryTicks(String stageId) {
        UUID target = playerPicker.getSelected();
        return target == null ? -1L : net.bananemdnsa.historystages.network.EditorDataCache
                .getIndividualTemporaryActiveTicks(target, stageId);
    }

    /**
     * Returns the rendered width of the mode badge for {@code entry}, or {@code 0} for
     * {@link StageMode#DEFAULT} (no badge). Must be given the same {@code remainingTicks}
     * the badge is drawn with, or the reserved width and the label drift apart.
     */
    private int modeBadgeWidth(StageEntry entry, long remainingTicks) {
        StageMode mode = entry.getMode();
        if (mode == StageMode.DEFAULT) return 0;
        int w = this.font.width(modeBadgeLabel(entry, remainingTicks)) + 8;
        if (mode.usesAutoTrigger() && isAutoEmpty(entry)) {
            w += 3 + this.font.width(MODE_BADGE_WARN);
        }
        return w;
    }

    /**
     * Draws the monochrome pill badge for {@link StageMode#AUTO} / {@link StageMode#EXTERNAL}
     * at (x, y). No-op for {@link StageMode#DEFAULT}. AUTO badges include the configured
     * trigger count (e.g. "Auto (3)"); empty AUTO badges additionally show a warn indicator.
     */
    private void drawModeBadge(GuiGraphics g, StageEntry entry, long remainingTicks, int x, int y) {
        StageMode mode = entry.getMode();
        if (mode == StageMode.DEFAULT) return;
        String label = modeBadgeLabel(entry, remainingTicks);
        int textW = this.font.width(label);
        int pillW = textW + 8;
        int pillH = 12;
        g.fill(x, y, x + pillW, y + pillH, 0x20FFFFFF);
        g.fill(x, y + pillH - 1, x + pillW, y + pillH, 0x30FFFFFF);
        g.drawString(this.font, label, x + 4, y + 2, 0xFFAAAAAA, false);
        if (mode.usesAutoTrigger() && isAutoEmpty(entry)) {
            g.drawString(this.font, MODE_BADGE_WARN, x + pillW + 3, y + 2, 0xFFAA55, false);
        }
    }

    /**
     * Marks a row whose stage is gated behind other stages. Unlike the mode and death
     * badges this is plain text in the info line, not a pill in the right-hand column —
     * it is a property of the stage's definition, not of its current state.
     *
     * @param info the info line it is placed behind, needed for its width
     */
    private void drawDepBadge(GuiGraphics g, String info, int contentLeft, int y) {
        String label = DEP_BADGE_PREFIX
                + Component.translatable("editor.historystages.badge.dependencies").getString();
        g.drawString(this.font, label, contentLeft + 22 + this.font.width(info) + 6, y,
                DEP_BADGE_COLOR, false);
    }

    /** Rendered width of the lose-on-death pill, or 0 when the stage isn't flagged. */
    private int deathBadgeWidth(StageEntry entry) {
        if (!entry.isLoseOnDeath()) return 0;
        return this.font.width(deathBadgeLabel()) + 8;
    }

    private String deathBadgeLabel() {
        return Component.translatable("editor.historystages.badge.lose_on_death").getString();
    }

    /**
     * Draws the lose-on-death pill at (x, y). Same shape as the mode badge but
     * red-tinted, so a stage that can be taken away reads differently at a glance.
     */
    private void drawDeathBadge(GuiGraphics g, int x, int y) {
        String label = deathBadgeLabel();
        int pillW = this.font.width(label) + 8;
        int pillH = 12;
        g.fill(x, y, x + pillW, y + pillH, 0x20FF5555);
        g.fill(x, y + pillH - 1, x + pillW, y + pillH, 0x40FF5555);
        g.drawString(this.font, label, x + 4, y + 2, 0xFFFF7777, false);
    }

    /**
     * Dialog screen that asks for a name before creating a stage, duplicating a stage, or
     * creating a folder.
     *
     * <p>Tree (global/individual) and kind (stage/folder) are independent choices — a folder
     * created in the root view still has to say which tree it belongs to — so they get one
     * selector each rather than a single combined one.
     */
    static class StageIdInputScreen extends AbstractInputScreen {
        private final StageOverviewScreen parent;
        private final String duplicateFromId;
        private boolean individual;
        /** Folder the new stage/folder is created in; {@code ""} is the tree root. */
        private final String targetFolder;
        /** True while standing inside a tree: the tree is decided by position, not by the user. */
        private final boolean treeFixed;
        /** What is being created — a stage or a folder. */
        private boolean creatingFolder = false;
        /** Typed value carried across the widget rebuild that a kind switch triggers. */
        private String pendingName = "";
        /**
         * Prefilled from the source when duplicating; otherwise left for the user to type. Also
         * carried across a kind switch, which the folder kind has no field for.
         */
        private String pendingDisplayName = "";

        // Dropdown state
        private boolean treeDropdownOpen = false;
        private boolean kindDropdownOpen = false;
        private int treeDropdownX, kindDropdownX, dropdownY;

        private static final int DROPDOWN_W = 80;
        private static final int DROPDOWN_H = 16;
        private static final int OPTION_H = 16;
        /** Horizontal gap between the two selectors. */
        private static final int DROPDOWN_GAP = 6;
        /** Margin of a dropdown against the dialog's edge, on the title row. */
        private static final int TITLE_ROW_INSET_X = 8;
        /** Vertical inset that centres the 16px button in the 20px title row. */
        private static final int TITLE_ROW_INSET_Y = 2;
        /** Gap between the dropdown button and the popup below it. */
        private static final int POPUP_OFFSET_Y = 18;

        private static final int TREE_GLOBAL_COLOR = 0xFFCC00;
        private static final int TREE_INDIVIDUAL_COLOR = 0xBBBBBB;
        /** Both kind options share one accent — selection is carried by the bar, not the hue. */
        private static final int KIND_COLOR = 0xFFCC00;

        protected StageIdInputScreen(StageOverviewScreen parent, String duplicateFromId,
                                     boolean individual, boolean treeFixed, String targetFolder) {
            super(parent, Component.translatable("editor.historystages.new_stage"));
            this.parent = parent;
            this.duplicateFromId = duplicateFromId;
            this.individual = individual;
            this.treeFixed = treeFixed;
            this.targetFolder = targetFolder;
            // A duplicate starts out as a copy of the source, so its name is the sensible
            // default here — the user only has to touch it when the copy should differ.
            StageEntry source = duplicateFromId == null ? null
                    : (individual ? StageManager.getIndividualStages() : StageManager.getStages())
                        .get(duplicateFromId);
            if (source != null) this.pendingDisplayName = source.getDisplayName();
        }

        /** The tree selector is pointless inside a tree — position already decided it. */
        private boolean showTreeDropdown() { return !treeFixed; }

        /** Duplicating a stage is always a stage, so the kind selector is hidden then. */
        private boolean showKindDropdown() { return duplicateFromId == null; }

        @Override
        protected int dialogWidth() { return 300; }

        /** The selectors occupy the right of the title row, so the headline keeps left. */
        @Override
        protected boolean titleCentered() { return false; }

        /** The kind selector flips what this dialog creates, so the headline follows it. */
        @Override
        protected Component titleText() {
            return Component.translatable(creatingFolder
                    ? "editor.historystages.new_folder"
                    : "editor.historystages.new_stage");
        }

        /** The stage list stays visible behind the dim, as it did before the dialog refactor. */
        @Override
        protected boolean renderParentBehind() { return true; }

        @Override
        protected Component confirmLabel() {
            return Component.translatable(duplicateFromId != null
                    ? "editor.historystages.duplicate" : "editor.historystages.confirm");
        }

        @Override
        protected List<InputField> fields() {
            InputField name = InputField.text("id")
                    .label(Component.translatable(creatingFolder
                            ? "editor.historystages.folder.name"
                            : "editor.historystages.field.stage_id"))
                    .maxLength(64)
                    .regex("[a-zA-Z0-9_\\-]*")
                    .initial(pendingName)
                    .validator(this::checkId);
            if (creatingFolder) return List.of(name);

            // Optional here on purpose: the detail screen still refuses to save an empty
            // display name, so leaving it blank costs nothing but a later stop there.
            return List.of(name, InputField.text("display_name")
                    .label(Component.translatable("editor.historystages.field.display_name"))
                    .maxLength(128)
                    .initial(pendingDisplayName));
        }

        /**
         * Emptiness, charset and collision checks, in the order the user is likely to hit them.
         * A folder collides only with its siblings, while a stage ID is the file name and must
         * therefore be unique across the whole tree, not just in this folder.
         *
         * <p>The charset check goes through {@link StagePaths#isValidSegment} rather than a
         * literal regex, so a name the loader would ignore — anything starting with {@code _} —
         * is refused here instead of being created and then dropped on the next reload.
         */
        private Component checkId(String id) {
            if (id.isEmpty()) return Component.translatable("editor.historystages.id_empty");
            if (!StagePaths.isValidSegment(id)) return Component.translatable("editor.historystages.id_invalid");
            if (creatingFolder) {
                if (StageFolderTree.exists(individual, StagePaths.join(targetFolder, id))) {
                    return Component.translatable("editor.historystages.folder.name_exists");
                }
                return null;
            }
            if (StageManager.getStages().containsKey(id) || StageManager.getIndividualStages().containsKey(id)) {
                return Component.translatable("editor.historystages.id_exists");
            }
            return null;
        }

        /**
         * The dropdown sits on the title row rather than in the content column, so it reserves
         * no vertical space of its own.
         */
        @Override
        protected int extraContentHeight() { return 0; }

        /**
         * Places both selectors on the title row. The tree owns the top-right slot; the kind
         * selector sits left of it, or takes the right slot itself when the tree is fixed —
         * a second control the user cannot change is worse than none.
         *
         * <p>Called from render and from the click handler, so hit-testing can never run
         * against coordinates an earlier frame happened to leave behind.
         */
        private void layoutDropdowns() {
            dropdownY = boxY + TITLE_ROW_INSET_Y;
            treeDropdownX = boxX + boxW - DROPDOWN_W - TITLE_ROW_INSET_X;
            // Both selectors sit side by side at the right edge; the title gives up the
            // centre for them and is drawn left-aligned instead (see titleCentered()).
            kindDropdownX = showTreeDropdown()
                    ? treeDropdownX - DROPDOWN_W - DROPDOWN_GAP
                    : treeDropdownX;
        }

        /** Hit test for a dropdown-width box at (slotX, slotY) of height {@code h}. */
        private boolean inSlot(double mx, double my, int slotX, int slotY, int h) {
            return mx >= slotX && mx <= slotX + DROPDOWN_W && my >= slotY && my < slotY + h;
        }

        /** Index of the popup option under the cursor for a dropdown at {@code x}, or -1. */
        private int optionAt(double mx, double my, int x) {
            int optY = dropdownY + POPUP_OFFSET_Y;
            for (int i = 0; i < 2; i++) {
                if (inSlot(mx, my, x, optY + OPTION_H * i, OPTION_H)) return i;
            }
            return -1;
        }

        private static void playClick() {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        private Component[] treeLabels() {
            return new Component[]{
                    Component.translatable("editor.historystages.stage_type.global"),
                    Component.translatable("editor.historystages.stage_type.individual")};
        }

        private Component[] kindLabels() {
            return new Component[]{
                    Component.translatable("editor.historystages.stage"),
                    Component.translatable("editor.historystages.folder")};
        }

        /**
         * Draws one two-option dropdown at {@code x} on the title row. Both selectors go
         * through this, so they cannot drift apart in geometry or hover treatment.
         *
         * @param colors   accent per option, used for the selected bar, the selected label and
         *                 the hover tint
         * @param selected index of the option currently in force
         */
        private void drawDropdown(GuiGraphics g, int x, boolean open, int mouseX, int mouseY,
                                  Component[] labels, int[] colors, int selected) {
            int color = colors[selected];
            boolean hovered = inSlot(mouseX, mouseY, x, dropdownY, DROPDOWN_H);

            // Dropdown button
            g.fill(x, dropdownY, x + DROPDOWN_W, dropdownY + DROPDOWN_H, hovered ? 0x40FFFFFF : 0x25FFFFFF);
            g.fill(x, dropdownY + DROPDOWN_H - 2, x + DROPDOWN_W, dropdownY + DROPDOWN_H,
                    hovered ? (color | 0xFF000000) : 0x60FFFFFF);
            g.drawString(this.font, labels[selected], x + 4, dropdownY + 4, color, false);
            // Arrow indicator
            g.drawString(this.font, open ? "▲" : "▼", x + DROPDOWN_W - 10, dropdownY + 4, 0x999999, false);

            if (!open) return;

            // The popup is an overlay: it overflows extraContentHeight() and must beat both the
            // error line and the widgets drawn after renderContent, hence the z translate.
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            int optY = dropdownY + POPUP_OFFSET_Y;
            int popupH = OPTION_H * labels.length;

            // Background
            g.fill(x - 1, optY - 1, x + DROPDOWN_W + 1, optY + popupH + 1, 0xFF333333);
            g.fill(x, optY, x + DROPDOWN_W, optY + popupH, 0xFF1A1A1A);

            for (int i = 0; i < labels.length; i++) {
                int oy = optY + OPTION_H * i;
                boolean optHovered = inSlot(mouseX, mouseY, x, oy, OPTION_H);
                if (optHovered) g.fill(x, oy, x + DROPDOWN_W, oy + OPTION_H, 0x30000000 | colors[i]);
                if (i == selected) g.fill(x, oy, x + 2, oy + OPTION_H, 0xFF000000 | colors[i]);
                g.drawString(this.font, labels[i], x + 6, oy + 4,
                        optHovered ? 0xFFFFFF : (i == selected ? colors[i] : 0xAAAAAA), false);
            }

            g.pose().popPose();
        }

        @Override
        protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            // Deliberately ignores the content column and anchors to the dialog's top-right, on
            // the title row, where this dropdown lived before the refactor.
            layoutDropdowns();

            if (showTreeDropdown()) {
                drawDropdown(g, treeDropdownX, treeDropdownOpen, mouseX, mouseY, treeLabels(),
                        new int[]{TREE_GLOBAL_COLOR, TREE_INDIVIDUAL_COLOR}, individual ? 1 : 0);
            }
            if (showKindDropdown()) {
                drawDropdown(g, kindDropdownX, kindDropdownOpen, mouseX, mouseY, kindLabels(),
                        new int[]{KIND_COLOR, KIND_COLOR}, creatingFolder ? 1 : 0);
            }
        }

        @Override
        protected boolean extraContentMouseClicked(double mx, double my, int button) {
            if (button != 0) return false;
            layoutDropdowns();

            // Options first: while open, the popup swallows every left click.
            if (treeDropdownOpen) {
                int picked = optionAt(mx, my, treeDropdownX);
                if (picked >= 0) { individual = picked == 1; playClick(); }
                treeDropdownOpen = false; // a click outside the popup just closes it
                return true;
            }
            if (kindDropdownOpen) {
                int picked = optionAt(mx, my, kindDropdownX);
                kindDropdownOpen = false;
                if (picked >= 0 && (picked == 1) != creatingFolder) {
                    setCreatingFolder(picked == 1);
                    playClick();
                }
                return true;
            }

            if (showTreeDropdown() && inSlot(mx, my, treeDropdownX, dropdownY, DROPDOWN_H)) {
                treeDropdownOpen = true;
                playClick();
                return true;
            }
            if (showKindDropdown() && inSlot(mx, my, kindDropdownX, dropdownY, DROPDOWN_H)) {
                kindDropdownOpen = true;
                playClick();
                return true;
            }
            return false;
        }

        /**
         * Switches between creating a stage and creating a folder. The field's label and its
         * collision check both depend on the kind, and {@code fields()} is only consulted
         * during init, so the widgets are rebuilt — carrying the typed value across.
         */
        private void setCreatingFolder(boolean folder) {
            if (fieldCount() > 0) pendingName = box(0).getValue();
            // The folder kind drops the display-name field; remember it so switching back
            // does not throw the value away.
            if (fieldCount() > 1) pendingDisplayName = box(1).getValue();
            creatingFolder = folder;
            this.rebuildWidgets();
        }

        @Override
        protected boolean extraContentKeyPressed(int keyCode) {
            if (keyCode == 256 && (treeDropdownOpen || kindDropdownOpen)) {
                treeDropdownOpen = false;
                kindDropdownOpen = false;
                return true;
            }
            return false;
        }

        @Override
        protected void onConfirm(InputValues values) {
            String id = values.getString("id");

            if (creatingFolder) {
                PacketHandler.sendToServer(new CreateFolderPacket(individual, StagePaths.join(targetFolder, id)));
                this.minecraft.setScreen(parent);
                return;
            }

            String displayName = values.getString("display_name");

            // A new stage is only written when the user saves in the detail screen, so the
            // target folder has to travel with it.
            if (duplicateFromId != null) {
                StageEntry source = individual
                        ? StageManager.getIndividualStages().get(duplicateFromId)
                        : StageManager.getStages().get(duplicateFromId);
                if (source != null) {
                    StageEntry copy = source.copy();
                    // The duplicate is written straight away, so an emptied field would
                    // persist as "Unknown Stage" — keep the source's name instead.
                    if (!displayName.isEmpty()) copy.setDisplayName(displayName);
                    // A stage edited on disk can be larger than the packet allows; in that case
                    // nothing was written, so do not open a detail screen for it.
                    if (StageSaver.send(id, copy, individual, true, targetFolder)) {
                        this.minecraft.setScreen(new StageDetailScreen(parent, id, copy, individual, targetFolder));
                    } else {
                        this.minecraft.setScreen(parent);
                    }
                } else {
                    this.minecraft.setScreen(parent);
                }
            } else {
                this.minecraft.setScreen(new StageDetailScreen(parent, id, null, individual, targetFolder,
                        displayName));
            }
        }
    }
}
