package net.bananemdnsa.historystages.client.editor.tab;

import net.bananemdnsa.historystages.api.editor.AbstractCategoryTab;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.editor.widget.ToggleControl;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.data.StageEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The merchant levels, which are five and always the same five — so this is a set of switches
 * rather than a list you add to.
 *
 * <p>Every other category can hold anything the game happens to have registered, and its tab is
 * therefore a picker and a list. A villager has exactly novice through master and always will;
 * offering an Add button onto a list of five known values would make the maintainer search a
 * dialog for something that could simply be on the screen. Off means the level trades normally,
 * on means it is gated — the same reading as every other switch in this editor.
 *
 * <p>Stored as the strings {@code "1"} to {@code "5"}, because the lock categories are defined
 * over lists of ids. The stage file still shows numbers: {@code TradeLevelListAdapter} converts
 * at the file boundary, which is the only place the difference is visible.
 */
public final class TradeLevelTab extends AbstractCategoryTab {

    /** Novice through master. Vanilla has no sixth, and a merchant cannot reach one. */
    private static final int LEVELS = 5;

    private static final int CAPTION_HEIGHT = 18;

    private final LockCategory<String> category;
    private final ToggleControl.State[] toggles = new ToggleControl.State[LEVELS];

    /**
     * The switches' rectangles from the last frame.
     *
     * <p>Recorded rather than recomputed: their width comes from measuring the labels, and the
     * font arrives with the render context only. Sound because a click always follows a frame.
     */
    private int toggleX;
    private int toggleW;

    /**
     * The host's font, kept from the last frame so the click handler can ask the switch which
     * half was hit. Taken from the render context rather than from {@code Minecraft}: a tab is
     * handed a font precisely so it does not have to go looking for one.
     */
    @Nullable
    private net.minecraft.client.gui.Font font;

    public TradeLevelTab(LockCategory<String> category, Runnable onChanged) {
        // No picker: there is nothing to pick from, and hasAddButton() hides the Add row.
        super(category, (onSelect, alreadyAdded) -> null, onChanged);
        this.category = category;
        for (int i = 0; i < LEVELS; i++) toggles[i] = new ToggleControl.State();
    }

    @Override
    public boolean hasAddButton() {
        return false;
    }

    /** Nothing to open. Without this the inherited version would dereference a picker that is null. */
    @Override
    public void openPicker(int centerX, int centerY, int parentWidth) {
    }

    @Override
    @Nullable
    public PickerOverlay activeOverlay() {
        return null;
    }

    @Override
    public int contentHeight(int width) {
        return CAPTION_HEIGHT + LEVELS * (EditorRowList.CARD_HEIGHT + EditorRowList.CARD_GAP);
    }

    /**
     * No rows to point at. The switches are the whole of the editing here, so a right-click menu
     * offering "copy id" and "remove" would be offering to remove something that is not a list
     * entry in the first place.
     */
    @Override
    public int rowAt(TabInputContext ctx) {
        return -1;
    }

    @Override
    public boolean renderContent(TabRenderContext ctx) {
        GuiGraphics g = ctx.graphics();
        g.drawString(ctx.font(), Component.translatable(
                        "editor.historystages.trades.levels.caption").getString(),
                ctx.x() + 6, ctx.y() + 4, 0xAAAAAA, false);

        int right = ctx.x() + ctx.width();
        font = ctx.font();
        toggleW = ToggleControl.width(ctx.font());
        toggleX = right - toggleW - 6;

        int y = ctx.y() + CAPTION_HEIGHT;
        for (int level = 1; level <= LEVELS; level++) {
            renderLevel(ctx, y, level);
            y += EditorRowList.CARD_HEIGHT + EditorRowList.CARD_GAP;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(TabInputContext ctx, int button) {
        if (button != 0 || font == null) return false;
        int y = ctx.y() + CAPTION_HEIGHT;
        for (int level = 1; level <= LEVELS; level++) {
            int bottom = y + EditorRowList.CARD_HEIGHT;
            if (ctx.mouseY() >= y && ctx.mouseY() < bottom
                    && ctx.mouseY() >= ctx.clipTop() && ctx.mouseY() < ctx.clipBottom()) {
                // The whole row is the target, not just the 14px switch inside it: the switch
                // still decides which value a click means, which is what makes clicking the value
                // that is already set do nothing.
                Boolean picked = ToggleControl.valueAt(font, toggleX, ctx.mouseX());
                if (picked == null) return false;
                setGated(level, picked);
                return true;
            }
            y = bottom + EditorRowList.CARD_GAP;
        }
        return false;
    }

    @Override
    public void load(StageEntry stage) {
        entries().clear();
        entries().addAll(category.read(stage));
    }

    @Override
    public void store(StageEntry stage) {
        category.write(stage, new ArrayList<>(entries()));
    }

    private void renderLevel(TabRenderContext ctx, int y, int level) {
        GuiGraphics g = ctx.graphics();
        int right = ctx.x() + ctx.width();
        int bottom = y + EditorRowList.CARD_HEIGHT;
        boolean gated = isGated(level);
        boolean hovered = !ctx.inputBlocked() && ctx.mouseX() >= ctx.x() && ctx.mouseX() < right
                && ctx.mouseY() >= Math.max(y, ctx.clipTop())
                && ctx.mouseY() < Math.min(bottom, ctx.clipBottom());

        g.fill(ctx.x(), y, right, bottom, hovered ? 0x50FFFFFF : 0x30FFFFFF);
        g.fill(ctx.x() + 1, y + 1, right - 1, bottom - 1, 0x20FFFFFF);
        if (gated) g.fill(ctx.x(), y, ctx.x() + 2, bottom, 0xCCFFCC00);

        g.drawString(ctx.font(), Component.translatable("merchant.level." + level).getString()
                        + " §8(" + level + ")",
                ctx.x() + 6, y + 7, hovered ? 0xFFFFFF : 0xBBBBBB, false);

        int toggleY = y + (EditorRowList.CARD_HEIGHT - ToggleControl.height()) / 2;
        Boolean segment = ctx.inputBlocked() ? null
                : ToggleControl.segmentAt(ctx.font(), toggleX, toggleY, ctx.mouseX(), ctx.mouseY());
        toggles[level - 1].update(gated, segment);
        ToggleControl.draw(g, ctx.font(), toggleX, toggleY, gated, toggles[level - 1], false);
    }

    private boolean isGated(int level) {
        return entries().contains(String.valueOf(level));
    }

    private void setGated(int level, boolean gated) {
        String key = String.valueOf(level);
        if (gated == entries().contains(key)) return;
        if (gated) {
            // Kept ascending so the stage file reads in the order a merchant climbs them, whatever
            // order the switches were flipped in.
            List<String> rebuilt = new ArrayList<>();
            for (int i = 1; i <= LEVELS; i++) {
                if (i == level || entries().contains(String.valueOf(i))) rebuilt.add(String.valueOf(i));
            }
            entries().clear();
            entries().addAll(rebuilt);
        } else {
            entries().remove(key);
        }
        markChanged();
    }
}
