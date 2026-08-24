package net.bananemdnsa.historystages.client.editor.dep;

import net.bananemdnsa.historystages.client.editor.tab.TabInputContext;
import net.bananemdnsa.historystages.client.editor.tab.TabRenderContext;
import net.bananemdnsa.historystages.client.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The XP requirement, which is a single value and therefore not a list at all: a caption, then
 * either the value with a consume toggle or a prompt to set one.
 *
 * <p>The proof that {@code renderContent} carries content that is not rows. Every other migrated
 * tab could have been expressed as a row list; this one cannot, and if the hook only worked for
 * lists it would be a row hook wearing a different name.
 */
public final class XpLevelTab extends AbstractDependencyTab {

    private static final int CAPTION_HEIGHT = 18;

    @Nullable
    private XpLevelDep xp;
    private Runnable onLevelNeeded = () -> { };
    /**
     * The consume toggle's rectangle from the last frame.
     *
     * <p>Recorded rather than recomputed: measuring the label needs the font, and the font arrives
     * with the render context only. Sound because a click always follows a frame.
     */
    private int toggleX;
    private int toggleW;

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

    public void toggleConsume() {
        if (xp == null) return;
        xp.setConsume(!xp.isConsume());
        markChanged();
    }

    public void clear() {
        xp = null;
        refreshRows();
        markChanged();
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
        // Only the toggle at the right edge reacts; the rest of the row is the value's display.
        if (ctx.mouseX() >= toggleX && ctx.mouseX() < toggleX + toggleW
                && ctx.mouseY() >= y + 3 && ctx.mouseY() < y + EditorRowList.CARD_HEIGHT - 3) {
            toggleConsume();
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

        String toggle = toggleLabel();
        toggleW = ctx.font().width(toggle) + 8;
        toggleX = right - toggleW - 2;
        boolean toggleHovered = !ctx.inputBlocked() && ctx.mouseX() >= toggleX
                && ctx.mouseX() < toggleX + toggleW
                && ctx.mouseY() >= y + 3 && ctx.mouseY() < bottom - 3;
        g.fill(toggleX, y + 3, toggleX + toggleW, bottom - 3,
                toggleHovered ? 0xFF3D3520 : 0xFF2A2A2A);
        g.drawString(ctx.font(), toggle, toggleX + 4, y + 7,
                toggleHovered ? 0xFFCC00 : 0xCCCCCC, false);
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

    private String toggleLabel() {
        return t(xp != null && xp.isConsume()
                ? "editor.historystages.dep.consume" : "editor.historystages.dep.check");
    }

    private static String t(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    protected void readFrom(DependencyGroup group) {
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
