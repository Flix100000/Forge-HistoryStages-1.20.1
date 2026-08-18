package net.bananemdnsa.historystages.data.graph;

/**
 * A position on the stage graph, in grid cells — never pixels. {@code x} is the dependency
 * layer, {@code y} the row within it. Zoom and pan never touch these values.
 */
public record GraphPos(int x, int y) {

    public static GraphPos of(int x, int y) {
        return new GraphPos(x, y);
    }
}
