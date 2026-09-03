package net.bananemdnsa.historystages.data.lock;

/**
 * Decides what a staleness signal should cause.
 *
 * <p>Two signals arrive and they are not the same thing. A recipe reload changes what the index
 * would contain, so it has to be read again. A stage change does not touch a single recipe — it
 * only changes whether the index is worth keeping.
 *
 * <p>They used to share one flag, which was harmless while the index existed only for packs that
 * gate a fluid. The editor keeps it alive now, and saving a stage in the editor would otherwise
 * re-encode every recipe in the pack — once per save.
 *
 * <p>Pure on purpose: the rule is small, easy to get subtly wrong, and impossible to notice once
 * it is wrong. A needless re-scan looks like a stutter, not like a bug.
 */
public final class FluidIndexStaleness {

    public enum Action {
        /** Read every recipe again. */
        REBUILD,
        /** Throw the index away; nothing wants it. */
        DROP,
        /** Leave it exactly as it is. */
        NOTHING
    }

    private FluidIndexStaleness() {
    }

    /**
     * @param recipesChanged   the recipe list was reloaded
     * @param relevanceChanged the stages changed, or someone started or stopped wanting an index
     * @param built            an index exists right now
     * @param wanted           someone wants one: a stage gates a fluid, or the editor asked
     */
    public static Action decide(boolean recipesChanged, boolean relevanceChanged,
                                boolean built, boolean wanted) {
        if (!recipesChanged && !relevanceChanged) return Action.NOTHING;
        if (!wanted) return built ? Action.DROP : Action.NOTHING;
        if (recipesChanged) return Action.REBUILD;
        return built ? Action.NOTHING : Action.REBUILD;
    }
}
