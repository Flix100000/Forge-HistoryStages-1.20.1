package net.bananemdnsa.historystages.client.editor.recipe;

import java.util.List;

import net.bananemdnsa.historystages.api.editor.RecipeTypeMeta;
import net.bananemdnsa.historystages.client.editor.widget.FluidIcon;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Draws one recipe card: accent bar, workstation icon, the input area in its real shape, arrow,
 * result, type name and namespace.
 *
 * <p>One renderer, two callers — the picker's detail column and the preview popup in
 * {@code StageDetailScreen}. Before this, the popup drew a real grid and the picker drew a row of
 * up to five icons, and only one of them could tell two shaped recipes apart.
 */
public final class RecipeCardRenderer {

    public static final int ACCENT_WIDTH = 2;
    private static final int WORKSTATION_SIZE = 18;
    private static final int ARROW_WIDTH = 10;
    private static final int GAP = 3;
    private static final int SIDE_PADDING = 4;

    private static final int CARD_BG = 0xFF252525;
    private static final int CARD_BG_HOVER = 0xFF353535;
    private static final int SELECTED_OUTLINE = 0xFFFFCC00;
    private static final int SLOT_BG = 0xFF141414;
    private static final int TEXT_DIM = 0xFF6F6F6F;
    private static final int TEXT_DIMMER = 0xFF5F5F5F;
    /** Edge on a fluid whose side we could not read. Distinct from every slot colour here. */
    private static final int UNKNOWN_SIDE = 0xFF9A6ACC;

    private RecipeCardRenderer() {
    }

    /** Width a card needs for this shape, so the detail column can size itself. */
    public static int cardWidth(RecipeCardLayout layout) {
        return SIDE_PADDING + ACCENT_WIDTH + GAP + WORKSTATION_SIZE + GAP
                + layout.contentWidth() + GAP + ARROW_WIDTH + GAP
                + RecipeCardLayout.RESULT_SIZE + SIDE_PADDING;
    }

    /**
     * Draws the card at {@code (x, y)} with the given width.
     *
     * @param fluidResult fluid this recipe produces, or {@code ""} — drawn in the result slot
     *                    only when there is no item result, which is the case that would
     *                    otherwise show an empty slot
     * @param fluids      the fluids on the ingredient row, in the order the layout reserved slots
     *                    for; entries whose side could not be read are marked
     * @param typeId   registry id of the recipe's type, for the accent and the name
     * @param recipeId full recipe id; only its namespace is drawn, the rest belongs in a tooltip
     */
    public static void render(GuiGraphics g, Font font, RecipeShape shape, ItemStack result,
                              String fluidResult, List<RecipeFluids.Ref> fluids,
                              String typeId, String recipeId,
                              int x, int y, int width, boolean hovered, boolean selected) {
        RecipeCardLayout layout = shape.layout();
        int height = layout.cardHeight();
        RecipeTypeMeta meta = RecipeTypeMetas.get(typeId);

        g.fill(x, y, x + width, y + height, hovered ? CARD_BG_HOVER : CARD_BG);
        if (selected) {
            g.renderOutline(x, y, width, height, SELECTED_OUTLINE);
        }
        g.fill(x, y, x + ACCENT_WIDTH, y + height, meta.accentColor());

        int midY = y + height / 2;

        ItemStack workstation = resolveWorkstation(meta);
        if (!workstation.isEmpty()) {
            g.renderItem(workstation, workstationLeft(x), midY - WORKSTATION_SIZE / 2);
        }

        int inputLeft = inputLeft(x);
        int inputTop = inputTop(midY, layout);
        for (int i = 0; i < layout.slotCount(); i++) {
            int sx = inputLeft + layout.slotX(i);
            int sy = inputTop + layout.slotY(i);
            g.fill(sx, sy, sx + RecipeCardLayout.SLOT_SIZE, sy + RecipeCardLayout.SLOT_SIZE, SLOT_BG);
            ItemStack stack = shape.slots().get(i);
            if (!stack.isEmpty()) g.renderItem(stack, sx + 1, sy + 1);
        }

        // A row of its own under the grid. A fluid has no position in the pattern, so putting
        // one in a grid cell would claim a place no recipe actually tells us.
        for (int i = 0; i < layout.fluidCount() && i < fluids.size(); i++) {
            int fx = inputLeft + layout.fluidSlotX(i);
            int fy = inputTop + layout.fluidSlotY(i);
            g.fill(fx, fy, fx + RecipeCardLayout.SLOT_SIZE, fy + RecipeCardLayout.SLOT_SIZE, SLOT_BG);
            RecipeFluids.Ref ref = fluids.get(i);
            FluidIcon.draw(g, ref.fluidId(), fx + 1, fy + 1, 16);
            if (!ref.sideKnown()) {
                // The lock treats this one as both ingredient and result, so the card has to
                // admit it rather than present it as a plain ingredient.
                g.fill(fx, fy, fx + RecipeCardLayout.SLOT_SIZE, fy + 1, UNKNOWN_SIDE);
                g.fill(fx, fy + RecipeCardLayout.SLOT_SIZE - 1,
                        fx + RecipeCardLayout.SLOT_SIZE, fy + RecipeCardLayout.SLOT_SIZE, UNKNOWN_SIDE);
            }
        }

        g.drawString(font, "→", inputLeft + layout.contentWidth() + GAP, midY - 4, TEXT_DIM, false);

        int resultLeft = resultLeft(x, layout);
        g.fill(resultLeft, midY - RecipeCardLayout.RESULT_SIZE / 2,
                resultLeft + RecipeCardLayout.RESULT_SIZE, midY + RecipeCardLayout.RESULT_SIZE / 2, SLOT_BG);
        // 22px slot, 16px icon: (22 - 16) / 2 = 3px margin on every side for a true center.
        if (!result.isEmpty()) {
            g.renderItem(result, resultLeft + 3, midY - 8);
        } else if (fluidResult != null && !fluidResult.isEmpty()) {
            // A recipe whose only output is a fluid. Without this its result slot sits empty and
            // the card looks like it makes nothing.
            FluidIcon.draw(g, fluidResult, resultLeft + 3, midY - 8, 16);
        } else {
            // Nothing here is known to be the output. An empty slot would read as a fault in the
            // recipe; this says the honest thing, which is that we could not tell.
            g.drawString(font, "?", resultLeft + RecipeCardLayout.RESULT_SIZE / 2 - 2, midY - 4,
                    UNKNOWN_SIDE, false);
        }

        String typeName = meta.nameLangKey().isEmpty()
                ? meta.displayFallback()
                : Component.translatable(meta.nameLangKey()).getString();
        g.drawString(font, typeName, x + width - font.width(typeName) - SIDE_PADDING,
                y + 2, TEXT_DIM, false);

        String namespace = recipeId != null && recipeId.contains(":")
                ? recipeId.substring(0, recipeId.indexOf(':'))
                : "";
        if (!namespace.isEmpty()) {
            g.drawString(font, namespace, x + width - font.width(namespace) - SIDE_PADDING,
                    y + height - 10, TEXT_DIMMER, false);
        }
    }

    /**
     * The stack in the slot under the cursor, or empty when the cursor is not on one.
     *
     * <p>Exists because the card draws items and nothing else — no names, no counts. Before the
     * popup shared this renderer it drew its own grid and put a tooltip on every ingredient, and
     * losing that would mean a slot you cannot identify. The result slot answers here too: the
     * old popup wrote the output's name under it, and the card has no room for that either.
     *
     * <p>Positions come from the same helpers {@link #render} lays the card out with, so the two
     * cannot drift apart.
     */
    public static ItemStack stackAt(RecipeShape shape, ItemStack result, int x, int y, int width,
                                    double mouseX, double mouseY) {
        RecipeCardLayout layout = shape.layout();
        int height = layout.cardHeight();
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return ItemStack.EMPTY;
        }
        int midY = y + height / 2;

        int i = layout.slotIndexAt(mouseX - inputLeft(x), mouseY - inputTop(midY, layout));
        if (i >= 0) {
            // A hole in a shaped pattern is a real slot holding nothing, and reports as empty.
            return i < shape.slots().size() ? shape.slots().get(i) : ItemStack.EMPTY;
        }

        int resultLeft = resultLeft(x, layout);
        if (mouseX >= resultLeft && mouseX < resultLeft + RecipeCardLayout.RESULT_SIZE
                && mouseY >= midY - RecipeCardLayout.RESULT_SIZE / 2
                && mouseY < midY + RecipeCardLayout.RESULT_SIZE / 2) {
            return result;
        }
        return ItemStack.EMPTY;
    }

    /**
     * The fluid id under the cursor — the ingredient row, or the result slot when a fluid fills
     * it. Empty when the cursor is on neither.
     *
     * <p>Separate from {@link #stackAt} because a fluid is not an {@code ItemStack}, and returning
     * one would mean inventing a bucket that need not exist.
     */
    public static String fluidAt(RecipeShape shape, String fluidResult,
                                 List<RecipeFluids.Ref> fluids,
                                 int x, int y, int width, double mouseX, double mouseY) {
        RecipeCardLayout layout = shape.layout();
        int height = layout.cardHeight();
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return "";
        int midY = y + height / 2;

        int i = layout.fluidIndexAt(mouseX - inputLeft(x), mouseY - inputTop(midY, layout));
        if (i >= 0 && i < fluids.size()) return fluids.get(i).fluidId();

        int resultLeft = resultLeft(x, layout);
        if (fluidResult != null && !fluidResult.isEmpty()
                && mouseX >= resultLeft && mouseX < resultLeft + RecipeCardLayout.RESULT_SIZE
                && mouseY >= midY - RecipeCardLayout.RESULT_SIZE / 2
                && mouseY < midY + RecipeCardLayout.RESULT_SIZE / 2) {
            return fluidResult;
        }
        return "";
    }

    private static int workstationLeft(int x) {
        return x + SIDE_PADDING + ACCENT_WIDTH + GAP;
    }

    private static int inputLeft(int x) {
        return workstationLeft(x) + WORKSTATION_SIZE + GAP;
    }

    private static int inputTop(int midY, RecipeCardLayout layout) {
        // Centres the input grid and the fluid row together. Centring on the grid alone pushed
        // the fluid row off the bottom of its own card.
        return midY - (layout.inputHeight() + layout.fluidHeight()) / 2;
    }

    private static int resultLeft(int x, RecipeCardLayout layout) {
        return inputLeft(x) + layout.contentWidth() + GAP + ARROW_WIDTH + GAP;
    }

    /**
     * The workstation stack for a type, or empty when nobody described it.
     *
     * <p>The metadata stores an item id rather than a stack so its registry stays unit-testable;
     * this is where the id becomes something drawable.
     */
    public static ItemStack resolveWorkstation(RecipeTypeMeta meta) {
        if (meta.workstationItemId().isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(meta.workstationItemId());
        if (id == null) return ItemStack.EMPTY;
        // BuiltInRegistries.ITEM is a DefaultedRegistry<Item>; get() is @Nonnull and falls back
        // to Items.AIR for an unresolved id rather than returning null. new ItemStack(Items.AIR)
        // reports isEmpty() == true (ItemStack.isEmpty() checks item == Items.AIR), so an unknown
        // id still ends up invisible to the caller without a separate null check.
        Item item = BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item);
    }
}
