package net.bananemdnsa.historystages.data.graph;

/**
 * A node's lock state as far as styling is concerned. Deliberately three values, not four:
 * a stage removed by the visibility filter is not drawn at all, so there is no {@code HIDDEN}
 * style to resolve.
 */
public enum NodeState {
    UNLOCKED,
    REACHABLE,
    LOCKED
}
