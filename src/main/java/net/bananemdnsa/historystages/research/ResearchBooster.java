package net.bananemdnsa.historystages.research;

import java.util.Comparator;

/**
 * Effect values for a single booster block. Reductions are fractions in [0.0, 0.9].
 *
 * <p>{@code minTier} and {@code tierMode} are reserved for sub-task 8c (tier-gating);
 * this port defaults them to {@code 1} / {@link TierMode#MIN} and the runtime logic
 * does not consult them yet.</p>
 */
public record ResearchBooster(double speedReduction, double costReduction, int minTier, TierMode tierMode) {

    public static final ResearchBooster NONE = new ResearchBooster(0.0, 0.0, 1, TierMode.MIN);
    public static final double MAX_REDUCTION = 0.9;

    /** Sort by combined strength (speed + cost). */
    public static final Comparator<ResearchBooster> BY_STRENGTH =
            Comparator.comparingDouble((ResearchBooster b) -> b.speedReduction + b.costReduction).reversed();

    public ResearchBooster(double speedReduction, double costReduction) {
        this(speedReduction, costReduction, 1, TierMode.MIN);
    }

    public boolean hasSpeed() {
        return speedReduction > 0.0;
    }

    public boolean hasCost() {
        return costReduction > 0.0;
    }
}
