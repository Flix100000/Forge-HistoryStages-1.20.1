package net.bananemdnsa.historystages.research;

public final class TierMatcher {
    private TierMatcher() {}

    public static boolean matches(int pedestalTier, int requiredTier, TierMode mode) {
        return switch (mode) {
            case MIN -> pedestalTier >= requiredTier;
            case EXACT -> pedestalTier == requiredTier;
        };
    }

    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> Integer.toString(tier);
        };
    }
}
