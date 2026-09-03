package net.bananemdnsa.historystages.client.editor.recipe;

/**
 * The size and slot positions of one recipe card's input area.
 *
 * <p>Cards are as tall as their contents. A furnace recipe reserving a 3x3 grid wastes two
 * thirds of its height, and with the detail column only ~120px tall that is the difference
 * between seeing two recipes and seeing five.
 *
 * <p>Coordinates are relative to the top-left of the input area, so the renderer can place the
 * whole block and this class never learns where on screen it ended up. No Minecraft type appears
 * here, so it can be unit tested.
 */
public final class RecipeCardLayout {

    /** Input slot edge, matching the game's own 18px slots. */
    public static final int SLOT_SIZE = 18;

    /** The result slot is drawn larger than an input, the way the game frames an output. */
    public static final int RESULT_SIZE = 22;

    /** Breathing room above and below the input block. */
    public static final int VERTICAL_PADDING = 4;

    /** Columns a crafting grid wraps at. */
    private static final int CRAFTING_COLS = 3;

    /**
     * Columns an unrecognised type wraps at. Wider than a crafting grid because a machine can
     * take more inputs than a bench.
     */
    private static final int SEQUENCE_COLS = 5;

    /**
     * The most columns any layout here can produce. A panel that has to hold a card of unknown
     * shape sizes itself from this: the detail column is a fixed width, and a card wider than it
     * loses its arrow and its result slot to the clip rectangle without saying so.
     */
    public static final int MAX_COLS = SEQUENCE_COLS;

    private final int cols;
    private final int rows;
    private final int fluidCount;

    private RecipeCardLayout(int cols, int rows) {
        this(cols, rows, 0);
    }

    private RecipeCardLayout(int cols, int rows, int fluidCount) {
        this.cols = cols;
        this.rows = rows;
        this.fluidCount = fluidCount;
    }

    /** A shaped crafting recipe, keeping the pattern's own dimensions. */
    public static RecipeCardLayout shaped(int width, int height) {
        return new RecipeCardLayout(Math.max(1, width), Math.max(1, height));
    }

    /** A single-input recipe: furnace, blast furnace, smoker, campfire, stonecutter. */
    public static RecipeCardLayout single() {
        return new RecipeCardLayout(1, 1);
    }

    /** A shapeless crafting recipe, wrapped into a crafting grid's width. */
    public static RecipeCardLayout shapeless(int ingredientCount) {
        return wrapped(ingredientCount, CRAFTING_COLS);
    }

    /** Anything else — a modded type whose shape we cannot know. */
    public static RecipeCardLayout sequence(int ingredientCount) {
        return wrapped(ingredientCount, SEQUENCE_COLS);
    }

    private static RecipeCardLayout wrapped(int ingredientCount, int cols) {
        // Never more columns than there are ingredients: three inputs in a five-wide grid drew
        // two empty slots and made the card wider than it had any reason to be.
        int actualCols = Math.max(1, Math.min(cols, ingredientCount));
        int rows = Math.max(1, (ingredientCount + actualCols - 1) / actualCols);
        return new RecipeCardLayout(actualCols, rows);
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    /**
     * The same layout with a row of fluid slots under the input grid.
     *
     * <p>A separate row rather than extra slots in the grid: a fluid's position in the pattern is
     * not recoverable from any recipe, so putting one in a grid cell would claim a place we do
     * not know. Kept off the factories because the shape of a recipe and the fluids it mentions
     * come from two different sources — the shape from the recipe, the fluids from an index that
     * reads the recipe's serialised form.
     */
    public RecipeCardLayout withFluids(int count) {
        return new RecipeCardLayout(cols, rows, Math.max(0, count));
    }

    public int fluidCount() {
        return fluidCount;
    }

    /** Columns the fluid row uses, wrapped at the same width the input area is capped to. */
    public int fluidCols() {
        return Math.min(fluidCount, MAX_COLS);
    }

    public int fluidRows() {
        return fluidCount == 0 ? 0 : (fluidCount + MAX_COLS - 1) / MAX_COLS;
    }

    public int fluidHeight() {
        return fluidRows() * SLOT_SIZE;
    }

    /** X of fluid slot {@code i}, relative to the input area's top-left. */
    public int fluidSlotX(int i) {
        return (i % MAX_COLS) * SLOT_SIZE;
    }

    /** Y of fluid slot {@code i}, relative to the input area's top-left — below the input grid. */
    public int fluidSlotY(int i) {
        return inputHeight() + (i / MAX_COLS) * SLOT_SIZE;
    }

    /** Index of the fluid slot at a point, or {@code -1} when the point is not in the row. */
    public int fluidIndexAt(double x, double y) {
        if (fluidCount == 0) return -1;
        double localY = y - inputHeight();
        if (x < 0 || x >= fluidCols() * SLOT_SIZE || localY < 0 || localY >= fluidHeight()) {
            return -1;
        }
        int col = (int) (x / SLOT_SIZE);
        int row = (int) (localY / SLOT_SIZE);
        int i = row * MAX_COLS + col;
        return i < fluidCount ? i : -1;
    }

    /**
     * The widest of the input grid and the fluid row — what the card actually has to make room
     * for. Sizing off the input grid alone clipped whichever block was wider.
     */
    public int contentWidth() {
        return Math.max(inputWidth(), fluidCols() * SLOT_SIZE);
    }

    /** Slots the input area holds, including the empty ones in a shaped pattern. */
    public int slotCount() {
        return cols * rows;
    }

    public int inputWidth() {
        return cols * SLOT_SIZE;
    }

    public int inputHeight() {
        return rows * SLOT_SIZE;
    }

    /**
     * Total card height: the input block, or the result slot when the input is shorter than it,
     * plus padding. Without the result being taken into account a one-row card clips its own
     * output.
     */
    public int cardHeight() {
        return Math.max(inputHeight() + fluidHeight(), RESULT_SIZE) + VERTICAL_PADDING * 2;
    }

    /** X of slot {@code i}, counting left to right then down, relative to the input area. */
    public int slotX(int i) {
        return (i % cols) * SLOT_SIZE;
    }

    /** Y of slot {@code i}, counting left to right then down, relative to the input area. */
    public int slotY(int i) {
        return (i / cols) * SLOT_SIZE;
    }

    /**
     * Index of the slot containing a point, or {@code -1} when the point is outside the input
     * area. Coordinates are in the same frame {@link #slotX} and {@link #slotY} answer in:
     * relative to the input area's top-left.
     *
     * <p>The inverse of those two, and it lives here rather than in the renderer so the tooltip
     * cannot end up naming a different slot than the one drawn under the cursor.
     */
    public int slotIndexAt(double x, double y) {
        if (x < 0 || x >= inputWidth() || y < 0 || y >= inputHeight()) return -1;
        int col = (int) (x / SLOT_SIZE);
        int row = (int) (y / SLOT_SIZE);
        return row * cols + col;
    }
}
