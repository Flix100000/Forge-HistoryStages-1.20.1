package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.dialog.CreditsScreen;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.bananemdnsa.historystages.client.editor.folder.FolderNameScreen;
import net.bananemdnsa.historystages.client.editor.folder.StageFolderTree;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.category.CategoryEntryCounter;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.network.serverbound.CreateFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.DeleteFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.DeleteStagePacket;
import net.bananemdnsa.historystages.network.serverbound.MoveFoldersPacket;
import net.bananemdnsa.historystages.network.serverbound.MoveStagesPacket;
import net.bananemdnsa.historystages.network.serverbound.RenameFolderPacket;
import net.bananemdnsa.historystages.network.EditorDataCache;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.ToggleStageLockPacket;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.client.cache.ClientPlayerStageCache;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.PlayerPickerDropdown;
import net.bananemdnsa.historystages.network.serverbound.RequestIndividualStatesPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleIndividualStageLockPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class StageOverviewScreen extends Screen {

    private static final int ENTRY_HEIGHT = 28;
    private static final int LIST_PADDING = 40;
    private static final int HEADER_HEIGHT = 30;
    private static final int SECTION_HEADER_HEIGHT = 22;
    /** Hover-animation key bases; folder rows must not collide with the stage keys i / 10000 + i. */
    private static final int GLOBAL_FOLDER_HOVER_KEY = 20000;
    private static final int INDIVIDUAL_FOLDER_HOVER_KEY = 30000;
    /** Width of the three bars marking a folder row, in pixels. */
    private static final int FOLDER_ICON_WIDTH = 7;
    /** Header menu button in the top-right corner. Its height matches the search bar's. */
    private static final int MENU_BUTTON_W = 30;
    private static final int MENU_BUTTON_H = 18;
    private static final int MENU_BUTTON_Y = 5;
    /** Gap between the gear and the caret, which are centred in the button as one pair. */
    private static final int MENU_ICON_GAP = 2;
    /** Right edge of the bottom-bar button row, past which the organize status text starts. */
    private static final int BOTTOM_BAR_END = 260;
    /** Left edge of the row list. Shared so the checkbox hit test cannot drift from the drawing. */
    private static final int LIST_LEFT = 20;
    /** How far the header caret turns per frame, as a fraction of the full flip. */
    private static final float MENU_CARET_SPEED = 0.18f;
    private static final String BREADCRUMB_SEPARATOR = " / ";
    /** Colour code carrying the dependency badge's gold; the raw colour is the fallback. */
    private static final String DEP_BADGE_PREFIX = "§6";
    private static final int DEP_BADGE_COLOR = 0xFFAA55;

    /** Width the organize checkbox column takes away from a row's content. */
    private static final int CHECKBOX_COLUMN_W = 16;
    /** Edge length of the checkbox itself. */
    private static final int CHECKBOX_SIZE = 10;
    /**
     * How far the cursor has to travel from the press before it counts as a drag. Below it
     * the press stays a click, so ticking a box never turns into an accidental move.
     */
    private static final int DRAG_THRESHOLD = 4;
    /** Band at the list's top and bottom edge that scrolls the list while a drag hovers it. */
    private static final int AUTO_SCROLL_EDGE = 22;
    /** Pixels the list scrolls per frame inside that band. */
    private static final int AUTO_SCROLL_SPEED = 5;
    /** How long the drop target keeps pulsing after a move, in milliseconds. */
    private static final long DROP_PULSE_MS = Timing.DROP_PULSE_MS;

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
    /** True while the header menu is the one the shared context menu is showing. */
    private boolean headerMenuOpen = false;
    /** 0 = caret points down, 1 = fully flipped up. */
    private final Anim menuCaret = new Anim();

    private record BreadcrumbHit(int x1, int x2, String path) {}
    private final List<BreadcrumbHit> breadcrumbHits = new ArrayList<>();
    /** Screen y of the breadcrumb row, recorded during render for hit-testing. */
    private int breadcrumbY = -1;

    // --- Organize mode ---
    /** While on, rows are ticked and dragged instead of opened. */
    private boolean organizeMode = false;
    private StyledButton doneButton;
    /**
     * Ticked stage IDs, insertion-ordered so the drag ghost and the packet list stay stable.
     * They all belong to {@link #selectionIndividual}'s tree: a stage cannot move between
     * trees, so a mixed selection could never be dropped anywhere as a whole.
     */
    private final Set<String> selectedStages = new LinkedHashSet<>();
    /** Ticked folder paths, in the same tree and with the same ordering rationale. */
    private final Set<String> selectedFolders = new LinkedHashSet<>();
    /** Tree the current selection lives in; meaningless while the selection is empty. */
    private boolean selectionIndividual = false;

    /** A press landed on a row and may still become a drag. */
    private boolean dragArmed = false;
    /** The press has passed {@link #DRAG_THRESHOLD} and is now a real drag. */
    private boolean dragStarted = false;
    private double pressX, pressY;
    /** Tree the armed/running drag belongs to. */
    private boolean dragIndividual = false;
    /** Folder paths the drag carries — the whole selection, or just the pressed row. */
    private final List<String> dragFolders = new ArrayList<>();
    /** Stage IDs the drag carries — the whole selection, or just the pressed row. */
    private final List<String> dragStages = new ArrayList<>();
    /** The stage row the press landed on, so a press that never became a drag can tick it. */
    private String pressedStageId = null;
    /** The folder row the press landed on: a press that never became a drag enters it. */
    private String pressedFolderPath = null;
    /** True when the press landed in the checkbox column, which ticks instead of entering. */
    private boolean pressedCheckbox = false;

    /** Where a drop would land this frame, and whether it would be accepted. */
    private DropTarget activeDropTarget = null;
    private boolean activeDropValid = false;
    /** Target of the last completed move, highlighted for {@link #DROP_PULSE_MS}. */
    private DropTarget pulseTarget = null;
    private long pulseStart = 0;

    /** A folder the drag can be dropped into, identified the same way everywhere: tree + path. */
    private record DropTarget(boolean individual, String path) {}

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

    /**
     * Horizontal slide played when the browsed folder changes. Without it the whole list is
     * swapped in one frame, which gives no sense of having moved into or out of anything —
     * and folders are new enough that the hierarchy still has to be taught.
     */
    private final Anim navSlide = new Anim(1.0f);
    /** Which way the incoming content travels: +1 when going deeper, -1 when coming back up. */
    private int navDirection = 1;
    /** How far the incoming list starts from its resting position, in pixels. */
    private static final float NAV_SLIDE_PX = 26.0f;

    /** Reveal of the organize-mode checkbox column, so the mode switch is legible. */
    private final Anim organizeReveal = new Anim();

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
            PacketHandler.sendToServer(new RequestIndividualStatesPacket());
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
                new net.bananemdnsa.historystages.network.serverbound.RequestTemporaryCountsPacket(target));
    }

    @Override
    protected void init() {
        stageOrder = StageManager.getStageOrder();
        individualStageOrder = StageManager.getIndividualStageOrder();

        // Pull the live temporary-stage unlock counts from the server for display.
        requestTemporaryCounts();
        PacketHandler.sendToServer(new RequestIndividualStatesPacket());

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
                Component.translatable("editor.historystages.new_stage_or_folder"),
                btn -> openStageIdInputDialog(null, false),
                10, this.height - 30, 120, 20));

        backButton = StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> navigateUp(),
                135, this.height - 30, 60, 20);
        this.addRenderableWidget(backButton);

        // Only visible while organize mode is on; init() also runs on a resize, so the buttons
        // have to be restored into whatever state the mode is already in.
        doneButton = StyledButton.of(
                Component.translatable("editor.historystages.organize.done"),
                btn -> setOrganizeMode(false),
                200, this.height - 30, 60, 20);
        doneButton.visible = organizeMode;
        this.addRenderableWidget(doneButton);

        // One menu replaces the separate config and graph buttons: the header only has
        // room for a couple of them, and more entries are coming.
        // The button carries no label \u2014 gear and caret are drawn together in render(), so
        // they stay centred as a pair and the caret can animate.
        this.addRenderableWidget(StyledButton.of(
                Component.empty(),
                btn -> openHeaderMenu(),
                menuButtonX(), MENU_BUTTON_Y, MENU_BUTTON_W, MENU_BUTTON_H));

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
        afterNavigate(1);
    }

    /** One level up; from a tree root back to the two-section root view. */
    /** Back to the two-section root view that lists both trees. */
    private void navigateToRoot() {
        browsingIndividual = null;
        currentPath = "";
        afterNavigate(-1);
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
        afterNavigate(-1);
    }

    /**
     * Shared tail of every navigation: resets the scroll and hover state the old level owned,
     * and arms the slide that carries the new level in.
     *
     * @param direction +1 when moving deeper, -1 when moving back up. The incoming list enters
     *                  from the side it conceptually came from, so the gesture matches the move.
     */
    private void afterNavigate(int direction) {
        scrollOffset = 0;
        smoothScroll.set(0.0f);
        hoverProgress.clear();
        navDirection = direction;
        navSlide.set(0.0f);
        applyFilter();
    }

    private boolean matchesFilter(String stageId, StageEntry entry, String query) {
        if (stageId.toLowerCase().contains(query)) return true;
        if (entry != null && entry.getDisplayName().toLowerCase().contains(query)) return true;
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
    private void drawFolderRow(GuiGraphics g, StageFolderTree.Folder folder, boolean individual, int hoverKey,
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

        int contentLeft = listLeft + contentIndent();
        float reveal = Ease.outCubic(organizeReveal.value());
        if (reveal > 0.01f) {
            drawCheckbox(g, listLeft + 4, entryTop + 8,
                    isFolderSelected(folder.path(), individual), accentColor, reveal);
        }

        drawFolderIcon(g, contentLeft + 5, entryTop + 7, accentColor);
        g.drawString(this.font, folder.name(), contentLeft + 16, entryTop + 4,
                progress > 0.01f ? 0xFFFFFF : 0xEEEEEE, false);

        String info = Component.translatable("editor.historystages.folder.stage_count",
                folder.stageCount()).getString();
        int infoColor = (int) (0x88 + progress * 0x33);
        g.drawString(this.font, info, contentLeft + 22, entryTop + 15,
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

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op — we draw our own background in render() and want to avoid 1.21's menu blur shader
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
        // Refresh stage list if another admin changed definitions (broadcast via SyncStageDefinitionsPacket)
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
            // A ticked stage another admin deleted would otherwise ride along in the next
            // move packet and turn a move that worked into a failure toast.
            if (!selectedStages.isEmpty()) {
                selectedStages.retainAll(selectionIndividual
                        ? StageManager.getIndividualStages().keySet()
                        : StageManager.getStages().keySet());
            }
            selectedFolders.removeIf(path -> !StageFolderTree.exists(selectionIndividual, path));
            applyFilter();
        }

        // Smooth scroll
        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

        // Advanced once per frame here rather than inside contentIndent(), which is called
        // many times per frame and from the click paths as well.
        organizeReveal.ramp(organizeMode, Timing.REVEAL_MS, Timing.REVEAL_MS);

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
        int listLeft = LIST_LEFT;
        int listRight = this.width - 20;

        // Auto-scroll while a drag hovers the list's edges — without it every target below
        // the fold is unreachable, because the cursor is busy holding the drag.
        if (dragStarted && maxScroll > 0 && mouseX >= listLeft && mouseX <= listRight) {
            if (mouseY >= listTop - AUTO_SCROLL_EDGE && mouseY < listTop + AUTO_SCROLL_EDGE) {
                scrollOffset = Math.max(0, scrollOffset - AUTO_SCROLL_SPEED);
            } else if (mouseY > listBottom - AUTO_SCROLL_EDGE && mouseY <= listBottom + AUTO_SCROLL_EDGE) {
                scrollOffset = Math.min(maxScroll, scrollOffset + AUTO_SCROLL_SPEED);
            }
        }

        // Resolved once per frame so the highlight and the drop itself agree on the target.
        // The breadcrumb geometry it reads is the one the previous frame recorded, exactly
        // like the breadcrumb click path.
        if (dragStarted) {
            activeDropTarget = dropTargetAt(mouseX, mouseY);
            activeDropValid = canDropOn(activeDropTarget);
        } else {
            activeDropTarget = null;
            activeDropValid = false;
        }

        guiGraphics.enableScissor(listLeft, listTop, listRight, listBottom);

        // Folder navigation: the new level slides in from the side it came from, clipped by
        // the list scissor. Hit testing deliberately ignores the offset \u2014 it is over in under
        // two tenths of a second, and freezing input for it would cost more than it buys.
        float navT = Ease.inOutCubic(navSlide.ramp(1.0f, Timing.NAV_SLIDE_MS));
        boolean navigating = navT < 0.999f;
        if (navigating) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate((1.0f - navT) * NAV_SLIDE_PX * navDirection, 0.0f, 0.0f);
        }

        boolean overlayOpen = contextMenu != null && contextMenu.isVisible();
        // The cursor is ignored while the list is still moving, so a row cannot light up under
        // a pointer that is not actually on it yet.
        int effectiveMouseX = overlayOpen || navigating ? -1 : mouseX;
        int effectiveMouseY = overlayOpen || navigating ? -1 : mouseY;

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
            drawFolderRow(guiGraphics, globalFolders.get(i), false, GLOBAL_FOLDER_HOVER_KEY + i, entryTop,
                    listLeft, listRight, listTop, listBottom, effectiveMouseX, effectiveMouseY, 0xFFCC00);
        }

        // --- Global Stages ---
        for (int i = 0; i < filteredStageOrder.size(); i++) {
            String stageId = filteredStageOrder.get(i);
            StageEntry entry = stages.get(stageId);
            if (entry == null) continue;

            int entryTop = rowTop(layout.globalRowsY(), globalFolders.size() + i);
            int entryBottom = entryTop + ENTRY_HEIGHT - 2;

            if (entryBottom < listTop || entryTop > listBottom) { continue; }

            // Lock button bounds (calculated early for hover exclusion). Organize mode hides
            // the button — the row's click means "tick" there, so leaving it would be a
            // mis-click hazard — and the content simply extends to the list edge instead.
            boolean unlocked = ClientStageCache.isStageUnlocked(stageId);
            String lockLabel = Component.translatable(unlocked ? "editor.historystages.lock" : "editor.historystages.unlock").getString();
            int lockBtnW = Math.max(50, this.font.width(lockLabel) + 12);
            int lockBtnX = organizeMode ? listRight - 10 : listRight - lockBtnW - 10;
            int lockBtnH = 16;
            int lockBtnY = entryTop + 5;
            boolean onLockBtn = !organizeMode
                    && effectiveMouseX >= lockBtnX && effectiveMouseX <= lockBtnX + lockBtnW
                    && effectiveMouseY >= lockBtnY && effectiveMouseY <= lockBtnY + lockBtnH;
            int contentLeft = listLeft + contentIndent();

            boolean hovered = effectiveMouseX >= listLeft && effectiveMouseX <= listRight
                    && effectiveMouseY >= Math.max(entryTop, listTop) && effectiveMouseY <= Math.min(entryBottom, listBottom)
                    && !onLockBtn;

            if (hovered) { currentHovered = i; currentHoveredStage = i; }

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

            float cbReveal = Ease.outCubic(organizeReveal.value());
            if (cbReveal > 0.01f) {
                drawCheckbox(guiGraphics, listLeft + 4, entryTop + 8,
                        isSelected(stageId, false), 0xFFCC00, cbReveal);
            }

            // Lock/unlock icon
            String icon = unlocked ? "\u2714" : "\uD83D\uDD12";
            int iconColor = unlocked ? 0xFFCC00 : 0x888888;
            guiGraphics.drawString(this.font, icon, contentLeft + 5, entryTop + 6, iconColor, false);

            // Mode badge (pill placed to the LEFT of the lock button, vertically centered)
            long remainingTicks = EditorDataCache.getTemporaryActiveTicks(stageId);
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
            int nameX = contentLeft + 16;
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

            // Item count info
            int itemCount = CategoryEntryCounter.totalEntries(entry);
            String info = Component.translatable("editor.historystages.entries", itemCount).getString();
            int infoColor = (int) (0x88 + progress * 0x33);
            guiGraphics.drawString(this.font, info, contentLeft + 22, entryTop + 15, (0xFF << 24) | (infoColor << 16) | (infoColor << 8) | infoColor, false);
            if (entry.hasDependencies()) drawDepBadge(guiGraphics, info, contentLeft, entryTop + 15);

            // Lock/Unlock toggle button (right side) - bounds already calculated above
            if (!organizeMode) {
                boolean lockBtnHovered = onLockBtn && mouseY >= listTop && mouseY <= listBottom;

                int lockBg = lockBtnHovered ? 0x50FFCC00 : 0x25FFFFFF;
                guiGraphics.fill(lockBtnX, lockBtnY, lockBtnX + lockBtnW, lockBtnY + lockBtnH, lockBg);
                // Bottom accent (gold, like StyledButton)
                int lockAccent = lockBtnHovered ? 0xFFFFCC00 : 0x60FFCC00;
                guiGraphics.fill(lockBtnX, lockBtnY + lockBtnH - 1, lockBtnX + lockBtnW, lockBtnY + lockBtnH, lockAccent);

                int lockTextColor = lockBtnHovered ? 0xFFFFFF : 0xCCCCCC;
                int textW = this.font.width(lockLabel);
                guiGraphics.drawString(this.font, lockLabel, lockBtnX + (lockBtnW - textW) / 2, lockBtnY + 4, lockTextColor, false);
            }
        }

        // --- Individual Stages Section ---
        if (!showIndividualSection()) {
            pickerVisible = false;
            playerPicker.close();
        } else {
            int sectionY = layout.individualHeaderY();

            // The picker sticks inside the viewport while any part of the individual
            // section is on screen, so scrolling the header away does not take the
            // target selector with it. Organize mode hides it: it targets the per-row
            // Lock/Unlock buttons, which are gone there, and it would sit as a clickable
            // overlay on rows whose click now means "tick this box".
            int sectionBottom = sectionY + SECTION_HEADER_HEIGHT
                    + (individualFolders.size() + filteredIndividualStageOrder.size()) * ENTRY_HEIGHT;
            pickerVisible = !organizeMode && sectionBottom > listTop && sectionY < listBottom;
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
                drawFolderRow(guiGraphics, individualFolders.get(i), true, INDIVIDUAL_FOLDER_HOVER_KEY + i, folderTop,
                        listLeft, listRight, listTop, listBottom, effectiveMouseX, effectiveMouseY, 0xBBBBBB);
            }

            for (int i = 0; i < filteredIndividualStageOrder.size(); i++) {
                String stageId = filteredIndividualStageOrder.get(i);
                StageEntry entry = individualStages.get(stageId);
                if (entry == null) continue;

                int entryTop = rowTop(indY, individualFolders.size() + i);
                int entryBottom = entryTop + ENTRY_HEIGHT - 2;

                if (entryBottom < listTop || entryTop > listBottom) continue;

                // Unique hover key for individual stages (offset to avoid collision with global)
                int hoverKey = 10000 + i;

                // Lock button bounds are needed before the hover test so the row does not
                // light up while the cursor is on the button — same as the global rows.
                int state = individualState(stageId);
                String lockLabel = Component.translatable(
                        state == 2 ? "editor.historystages.lock" : "editor.historystages.unlock").getString();
                int lockBtnW = Math.max(50, this.font.width(lockLabel) + 12);
                int lockBtnX = organizeMode ? listRight - 10 : listRight - lockBtnW - 10;
                int lockBtnH = 16;
                int lockBtnY = entryTop + 5;
                boolean onLockBtn = !organizeMode
                        && effectiveMouseX >= lockBtnX && effectiveMouseX <= lockBtnX + lockBtnW
                        && effectiveMouseY >= lockBtnY && effectiveMouseY <= lockBtnY + lockBtnH;
                int contentLeft = listLeft + contentIndent();

                boolean hovered = effectiveMouseX >= listLeft && effectiveMouseX <= listRight
                        && effectiveMouseY >= Math.max(entryTop, listTop) && effectiveMouseY <= Math.min(entryBottom, listBottom)
                        && !onLockBtn;

                if (hovered) { currentHovered = hoverKey; currentHoveredStage = hoverKey; }

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
                float cbReveal = Ease.outCubic(organizeReveal.value());
                if (cbReveal > 0.01f) {
                    drawCheckbox(guiGraphics, listLeft + 4, entryTop + 8,
                            isSelected(stageId, true), 0xBBBBBB, cbReveal);
                }
                guiGraphics.drawString(this.font, stateIcon, contentLeft + 5, entryTop + 6, stateColor, false);

                // Mode badge sits left of the lock button, same as on global rows. The
                // countdown follows the picked player; under "@a" there is none to show.
                long remainingTicks = individualTemporaryTicks(stageId);
                int badgeWidth = modeBadgeWidth(entry, remainingTicks);
                int badgeY = entryTop + (ENTRY_HEIGHT - 12) / 2 - 1;
                int badgeX = lockBtnX - badgeWidth - 6;
                if (badgeWidth > 0) drawModeBadge(guiGraphics, entry, remainingTicks, badgeX, badgeY);

                // Temporary "used/max" count, left of the mode badge — same pairing as on
                // the global rows, and likewise empty for every other mode.
                String countText = temporaryCountText(entry, individualTemporaryCount(stageId));
                int modeLeft = (badgeWidth > 0) ? badgeX : lockBtnX;
                int countW = countText.isEmpty() ? 0 : this.font.width(countText) + 6;
                int countX = modeLeft - countW;
                if (!countText.isEmpty()) {
                    guiGraphics.drawString(this.font, countText, countX + 2, badgeY + 2, 0xFFAAAAAA, false);
                }

                // Lose-on-death badge, left of the count. Individual stages only —
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
                int nameX = contentLeft + 16;
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
                        guiGraphics.drawString(this.font, displayText, nameX - scrollOff, entryTop + 4, nameColor, false);
                        guiGraphics.disableScissor();
                    } else {
                        guiGraphics.drawString(this.font, displayText, nameX, entryTop + 4, nameColor, false);
                    }
                } else {
                    guiGraphics.drawString(this.font, displayText, nameX, entryTop + 4, nameColor, false);
                }

                // Item count info
                int itemCount = CategoryEntryCounter.totalEntries(entry);
                String info = Component.translatable("editor.historystages.entries", itemCount).getString();
                int infoColor = (int) (0x88 + progress * 0x33);
                guiGraphics.drawString(this.font, info, contentLeft + 22, entryTop + 15, (0xFF << 24) | (infoColor << 16) | (infoColor << 8) | infoColor, false);
                // Individual stages carry dependencies just like global ones, so the marker
                // belongs on these rows too.
                if (entry.hasDependencies()) drawDepBadge(guiGraphics, info, contentLeft, entryTop + 15);

                // Lock/Unlock toggle button, mirroring the global rows.
                if (!organizeMode) {
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
        }

        lastHoveredIndex = currentHovered;

        // Update marquee tracking
        if (currentHoveredStage != hoveredStageIndex) {
            hoveredStageIndex = currentHoveredStage;
            stageHoverStartTime = System.currentTimeMillis();
        }

        // Drop marker and post-drop pulse, both drawn from targetRect() so they sit on the
        // very row the hit test resolved. Still inside the list scissor, so a target scrolled
        // out of view is clipped away instead of painting over the header.
        if (activeDropTarget != null) {
            int[] rect = targetRect(activeDropTarget);
            if (rect != null) drawTargetOutline(guiGraphics, rect, activeDropValid ? 0xFFCC00 : 0xFF5555, 1.0f);
        }
        if (pulseTarget != null) {
            long age = System.currentTimeMillis() - pulseStart;
            if (age >= DROP_PULSE_MS) {
                pulseTarget = null;
            } else {
                int[] rect = targetRect(pulseTarget);
                if (rect != null) {
                    // Swells and fades rather than fading linearly, so the confirmation reads
                    // as a beat instead of a highlight that happens to be going away.
                    drawTargetOutline(guiGraphics, rect, 0xFFCC00,
                            Ease.pulse((float) age / DROP_PULSE_MS));
                }
            }
        }

        if (navigating) {
            guiGraphics.pose().popPose();
        }
        guiGraphics.disableScissor();

        // Drawn after the list scissor so the picker is never clipped; its y is already
        // clamped into the viewport.
        if (pickerVisible) {
            playerPicker.renderButton(guiGraphics, this.font, effectiveMouseX, effectiveMouseY);
        }

        if (maxScroll > 0) {
            int scrollBarHeight = Math.max(20, (int) ((float) (listBottom - listTop) / (maxScroll + (listBottom - listTop)) * (listBottom - listTop)));
            int scrollBarY = listTop + (int) ((float) scrollOffset / maxScroll * (listBottom - listTop - scrollBarHeight));
            guiGraphics.fill(listRight + 2, scrollBarY, listRight + 5, scrollBarY + scrollBarHeight, 0x80FFFFFF);
        }

        // Organize status line, right of the bottom-bar buttons: how much is ticked, and what
        // to do with it.
        if (organizeMode) {
            String selectedText = Component.translatable("editor.historystages.organize.selected",
                    selectionSize()).getString();
            String hintText = Component.translatable("editor.historystages.organize.hint").getString();
            // Left-aligned right after the button row, but pushed back inside the window if a
            // long translation would otherwise run off the right edge.
            int statusW = Math.max(this.font.width(selectedText), this.font.width(hintText));
            int statusX = Math.min(BOTTOM_BAR_END + 10, Math.max(10, this.width - 10 - statusW));
            guiGraphics.drawString(this.font, selectedText, statusX, this.height - 29, 0xFFCC00, false);
            guiGraphics.drawString(this.font, hintText, statusX, this.height - 18, 0x888888, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // The shared context menu is also used for row right-clicks, so the caret only
        // tracks it while the header opened it.
        if (!contextMenu.isVisible()) headerMenuOpen = false;
        menuCaret.ramp(headerMenuOpen, Timing.POPUP_MS, Timing.POPUP_MS);
        drawMenuButtonContent(guiGraphics);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        contextMenu.render(guiGraphics, this.font, mouseX, mouseY);
        guiGraphics.pose().popPose();

        if (pickerVisible) {
            playerPicker.renderPopup(guiGraphics, this.font, effectiveMouseX, effectiveMouseY);
        }

        // Drag ghost, drawn last so nothing can cover what the cursor is carrying.
        if (dragStarted) {
            String ghost = dragGhostLabel();
            if (!ghost.isEmpty()) {
                int ghostW = this.font.width(ghost) + 8;
                int ghostX = Math.min(mouseX + 8, this.width - ghostW - 2);
                int ghostY = Math.min(mouseY + 8, this.height - 16);
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 400);
                guiGraphics.fill(ghostX, ghostY, ghostX + ghostW, ghostY + 14, 0xE0101010);
                guiGraphics.fill(ghostX, ghostY + 13, ghostX + ghostW, ghostY + 14, 0xFFFFCC00);
                guiGraphics.drawString(this.font, ghost, ghostX + 4, ghostY + 3, 0xFFFFFF, false);
                guiGraphics.pose().popPose();
            }
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

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int listTop = HEADER_HEIGHT + 5;
        int listBottom = this.height - 40;
        int listLeft = LIST_LEFT;
        int listRight = this.width - 20;

        if (maxScroll > 0 && mouseX >= listRight + 1 && mouseX <= listRight + 6
                && mouseY >= listTop && mouseY <= listBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY, listTop, listBottom);
            return true;
        }

        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) return false;

        // The rows are mid-slide, so nothing in the list is where a hit test would put it.
        if (navSlide.value() < 0.999f || organizeSettling()) return true;

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
                // Only the left button belongs to organize mode; a right click keeps opening
                // the row's own menu, which is where deleting a single entry lives.
                if (organizeMode && button == 0) {
                    return organizeFolderPressed(globalFolders.get(i), false, button, mouseX, mouseY);
                }
                return folderRowClicked(globalFolders.get(i), false, button, mouseX, mouseY);
            }
        }

        // Global stages
        for (int i = 0; i < filteredStageOrder.size(); i++) {
            String stageId = filteredStageOrder.get(i);
            StageEntry entry = stages.get(stageId);
            if (entry == null) continue;

            int entryTop = rowTop(layout.globalRowsY(), globalFolders.size() + i);
            int entryBottom = entryTop + ENTRY_HEIGHT - 2;

            if (mouseY >= entryTop && mouseY <= entryBottom) {
                if (organizeMode && button == 0) return organizeStagePressed(stageId, false, button, mouseX, mouseY);

                // Check lock/unlock button click
                boolean unlocked = ClientStageCache.isStageUnlocked(stageId);
                String lockLabelClick = Component.translatable(unlocked ? "editor.historystages.lock" : "editor.historystages.unlock").getString();
                int lockBtnWClick = Math.max(50, this.font.width(lockLabelClick) + 12);
                int lockBtnX = listRight - lockBtnWClick - 10;
                int lockBtnY = entryTop + 5;
                if (button == 0 && mouseX >= lockBtnX && mouseX <= lockBtnX + lockBtnWClick
                        && mouseY >= lockBtnY && mouseY <= lockBtnY + 16) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    PacketHandler.sendToServer(new ToggleStageLockPacket(stageId, !unlocked));
                    return true;
                }

                // Right-click context menu
                if (button == 1) {
                    contextMenu = new ContextMenu();
                    // Organize mode is for structuring, not authoring: a left click ticks the
                    // row, so nothing in its menu may open the stage editor. Duplicate ends
                    // there too, so it goes with Edit and only Delete remains.
                    if (!organizeMode) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(), () -> {
                            this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry, false));
                        });
                        contextMenu.addEntry(Component.translatable("editor.historystages.duplicate").getString(), () -> {
                            openStageIdInputDialog(stageId, false);
                        });
                    }
                    contextMenu.addEntry(Component.translatable("editor.historystages.delete").getString(), () -> {
                        Screen self = this;
                        this.minecraft.setScreen(new ConfirmDialog(this,
                                Component.translatable("editor.historystages.confirm_delete_title"),
                                Component.translatable("editor.historystages.confirm_delete", stageId),
                                () -> { PacketHandler.sendToServer(new DeleteStagePacket(stageId, false)); stageOrder.remove(stageId); applyFilter(); Minecraft.getInstance().setScreen(self); }));
                    });
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    contextMenu.show((int) mouseX, (int) mouseY, this.font);
                    return true;
                }

                // Left-click on stage entry -> open detail editor
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
                    if (organizeMode && button == 0) {
                        return organizeFolderPressed(individualFolders.get(i), true, button, mouseX, mouseY);
                    }
                    return folderRowClicked(individualFolders.get(i), true, button, mouseX, mouseY);
                }
            }

            for (int i = 0; i < filteredIndividualStageOrder.size(); i++) {
                String stageId = filteredIndividualStageOrder.get(i);
                StageEntry entry = individualStages.get(stageId);
                if (entry == null) continue;

                int entryTop = rowTop(indY, individualFolders.size() + i);
                int entryBottom = entryTop + ENTRY_HEIGHT - 2;

                if (mouseY >= entryTop && mouseY <= entryBottom) {
                    if (organizeMode && button == 0) return organizeStagePressed(stageId, true, button, mouseX, mouseY);

                    int state = individualState(stageId);
                    String lockLabelClick = Component.translatable(
                            state == 2 ? "editor.historystages.lock" : "editor.historystages.unlock").getString();
                    int lockBtnWClick = Math.max(50, this.font.width(lockLabelClick) + 12);
                    int lockBtnX = listRight - lockBtnWClick - 10;
                    int lockBtnY = entryTop + 5;
                    if (button == 0 && mouseX >= lockBtnX && mouseX <= lockBtnX + lockBtnWClick
                            && mouseY >= lockBtnY && mouseY <= lockBtnY + 16) {
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        PacketHandler.sendToServer(new ToggleIndividualStageLockPacket(
                                stageId, Optional.ofNullable(playerPicker.getSelected()), state != 2));
                        return true;
                    }

                    if (button == 1) {
                        contextMenu = new ContextMenu();
                        if (!organizeMode) {
                            contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(), () -> {
                                this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry, true));
                            });
                            contextMenu.addEntry(Component.translatable("editor.historystages.duplicate").getString(), () -> {
                                openStageIdInputDialog(stageId, true);
                            });
                        }
                        contextMenu.addEntry(Component.translatable("editor.historystages.delete").getString(), () -> {
                            Screen self = this;
                            this.minecraft.setScreen(new ConfirmDialog(this,
                                    Component.translatable("editor.historystages.confirm_delete_title"),
                                    Component.translatable("editor.historystages.confirm_delete", stageId),
                                    () -> { PacketHandler.sendToServer(new DeleteStagePacket(stageId, true)); individualStageOrder.remove(stageId); applyFilter(); Minecraft.getInstance().setScreen(self); }));
                        });
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        contextMenu.show((int) mouseX, (int) mouseY, this.font);
                        return true;
                    }

                    // Left-click -> open detail editor (individual mode)
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
    private int menuButtonX() {
        return this.width - MENU_BUTTON_W - 10;
    }

    /**
     * Draws the gear and the caret as one pair, centred in the menu button. They used to be
     * placed independently — the gear centred by the button, the caret at a fixed offset from
     * the screen edge — which left the two looking unrelated.
     */
    private void drawMenuButtonContent(GuiGraphics g) {
        String gear = "⚙";
        int gearW = this.font.width(gear);
        int caretW = this.font.width("▾");
        int pairW = gearW + MENU_ICON_GAP + caretW;
        int startX = menuButtonX() + (MENU_BUTTON_W - pairW) / 2;
        int textY = MENU_BUTTON_Y + (MENU_BUTTON_H - 8) / 2;

        g.drawString(this.font, gear, startX, textY, 0xCCCCCC, false);
        drawMenuCaret(g, startX + gearW + MENU_ICON_GAP, textY);
    }

    /**
     * Flips the header caret between ▾ and ▴ over a few frames. Drawn on top of the
     * button rather than as its label, because a label cannot be rotated.
     */
    private void drawMenuCaret(GuiGraphics g, int x, int y) {
        String caret = "▾";
        float halfW = this.font.width(caret) / 2.0f;
        g.pose().pushPose();
        g.pose().translate(x + halfW, y + 4.0f, 100.0f);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
                Ease.outCubic(menuCaret.value()) * 180.0f));
        g.pose().translate(-halfW, -4.0f, 0.0f);
        g.drawString(this.font, caret, 0, 0, 0xFFCC00, false);
        g.pose().popPose();
    }

    /** Drops the header menu open under its button, right-aligned with it. */
    private void openHeaderMenu() {
        headerMenuOpen = true;
        contextMenu = new ContextMenu();
        contextMenu.addEntry(Component.translatable("editor.historystages.config_title").getString(),
                () -> this.minecraft.setScreen(new ConfigEditorScreen(this)));
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.button").getString(),
                () -> this.minecraft.setScreen(new StageGraphScreen(this)));
        contextMenu.addEntry(Component.translatable("editor.historystages.menu.organize").getString(),
                () -> setOrganizeMode(true));
        contextMenu.addEntry(Component.translatable("editor.historystages.menu.credits").getString(),
                () -> this.minecraft.setScreen(new CreditsScreen(this)));
        contextMenu.showRightAligned(menuButtonX() + MENU_BUTTON_W,
                MENU_BUTTON_Y + MENU_BUTTON_H + 3, this.font);
    }

    /**
     * Turns organize mode on or off. Leaving drops the selection and any running drag —
     * a selection that survived the mode would be invisible and still act on the next entry.
     */
    private void setOrganizeMode(boolean on) {
        organizeMode = on;
        clearSelection();
        clearDrag();
        pulseTarget = null;
        if (doneButton != null) doneButton.visible = on;
    }

    /**
     * Horizontal room the checkbox column takes from every row's content while the mode is on.
     * Follows {@link #organizeReveal}, so entering and leaving organize mode pushes the rows
     * aside instead of relaying the whole list between two frames.
     */
    private int contentIndent() {
        return Math.round(CHECKBOX_COLUMN_W * Ease.outCubic(organizeReveal.value()));
    }

    /**
     * True while the checkbox column is still sliding. Row input is held off until it settles:
     * the rows are drawn at an offset the hit tests do not know about, and a tick landing on
     * the wrong row is worse than a tenth of a second of delay.
     */
    private boolean organizeSettling() {
        return !organizeReveal.isAt(organizeMode ? 1.0f : 0.0f);
    }

    private boolean isSelected(String stageId, boolean individual) {
        return selectionIndividual == individual && selectedStages.contains(stageId);
    }

    private boolean isFolderSelected(String path, boolean individual) {
        return selectionIndividual == individual && selectedFolders.contains(path);
    }

    /** Everything ticked, stages and folders together. */
    private int selectionSize() {
        return selectedStages.size() + selectedFolders.size();
    }

    private void clearSelection() {
        selectedStages.clear();
        selectedFolders.clear();
    }

    /**
     * Drops the whole selection when the click comes from the other tree. The selection is
     * confined to one tree: dropping a mixed selection into a folder would move part of it
     * across trees, which the loader does not support.
     */
    private void enterSelectionTree(boolean individual) {
        if (selectionSize() > 0 && selectionIndividual != individual) clearSelection();
        selectionIndividual = individual;
    }

    private void toggleSelection(String stageId, boolean individual) {
        enterSelectionTree(individual);
        if (!selectedStages.remove(stageId)) selectedStages.add(stageId);
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void toggleFolderSelection(String path, boolean individual) {
        enterSelectionTree(individual);
        if (!selectedFolders.remove(path)) selectedFolders.add(path);
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /**
     * Records a press on a row without acting on it yet: only the release decides whether it
     * was a click or a drag.
     *
     * <p>A press on a ticked stage carries the whole selection, a press on any other row
     * carries just that row and leaves the selection alone — dragging something the user did
     * not tick must not silently redefine what is ticked.
     */
    private void armDrag(boolean individual, String folderPath, String stageId,
                         boolean onCheckbox, double mouseX, double mouseY) {
        dragArmed = true;
        dragStarted = false;
        pressX = mouseX;
        pressY = mouseY;
        dragIndividual = individual;
        pressedFolderPath = folderPath;
        pressedStageId = stageId;
        pressedCheckbox = onCheckbox;
        dragStages.clear();
        dragFolders.clear();

        boolean pressedIsSelected = stageId != null
                ? isSelected(stageId, individual)
                : isFolderSelected(folderPath, individual);
        if (pressedIsSelected) {
            dragStages.addAll(selectedStages);
            dragFolders.addAll(selectedFolders);
        } else if (stageId != null) {
            dragStages.add(stageId);
        } else {
            dragFolders.add(folderPath);
        }
    }

    private void clearDrag() {
        dragArmed = false;
        dragStarted = false;
        pressedFolderPath = null;
        pressedStageId = null;
        pressedCheckbox = false;
        dragStages.clear();
        dragFolders.clear();
        activeDropTarget = null;
        activeDropValid = false;
    }

    /**
     * The folder under the cursor a drag could be dropped on: a folder row at the current
     * level, or a breadcrumb segment. The breadcrumb matters because it is the only way to
     * move something <em>out</em> of the browsed folder — no ancestor is ever drawn as a row.
     *
     * <p>Row positions come from {@link #layout} and {@link #rowTop} fed with
     * {@code smoothScroll}, exactly as {@link #render} and {@link #mouseClicked} do.
     */
    private DropTarget dropTargetAt(double mouseX, double mouseY) {
        int listTop = HEADER_HEIGHT + 5;
        int listBottom = this.height - 40;
        int listLeft = LIST_LEFT;
        int listRight = this.width - 20;

        // Breadcrumb hits are recorded while drawing, same as the breadcrumb click path uses.
        if (browsingIndividual != null && breadcrumbY >= 0
                && mouseY >= breadcrumbY - 2 && mouseY <= breadcrumbY + 12) {
            for (BreadcrumbHit hit : breadcrumbHits) {
                if (mouseX >= hit.x1() - 2 && mouseX <= hit.x2() + 2) {
                    return new DropTarget(browsingIndividual, hit.path());
                }
            }
        }

        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) return null;

        ListLayout layout = layout(listTop, Math.round(smoothScroll.value()));
        for (int i = 0; i < globalFolders.size(); i++) {
            int folderTop = rowTop(layout.globalRowsY(), i);
            if (mouseY >= folderTop && mouseY <= folderTop + ENTRY_HEIGHT - 2) {
                return new DropTarget(false, globalFolders.get(i).path());
            }
        }
        if (showIndividualSection()) {
            int indY = layout.individualRowsY();
            for (int i = 0; i < individualFolders.size(); i++) {
                int folderTop = rowTop(indY, i);
                if (mouseY >= folderTop && mouseY <= folderTop + ENTRY_HEIGHT - 2) {
                    return new DropTarget(true, individualFolders.get(i).path());
                }
            }
        }
        return null;
    }

    /**
     * Screen rectangle of a drop target as {@code {x1, y1, x2, y2}}, or null when it is not
     * on screen right now. Shares {@link #layout} / {@link #rowTop} with everything else, so
     * the highlight cannot drift away from the row the hit test picked.
     */
    private int[] targetRect(DropTarget target) {
        if (target == null) return null;
        int listTop = HEADER_HEIGHT + 5;
        int listLeft = LIST_LEFT;
        int listRight = this.width - 20;

        if (browsingIndividual != null && target.individual() == browsingIndividual && breadcrumbY >= 0) {
            for (BreadcrumbHit hit : breadcrumbHits) {
                if (hit.path().equals(target.path())) {
                    return new int[]{hit.x1() - 2, breadcrumbY - 2, hit.x2() + 2, breadcrumbY + 11};
                }
            }
        }

        ListLayout layout = layout(listTop, Math.round(smoothScroll.value()));
        List<StageFolderTree.Folder> folders = target.individual() ? individualFolders : globalFolders;
        int rowsY = target.individual() ? layout.individualRowsY() : layout.globalRowsY();
        if (target.individual() && !showIndividualSection()) return null;
        for (int i = 0; i < folders.size(); i++) {
            if (!folders.get(i).path().equals(target.path())) continue;
            int folderTop = rowTop(rowsY, i);
            return new int[]{listLeft, folderTop, listRight, folderTop + ENTRY_HEIGHT - 2};
        }
        return null;
    }

    /**
     * Whether the running drag may be dropped on {@code target}. Refusals are shown rather
     * than swallowed, so the user learns the rule instead of watching a drop do nothing.
     */
    private boolean canDropOn(DropTarget target) {
        if (target == null) return false;
        // A stage file cannot change trees: individual stages do not support the same
        // categories, and the loader strips what does not belong.
        if (target.individual() != dragIndividual) return false;

        // A folder cannot move into itself or into anything below it — it would disappear
        // into its own subtree. One offending folder in the drag refuses the whole drop.
        for (String folder : dragFolders) {
            if (target.path().equals(folder)) return false;
            if (target.path().startsWith(folder + "/")) return false;
        }

        // At least one item has to actually change folder, otherwise the drop is a no-op.
        for (String folder : dragFolders) {
            if (!StagePaths.parent(folder).equals(target.path())) return true;
        }
        for (String stageId : dragStages) {
            if (!StageManager.getStageFolder(stageId, dragIndividual).equals(target.path())) return true;
        }
        return false;
    }

    /**
     * Sends the move as one packet — never one per stage — and drops the selection, which has
     * done its job once the move is on its way. The list itself is not touched: the server
     * reloads and broadcasts, and the folder-signature check in {@link #render} picks it up.
     */
    private void performDrop(DropTarget target) {
        if (dragFolders.isEmpty() && dragStages.isEmpty()) return;

        if (!dragFolders.isEmpty()) {
            PacketHandler.sendToServer(new MoveFoldersPacket(dragIndividual, new ArrayList<>(dragFolders), target.path()));
        }
        if (!dragStages.isEmpty()) {
            PacketHandler.sendToServer(new MoveStagesPacket(dragIndividual, new ArrayList<>(dragStages), target.path()));
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        clearSelection();
        pulseTarget = target;
        pulseStart = System.currentTimeMillis();
    }

    /** Left press on a stage row while organize mode is on; only the release acts on it. */
    private boolean organizeStagePressed(String stageId, boolean individual, int button,
                                         double mouseX, double mouseY) {
        armDrag(individual, null, stageId, false, mouseX, mouseY);
        return true;
    }

    /**
     * Press on a folder row while organize mode is on; the release decides drag vs. tick vs.
     * navigate.
     *
     * <p>A folder row differs from a stage row on purpose: a stage row has no competing
     * action, so a click anywhere on it ticks. A folder still has to be enterable — walking
     * into a folder is how the user reaches a drop target — so only the checkbox column ticks
     * and the rest of the row keeps navigating.
     */
    private boolean organizeFolderPressed(StageFolderTree.Folder folder, boolean individual, int button,
                                          double mouseX, double mouseY) {
        boolean onCheckbox = mouseX < LIST_LEFT + CHECKBOX_COLUMN_W;
        armDrag(individual, folder.path(), null, onCheckbox, mouseX, mouseY);
        return true;
    }

    /** Draws the organize checkbox for one row. */
    /**
     * @param reveal 0..1 opacity, so the column fades with the same animation that slides the
     *               row content aside instead of popping in at full strength.
     */
    private void drawCheckbox(GuiGraphics g, int x, int y, boolean checked, int accent, float reveal) {
        int border = checked ? (0xFF000000 | accent) : 0xFF777777;
        g.fill(x, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, Fade.alpha(0x40000000, reveal));
        g.fill(x, y, x + CHECKBOX_SIZE, y + 1, Fade.alpha(border, reveal));
        g.fill(x, y + CHECKBOX_SIZE - 1, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, Fade.alpha(border, reveal));
        g.fill(x, y, x + 1, y + CHECKBOX_SIZE, Fade.alpha(border, reveal));
        g.fill(x + CHECKBOX_SIZE - 1, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, Fade.alpha(border, reveal));
        if (checked) {
            g.fill(x + 3, y + 3, x + CHECKBOX_SIZE - 3, y + CHECKBOX_SIZE - 3,
                    Fade.alpha(0xFF000000 | accent, reveal));
        }
    }

    /** Tinted box plus border marking a drop target — gold when accepted, red when refused. */
    private void drawTargetOutline(GuiGraphics g, int[] rect, int color, float strength) {
        int alpha = (int) (0xFF * Math.max(0.0f, Math.min(1.0f, strength)));
        if (alpha < 4) return;
        int rgb = color & 0xFFFFFF;
        g.fill(rect[0], rect[1], rect[2], rect[3], ((alpha / 5) << 24) | rgb);
        g.fill(rect[0], rect[1], rect[2], rect[1] + 1, (alpha << 24) | rgb);
        g.fill(rect[0], rect[3] - 1, rect[2], rect[3], (alpha << 24) | rgb);
        g.fill(rect[0], rect[1], rect[0] + 1, rect[3], (alpha << 24) | rgb);
        g.fill(rect[2] - 1, rect[1], rect[2], rect[3], (alpha << 24) | rgb);
    }

    /** Label carried by the drag ghost: the row's name, or the count for a multi-drag. */
    private String dragGhostLabel() {
        int total = dragStages.size() + dragFolders.size();
        if (total == 0) return "";
        if (total == 1) {
            return dragStages.isEmpty() ? StagePaths.name(dragFolders.get(0)) : dragStages.get(0);
        }
        return Component.translatable("editor.historystages.organize.selected", total).getString();
    }

    private void openStageIdInputDialog(String duplicateFromId, boolean individual) {
        if (duplicateFromId != null) {
            // A duplicate inherits its tree from the row that was clicked — search results
            // span both trees, so neither the browsed tree nor a selector may override it.
            // Marking the tree fixed hides that selector; the kind selector is already
            // hidden for duplicates, so the dialog shows none. The copy lands next to
            // its source.
            this.minecraft.setScreen(new StageIdInputScreen(this, duplicateFromId, individual,
                    true, StageManager.getStageFolder(duplicateFromId, individual), false));
            return;
        }
        boolean treeFixed = browsingIndividual != null;
        // Organize mode is for structuring what exists, so the dialog only creates folders
        // there — the target to sort into — and the kind selector goes away with the choice.
        this.minecraft.setScreen(new StageIdInputScreen(this, duplicateFromId,
                treeFixed ? browsingIndividual : individual, treeFixed, currentPath, organizeMode));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pickerVisible && playerPicker.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 10));
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
        if (dragArmed && button == 0) {
            if (!dragStarted) {
                double dx = mouseX - pressX;
                double dy = mouseY - pressY;
                // A search shows neither folder rows nor the breadcrumb, so a drag started
                // there could never be dropped on anything; the press stays a click that
                // ticks the box.
                if (searchFilter.trim().isEmpty()
                        && dx * dx + dy * dy > (double) DRAG_THRESHOLD * DRAG_THRESHOLD) {
                    dragStarted = true;
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) { draggingScrollbar = false; return true; }

        if (dragArmed && button == 0) {
            if (dragStarted) {
                DropTarget target = dropTargetAt(mouseX, mouseY);
                if (canDropOn(target)) performDrop(target);
            } else if (pressedStageId != null) {
                // Never became a drag, so the press was a plain click: a stage row ticks
                // wherever it was hit.
                toggleSelection(pressedStageId, dragIndividual);
            } else if (pressedFolderPath != null) {
                // A folder ticks only from the checkbox column; anywhere else it opens.
                if (pressedCheckbox) {
                    toggleFolderSelection(pressedFolderPath, dragIndividual);
                } else {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    navigateInto(dragIndividual, pressedFolderPath);
                }
            }
            clearDrag();
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
            // Snapped, not eased: while the thumb is held the list must track the
            // cursor exactly, or the thumb drifts from where the pointer is.
            smoothScroll.set((float) scrollOffset);
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

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void onClose() { this.minecraft.setScreen(null); }
    @Override public boolean isPauseScreen() { return true; }

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
        return EditorDataCache.getTemporaryCount(stageId);
    }

    /**
     * Live unlock count for an individual temporary stage row, or -1 while the picker is
     * on "@a". Per-player counts differ between players, so there is no honest single
     * number to show for "everyone" — the row drops the label instead of inventing one.
     */
    private int individualTemporaryCount(String stageId) {
        UUID target = playerPicker.getSelected();
        return target == null ? -1 : EditorDataCache.getIndividualTemporaryCount(target, stageId);
    }

    /** Remaining ticks until re-lock for an individual row, or -1 under "@a". */
    private long individualTemporaryTicks(String stageId) {
        UUID target = playerPicker.getSelected();
        return target == null ? -1L : EditorDataCache.getIndividualTemporaryActiveTicks(target, stageId);
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
        /** Organize mode creates folders only, so the kind is fixed and its selector hidden. */
        private final boolean foldersOnly;
        /** Typed value carried across the widget rebuild that a kind switch triggers. */
        private String pendingName = "";
        /** Same for the display name, which the folder kind has no field for. */
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
                                     boolean individual, boolean treeFixed, String targetFolder,
                                     boolean foldersOnly) {
            super(parent, Component.translatable("editor.historystages.new_stage"));
            this.parent = parent;
            this.duplicateFromId = duplicateFromId;
            this.individual = individual;
            this.treeFixed = treeFixed;
            this.targetFolder = targetFolder;
            this.foldersOnly = foldersOnly;
            this.creatingFolder = foldersOnly;
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
        private boolean showKindDropdown() { return duplicateFromId == null && !foldersOnly; }

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
