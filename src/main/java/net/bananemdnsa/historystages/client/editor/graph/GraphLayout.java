package net.bananemdnsa.historystages.client.editor.graph;

import java.util.ArrayList;
import java.util.List;

/** Assigns x/y (graph-space) to the nodes of a {@link GraphModel}. Pure logic. */
public class GraphLayout {

    public static final float COLUMN_DX = 220f;   // horizontal distance focus -> side column
    public static final float ROW_DY = 60f;       // vertical spacing between stacked stages

    public static final float RING_R0 = 130f;     // radius of the first satellite ring
    public static final float RING_SPACING = 64f; // radius increase per additional ring
    public static final int RING_CAPACITY = 6;    // max satellites per ring
    public static final float ARC_SWEEP = 2.4f;   // angular width of each category arc (radians)

    private static final float DOWN = (float) (Math.PI / 2.0);   // +y
    private static final float UP = (float) (-Math.PI / 2.0);    // -y

    /** Mutates node.x / node.y in {@code model}. Focus is anchored at (0,0). */
    public static void apply(GraphModel model) {
        model.focus.x = 0f;
        model.focus.y = 0f;

        stackColumn(collect(model, GraphNode.Zone.LEFT, null), -COLUMN_DX);
        stackColumn(collect(model, GraphNode.Zone.RIGHT, null), COLUMN_DX);

        // Satellites split by category: details fan downward, triggers fan upward.
        List<GraphNode> details = collect(model, GraphNode.Zone.SATELLITE, GraphNode.Category.DETAIL);
        List<GraphNode> triggers = collect(model, GraphNode.Zone.SATELLITE, GraphNode.Category.TRIGGER);
        placeArc(details, DOWN);
        placeArc(triggers, UP);
    }

    private static List<GraphNode> collect(GraphModel model, GraphNode.Zone zone, GraphNode.Category cat) {
        List<GraphNode> out = new ArrayList<>();
        for (GraphNode n : model.nodes) {
            if (n.zone != zone) continue;
            if (cat != null && n.category != cat) continue;
            out.add(n);
        }
        return out;
    }

    private static void stackColumn(List<GraphNode> col, float x) {
        int n = col.size();
        if (n == 0) return;
        float startY = -((n - 1) * ROW_DY) / 2f;
        for (int i = 0; i < n; i++) {
            col.get(i).x = x;
            col.get(i).y = startY + i * ROW_DY;
        }
    }

    /** Places nodes ring by ring within an arc centered on {@code centerAngle}. */
    private static void placeArc(List<GraphNode> sats, float centerAngle) {
        int n = sats.size();
        if (n == 0) return;
        for (int i = 0; i < n; i++) {
            int ring = i / RING_CAPACITY;
            int idxInRing = i % RING_CAPACITY;
            int countInRing = Math.min(RING_CAPACITY, n - ring * RING_CAPACITY);
            float radius = RING_R0 + ring * RING_SPACING;
            float angle;
            if (countInRing == 1) {
                angle = centerAngle;
            } else {
                angle = centerAngle - ARC_SWEEP / 2f + ARC_SWEEP * (idxInRing / (float) (countInRing - 1));
            }
            sats.get(i).x = radius * (float) Math.cos(angle);
            sats.get(i).y = radius * (float) Math.sin(angle);
        }
    }
}
