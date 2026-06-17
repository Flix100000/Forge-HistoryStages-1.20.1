package net.bananemdnsa.historystages.client.editor.graph;

import java.util.ArrayList;
import java.util.List;

/** A single node in the focus graph: a stage, a dependency detail, or a trigger. */
public class GraphNode {

    /** Visual/logical kind of node. */
    public enum Type {
        STAGE_GLOBAL,
        STAGE_INDIVIDUAL,
        DETAIL_ITEM,
        DETAIL_XP,
        DETAIL_ADVANCEMENT,
        DETAIL_KILL,
        DETAIL_STAT,
        DETAIL_SCOREBOARD,
        TRIGGER
    }

    /** Shape category — drives the rendered shape (circle / diamond / hexagon). */
    public enum Category { STAGE, DETAIL, TRIGGER }

    /** Which column/region the layout places this node in. */
    public enum Zone { LEFT, CENTER, RIGHT, SATELLITE }

    /** Stable id: the stage id for stage nodes, or a synthetic id for detail/trigger nodes. */
    public final String id;
    public final Type type;
    public final Category category;
    public final Zone zone;
    /** Short label/letter drawn on or under the node ("" when an icon is shown instead). */
    public final String label;
    /** Multi-line tooltip shown on hover. */
    public final List<String> tooltip;
    /** Lock state — only meaningful for STAGE_GLOBAL (green/grey). */
    public final boolean unlocked;
    /** Item/block id to render as a 16x16 sprite, or null. */
    public final String itemIcon;
    /** Entity id to render as a spinning 3D model, or null. */
    public final String entityId;

    // Assigned by GraphLayout; in graph-space (pre pan/zoom).
    public float x;
    public float y;

    /** Full constructor. */
    public GraphNode(String id, Type type, Category category, Zone zone, String label,
                     boolean unlocked, String itemIcon, String entityId, List<String> tooltip) {
        this.id = id;
        this.type = type;
        this.category = category;
        this.zone = zone;
        this.label = label != null ? label : "";
        this.unlocked = unlocked;
        this.itemIcon = (itemIcon != null && !itemIcon.isEmpty()) ? itemIcon : null;
        this.entityId = (entityId != null && !entityId.isEmpty()) ? entityId : null;
        this.tooltip = tooltip != null ? tooltip : new ArrayList<>();
    }

    /** Back-compat constructor — derives the category from the type, no icon. */
    public GraphNode(String id, Type type, Zone zone, String label, boolean unlocked, List<String> tooltip) {
        this(id, type, categoryFor(type), zone, label, unlocked, null, null, tooltip);
    }

    private static Category categoryFor(Type t) {
        if (t == Type.STAGE_GLOBAL || t == Type.STAGE_INDIVIDUAL) return Category.STAGE;
        if (t == Type.TRIGGER) return Category.TRIGGER;
        return Category.DETAIL;
    }

    public boolean isStage() {
        return type == Type.STAGE_GLOBAL || type == Type.STAGE_INDIVIDUAL;
    }
}
