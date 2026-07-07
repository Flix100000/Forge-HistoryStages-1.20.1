package net.bananemdnsa.historystages.client.editor.graph;

/** A directed connection between two nodes. */
public class GraphEdge {
    public final GraphNode from;
    public final GraphNode to;
    /** True for OR-group dependencies (drawn dashed); false for AND (solid). */
    public final boolean dashed;

    public GraphEdge(GraphNode from, GraphNode to, boolean dashed) {
        this.from = from;
        this.to = to;
        this.dashed = dashed;
    }
}
