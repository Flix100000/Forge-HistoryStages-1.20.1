package net.bananemdnsa.historystages.client.editor.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How tall a card is and where its input slots sit. The whole reason cards vary in height is
 * that a furnace recipe should not reserve a 3x3 grid it will never fill, so the sizes here are
 * the feature, not an implementation detail.
 */
class RecipeCardLayoutTest {

    @Test
    void aShapedRecipeKeepsItsOwnWidthAndHeight() {
        RecipeCardLayout layout = RecipeCardLayout.shaped(3, 2);
        assertEquals(3, layout.cols());
        assertEquals(2, layout.rows());
    }

    @Test
    void aThreeByThreeCardIsTallerThanAOneByOneCard() {
        int big = RecipeCardLayout.shaped(3, 3).cardHeight();
        int small = RecipeCardLayout.single().cardHeight();
        assertTrue(big > small, "a furnace card reserving a 3x3 grid is the thing we removed");
    }

    @Test
    void aSingleInputIsOneSlot() {
        RecipeCardLayout layout = RecipeCardLayout.single();
        assertEquals(1, layout.cols());
        assertEquals(1, layout.rows());
        assertEquals(1, layout.slotCount());
    }

    @Test
    void aShapelessRecipeWrapsAtThreeColumns() {
        // Five shapeless ingredients: 3 + 2.
        RecipeCardLayout layout = RecipeCardLayout.shapeless(5);
        assertEquals(3, layout.cols());
        assertEquals(2, layout.rows());
    }

    @Test
    void aShapelessRecipeOfNineIsAFullGrid() {
        RecipeCardLayout layout = RecipeCardLayout.shapeless(9);
        assertEquals(3, layout.cols());
        assertEquals(3, layout.rows());
    }

    @Test
    void anUnknownTypeLaysItsIngredientsOutInFives() {
        // A modded machine can take more inputs than a crafting grid holds. Five per row keeps
        // the card no wider than the detail column.
        RecipeCardLayout layout = RecipeCardLayout.sequence(7);
        assertEquals(5, layout.cols());
        assertEquals(2, layout.rows());
    }

    @Test
    void aWrappedRecipeNeverHasMoreColumnsThanIngredients() {
        // Three inputs used to be laid out five wide, leaving two empty slots and — worse — a
        // card wider than the column that has to draw it, so its result slot got clipped away.
        assertEquals(3, RecipeCardLayout.sequence(3).cols());
        assertEquals(1, RecipeCardLayout.sequence(3).rows());
        assertEquals(2, RecipeCardLayout.shapeless(2).cols());
    }

    @Test
    void noLayoutIsWiderThanMaxCols() {
        // What the picker sizes its detail column from. If any factory can exceed it, cards
        // silently lose their arrow and result to the clip rectangle.
        assertTrue(RecipeCardLayout.sequence(100).cols() <= RecipeCardLayout.MAX_COLS);
        assertTrue(RecipeCardLayout.shapeless(100).cols() <= RecipeCardLayout.MAX_COLS);
    }

    @Test
    void aRecipeWithNoIngredientsStillHasOneRow() {
        // Some special recipes report an empty ingredient list. A zero-row card would collapse
        // into an unclickable sliver.
        assertEquals(1, RecipeCardLayout.shapeless(0).rows());
        assertEquals(1, RecipeCardLayout.sequence(0).rows());
    }

    @Test
    void slotsAreLaidOutLeftToRightThenDown() {
        RecipeCardLayout layout = RecipeCardLayout.shaped(3, 2);
        assertEquals(0, layout.slotX(0));
        assertEquals(RecipeCardLayout.SLOT_SIZE, layout.slotX(1));
        assertEquals(0, layout.slotY(0));
        assertEquals(0, layout.slotY(2));
        assertEquals(RecipeCardLayout.SLOT_SIZE, layout.slotY(3));
    }

    @Test
    void aOneRowCardIsSizedByItsResultSlotNotItsInputRow() {
        // On a furnace card the result slot (22) is taller than the single input row (18), so
        // the card has to be sized from the result. Asserting merely ">= RESULT_SIZE" cannot
        // see this: one input row plus padding already clears 22 on its own, so the assertion
        // held even with the max() deleted.
        assertEquals(RecipeCardLayout.RESULT_SIZE + RecipeCardLayout.VERTICAL_PADDING * 2,
                RecipeCardLayout.single().cardHeight());
    }

    @Test
    void aTallCardIsSizedByItsInputGrid() {
        // The other side of the same max(): once the grid is taller than the result, the grid
        // wins. Without both, a mutation to either branch slips through.
        assertEquals(3 * RecipeCardLayout.SLOT_SIZE + RecipeCardLayout.VERTICAL_PADDING * 2,
                RecipeCardLayout.shaped(3, 3).cardHeight());
    }

    @Test
    void aPointInsideTheGridNamesTheSlotDrawnThere() {
        RecipeCardLayout layout = RecipeCardLayout.shaped(3, 2);
        // Round trip: whatever slotX/slotY put a slot at has to come back as that slot.
        for (int i = 0; i < layout.slotCount(); i++) {
            assertEquals(i, layout.slotIndexAt(layout.slotX(i) + 1, layout.slotY(i) + 1),
                    "slot " + i + " does not answer for its own rectangle");
        }
    }

    @Test
    void slotBoundariesBelongToTheSlotBelowAndRight() {
        RecipeCardLayout layout = RecipeCardLayout.shaped(3, 2);
        // The seam between two slots has to fall on exactly one of them, or a column of pixels
        // names the wrong item.
        assertEquals(0, layout.slotIndexAt(RecipeCardLayout.SLOT_SIZE - 1, 0));
        assertEquals(1, layout.slotIndexAt(RecipeCardLayout.SLOT_SIZE, 0));
        assertEquals(3, layout.slotIndexAt(0, RecipeCardLayout.SLOT_SIZE));
    }

    @Test
    void aPointOutsideTheGridNamesNothing() {
        RecipeCardLayout layout = RecipeCardLayout.shaped(3, 2);
        assertEquals(-1, layout.slotIndexAt(-1, 0));
        assertEquals(-1, layout.slotIndexAt(0, -1));
        assertEquals(-1, layout.slotIndexAt(layout.inputWidth(), 0));
        assertEquals(-1, layout.slotIndexAt(0, layout.inputHeight()));
    }

    @Test
    void aLayoutWithoutFluidsIsUnchanged() {
        RecipeCardLayout plain = RecipeCardLayout.shaped(3, 3);
        RecipeCardLayout withNone = RecipeCardLayout.shaped(3, 3).withFluids(0);
        assertEquals(plain.cardHeight(), withNone.cardHeight());
        assertEquals(0, withNone.fluidCount());
        assertEquals(0, withNone.fluidRows());
    }

    @Test
    void aFluidRowMakesTheCardOneSlotTaller() {
        int without = RecipeCardLayout.shaped(3, 3).cardHeight();
        int with = RecipeCardLayout.shaped(3, 3).withFluids(2).cardHeight();
        assertEquals(without + RecipeCardLayout.SLOT_SIZE, with);
    }

    @Test
    void theFluidRowSitsDirectlyUnderTheInputGrid() {
        RecipeCardLayout layout = RecipeCardLayout.shaped(3, 2).withFluids(2);
        assertEquals(2 * RecipeCardLayout.SLOT_SIZE, layout.fluidSlotY(0));
        assertEquals(0, layout.fluidSlotX(0));
        assertEquals(RecipeCardLayout.SLOT_SIZE, layout.fluidSlotX(1));
    }

    @Test
    void manyFluidsWrapAtTheSameWidthAsInputs() {
        // Otherwise a recipe with eight fluid inputs draws a row wider than the column that has
        // to hold it, and the overhang is silently clipped.
        RecipeCardLayout layout = RecipeCardLayout.single().withFluids(8);
        assertEquals(RecipeCardLayout.MAX_COLS, layout.fluidCols());
        assertEquals(2, layout.fluidRows());
        assertEquals(0, layout.fluidSlotX(RecipeCardLayout.MAX_COLS));
        assertEquals(2 * RecipeCardLayout.SLOT_SIZE, layout.fluidSlotY(RecipeCardLayout.MAX_COLS));
    }

    @Test
    void contentWidthCoversWhicheverBlockIsWider() {
        // A one-input furnace recipe with four fluids is wider than its input grid.
        RecipeCardLayout layout = RecipeCardLayout.single().withFluids(4);
        assertEquals(4 * RecipeCardLayout.SLOT_SIZE, layout.contentWidth());
        // And the other way round.
        assertEquals(3 * RecipeCardLayout.SLOT_SIZE,
                RecipeCardLayout.shaped(3, 3).withFluids(1).contentWidth());
    }

    @Test
    void aPointInTheFluidRowNamesTheFluidSlot() {
        RecipeCardLayout layout = RecipeCardLayout.shaped(3, 2).withFluids(3);
        for (int i = 0; i < 3; i++) {
            assertEquals(i, layout.fluidIndexAt(layout.fluidSlotX(i) + 1, layout.fluidSlotY(i) + 1));
        }
        assertEquals(-1, layout.fluidIndexAt(-1, layout.fluidSlotY(0) + 1));
        assertEquals(-1, layout.fluidIndexAt(0, 0));
    }

    @Test
    void theInputHitTestIgnoresTheFluidRow() {
        // The two rows must not both claim the same pixel, or a tooltip names the wrong thing.
        RecipeCardLayout layout = RecipeCardLayout.shaped(3, 2).withFluids(1);
        int fluidY = layout.fluidSlotY(0) + 1;
        assertEquals(-1, layout.slotIndexAt(0, fluidY));
    }

    @Test
    void slotCountMatchesTheGrid() {
        assertEquals(6, RecipeCardLayout.shaped(3, 2).slotCount());
        assertEquals(6, RecipeCardLayout.shapeless(5).slotCount());
        assertEquals(10, RecipeCardLayout.sequence(7).slotCount());
    }
}
