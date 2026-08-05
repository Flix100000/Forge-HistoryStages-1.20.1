package net.bananemdnsa.historystages.data.graph;

/**
 * One stage's per-state style overrides, layered on top of its all-states {@code style} block.
 *
 * <p>Three named fields rather than a {@code Map<NodeState, StageStyle>}: Gson keys an enum map
 * by {@code name()}, which would put {@code UNLOCKED} into a hand-edited file unless
 * {@link NodeState}'s constants grew {@code @SerializedName} annotations — and that enum is used
 * in several places that have nothing to do with this file. Named fields also let Gson drop a
 * mistyped key on its own instead of carrying it around.
 */
public class StateStyles {

    /** Each may be null, meaning "nothing overridden for this state". */
    public StageStyle unlocked;
    public StageStyle reachable;
    public StageStyle locked;

    public StageStyle get(NodeState state) {
        if (state == null) return null;
        return switch (state) {
            case UNLOCKED -> unlocked;
            case REACHABLE -> reachable;
            case LOCKED -> locked;
        };
    }

    public void set(NodeState state, StageStyle style) {
        if (state == null) return;
        switch (state) {
            case UNLOCKED -> unlocked = style;
            case REACHABLE -> reachable = style;
            case LOCKED -> locked = style;
        }
    }

    public boolean isEmpty() {
        return isBlank(unlocked) && isBlank(reachable) && isBlank(locked);
    }

    private static boolean isBlank(StageStyle style) {
        return style == null || style.isEmpty();
    }

    public StateStyles copy() {
        StateStyles out = new StateStyles();
        out.unlocked = unlocked == null ? null : unlocked.copy();
        out.reachable = reachable == null ? null : reachable.copy();
        out.locked = locked == null ? null : locked.copy();
        return out;
    }
}
