package net.bananemdnsa.historystages.api.editor;

/**
 * The content rectangle and the cursor, for the input hooks on {@link EditorTab}.
 *
 * <p>The same rectangle {@link TabRenderContext} carries, with the scroll already applied, so a
 * hit test in an input hook and the drawing it refers to agree by construction.
 */
public record TabInputContext(int x, int y, int width, int clipTop, int clipBottom,
                              double mouseX, double mouseY) {
}
