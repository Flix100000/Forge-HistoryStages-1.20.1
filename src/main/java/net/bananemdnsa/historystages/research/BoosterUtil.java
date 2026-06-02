package net.bananemdnsa.historystages.research;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class BoosterUtil {
    private BoosterUtil() {}

    /**
     * Compute the reduced item count for a dependency. Always rounds up,
     * minimum 1 if the original was at least 1.
     */
    public static int effectiveCount(int original, double costReduction) {
        if (costReduction <= 0.0 || original <= 0) return original;
        return Math.max(1, (int) Math.ceil(original * (1.0 - costReduction)));
    }

    /** Speed multiplier shown to the player, e.g. 1/(1-0.25) = 1.333. */
    public static double speedMultiplier(double speedReduction) {
        if (speedReduction <= 0.0) return 1.0;
        return 1.0 / (1.0 - Math.min(ResearchBooster.MAX_REDUCTION, speedReduction));
    }

    /** Convert a [0.0, 0.9] reduction to its rounded percentage (0–90). */
    public static int percent(double reduction) {
        if (reduction <= 0.0) return 0;
        return (int) Math.round(reduction * 100);
    }

    /** Format the multiplier as "x1.33" using two decimals. */
    public static String formatMultiplier(double speedReduction) {
        return String.format("×%.2f", speedMultiplier(speedReduction));
    }

    /**
     * Build the standard "Research Booster" description lines for a booster — one
     * header line plus optional speed / cost lines. Shared by the tooltip event,
     * Jade integration, and the JEI/EMI info recipes so the formatting stays
     * consistent across all surfaces.
     */
    public static List<Component> describe(ResearchBooster booster) {
        List<Component> lines = new ArrayList<>(3);
        lines.add(Component.translatable("jei.historystages.research_booster.header"));
        if (booster.hasSpeed()) {
            lines.add(Component.translatable(
                    "jei.historystages.research_booster.speed", formatMultiplier(booster.speedReduction())));
        }
        if (booster.hasCost()) {
            lines.add(Component.translatable(
                    "jei.historystages.research_booster.cost", percent(booster.costReduction())));
        }
        return lines;
    }
}
