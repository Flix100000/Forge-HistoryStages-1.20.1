package net.bananemdnsa.historystages.research;

/**
 * How a booster's minimum-tier requirement is interpreted. Full gating logic
 * lands in sub-task 8c — this enum is kept here so {@link ResearchBooster}'s
 * signature is forward-compatible.
 */
public enum TierMode {
    MIN,
    EXACT
}
