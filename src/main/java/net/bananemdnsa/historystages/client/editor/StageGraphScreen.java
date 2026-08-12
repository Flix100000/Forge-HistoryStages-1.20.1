package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.client.editor.dialog.StageInfoTextScreen;
import net.bananemdnsa.historystages.client.editor.graph.GraphCanvas;
import net.bananemdnsa.historystages.client.editor.graph.GraphDetailScreen;
import net.bananemdnsa.historystages.client.editor.graph.GraphLegend;
import net.bananemdnsa.historystages.client.editor.graph.GraphSidebar;
import net.bananemdnsa.historystages.client.editor.graph.GraphViewFilter;
import net.bananemdnsa.historystages.client.editor.graph.StageGraphConfig;
import net.bananemdnsa.historystages.client.editor.graph.StageGraphModel;
import net.bananemdnsa.historystages.client.editor.graph.StageStyleClipboard;
import net.bananemdnsa.historystages.client.editor.toast.EditorToast;
import net.bananemdnsa.historystages.client.editor.toast.EditorToastHandler;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphLayoutData;
import net.bananemdnsa.historystages.data.graph.GraphPos;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.RearrangeGraphPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestStageDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphPositionsPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphStylePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Host screen for the stage graph: composes {@link GraphSidebar} and {@link GraphCanvas} into
 * one view, opened either from the in-game editor or from the pause screen. Clicking a node
 * opens {@link GraphDetailScreen} over it.
 *
 * <p>One class with a {@link Mode} flag rather than two screen classes — two views onto the
 * same map whose rendering drifts apart is the worst outcome available here. The two modes
 * differ only in which {@link GraphViewFilter} builds the model, whether the editor's
 * navigation button is shown, and whether the view fits itself to the visible nodes on open;
 * everything else (layout, rendering, input routing) is shared.
 */
public class StageGraphScreen extends Screen {

    public enum Mode { PLAYER, EDITOR }

    private static final int TOP_BAR_H = 24;

    /**
     * Side column widths. Both are capped against the window so the canvas keeps the majority of
     * the screen: the fixed 200/260 they started at ate well over half a 1280-wide window once
     * both were open, which is the wrong split for a view whose whole point is the map.
     */
    private static final int SIDEBAR_W = 165;
    /** The sidebar may not take more than this share of the window. */
    private static final float MAX_COLUMN_SHARE = 0.22f;

    /** Movement below this, in pixels, still counts as a click rather than a drag. */
    private static final int CLICK_SLOP = 4;

    private double pressX;
    private double pressY;
    private boolean pressedOnCanvas;

    /** Combined unlock-cache version last folded into {@link #model}; see refreshOnUnlockChange. */
    private int lastUnlockVersion = Integer.MIN_VALUE;

    /**
     * Stages a {@link RequestStageDependencyPacket} has already gone out for while this screen
     * has been open. The detail window is built fresh on every open and so cannot remember this
     * itself; this screen outlives all of them, which is why the request is fired from here.
     */
    private final Set<String> pendingDependencyRequests = new HashSet<>();

    private static final int BACK_BUTTON_X = 6;
    private static final int BACK_BUTTON_Y = 2;
    private static final int BACK_BUTTON_W = 60;
    private static final int BACK_BUTTON_H = 18;

    private static final int REARRANGE_BUTTON_X = BACK_BUTTON_X + BACK_BUTTON_W + 6;
    private static final int REARRANGE_BUTTON_W = 90;

    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int BACKGROUND_COLOR = 0xFF101010;

    private final Screen parent;
    private final Mode mode;

    private GraphCanvas canvas;
    /** Null whenever {@code [general] showSidebar} is off — this screen owns that decision. */
    private GraphSidebar sidebar;
    /** Null whenever {@code [general] showLegend} is off — this screen owns that decision too. */
    private GraphLegend legend;
    private StageGraphModel model;

    /** Fresh instance per open, exactly as {@code StageOverviewScreen} does it. Editor mode only. */
    private ContextMenu contextMenu;

    /** Editor view — from {@code StageOverviewScreen}. Unfiltered, with the authoring tools. */
    public StageGraphScreen(Screen parent) {
        this(parent, Mode.EDITOR);
    }

    /** Player view — from the pause screen. Filtered, read-only. */
    public static StageGraphScreen forPlayer(Screen parent) {
        return new StageGraphScreen(parent, Mode.PLAYER);
    }

    private StageGraphScreen(Screen parent, Mode mode) {
        super(resolveTitle());
        this.parent = parent;
        this.mode = mode;
    }

    /**
     * Literal text, unless a translation exists for it — mirrors {@code GraphDetailScreen.describe},
     * down to the {@code &} colour codes literal text may carry. That is what the rich text editor
     * behind this config key writes.
     */
    private static Component resolveTitle() {
        String raw = GraphConfig.GRAPH.title.get();
        return I18n.exists(raw) ? Component.translatable(raw)
                : Component.literal(raw.replace('&', '§'));
    }

    @Override
    protected void init() {
        boolean editorView = mode == Mode.EDITOR;
        model = buildModel();

        int contentTop = TOP_BAR_H;
        int contentHeight = Math.max(0, this.height - TOP_BAR_H);

        int columnCap = Math.max(90, Math.round(this.width * MAX_COLUMN_SHARE));
        boolean showSidebar = GraphConfig.GRAPH.showSidebar.get();
        int sidebarFullW = showSidebar ? Math.min(SIDEBAR_W, columnCap) : 0;

        // A ConfirmDialog is a full screen swap, so confirming Re-arrange or a freeze re-enters
        // this method on the very same instance. Reusing the existing canvas/sidebar (updating
        // their bounds and model in place) rather than constructing fresh ones keeps the
        // author's pan/zoom and sidebar scroll exactly where they left them; only the very first
        // open constructs anything.
        if (canvas == null) {
            canvas = new GraphCanvas(model, sidebarFullW, contentTop,
                    Math.max(0, this.width - sidebarFullW), contentHeight, editorView);
            canvas.setDragHandler(this::handleDrop);
        } else {
            canvas.setModel(model);
        }

        if (showSidebar) {
            if (sidebar == null) {
                sidebar = new GraphSidebar(canvas);
                // A list click centres the map and marks the stage — it deliberately does not
                // open the detail window. Paging down the list would otherwise throw a modal up
                // and down on every single click. The window is reached through a node.
                sidebar.setSelectHandler(canvas::highlight);
            }
            sidebar.setBounds(0, contentTop, sidebarFullW, contentHeight);
            sidebar.setModel(model);
        } else {
            sidebar = null;
        }

        // The sidebar owns how wide it currently draws itself — it may be collapsed to its rail,
        // or mid-animation after a re-init — so the canvas is positioned from that, not from the
        // configured width.
        int sidebarW = sidebar == null ? 0 : sidebar.currentWidth();
        canvas.setBounds(sidebarW, contentTop, Math.max(0, this.width - sidebarW), contentHeight);

        // Explains the map to whoever is looking at it, so it is gated only on showLegend — not
        // on editor vs. player mode, unlike the back/re-arrange buttons below. Bounded to the
        // canvas's own area, which is now everything right of the sidebar: the detail view is a
        // window rather than a docked strip, so nothing on the right needs keeping clear.
        if (GraphConfig.GRAPH.showLegend.get()) {
            if (legend == null) legend = new GraphLegend();
            legend.setBounds(sidebarW, contentTop, Math.max(0, this.width - sidebarW), contentHeight);
        } else {
            legend = null;
        }

        if (editorView) {
            // Player view relies on ESC (its parent is the pause screen) and must not offer any
            // editor-style navigation or authoring control.
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> this.minecraft.setScreen(parent),
                    BACK_BUTTON_X, BACK_BUTTON_Y, BACK_BUTTON_W, BACK_BUTTON_H));
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.graph.rearrange"),
                    btn -> confirmRearrange(),
                    REARRANGE_BUTTON_X, BACK_BUTTON_Y, REARRANGE_BUTTON_W, BACK_BUTTON_H));
        } else if (GraphConfig.GRAPH.fitOnOpen.get()) {
            // Without this a filtered map opens on empty space: positions are frozen, so
            // filtered-out stages leave real gaps rather than shrinking the layout.
            canvas.fitToContent();
        }
    }

    private StageGraphModel buildModel() {
        boolean editorView = mode == Mode.EDITOR;
        GraphViewFilter filter = editorView ? GraphViewFilter.passThrough() : GraphViewFilter.fromConfig();
        return StageGraphModel.build(filter, editorView);
    }

    /**
     * Rebuilds the model when the player's unlocked stages change while the graph is open.
     *
     * <p>Both a node's colour and an edge's met/open state are baked into the model at build
     * time, and the model was only ever built in {@link #init}. Unlocking a stage with the graph
     * open therefore left the picture half-updated until it was reopened — the very moment the
     * view is most worth watching.
     */
    private void refreshOnUnlockChange() {
        int version = ClientStageCache.version() + ClientIndividualStageCache.version();
        if (version == lastUnlockVersion) return;
        lastUnlockVersion = version;

        model = buildModel();
        canvas.setModel(model);
        if (sidebar != null) sidebar.setModel(model);
        // Styles are resolved per lock state, so every cached one is now suspect.
        StageGraphConfig.invalidateCache();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op — render() draws its own opaque background; this avoids 1.21's menu blur shader.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);

        refreshOnUnlockChange();

        // The sidebar can be collapsed to a rail, and animates between the two widths. Its edge
        // is where the canvas viewport starts, so both it and the legend are re-bounded every
        // frame; setBounds only moves the viewport and leaves pan and zoom untouched, so the map
        // holds still and simply reveals more area on the left.
        if (sidebar != null) {
            int sidebarW = sidebar.advanceWidth();
            int contentHeight = Math.max(0, this.height - TOP_BAR_H);
            canvas.setBounds(sidebarW, TOP_BAR_H, Math.max(0, this.width - sidebarW), contentHeight);
            if (legend != null) {
                legend.setBounds(sidebarW, TOP_BAR_H, Math.max(0, this.width - sidebarW), contentHeight);
            }
        }

        canvas.render(g, this.font, mouseX, mouseY, partialTick);
        if (sidebar != null) {
            sidebar.render(g, this.font, mouseX, mouseY);
        }
        if (legend != null) {
            legend.render(g, this.font, mouseX, mouseY);
        }

        g.drawCenteredString(this.font, this.title, this.width / 2, 8, TITLE_COLOR);

        super.render(g, mouseX, mouseY, partialTick); // draws the back/re-arrange buttons, editor mode only

        // Rendered last, above everything above, with a z-translate — same convention as
        // StageOverviewScreen's context menu.
        if (contextMenu != null && contextMenu.isVisible()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            contextMenu.render(g, this.font, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    // --- Input --------------------------------------------------------------------------------
    //
    // Routing order is context menu, sidebar, legend, canvas: the context menu is rendered last
    // (on top of everything) and must claim a click anywhere before anything under it reacts —
    // same convention StageOverviewScreen uses. The sidebar sits in front of the canvas, so it
    // gets its turn first. The legend floats over the canvas area, so it is checked right before
    // the canvas — a header toggle or a click on its open body must never fall through to a node
    // underneath. The detail view is a separate screen and takes no part in this routing.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu != null && contextMenu.isVisible()) {
            contextMenu.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (sidebar != null && sidebar.mouseClicked(mouseX, mouseY, button)) return true;
        if (legend != null && legend.mouseClicked(mouseX, mouseY, button)) return true;
        if (mode == Mode.EDITOR && button == 1 && tryOpenContextMenu(mouseX, mouseY)) return true;
        if (handleCanvasClick(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Right-click on a node: builds and shows {@link #contextMenu}. Editor mode only. */
    private boolean tryOpenContextMenu(double mouseX, double mouseY) {
        String hit = canvas.nodeAt(mouseX, mouseY);
        if (hit == null) return false;
        StageGraphModel.Node node = model.nodes().get(hit);
        if (node == null) return false;

        contextMenu = new ContextMenu();
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.edit_stage").getString(),
                () -> openStageEditor(node));
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.edit_info").getString(),
                () -> this.minecraft.setScreen(new StageInfoTextScreen(this, node.stageId(), node.individual())));
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.edit_style").getString(),
                () -> this.minecraft.setScreen(new StageStyleScreen(this, node.stageId(), node.individual())));
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.copy_style").getString(),
                () -> copyStyle(node));
        if (!StageStyleClipboard.isEmpty()) {
            contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.paste_style").getString(),
                    () -> pasteStyle(node));
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        contextMenu.show((int) mouseX, (int) mouseY, this.font);
        return true;
    }

    private void openStageEditor(StageGraphModel.Node node) {
        Map<String, StageEntry> stages = node.individual()
                ? StageManager.getIndividualStages() : StageManager.getStages();
        StageEntry entry = stages.get(node.stageId());
        if (entry == null) return; // stage vanished (e.g. deleted from another client) between hit and click
        this.minecraft.setScreen(new StageDetailScreen(this, node.stageId(), entry, node.individual()));
    }

    /**
     * A node with nothing to copy leaves the clipboard alone. Overwriting it with an empty set
     * would destroy a style copied two nodes ago and make "Paste style" disappear from the menu,
     * with nothing on screen to explain either — so both outcomes say so with a toast.
     */
    private void copyStyle(StageGraphModel.Node node) {
        GraphStageData.Entry entry =
                GraphStageData.get().tree(node.individual()).get(node.stageId());
        if (entry == null || !entry.hasStyles()) {
            EditorToastHandler.show(EditorToast.Level.INFO,
                    Component.translatable("editor.historystages.graph.style.copy.empty.title"),
                    Component.translatable("editor.historystages.graph.style.copy.empty.message"));
            return;
        }
        StageStyleClipboard.copy(entry);
        EditorToastHandler.copiedToClipboard(node.stageId());
    }

    /**
     * Writes the clipboard onto a node, replacing whatever it had — a paste that left half the
     * old look in place would not be a paste.
     *
     * <p>Asks only when something would actually be lost. Always asking would make the case this
     * menu exists for, bringing several nodes into line quickly, tedious; never asking means a
     * misclick silently destroys hand-set values with no way back.
     */
    private void pasteStyle(StageGraphModel.Node node) {
        GraphStageData.Entry clipboard = StageStyleClipboard.get();
        if (clipboard == null) return;

        GraphStageData.Entry existing =
                GraphStageData.get().tree(node.individual()).get(node.stageId());
        boolean wouldOverwrite = existing != null && !existing.copyStyles().isEmpty();

        if (!wouldOverwrite) {
            applyPaste(node, clipboard);
            return;
        }

        this.minecraft.setScreen(new ConfirmDialog(this,
                Component.translatable("editor.historystages.graph.style.paste.title"),
                Component.translatable("editor.historystages.graph.style.paste.confirm"),
                () -> {
                    applyPaste(node, clipboard);
                    this.minecraft.setScreen(this);
                }));
    }

    private void applyPaste(StageGraphModel.Node node, GraphStageData.Entry clipboard) {
        PacketHandler.sendToServer(new SaveStageGraphStylePacket(
                node.stageId(), node.individual(), GraphStageData.entryToJson(clipboard)));
        // Same optimistic update the style screen does, and the same reason: on a dedicated
        // server the node would otherwise keep its old look until the broadcast returns.
        GraphStageData.set(GraphStageData.get()
                .withStyle(node.stageId(), node.individual(), clipboard));
        StageGraphConfig.invalidateCache();
    }

    /**
     * Forwards the click to {@link GraphCanvas#mouseClicked}, which arms panning on empty space.
     * Returns false untouched when the click was outside the canvas viewport, so callers never
     * mistake an off-canvas click for one on the map.
     */
    private boolean handleCanvasClick(double mouseX, double mouseY, int button) {
        if (!canvas.mouseClicked(mouseX, mouseY, button)) return false;
        // Opening deliberately does NOT happen here — see mouseReleased. Opening the detail
        // window on press means grabbing a node to move it throws a modal up over the very area
        // you were about to drag into.
        if (button == 0) {
            pressX = mouseX;
            pressY = mouseY;
            pressedOnCanvas = true;
        }
        return true;
    }

    /**
     * Opens the detail window on a node, asking the server for its dependency data first when
     * the cache has none.
     *
     * <p>The request needs no Research Pedestal and goes out at most once per stage per open
     * screen. The window rebuilds its rows whenever the cached result changes, so a reply that
     * lands while it is already up fills it in.
     */
    private void openDetail(String graphKey) {
        StageGraphModel.Node node = model.nodes().get(graphKey);
        if (node == null) return; // vanished between hit test and release

        if (ClientDependencyCache.get(node.stageId(), node.individual()) == null
                && pendingDependencyRequests.add(graphKey)) {
            PacketHandler.sendToServer(new RequestStageDependencyPacket(node.stageId(), node.individual()));
        }
        this.minecraft.setScreen(new GraphDetailScreen(this, model, node));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Before the canvas: a scrollbar drag starts inside the sidebar but travels well past
        // its edges, and must not turn into a pan the moment the cursor leaves the strip.
        if (sidebar != null && sidebar.mouseDragged(mouseY)) return true;
        if (canvas.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (sidebar != null) sidebar.mouseReleased();
        boolean handled = canvas.mouseReleased(mouseX, mouseY, button);

        // A press that never turned into a drag is a click, and only then does the window open.
        // The same threshold the canvas uses to promote a press into a drag, so the two agree
        // on where a click stops being a click.
        if (pressedOnCanvas && button == 0) {
            pressedOnCanvas = false;
            double dx = mouseX - pressX;
            double dy = mouseY - pressY;
            if (dx * dx + dy * dy <= (double) CLICK_SLOP * CLICK_SLOP) {
                String hit = canvas.nodeAt(mouseX, mouseY);
                canvas.highlight(hit);
                if (hit != null) openDetail(hit);
            }
            return true;
        }
        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (sidebar != null && sidebar.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (canvas.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (sidebar != null && sidebar.charTyped(codePoint)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The sidebar only consumes ESC while its filter dropdown is open; otherwise it falls
        // through to super, whose default shouldCloseOnEsc()/onClose() closes this screen.
        if (sidebar != null && sidebar.keyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // --- Authoring: drag/freeze, re-arrange -----------------------------------------------

    /**
     * {@link GraphCanvas.DragHandler}: a drag just completed. The canvas has not moved anything
     * yet — {@code commit} does that — so an already-frozen tree commits and saves immediately,
     * while an unfrozen tree is gated behind a confirmation, and declining it leaves the canvas
     * untouched.
     */
    private void handleDrop(boolean individual, boolean frozen, Map<String, GraphPos> positions, Runnable commit) {
        if (frozen) {
            commit.run();
            sendPositions(individual, positions);
            return;
        }

        this.minecraft.setScreen(new ConfirmDialog(this,
                Component.translatable("editor.historystages.graph.freeze.title"),
                Component.translatable("editor.historystages.graph.freeze.confirm"),
                () -> {
                    commit.run();
                    sendPositions(individual, positions);
                    this.minecraft.setScreen(this);
                }));
    }

    private void sendPositions(boolean individual, Map<String, GraphPos> positions) {
        PacketHandler.sendToServer(new SaveGraphPositionsPacket(individual, positions));
        // Optimistic local update, mirroring GraphLayoutData.freeze's in-memory effect only —
        // saving graph_layout.json is the server's job (the packet handler already does it);
        // doing it here too would write an unwanted copy into this client's own settings folder.
        GraphLayoutData.Snapshot cur = GraphLayoutData.get();
        Map<String, GraphPos> copy = new HashMap<>(positions);
        // Sending positions IS freezing, so the flag flips here too — otherwise the confirmation
        // would come back on the very next drag.
        GraphLayoutData.set(individual
                ? new GraphLayoutData.Snapshot(cur.global(), copy, cur.globalFrozen(), true)
                : new GraphLayoutData.Snapshot(copy, cur.individual(), true, cur.individualFrozen()));
    }

    private void confirmRearrange() {
        this.minecraft.setScreen(new ConfirmDialog(this,
                Component.translatable("editor.historystages.graph.rearrange"),
                Component.translatable("editor.historystages.graph.rearrange.confirm"),
                this::performRearrange));
    }

    private void performRearrange() {
        PacketHandler.sendToServer(new RearrangeGraphPacket(false));
        PacketHandler.sendToServer(new RearrangeGraphPacket(true));

        // Optimistic local preview: GraphAutoLayout is pure, side-effect-free logic shared by
        // both sides, and StageManager.recomputeGraphLayout() deliberately never saves — calling
        // it here mirrors exactly what the server just kicked off, in memory only, so the screen
        // updates immediately (via the re-init below) instead of sitting stale until the round
        // trip completes. The eventual SyncStageDefinitionsPacket reply overwrites this with the
        // authoritative result.
        GraphLayoutData.set(GraphLayoutData.Snapshot.empty());
        StageManager.recomputeGraphLayout();

        this.minecraft.setScreen(this);
    }
}
