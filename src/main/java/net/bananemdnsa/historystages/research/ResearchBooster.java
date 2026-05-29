package net.bananemdnsa.historystages.research;

/**
 * Effect values for a single booster block. Reductions are fractions in [0.0, 0.9].
 */
public record ResearchBooster(double speedReduction, double costReduction) {
    public static final ResearchBooster NONE = new ResearchBooster(0.0, 0.0);
    public static final double MAX_REDUCTION = 0.9;

    public boolean hasSpeed() {
        return speedReduction > 0.0;
    }

    public boolean hasCost() {
        return costReduction > 0.0;
    }
}
