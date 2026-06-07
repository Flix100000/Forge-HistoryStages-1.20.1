package net.bananemdnsa.historystages.data.auto.conditions;

/**
 * A single discovery condition that, when matched against an event payload,
 * counts toward unlocking an auto-stage.
 *
 * <p>Implementations must produce a stable {@link #signature()} hash derived
 * from their type and field values. The hash is the identity used for
 * progress storage (NOT the JSON array index), so that editing a stage's
 * trigger list does not corrupt player progress.</p>
 *
 * <p>Note: this interface lives in the {@code conditions} subpackage rather
 * than {@code data.auto} so that the JLS sealed-permits-same-package rule
 * (for the unnamed module) is satisfied.</p>
 */
public sealed interface TriggerCondition permits
        BiomeTrigger,
        StructureTrigger,
        DimensionTrigger,
        ItemTrigger,
        EntityTrigger,
        BlockPlaceTrigger,
        BlockBreakTrigger,
        AdvancementTrigger,
        PlaytimeTrigger {

    /** Discriminator string used in JSON ({@code "biome"}, {@code "entity"}, ...). */
    String type();

    /**
     * Stable 64-bit identity hash. Must depend ONLY on type and value fields,
     * never on memory addresses, JSON array position, or runtime state.
     */
    long signature();

    /** FNV-1a 64-bit prime — used to mix the type hash and the value hash. */
    long FNV_PRIME_64 = 1099511628211L;

    /**
     * Default signature derivation: stable mix of the trigger's type discriminator and a
     * single string value (typically the id). Records with multiple value components
     * should pass a composite string (e.g. {@code id + "|" + subMode}) or override
     * {@link #signature()} directly.
     *
     * <p>Relies on {@link String#hashCode()}, which is contractually stable per the
     * Java Language Specification — safe to persist as a progress identity hash.</p>
     */
    default long defaultSignature(String value) {
        long h = type().hashCode() & 0xFFFFFFFFL;
        return h * FNV_PRIME_64 ^ (String.valueOf(value).hashCode() & 0xFFFFFFFFL);
    }
}
