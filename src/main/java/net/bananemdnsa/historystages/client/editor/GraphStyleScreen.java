package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.dialog.ColorInputScreen;
import net.bananemdnsa.historystages.client.editor.graph.NodeShapes;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.client.editor.widget.list.ConfigRowList;
import net.bananemdnsa.historystages.data.graph.GraphColors;
import net.bananemdnsa.historystages.data.graph.ResolvedStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The six node-style blocks of {@code graph.toml}, one at a time, next to a live preview of the
 * node they describe.
 *
 * <p>Sixty keys as sixty rows in the Graph tab would be the same ten settings repeated six times.
 * Here the collection and the lock state are a pair of switches, and only the ten keys they
 * select are on screen.
 *
 * <p>The screen owns no values. It edits the very {@link ConfigEditorScreen.ConfigEntry} objects
 * the config editor holds, so one Save covers everything and the unsaved-changes marker keeps
 * telling the truth across both screens.
 */
public class GraphStyleScreen extends Screen {

    private static final List<String> COLLECTIONS = List.of("global", "individual");
    private static final List<String> STATES = List.of("unlocked", "reachable", "locked");

    /** Matches {@code GraphCanvas.BASE_NODE_RADIUS}, so the preview is the node at zoom 1. */
    private static final int BASE_NODE_RADIUS = 15;

    private static final int SWITCH_H = 16;
    private static final int SWITCH_GAP = 2;
    private static final int COLLECTION_Y = 28;
    private static final int STATE_Y = COLLECTION_Y + SWITCH_H + 4;
    private static final int ROWS_TOP = STATE_Y + SWITCH_H + 8;
    /** Space kept clear at the bottom for the Back button. */
    private static final int BUTTON_BAND = 40;
    /** Widest the two switch bars get; they shrink with a narrow window rather than overflow. */
    private static final int SWITCH_BAR_MAX_W = 300;

    private final ConfigEditorScreen parent;
    private final Map<String, List<ConfigEditorScreen.ConfigEntry>> blocks;
    private final ConfigRowList rows = new ConfigRowList();

    private String collection = COLLECTIONS.get(0);
    private String state = STATES.get(0);

    private final Map<String, Anim> switchHover = new HashMap<>();
    private final EditorTooltip tooltip = new EditorTooltip();
    private EnumDropdown openDropdown;

    /**
     * Bottom of the row area. Ten rows are 240px tall, which does not fit above the Back button
     * at the larger GUI scales, so the rows get a scrollable band rather than running under it.
     */
    private int rowsBottom;
    private int maxScroll;
    private double scrollOffset;
    private final Anim smoothScroll = new Anim();
    private final Anim scrollThumbHover = new Anim();
    private boolean draggingScrollbar;

    /** Switch geometry, filled in {@link #init} and read by both render and click paths. */
    private final Map<String, int[]> switchRects = new HashMap<>();

    public GraphStyleScreen(ConfigEditorScreen parent) {
        super(Component.translatable("editor.historystages.config.graph.style"));
        this.parent = parent;
        this.blocks = parent.styleEntries();
    }

    private List<ConfigEditorScreen.ConfigEntry> current() {
        return blocks.getOrDefault(collection + "." + state, List.of());
    }

    /** The row for one style leaf, or null when the block does not carry it. */
    private ConfigEditorScreen.ConfigEntry entry(String leaf) {
        for (ConfigEditorScreen.ConfigEntry e : current()) {
            if (e.key.endsWith("." + leaf)) return e;
        }
        return null;
    }

    private String value(String leaf, String fallback) {
        ConfigEditorScreen.ConfigEntry e = entry(leaf);
        return e == null ? fallback : e.value;
    }

    private double number(String leaf, double fallback) {
        try {
            return Double.parseDouble(value(leaf, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            // A half-typed value is not worth blanking the preview over.
            return fallback;
        }
    }

    @Override
    protected void init() {
        switchRects.clear();
        layoutSwitches(COLLECTIONS, "c:", COLLECTION_Y);
        layoutSwitches(STATES, "s:", STATE_Y);

        rowsBottom = this.height - BUTTON_BAND;
        updateMaxScroll();

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> onClose(), 10, this.height - 30, 60, 20));

        // Saves through the config editor, so this covers every tab and not just the block on
        // screen. Deliberately does not navigate: the point of saving here is to keep tweaking.
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> parent.saveConfig(), this.width / 2 - 50, this.height - 30, 100, 20));
    }

    private void updateMaxScroll() {
        int contentHeight = current().size() * ConfigRowList.ENTRY_HEIGHT;
        maxScroll = Math.max(0, contentHeight - (rowsBottom - ROWS_TOP));
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private void layoutSwitches(List<String> options, String prefix, int y) {
        int total = Math.min(SWITCH_BAR_MAX_W, this.width - 40);
        int each = (total - SWITCH_GAP * (options.size() - 1)) / options.size();
        int x = this.width / 2 - total / 2;
        for (String option : options) {
            switchRects.put(prefix + option, new int[]{x, y, each});
            x += each + SWITCH_GAP;
        }
    }

    private boolean isActive(String prefix, String option) {
        return ("c:".equals(prefix) ? collection : state).equals(option);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Own background, drawn in render() — avoids 1.21's menu blur shader, as the config screen does.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

        g.fill(0, 0, this.width, this.height, 0xE0101010);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        renderSwitches(g, COLLECTIONS, "c:", mouseX, mouseY);
        renderSwitches(g, STATES, "s:", mouseX, mouseY);

        String hoveredDesc = null;
        g.enableScissor(contentLeft() - 6, ROWS_TOP, contentRight() + 6, rowsBottom);
        int y = rowTop(0);
        for (ConfigEditorScreen.ConfigEntry entry : current()) {
            rows.renderRow(g, entry, contentLeft(), y, contentRight(), mouseX, mouseY);
            if (mouseX >= contentLeft() && mouseX <= contentRight()
                    && mouseY >= Math.max(y, ROWS_TOP)
                    && mouseY < Math.min(y + ConfigRowList.ENTRY_HEIGHT, rowsBottom)) {
                hoveredDesc = entry.descKey;
            }
            y += ConfigRowList.ENTRY_HEIGHT;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int barH = Math.max(20, (rowsBottom - ROWS_TOP) * (rowsBottom - ROWS_TOP)
                    / (maxScroll + rowsBottom - ROWS_TOP));
            int barY = ROWS_TOP + Math.round(smoothScroll.value() / maxScroll
                    * (rowsBottom - ROWS_TOP - barH));
            boolean barHovered = mouseX >= scrollbarX() - 1 && mouseX <= scrollbarX() + 4
                    && mouseY >= ROWS_TOP && mouseY <= rowsBottom;
            float bh = Ease.outCubic(scrollThumbHover.ramp(barHovered || draggingScrollbar,
                    Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            g.fill(scrollbarX(), ROWS_TOP, scrollbarX() + 3, rowsBottom, 0x20FFFFFF);
            g.fill(scrollbarX(), barY, scrollbarX() + 3, barY + barH,
                    Fade.mix(0x80FFFFFF, 0xFFFFCC00, bh));
        }

        // The panel spans the same band as the rows, so both grow and shrink with the window
        // together instead of one of them keeping a size the other no longer has.
        renderPreview(g, contentRight() + 30, ROWS_TOP, rowsBottom - ROWS_TOP);

        // Unsaved-changes marker, the same one the config editor shows — the Save button here
        // would otherwise give no clue whether there is anything left to save.
        if (parent.hasChanges()) {
            int dotX = this.width / 2 + 55;
            float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS)
                    / Timing.BREATHE_PERIOD_MS;
            g.fill(dotX, this.height - 25, dotX + 6, this.height - 19,
                    Fade.rgba(0xFFCC00, 0.4f + 0.6f * Ease.breathe(phase)));
            ConfigEditorScreen.drawSmallText(g,
                    Component.translatable("editor.historystages.unsaved").getString(),
                    dotX + 9, this.height - 24, 0xFFCC00);
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (openDropdown != null) {
            // On top of the collapsed button the row drew, so the open state is visible.
            openDropdown.renderButton(g, this.font, mouseX, mouseY);
            openDropdown.renderPopup(g, this.font, mouseX, mouseY);
            if (openDropdown.isExpanded()) hoveredDesc = null;
        }

        boolean hasText = hoveredDesc != null
                && net.minecraft.client.resources.language.I18n.exists(hoveredDesc);
        tooltip.render(g, this.font, hasText ? hoveredDesc : null,
                hasText ? Component.translatable(hoveredDesc).getString() : null,
                mouseX, mouseY, this.width, this.height);
    }

    /** Screen y of the row at {@code index}, with the current scroll applied. */
    private int rowTop(int index) {
        return ROWS_TOP - Math.round(smoothScroll.value()) + index * ConfigRowList.ENTRY_HEIGHT;
    }

    private int contentLeft() {
        return 24;
    }

    /** Left edge of the scrollbar track. Drawn 3px wide; the click area adds a pixel each side. */
    private int scrollbarX() {
        return contentRight() + 8;
    }

    private void updateScrollFromMouse(double mouseY) {
        int track = rowsBottom - ROWS_TOP;
        float ratio = (float) Math.max(0, Math.min(1, (mouseY - ROWS_TOP) / (double) track));
        scrollOffset = Math.max(0, Math.min(maxScroll, Math.round(ratio * maxScroll)));
        // Snapped, not eased: while the thumb is held the list must track the cursor exactly,
        // or the thumb drifts away from the pointer.
        smoothScroll.set((float) scrollOffset);
    }

    /**
     * Right edge of the row column. Half the window plus a little, so the rows keep their share
     * as the window grows and the preview takes the rest.
     */
    private int contentRight() {
        return this.width / 2 + 40;
    }

    private void renderSwitches(GuiGraphics g, List<String> options, String prefix,
                               int mouseX, int mouseY) {
        for (String option : options) {
            int[] r = switchRects.get(prefix + option);
            if (r == null) continue;
            boolean active = isActive(prefix, option);
            boolean hovered = mouseX >= r[0] && mouseX < r[0] + r[2]
                    && mouseY >= r[1] && mouseY < r[1] + SWITCH_H;
            float hp = Ease.outCubic(switchHover.computeIfAbsent(prefix + option, k -> new Anim())
                    .ramp(hovered && !active, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

            g.fill(r[0], r[1], r[0] + r[2], r[1] + SWITCH_H,
                    active ? 0x40FFCC00 : Fade.mix(0x15FFFFFF, 0x25FFFFFF, hp));
            if (active) {
                g.fill(r[0], r[1] + SWITCH_H - 2, r[0] + r[2], r[1] + SWITCH_H, 0xFFFFCC00);
            }
            String label = Component.translatable(
                    "editor.historystages.config.graph.style."
                            + ("c:".equals(prefix) ? "collection." : "state.") + option).getString();
            g.drawCenteredString(this.font, label, r[0] + r[2] / 2, r[1] + 4,
                    active ? 0xFFFFFF : Fade.mix(0xFF999999, 0xFFDDDDDD, hp));
        }
    }

    /**
     * The node as the graph would draw it, built through {@link ResolvedStyle} and the same
     * {@link NodeShapes} entry points the canvas uses — so a preview that disagrees with the
     * graph would have to be a bug in the drawing code, not a second implementation of it.
     *
     * <p>The panel fills the row band, and the node inside it is drawn at the size the graph
     * gives it at zoom 1.0. Only when the panel is too small for that — a large {@code size} in
     * a short window — is the node scaled down to fit, so the correspondence holds wherever it
     * can and the node never spills out of its frame where it cannot.
     */
    private void renderPreview(GuiGraphics g, int x, int y, int h) {
        int w = this.width - x - 24;
        if (w < 60 || h < 40) return;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF555555);
        g.fill(x, y, x + w, y + h, 0xFF1A1A1A);

        ResolvedStyle style = new ResolvedStyle(
                value("shape", "ROUNDED"),
                number("size", 1.0),
                (int) number("cornerRadius", 4),
                GraphColors.parse(value("border", "#FFFFFF"), 0xFFFFFF),
                (int) number("borderWidth", 2),
                GraphColors.parse(value("fill", "#000000"), 0),
                number("fillOpacity", 0.35),
                value("label", "DISPLAY_NAME"),
                GraphColors.parse(value("labelColor", "#DDDDDD"), 0xDDDDDD),
                Boolean.parseBoolean(value("checkmark", "false")));

        int cx = x + w / 2;
        int cy = y + h / 2 - 10;

        // Graph-true size first; the clamp only bites in a window too short to show it whole.
        // The label and the checkmark badge both stick out past the radius, hence the margin.
        int wanted = Math.max(3, Math.round(BASE_NODE_RADIUS * (float) style.size()));
        int room = Math.max(3, Math.min(w / 2 - 8, h / 2 - 20));
        int r = Math.min(wanted, room);

        NodeShapes.draw(g, style.shape(), cx, cy, r,
                style.fillArgb(), style.border(), Math.max(0, style.borderWidth()));
        if (style.checkmark()) {
            NodeShapes.checkmark(g, cx, cy, r, style.border());
        }

        if (!"NONE".equals(style.label())) {
            String sample = Component.translatable("ID".equals(style.label())
                    ? "editor.historystages.config.graph.style.sample_id"
                    : "editor.historystages.config.graph.style.sample_name").getString();
            g.drawCenteredString(this.font, sample, cx, cy + r + 4, style.labelColor());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (openDropdown != null) {
            boolean wasExpanded = openDropdown.isExpanded();
            if (openDropdown.mouseClicked(mouseX, mouseY)) return true;
            openDropdown = null;
            if (wasExpanded) return true;
        }

        for (Map.Entry<String, int[]> e : switchRects.entrySet()) {
            int[] r = e.getValue();
            if (mouseX < r[0] || mouseX >= r[0] + r[2]
                    || mouseY < r[1] || mouseY >= r[1] + SWITCH_H) continue;
            String key = e.getKey();
            if (key.startsWith("c:")) {
                collection = key.substring(2);
            } else {
                state = key.substring(2);
            }
            // Every block has the same rows, but a switch must not leave a scroll position the
            // new block cannot reach.
            updateMaxScroll();
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (maxScroll > 0 && mouseX >= scrollbarX() - 1 && mouseX <= scrollbarX() + 4
                && mouseY >= ROWS_TOP && mouseY <= rowsBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        // Outside the row band the rows are not drawn, so they must not be clickable either.
        if (mouseY < ROWS_TOP || mouseY >= rowsBottom) return false;

        int y = rowTop(0);
        for (ConfigEditorScreen.ConfigEntry entry : current()) {
            if (rows.hitTest(entry, contentLeft(), contentRight(), y, mouseX, mouseY)) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                clickRow(entry, y, rows.toggleValueAt(entry, contentLeft(), mouseX));
                return true;
            }
            y += ConfigRowList.ENTRY_HEIGHT;
        }
        return false;
    }

    /**
     * The five row types a style block can contain. Deliberately not a call into the config
     * editor's own handler: the dialogs it opens return to the screen they were given, and these
     * have to come back here rather than to the tab.
     */
    private void clickRow(ConfigEditorScreen.ConfigEntry entry, int rowY, Boolean pickedHalf) {
        switch (entry.type) {
            // The switch sets the half that was clicked; landing on the current value is a no-op.
            case BOOLEAN -> {
                if (pickedHalf != null) entry.value = String.valueOf(pickedHalf.booleanValue());
            }
            case COLOR -> this.minecraft.setScreen(new ColorInputScreen(this, entry));
            case ENUM -> {
                EnumDropdown dropdown = new EnumDropdown(
                        entry.enumConstants, entry.value, ConfigRowList.DROPDOWN_MIN_WIDTH,
                        constant -> ConfigRowList.enumLabel(entry.enumType, constant),
                        picked -> entry.value = picked);
                dropdown.setPosition(rows.controlX(entry, contentLeft()),
                        rowY + ConfigRowList.DROPDOWN_INSET_Y);
                dropdown.expand();
                openDropdown = dropdown;
            }
            case INTEGER, DOUBLE, STRING ->
                    this.minecraft.setScreen(new ConfigEditorScreen.ValueInputScreen(this, entry));
            default -> {
                // No other type appears in a style block.
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // The popup is anchored to a fixed screen position, so it cannot follow its row.
        openDropdown = null;
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 16));
        return true;
    }

    @Override
    public void onClose() {
        openDropdown = null;
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
