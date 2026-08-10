package net.bananemdnsa.historystages.data.lock;

/**
 * One generation restriction for a structure id or {@code #tag}.
 *
 * <p>{@code max} is how many instances may generate during {@link #phase}. Zero means none, which
 * is what the old boolean {@code block_generation} entry expressed — {@code WHILE_LOCKED} with
 * {@code max 0} is exactly the legacy behaviour, and {@link #isLegacyBlock()} lets the adapter
 * write those back as bare strings.
 *
 * <p>"Unlimited" is not a value here; it is the absence of a rule.
 */
public record StructureGenerationRule(String id, GenerationPhase phase, int max, boolean resetOnRelock) {

    public StructureGenerationRule {
        phase = phase != null ? phase : GenerationPhase.WHILE_LOCKED;
        max = Math.max(0, max);
    }

    public static StructureGenerationRule blockEntirely(String id) {
        return new StructureGenerationRule(id, GenerationPhase.WHILE_LOCKED, 0, false);
    }

    public boolean isLegacyBlock() {
        return phase == GenerationPhase.WHILE_LOCKED && max == 0 && !resetOnRelock;
    }
}
