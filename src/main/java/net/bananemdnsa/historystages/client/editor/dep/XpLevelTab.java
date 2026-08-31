package net.bananemdnsa.historystages.client.editor.dep;

import net.bananemdnsa.historystages.api.editor.AbstractDependencyTab;

import java.util.List;

import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DropdownChrome;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DropdownOverlay;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The XP requirement, which is a single value and therefore not a list at all: a caption, then
 * either the value with a consume picker or a prompt to set one.
 *
 * <p>The proof that {@code renderContent} carries content that is not rows. Every other migrated
 * tab could have been expressed as a row list; this one cannot, and if the hook only worked for
 * lists it would be a row hook wearing a different name.
 *
 * <p>Whether the levels are taken or only looked at is a dropdown rather than a flip. Two states
 * are the case where a toggle is still defensible, but this one sits beside the individual-stage
 * picker in the same editor, and a control that looks the same has to behave the same — a click
 * that changes the value under the cursor and a click that opens a list are not the same gesture.
 */
public final class XpLevelTab extends AbstractDependencyTab {

    private static final int CAPTION_HEIGHT = 18;

    /** What the picker stores. The dep itself keeps a boolean; these are its two faces. */
    private static final String CHECK = "check";
    private static final String CONSUME = "consume";
    private static final List<String> CONSUME_MODES = List.of(CHECK, CONSUME);

    @Nullable
    private XpLevelDep xp;
    private Runnable onLevelNeeded = () -> { };
    /**
     * The consume picker's rectangle from the last frame.
     *
     * <p>Recorded rather than recomputed: measuring the label needs the font, and the font arrives
     * with the render context only. Sound because a click always follows a frame.
     */
    private int toggleX;
    private int toggleW;

    /** Built on first use: it measures its labels against the font, which needs a running client. */
    @Nullable
    private DropdownOverlay consumeOverlay;

    public XpLevelTab(Requirement requirement, Runnable onChanged) {
        // No picker: there is nothing to pick from, and hasAddButton() hides the Add row.
        super(requirement, (onSelect, alreadyAdded) -> null, onChanged);
    }

    @Override
    public boolean hasAddButton() {
        return false;
    }

    /** What the host does when a level has to be entered. A tab has no screen to push a dialog onto. */
    public void setOnLevelNeeded(Runnable handler) {
        this.onLevelNeeded = handler;
    }

    public int level() {
        return xp == null ? 0 : xp.getLevel();
    }

    public void setLevel(int level) {
        if (xp == null) xp = new XpLevelDep(level, false);
        else xp.setLevel(level);
        refreshRows();
        markChanged();
    }

    /** Flips it. The context menu's way in, and with two states still an honest word for it. */
    public void toggleConsume() {
        if (xp == null) return;
        xp.setConsume(!xp.isConsume());
        markChanged();
    }

    public void clear() {
        closeConsumePicker();
        xp = null;
        refreshRows();
        markChanged();
    }

    @Override
    public void onShown() {
        closeConsumePicker();
    }

    @Override
    public int contentHeight(int width) {
        return CAPTION_HEIGHT + EditorRowList.CARD_HEIGHT + EditorRowList.CARD_GAP;
    }

    /** The single row, when the cursor is on it. There is only ever one, so it is 0 or -1. */
    @Override
    public int rowAt(TabInputContext ctx) {
        int y = ctx.y() + CAPTION_HEIGHT;
        boolean inRow = ctx.mouseX() >= ctx.x() && ctx.mouseX() < ctx.x() + ctx.width()
                && ctx.mouseY() >= y && ctx.mouseY() < y + EditorRowList.CARD_HEIGHT;
        return inRow && xp != null && xp.getLevel() > 0 ? 0 : -1;
    }

    @Override
    public boolean renderContent(TabRenderContext ctx) {
        int y = ctx.y();
        ctx.graphics().drawString(ctx.font(), t("editor.historystages.dep.required_xp"),
                ctx.x() + 6, y + 4, 0xAAAAAA, false);
        y += CAPTION_HEIGHT;

        if (xp != null && xp.getLevel() > 0) renderValue(ctx, y);
        else renderPrompt(ctx, y);
        return true;
    }

    /**
     * The consume picker while it is up.
     *
     * <p>This tab has no Add picker to fall back to — {@code pickerFactory} hands back null — so
     * the null is the whole of the other case.
     */
    @Override
    @Nullable
    public PickerOverlay activeOverlay() {
        if (consumeOverlay != null && consumeOverlay.isShowing()) return consumeOverlay;
        return super.activeOverlay();
    }

    @Override
    public boolean mouseClicked(TabInputContext ctx, int button) {
        if (button != 0) return false;
        int y = ctx.y() + CAPTION_HEIGHT;
        boolean inRow = ctx.mouseX() >= ctx.x() && ctx.mouseX() < ctx.x() + ctx.width()
                && ctx.mouseY() >= y && ctx.mouseY() < y + EditorRowList.CARD_HEIGHT;
        if (!inRow) return false;

        if (xp == null || xp.getLevel() <= 0) {
            onLevelNeeded.run();
            return true;
        }
        // Only the picker at the right edge reacts; the rest of the row is the value's display.
        if (ctx.mouseX() >= toggleX && ctx.mouseX() < toggleX + toggleW
                && ctx.mouseY() >= y + 3 && ctx.mouseY() < y + EditorRowList.CARD_HEIGHT - 3) {
            openConsumePicker(toggleX, y + 3, EditorRowList.CARD_HEIGHT - 6);
            return true;
        }
        return false;
    }

    private void renderValue(TabRenderContext ctx, int y) {
        GuiGraphics g = ctx.graphics();
        int right = ctx.x() + ctx.width();
        int bottom = y + EditorRowList.CARD_HEIGHT;
        boolean hovered = !ctx.inputBlocked() && ctx.mouseX() >= ctx.x() && ctx.mouseX() < right
                && ctx.mouseY() >= y && ctx.mouseY() < bottom;

        g.fill(ctx.x(), y, right, bottom, 0x30FFFFFF);
        g.fill(ctx.x() + 1, y + 1, right - 1, bottom - 1, 0x20FFFFFF);
        if (hovered) g.fill(ctx.x(), y, ctx.x() + 2, bottom, 0xCCFFCC00);

        String consumed = t(xp.isConsume()
                ? "editor.historystages.dep.consumed" : "editor.historystages.dep.checked_only");
        g.drawString(ctx.font(), Component.translatable("editor.historystages.dep.level",
                xp.getLevel(), consumed).getString(), ctx.x() + 6, y + 7, 0xDDDDDD, false);

        String label = modeLabel(currentMode()).getString();
        toggleW = ctx.font().width(label) + 8 + DropdownChrome.CARET_WIDTH;
        toggleX = right - toggleW - 2;
        boolean toggleHovered = !ctx.inputBlocked() && ctx.mouseX() >= toggleX
                && ctx.mouseX() < toggleX + toggleW
                && ctx.mouseY() >= y + 3 && ctx.mouseY() < bottom - 3;
        g.fill(toggleX, y + 3, toggleX + toggleW, bottom - 3,
                toggleHovered ? 0xFF3D3520 : 0xFF2A2A2A);
        g.drawString(ctx.font(), label, toggleX + 4, y + 7,
                toggleHovered ? 0xFFCC00 : 0xCCCCCC, false);
        DropdownChrome.drawCaret(g, toggleX + toggleW - 8, y + EditorRowList.CARD_HEIGHT / 2 - 2,
                toggleHovered ? 0xFFDDDDDD : 0xFF999999,
                consumeOverlay != null && consumeOverlay.isVisible() ? 1.0f : 0.0f);
        if (toggleHovered) {
            ctx.tooltip("toggle.xp", t(xp.isConsume()
                    ? "editor.historystages.dep.tooltip.consume"
                    : "editor.historystages.dep.tooltip.check_only"));
        }
    }

    private void renderPrompt(TabRenderContext ctx, int y) {
        GuiGraphics g = ctx.graphics();
        int right = ctx.x() + ctx.width();
        int bottom = y + EditorRowList.CARD_HEIGHT;
        boolean hovered = !ctx.inputBlocked() && ctx.mouseX() >= ctx.x() && ctx.mouseX() < right
                && ctx.mouseY() >= y && ctx.mouseY() < bottom;
        g.fill(ctx.x(), y, right, bottom, hovered ? 0x40FFCC00 : 0x20FFFFFF);
        g.drawCenteredString(ctx.font(), t("editor.historystages.dep.set_xp_level"),
                ctx.x() + ctx.width() / 2, y + 7, hovered ? 0xFFCC00 : 0x888888);
    }

    /** Opens the consume picker under the control that was clicked. */
    private void openConsumePicker(int x, int y, int height) {
        if (xp == null) return;
        if (consumeOverlay == null) {
            consumeOverlay = new DropdownOverlay(new EnumDropdown(CONSUME_MODES, currentMode(), 0,
                    XpLevelTab::modeLabel, this::applyMode));
        }
        consumeOverlay.dropdown().setValue(currentMode());
        consumeOverlay.openAt(x, y, height);
    }

    private void closeConsumePicker() {
        if (consumeOverlay != null) consumeOverlay.hide();
    }

    private void applyMode(String mode) {
        if (xp == null) return;
        xp.setConsume(CONSUME.equals(mode));
        markChanged();
    }

    private String currentMode() {
        return xp != null && xp.isConsume() ? CONSUME : CHECK;
    }

    private static Component modeLabel(String mode) {
        return Component.translatable(CONSUME.equals(mode)
                ? "editor.historystages.dep.consume" : "editor.historystages.dep.check");
    }

    private static String t(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    protected void readFrom(DependencyGroup group) {
        closeConsumePicker();
        XpLevelDep source = group.getXpLevel();
        xp = source == null ? null : new XpLevelDep(source.getLevel(), source.isConsume());
        refreshRows();
    }

    @Override
    public void store(DependencyGroup group) {
        group.setXpLevel(xp != null && xp.getLevel() > 0 ? xp : null);
    }

    /** One row when a level is set, none otherwise — this is what the group's entry count reads. */
    private void refreshRows() {
        rows().clear();
        if (xp != null && xp.getLevel() > 0) rows().add(String.valueOf(xp.getLevel()));
    }
}
