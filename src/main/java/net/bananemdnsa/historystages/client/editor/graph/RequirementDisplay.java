package net.bananemdnsa.historystages.client.editor.graph;

/**
 * How a single dependency requirement may honestly be presented in {@link GraphDetailScreen}.
 *
 * <p>The graph asks the server for a stage's requirements without a Research Pedestal, so
 * {@code DependencyChecker} runs with no deposited data. That is fine for anything it measures
 * off the live player, and useless for anything measured off a deposit: those come back as
 * {@code current = 0} and {@code fulfilled = false} no matter how much the player has actually
 * handed in. Showing "0/32" and an open circle there is not merely unhelpful, it is wrong.
 *
 * <p>So each requirement is sorted by what this view can actually stand behind. Deliberately
 * free of Minecraft types so the rules can be unit tested.
 */
public final class RequirementDisplay {

    private RequirementDisplay() {
    }

    public enum Kind {
        /**
         * Handed in at a pedestal, so neither its progress nor its fulfilment can be judged
         * here. Listed as a plain requirement — what is needed, not how far along you are.
         */
        DEPOSITED,
        /** Measured off the live player against a minimum, so a {@code current/required} holds. */
        COUNTED,
        /** Either done or not; the status glyph already says everything there is to say. */
        BINARY
    }

    /**
     * @param type       {@code DependencyResult.EntryResult.getType()}
     * @param canDeposit {@code EntryResult.canDeposit()}. For XP this is the only thing telling
     *                   a consuming requirement from a plain level check — and since this view
     *                   never sees a consuming one as fulfilled, it tracks "is consuming"
     *                   exactly.
     */
    public static Kind kindOf(String type, boolean canDeposit) {
        if ("item".equals(type)) return Kind.DEPOSITED;
        if ("xp_level".equals(type)) return canDeposit ? Kind.DEPOSITED : Kind.COUNTED;
        if ("entity_kill".equals(type) || "stat".equals(type)) return Kind.COUNTED;
        // Scoreboard is measured live, but carries a comparison operator: a requirement of
        // "score < 5" cannot be read as "3/5" without inverting its meaning. Its description
        // already spells the condition out, so the glyph is left to say the rest.
        return Kind.BINARY;
    }

    /** Whether a status glyph may be drawn for this requirement. */
    public static boolean showsStatus(Kind kind) {
        return kind != Kind.DEPOSITED;
    }

    /** Whether a {@code current/required} figure may be drawn for this requirement. */
    public static boolean showsAmount(Kind kind) {
        return kind == Kind.COUNTED;
    }
}
