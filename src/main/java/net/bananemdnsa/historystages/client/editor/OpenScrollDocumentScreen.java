package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.ClientToastHandler;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.graph.GraphColors;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapterEntry;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapterMode;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapters;
import net.bananemdnsa.historystages.data.scroll.OpenScrollOverviewBlockEntry;
import net.bananemdnsa.historystages.data.scroll.OpenScrollOverviewBlocks;
import net.bananemdnsa.historystages.screen.OpenScrollGeometry;
import net.bananemdnsa.historystages.screen.OpenScrollTabs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor for what the open scroll document shows: its chapters and its overview blocks, both in
 * order. Two lists rather than two screens, because they answer the same question and a pack maker
 * setting one almost always wants to see the other.
 *
 * <p>Edits the {@code openScrollChapters} and {@code openScrollOverviewBlocks} entries owned by
 * {@link ConfigEditorScreen} directly, so one Save there covers both and the unsaved-changes
 * marker stays honest — the same arrangement {@link GraphStyleScreen} uses.
 *
 * <p>Chrome, spacing and controls follow this editor's usual drag-and-drop list convention: same
 * row height, same handle, same checkbox, same section rule, same button band as the rest of the
 * config editor's sub-screens.
 */
public class OpenScrollDocumentScreen extends Screen {

    private static final ResourceLocation SHEET =
            new ResourceLocation(HistoryStages.MOD_ID, "textures/gui/scroll/open_gui.png");

    private static final int ROWS_TOP = 30;
    private static final int BUTTON_BAND = 40;
    private static final int ROW_H = 24;
    private static final int SECTION_HEADER_H = 18;
    private static final int SECTION_GAP = 10;

    private static final int HANDLE_W = 12;
    private static final int CHECKBOX_W = 12;
    private static final int CONTROL_GAP = 3;
    private static final int RESET_W = 10;
    private static final int RESET_GUTTER_W = RESET_W + 4;

    /** Width of the icons/text switch. */
    private static final int MODE_W = 44;

    private static final int DRAG_THRESHOLD = 3;

    /** Section indices, used for drag scoping — a row never moves between the two lists. */
    private static final int SEC_CHAPTERS = 0;
    private static final int SEC_BLOCKS = 1;

    private final ConfigEditorScreen parent;
    private final ConfigEditorScreen.ConfigEntry chaptersEntry;
    private final ConfigEditorScreen.ConfigEntry blocksEntry;

    private final List<OpenScrollChapterEntry> chapters = new ArrayList<>();
    private final List<OpenScrollOverviewBlockEntry> blocks = new ArrayList<>();

    private final EditorTooltip tooltip = new EditorTooltip();
    private final Map<String, Anim> rowHover = new HashMap<>();

    private int rowsBottom;
    private int maxScroll;
    private double scrollOffset;
    private final Anim smoothScroll = new Anim();
    private boolean draggingScrollbar;

    // --- drag & drop ---
    private int dragSection = -1;
    private boolean dragArmed;
    private boolean dragStarted;
    private int dragFrom = -1;
    private int dragTarget = -1;
    private double pressX;
    private double pressY;

    private String pulseId;
    private long pulseStart;

    /** One laid-out row: which list it belongs to, its index in that list, and its screen y. */
    private record PositionedRow(int section, int index, String id, int y) {}

    public OpenScrollDocumentScreen(ConfigEditorScreen parent) {
        super(Component.translatable("editor.historystages.open_scroll.title"));
        this.parent = parent;
        this.chaptersEntry = parent.findCommonEntry("openScrollChapters");
        this.blocksEntry = parent.findCommonEntry("openScrollOverviewBlocks");
        chapters.addAll(OpenScrollChapters.parse(split(chaptersEntry.value)));
        blocks.addAll(OpenScrollOverviewBlocks.parse(split(blocksEntry.value)));
    }

    /** The editor keeps list values as one ';'-joined string, the way CommonConfigSync sends them. */
    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(";"));
    }

    /**
     * Writes both lists back into the parent's entries. Called after every edit rather than on a
     * Save button of our own, because the parent owns saving and must never see a stale value.
     */
    private void writeBack() {
        chaptersEntry.value = String.join(";", chapters.stream().map(OpenScrollChapters::encode).toList());
        blocksEntry.value = String.join(";", blocks.stream().map(OpenScrollOverviewBlocks::encode).toList());
    }

    @Override
    protected void init() {
        rowsBottom = this.height - BUTTON_BAND;
        updateMaxScroll();

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> onClose(), 10, this.height - 30, 60, 20));

        // Saves through the config editor, so this covers every tab and not just the two lists on
        // screen. Deliberately does not navigate: the point of saving here is to keep tweaking.
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> parent.saveConfig(), this.width / 2 - 50, this.height - 30, 100, 20));

        // Behind a confirm because it throws away both lists at once, unlike a single toggle.
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.reset"),
                btn -> this.minecraft.setScreen(new ConfirmDialog(
                        this,
                        Component.translatable("editor.historystages.open_scroll.reset_all_title"),
                        Component.translatable("editor.historystages.open_scroll.reset_all"),
                        () -> {
                            resetAll();
                            this.minecraft.setScreen(this);
                        })),
                this.width - 70, this.height - 30, 60, 20));
    }

    private void resetAll() {
        chapters.clear();
        chapters.addAll(OpenScrollChapters.defaults());
        blocks.clear();
        blocks.addAll(OpenScrollOverviewBlocks.defaults());
        writeBack();
    }

    // --- layout ---

    private int contentLeft() { return 24; }

    private int contentRight() { return this.width / 2 + 40; }

    private int scrollbarX() { return contentRight() + 8; }

    private int rowContentLeft() { return contentLeft() + RESET_GUTTER_W; }

    private int totalContentHeight() {
        return 2 * SECTION_HEADER_H + (chapters.size() + blocks.size()) * ROW_H + SECTION_GAP;
    }

    private void updateMaxScroll() {
        maxScroll = Math.max(0, totalContentHeight() - (rowsBottom - ROWS_TOP));
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private int headerY(int section) {
        int y = ROWS_TOP - Math.round(smoothScroll.value());
        if (section == SEC_CHAPTERS) return y;
        return y + SECTION_HEADER_H + chapters.size() * ROW_H + SECTION_GAP;
    }

    private int sectionTop(int section) {
        return headerY(section) + SECTION_HEADER_H;
    }

    private int sectionSize(int section) {
        return section == SEC_CHAPTERS ? chapters.size() : blocks.size();
    }

    private String rowId(int section, int index) {
        return section == SEC_CHAPTERS
                ? "chapter." + chapters.get(index).chapter().serialize()
                : "block." + blocks.get(index).block().serialize();
    }

    /**
     * Where a row visually sits while a drag runs: shifted one slot towards the gap the dragged
     * row left behind, or unchanged outside that span.
     */
    private int visualSlot(int section, int index) {
        if (!dragStarted || section != dragSection) return index;
        if (dragFrom < dragTarget) {
            if (index > dragFrom && index <= dragTarget) return index - 1;
        } else if (dragFrom > dragTarget) {
            if (index >= dragTarget && index < dragFrom) return index + 1;
        }
        return index;
    }

    private List<PositionedRow> layoutRows() {
        List<PositionedRow> out = new ArrayList<>();
        for (int section = SEC_CHAPTERS; section <= SEC_BLOCKS; section++) {
            int top = sectionTop(section);
            for (int i = 0; i < sectionSize(section); i++) {
                out.add(new PositionedRow(section, i, rowId(section, i),
                        top + visualSlot(section, i) * ROW_H));
            }
        }
        return out;
    }

    private int computeDragTarget(double mouseY) {
        int rel = (int) Math.floor((mouseY - sectionTop(dragSection)) / (double) ROW_H);
        return Math.max(0, Math.min(sectionSize(dragSection) - 1, rel));
    }

    // --- rendering ---

    // No renderBackground override here: unlike 1.21, Screen#render on 1.20.1 never calls it
    // automatically, so overriding it would be dead code. The dimmed backdrop below is drawn
    // directly at the top of render() instead.

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);
        if (dragStarted) dragTarget = computeDragTarget(mouseY);

        g.fill(0, 0, this.width, this.height, 0xE0101010);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int left = contentLeft();
        int right = contentRight();
        g.enableScissor(left - 4, ROWS_TOP, right + 4, rowsBottom);

        drawSectionHeader(g, "editor.historystages.open_scroll.chapters", headerY(SEC_CHAPTERS));
        drawSectionHeader(g, "editor.historystages.open_scroll.blocks", headerY(SEC_BLOCKS));

        String hoveredControlKey = null;
        int rowLeft = rowContentLeft();
        int checkboxHoverX = rowLeft + HANDLE_W + CONTROL_GAP;
        for (PositionedRow row : layoutRows()) {
            boolean visible = row.y() + ROW_H > ROWS_TOP && row.y() < rowsBottom;
            if (!visible) continue;
            renderRow(g, row, mouseX, mouseY);

            if (mouseX >= left && mouseX <= right
                    && mouseY >= Math.max(row.y(), ROWS_TOP)
                    && mouseY < Math.min(row.y() + ROW_H, rowsBottom)) {
                if (mouseX >= rowLeft && mouseX < rowLeft + HANDLE_W) {
                    hoveredControlKey = "editor.historystages.open_scroll.reorder";
                } else if (mouseX >= checkboxHoverX && mouseX < checkboxHoverX + CHECKBOX_W) {
                    hoveredControlKey = "editor.historystages.open_scroll.enabled";
                } else if (row.section() == SEC_CHAPTERS && mouseX >= modeX()) {
                    hoveredControlKey = chapters.get(row.index()).chapter().isTextOnly()
                            ? "editor.historystages.open_scroll.mode_fixed"
                            : "editor.historystages.open_scroll.mode";
                }
            }
        }

        if (pulseId != null) {
            long age = System.currentTimeMillis() - pulseStart;
            if (age >= Timing.DROP_PULSE_MS) {
                pulseId = null;
            } else {
                for (PositionedRow row : layoutRows()) {
                    if (!row.id().equals(pulseId)) continue;
                    if (row.y() + ROW_H > ROWS_TOP && row.y() < rowsBottom) {
                        drawPulseOutline(g, row.y(), Ease.pulse((float) age / Timing.DROP_PULSE_MS));
                    }
                    break;
                }
            }
        }

        g.disableScissor();

        if (maxScroll > 0) {
            int track = rowsBottom - ROWS_TOP;
            int barH = Math.max(20, track * track / (maxScroll + track));
            int barY = ROWS_TOP + Math.round(smoothScroll.value() / maxScroll * (track - barH));
            g.fill(scrollbarX(), ROWS_TOP, scrollbarX() + 3, rowsBottom, 0x20FFFFFF);
            g.fill(scrollbarX(), barY, scrollbarX() + 3, barY + barH, 0x80FFFFFF);
        }

        renderPreview(g, contentRight() + 30, ROWS_TOP, rowsBottom - ROWS_TOP);

        if (dragStarted) drawGhost(g, mouseX, mouseY);

        if (parent.hasChanges()) {
            int dotX = this.width / 2 + 55;
            float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS) / Timing.BREATHE_PERIOD_MS;
            g.fill(dotX, this.height - 25, dotX + 6, this.height - 19,
                    Fade.rgba(0xFFCC00, 0.4f + 0.6f * Ease.breathe(phase)));
            ConfigEditorScreen.drawSmallText(g,
                    Component.translatable("editor.historystages.unsaved").getString(),
                    dotX + 9, this.height - 24, 0xFFCC00);
        }

        super.render(g, mouseX, mouseY, partialTick);

        tooltip.render(g, this.font, hoveredControlKey,
                hoveredControlKey == null ? null : Component.translatable(hoveredControlKey).getString(),
                mouseX, mouseY, this.width, this.height);
    }

    private void drawSectionHeader(GuiGraphics g, String key, int y) {
        if (y + SECTION_HEADER_H < ROWS_TOP || y > rowsBottom) return;
        g.fill(contentLeft(), y + 13, contentRight(), y + 14, 0xFF444444);
        g.drawString(this.font, Component.translatable(key).getString(),
                contentLeft(), y + 2, 0xFF888888, false);
    }

    private int modeX() { return contentRight() - MODE_W - 4; }

    private void renderRow(GuiGraphics g, PositionedRow row, int mouseX, int mouseY) {
        int y = row.y();
        int left = contentLeft();
        int right = contentRight();

        boolean hovered = mouseX >= left && mouseX <= right
                && mouseY >= Math.max(y, ROWS_TOP) && mouseY < Math.min(y + ROW_H, rowsBottom);
        float hp = Ease.outCubic(rowHover.computeIfAbsent(row.id(), k -> new Anim())
                .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        if (hp > 0.001f) {
            g.fill(left, y, right, y + ROW_H - 2, Fade.rgba(0xFFFFFF, 0.08f * hp));
            g.fill(left, y, left + 1, y + ROW_H - 2, Fade.rgba(0xFFCC00, hp * 0.8f));
        }

        int cx = rowContentLeft();
        boolean onHandle = mouseX >= cx && mouseX < cx + HANDLE_W && mouseY >= y && mouseY < y + ROW_H;
        drawHandle(g, cx + 1, y + (ROW_H - 12) / 2, onHandle);
        cx += HANDLE_W + CONTROL_GAP;

        boolean enabled = row.section() == SEC_CHAPTERS
                ? chapters.get(row.index()).enabled()
                : blocks.get(row.index()).enabled();
        drawCheckbox(g, cx, y + (ROW_H - 10) / 2, enabled);
        cx += CHECKBOX_W + CONTROL_GAP;

        String label = Component.translatable(row.section() == SEC_CHAPTERS
                ? "gui.historystages.open_scroll." + row.id()
                : "editor.historystages.open_scroll." + row.id()).getString();
        g.drawString(this.font, label, cx, y + (ROW_H - 8) / 2,
                enabled ? (hp > 0.001f ? 0xFFFFFF : 0xDDDDDD) : 0xFF777777, false);

        if (row.section() == SEC_CHAPTERS) {
            OpenScrollChapterEntry entry = chapters.get(row.index());
            // Overview and world have no icon form. The switch is shown disabled rather than
            // hidden: a missing control reads as a bug, a greyed one reads as a rule.
            boolean fixed = entry.chapter().isTextOnly();
            int mx = modeX();
            int my = y + (ROW_H - 14) / 2;
            boolean modeHovered = !fixed && mouseX >= mx && mouseX < mx + MODE_W
                    && mouseY >= y && mouseY < y + ROW_H;
            g.fill(mx, my, mx + MODE_W, my + 14, modeHovered ? 0xFF3A3A3A : 0xFF2A2A2A);
            int border = fixed ? 0xFF444444 : (modeHovered ? 0xFFFFCC00 : 0xFF555555);
            g.fill(mx, my, mx + MODE_W, my + 1, border);
            g.fill(mx, my + 13, mx + MODE_W, my + 14, border);
            g.fill(mx, my, mx + 1, my + 14, border);
            g.fill(mx + MODE_W - 1, my, mx + MODE_W, my + 14, border);
            String mode = Component.translatable("editor.historystages.open_scroll.mode."
                    + entry.mode().serialize()).getString();
            g.drawString(this.font, this.font.plainSubstrByWidth(mode, MODE_W - 8),
                    mx + 4, my + 3, fixed ? 0xFF777777 : 0xFFDDDDDD, false);
        }
    }

    private void drawHandle(GuiGraphics g, int x, int y, boolean active) {
        int color = active ? 0xFFEEEEEE : 0xFF888888;
        for (int col = 0; col < 2; col++) {
            for (int row = 0; row < 3; row++) {
                int dx = x + col * 4;
                int dy = y + row * 4;
                g.fill(dx, dy, dx + 2, dy + 2, color);
            }
        }
    }

    private void drawCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        int border = checked ? 0xFFFFCC00 : 0xFF777777;
        g.fill(x, y, x + 10, y + 10, 0x40000000);
        g.fill(x, y, x + 10, y + 1, border);
        g.fill(x, y + 9, x + 10, y + 10, border);
        g.fill(x, y, x + 1, y + 10, border);
        g.fill(x + 9, y, x + 10, y + 10, border);
        if (checked) g.fill(x + 2, y + 2, x + 8, y + 8, 0xFFFFCC00);
    }

    private void drawPulseOutline(GuiGraphics g, int y, float strength) {
        int alpha = (int) (0xFF * Math.max(0.0f, Math.min(1.0f, strength)));
        if (alpha < 4) return;
        int x1 = contentLeft() - 2, x2 = contentRight() + 2, y1 = y - 1, y2 = y + ROW_H - 1;
        int rgb = 0xFFCC00;
        g.fill(x1, y1, x2, y1 + 1, (alpha << 24) | rgb);
        g.fill(x1, y2 - 1, x2, y2, (alpha << 24) | rgb);
        g.fill(x1, y1, x1 + 1, y2, (alpha << 24) | rgb);
        g.fill(x2 - 1, y1, x2, y2, (alpha << 24) | rgb);
    }

    private void drawGhost(GuiGraphics g, int mouseX, int mouseY) {
        String id = rowId(dragSection, dragFrom);
        String label = Component.translatable(dragSection == SEC_CHAPTERS
                ? "gui.historystages.open_scroll." + id
                : "editor.historystages.open_scroll." + id).getString();
        int gw = this.font.width(label) + 10;
        int gx = Math.min(mouseX + 8, this.width - gw - 4);
        int gy = Math.min(mouseY - 8, this.height - 18);
        g.pose().pushPose();
        g.pose().translate(0, 0, 410);
        g.fill(gx, gy, gx + gw, gy + 16, 0xE0101010);
        g.fill(gx, gy + 15, gx + gw, gy + 16, 0xFFFFCC00);
        g.drawString(this.font, label, gx + 5, gy + 4, 0xFFFFFF, false);
        g.pose().popPose();
    }

    /**
     * Live parchment built from the edited lists against an invented sample stage rather than real
     * pack data — otherwise which states are visible would depend on what happens to be installed,
     * and half of them would never show. Framed and scissored to its own panel so a long line is
     * clipped instead of escaping over the row list.
     */
    private void renderPreview(GuiGraphics g, int px, int py, int panelH) {
        int panelW = Math.max(0, this.width - px - 24);
        if (panelW < 60 || panelH < 60) return;

        g.fill(px, py, px + panelW, py + panelH, 0xFF181818);
        g.fill(px, py, px + panelW, py + 1, 0xFF444444);
        g.fill(px, py + panelH - 1, px + panelW, py + panelH, 0xFF444444);
        g.fill(px, py, px + 1, py + panelH, 0xFF444444);
        g.fill(px + panelW - 1, py, px + panelW, py + panelH, 0xFF444444);

        g.enableScissor(px + 1, py + 1, px + panelW - 1, py + panelH - 1);

        // The parchment is 186x218; at the GUI scales players actually use, the pane beside the
        // row list is narrower than that. Scaling it down beats clipping it — and beats the
        // earlier "too small, draw nothing", which is why the preview was simply absent.
        float scale = Math.min(1.0f, Math.min((panelW - 12.0f) / OpenScrollGeometry.WIDTH,
                (panelH - 12.0f) / OpenScrollGeometry.HEIGHT));
        g.pose().pushPose();
        g.pose().translate(px + (panelW - OpenScrollGeometry.WIDTH * scale) / 2.0f, py + 6.0f, 0.0f);
        g.pose().scale(scale, scale, 1.0f);

        int sx = 0;
        int sy = 0;
        g.blit(SHEET, sx - OpenScrollGeometry.SHEET_X, sy - OpenScrollGeometry.SHEET_Y,
                0, 0, OpenScrollGeometry.SHEET_SIZE, OpenScrollGeometry.SHEET_SIZE,
                OpenScrollGeometry.SHEET_SIZE, OpenScrollGeometry.SHEET_SIZE);

        int x = sx + OpenScrollGeometry.CONTENT_X;
        int width = OpenScrollGeometry.CONTENT_WIDTH;

        // Ink comes from the live rows, not from constants: those colours are edited two screens
        // away in this very editor, and a preview that ignored them would be lying.
        int inkHeading = ink("openScrollInkHeading", 0x3F2D13);
        int inkBody = ink("openScrollInkBody", 0x4A3416);
        int inkFaint = ink("openScrollInkFaint", 0x7A5A2C);

        List<String> labels = new ArrayList<>();
        for (OpenScrollChapterEntry entry : chapters) {
            if (!entry.enabled()) continue;
            labels.add(Component.translatable("gui.historystages.open_scroll.chapter."
                    + entry.chapter().serialize()).getString());
        }
        // The first enabled chapter is what a reader opens on, so that is what the preview shows.
        List<OpenScrollTabs.Tab> tabs = OpenScrollTabs.layout(labels, 0, width, this.font::width);
        int ty = sy + OpenScrollGeometry.TABS_Y;
        for (int i = 0; i < tabs.size(); i++) {
            OpenScrollTabs.Tab tab = tabs.get(i);
            g.drawString(this.font, tab.label(), x + tab.x(), ty,
                    0xFF000000 | (i == 0 ? inkHeading : inkFaint), false);
            if (i == 0) {
                g.fill(x + tab.x(), ty + OpenScrollGeometry.TABS_HEIGHT,
                        x + tab.x() + tab.width(), ty + OpenScrollGeometry.TABS_HEIGHT + 1,
                        0xFF000000 | inkHeading);
            }
        }
        g.fill(x, sy + OpenScrollGeometry.RULE_TOP_Y, x + width,
                sy + OpenScrollGeometry.RULE_TOP_Y + 1, (0x59 << 24) | inkFaint);

        // The overview page has no search line, but the preview shows the one the other chapters
        // get, because turning it off is a decision made in this editor too.
        boolean searchShown = "true".equalsIgnoreCase(
                parent.findCommonEntry("openScrollShowSearch").value);
        if (searchShown) {
            int qy = sy + OpenScrollGeometry.SEARCH_Y;
            g.drawString(this.font, "»", x, qy, 0xFF000000 | inkFaint, false);
            g.fill(x + 9, qy + 9, x + width, qy + 10, (0x73 << 24) | inkFaint);
        }

        int y = sy + OpenScrollGeometry.RULE_TOP_Y + 4;
        for (OpenScrollOverviewBlockEntry entry : blocks) {
            if (!entry.enabled()) continue;
            y = switch (entry.block()) {
                case ICON -> {
                    // A real item, drawn at the real 32px, rather than a coloured rectangle.
                    ItemStack icon = ClientToastHandler.resolveIcon(Config.COMMON.defaultStageIcon.get());
                    g.pose().pushPose();
                    g.pose().translate((float) (x + (width - 32) / 2), (float) y, 0.0f);
                    g.pose().scale(2.0f, 2.0f, 1.0f);
                    g.renderItem(icon, 0, 0);
                    g.pose().popPose();
                    yield y + 34;
                }
                case TITLE -> {
                    Component t = Component.translatable("editor.historystages.open_scroll.sample_stage");
                    g.drawString(this.font, t, x + (width - this.font.width(t)) / 2, y,
                            0xFF000000 | inkHeading, false);
                    yield y + 11;
                }
                case DESCRIPTION -> {
                    // Real wrapped prose, wrapped at the real width. The ruled bars this used to
                    // draw showed the block's position but nothing about how it actually reads.
                    Component text = Component.translatable("editor.historystages.open_scroll.sample_description");
                    for (FormattedCharSequence line : this.font.split(text, width)) {
                        g.drawString(this.font, line, x, y, 0xFF000000 | inkBody, false);
                        y += 10;
                    }
                    yield y + 1;
                }
                case COUNTS -> {
                    // Three stacked lines and the real lang keys, matching the document exactly.
                    int[] values = {34, 6, 3};
                    String[] keys = {"items", "creatures", "world"};
                    for (int i = 0; i < keys.length; i++) {
                        Component line = Component.translatable(
                                "gui.historystages.open_scroll.counts." + keys[i], values[i]);
                        g.drawString(this.font, line, x + (width - this.font.width(line)) / 2, y,
                                0xFF000000 | inkFaint, false);
                        y += 10;
                    }
                    yield y + 1;
                }
            };
        }
        g.pose().popPose();
        g.disableScissor();
    }

    /** Current value of one ink row, so the preview follows a colour edit without a save. */
    private int ink(String key, int fallback) {
        return GraphColors.parse(parent.findCommonEntry(key).value, fallback);
    }

    // --- input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (maxScroll > 0 && mouseX >= scrollbarX() && mouseX < scrollbarX() + 3
                && mouseY >= ROWS_TOP && mouseY < rowsBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        if (mouseY >= ROWS_TOP && mouseY < rowsBottom
                && mouseX >= contentLeft() && mouseX <= contentRight()) {
            for (PositionedRow row : layoutRows()) {
                if (mouseY < row.y() || mouseY >= row.y() + ROW_H) continue;

                int cx = rowContentLeft();
                if (mouseX >= cx && mouseX < cx + HANDLE_W) {
                    dragSection = row.section();
                    dragArmed = true;
                    dragFrom = row.index();
                    dragTarget = row.index();
                    pressX = mouseX;
                    pressY = mouseY;
                    return true;
                }
                cx += HANDLE_W + CONTROL_GAP;
                if (mouseX >= cx && mouseX < cx + CHECKBOX_W) {
                    toggleEnabled(row.section(), row.index());
                    return true;
                }
                if (row.section() == SEC_CHAPTERS && mouseX >= modeX() && mouseX < modeX() + MODE_W) {
                    toggleMode(row.index());
                    return true;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleEnabled(int section, int index) {
        if (section == SEC_CHAPTERS) {
            OpenScrollChapterEntry e = chapters.get(index);
            chapters.set(index, new OpenScrollChapterEntry(e.chapter(), !e.enabled(), e.mode()));
        } else {
            OpenScrollOverviewBlockEntry e = blocks.get(index);
            blocks.set(index, new OpenScrollOverviewBlockEntry(e.block(), !e.enabled()));
        }
        writeBack();
    }

    private void toggleMode(int index) {
        OpenScrollChapterEntry e = chapters.get(index);
        if (e.chapter().isTextOnly()) return;
        OpenScrollChapterMode next = e.mode() == OpenScrollChapterMode.ICONS
                ? OpenScrollChapterMode.TEXT : OpenScrollChapterMode.ICONS;
        chapters.set(index, new OpenScrollChapterEntry(e.chapter(), e.enabled(), next));
        writeBack();
    }

    /**
     * Maps the cursor onto the scroll range using the travel the thumb actually has, not the whole
     * track. Ignoring the thumb's own height is what makes a bar feel like it lags the cursor:
     * grabbing the middle of the thumb at the top would jump it, and the ends become unreachable.
     */
    private void updateScrollFromMouse(double mouseY) {
        int track = rowsBottom - ROWS_TOP;
        int barH = Math.max(20, track * track / (maxScroll + track));
        double travel = Math.max(1, track - barH);
        double ratio = (mouseY - ROWS_TOP - barH / 2.0) / travel;
        scrollOffset = Math.max(0, Math.min(maxScroll, Math.round(ratio * maxScroll)));
        // Snapped rather than eased: while dragging, the thumb must sit under the cursor.
        smoothScroll.set((float) scrollOffset);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (dragArmed && !dragStarted
                && (Math.abs(mouseX - pressX) > DRAG_THRESHOLD || Math.abs(mouseY - pressY) > DRAG_THRESHOLD)) {
            dragStarted = true;
        }
        if (dragStarted) {
            dragTarget = computeDragTarget(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        if (dragStarted && dragFrom != dragTarget) {
            if (dragSection == SEC_CHAPTERS) chapters.add(dragTarget, chapters.remove(dragFrom));
            else blocks.add(dragTarget, blocks.remove(dragFrom));
            writeBack();
            pulseId = rowId(dragSection, dragTarget);
            pulseStart = System.currentTimeMillis();
        }
        dragArmed = false;
        dragStarted = false;
        dragSection = -1;
        dragFrom = -1;
        dragTarget = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll <= 0) return false;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
        return true;
    }

    @Override
    public void onClose() {
        writeBack();
        this.minecraft.setScreen(parent);
    }
}
