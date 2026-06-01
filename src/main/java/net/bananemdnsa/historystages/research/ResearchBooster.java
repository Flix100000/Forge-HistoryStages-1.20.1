package net.bananemdnsa.historystages.research;

import java.util.Comparator;

/**
 * Effect values for a single booster block. Reductions are fractions in [0.0, 0.9].
 *
 * <p>{@code minTier} + {@code tierMode} gate which pedestal tiers this booster
 * works under. See {@link TierMatcher#matches(int, int, TierMode)}.</p>
 */
public record ResearchBooster(double speedReduction, double costReduction,
                              int minTier, TierMode tierMode) {
    public static final ResearchBooster NONE = new ResearchBooster(0.0, 0.0, 1, TierMode.MIN);
    public static final double MAX_REDUCTION = 0.9;

    /** Speed wins; tie-break on cost. */
    public static final Comparator<ResearchBooster> BY_STRENGTH =
            Comparator.comparingDouble(ResearchBooster::speedReduction)
                    .thenComparingDouble(ResearchBooster::costReduction);

    public boolean hasSpeed() {
        return speedReduction > 0.0;
    }

    public boolean hasCost() {
        return costReduction > 0.0;
    }
}
